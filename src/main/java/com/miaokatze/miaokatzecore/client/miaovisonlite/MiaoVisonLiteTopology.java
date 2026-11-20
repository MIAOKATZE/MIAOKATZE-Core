// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Background neighbor topology for detached, immutable region data. Publication and GL stay with the caller. */
public final class MiaoVisonLiteTopology<K, C, F> implements AutoCloseable {

    public static final int KNOWN = 1, VISIBLE = 2, OPAQUE = 4;

    private static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final int WORKERS = Math.max(
        1,
        Math.min(
            2,
            Runtime.getRuntime()
                .availableProcessors() - 1));
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        WORKERS,
        WORKERS,
        30,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(WORKERS * 2),
        work -> {
            Thread thread = new Thread(work, "MiaoVisonLite topology");
            thread.setDaemon(true);
            return thread;
        });

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    /** All methods use immutable inputs and may be called on the worker. */
    public interface View<C, F> {

        C at(int x, int y, int z);

        int x(C cell);

        int y(C cell);

        int z(C cell);

        int flags(C cell);

        /** Returns the projected-geometry input, or null if this visible cell adds no geometry. */
        F faceInput(C cell, int exposed, int sourceOrdinal);
    }

    public static final class Run<C, F> {

        public final boolean ordinary;
        public final int end;
        public final List<C> visible;
        public final List<F> faces;

        private Run(boolean ordinary, int end, List<C> visible, List<F> faces) {
            this.ordinary = ordinary;
            this.end = end;
            this.visible = Collections.unmodifiableList(visible);
            this.faces = Collections.unmodifiableList(faces);
        }
    }

    private final Map<K, List<Run<C, F>>> ready = new ConcurrentHashMap<>();
    private final List<FutureTask<Void>> tasks = new ArrayList<>();
    private final Consumer<FutureTask<Void>> remove;
    private volatile boolean closed;

    private MiaoVisonLiteTopology(Consumer<FutureTask<Void>> remove) {
        this.remove = remove;
    }

    private FutureTask<Void> task(Iterator<Map.Entry<K, List<C>>> source, View<C, F> view) {
        return new FutureTask<>(() -> {
            while (!cancelled()) {
                Map.Entry<K, List<C>> region;
                synchronized (source) {
                    if (!source.hasNext()) break;
                    region = source.next();
                }
                List<Run<C, F>> runs = prepareRegion(region.getValue(), view, this::cancelled);
                if (runs == null) break;
                publish(region.getKey(), runs);
            }
            return null;
        });
    }

    private boolean cancelled() {
        return closed || Thread.currentThread()
            .isInterrupted();
    }

    private synchronized void publish(K key, List<Run<C, F>> runs) {
        if (!closed) ready.put(key, runs);
    }

    /** Uses the shared, bounded topology workers, independently of the consumer's asset I/O. */
    public static <K, C, F> MiaoVisonLiteTopology<K, C, F> start(Map<K, List<C>> regions, View<C, F> view) {
        return start(regions, view, MiaoVisonLiteTopology::schedule, EXECUTOR::remove, WORKERS);
    }

    private static boolean schedule(FutureTask<Void> task) {
        try {
            EXECUTOR.execute(task);
            return true;
        } catch (RejectedExecutionException busy) {
            return false;
        }
    }

    /** The scheduler can decline work and leave the caller's normal render-thread cursor in charge. */
    public static <K, C, F> MiaoVisonLiteTopology<K, C, F> start(Map<K, List<C>> regions, View<C, F> view,
        Predicate<FutureTask<Void>> submit, Consumer<FutureTask<Void>> remove) {
        return start(regions, view, submit, remove, 1);
    }

    private static <K, C, F> MiaoVisonLiteTopology<K, C, F> start(Map<K, List<C>> regions, View<C, F> view,
        Predicate<FutureTask<Void>> submit, Consumer<FutureTask<Void>> remove, int workers) {
        if (regions.isEmpty()) return null;
        MiaoVisonLiteTopology<K, C, F> topology = new MiaoVisonLiteTopology<>(remove);
        Iterator<Map.Entry<K, List<C>>> source = regions.entrySet()
            .iterator();
        for (int worker = 0; worker < Math.min(workers, regions.size()); worker++) {
            FutureTask<Void> task = topology.task(source, view);
            if (submit.test(task)) topology.tasks.add(task);
            else break;
        }
        return topology.tasks.isEmpty() ? null : topology;
    }

    public List<Run<C, F>> take(K region) {
        return closed ? null : ready.remove(region);
    }

    public boolean done() {
        for (FutureTask<Void> task : tasks) if (!task.isDone()) return false;
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            for (FutureTask<Void> task : tasks) {
                task.cancel(true);
                remove.accept(task);
            }
        } finally {
            ready.clear();
        }
    }

    /** Admits ordinary cells in their original order. Unknown cells and their neighbors use the caller's cursor. */
    public static <C, F> List<Run<C, F>> prepareRegion(List<C> cells, View<C, F> view) {
        return prepareRegion(cells, view, () -> false);
    }

    private static <C, F> List<Run<C, F>> prepareRegion(List<C> cells, View<C, F> view, BooleanSupplier cancelled) {
        List<Run<C, F>> runs = new ArrayList<>();
        List<C> visible = Collections.emptyList();
        List<F> faces = Collections.emptyList();
        boolean ordinary = false;
        for (int i = 0; i < cells.size(); i++) {
            if ((i & 127) == 0 && cancelled.getAsBoolean()) return null;
            C cell = cells.get(i);
            int flags = view.flags(cell);
            int exposed = (flags & KNOWN) == 0 ? -1 : (flags & VISIBLE) != 0 ? exposed(cell, view) : 0;
            if (i == 0 || ordinary != (exposed >= 0)) {
                if (i != 0) runs.add(new Run<>(ordinary, i, visible, faces));
                ordinary = exposed >= 0;
                visible = ordinary ? new ArrayList<>() : Collections.emptyList();
                faces = ordinary ? new ArrayList<>() : Collections.emptyList();
            }
            if (!ordinary || (flags & VISIBLE) == 0) continue;
            visible.add(cell);
            F input = view.faceInput(cell, exposed, i + 1);
            if (input != null) faces.add(input);
        }
        if (!cells.isEmpty()) runs.add(new Run<>(ordinary, cells.size(), visible, faces));
        return Collections.unmodifiableList(runs);
    }

    private static <C, F> int exposed(C cell, View<C, F> view) {
        int exposed = 0;
        int x = view.x(cell), y = view.y(cell), z = view.z(cell);
        for (int side = 0; side < NORMALS.length; side++) {
            int[] normal = NORMALS[side];
            C neighbor = view.at(x + normal[0], y + normal[1], z + normal[2]);
            int flags = neighbor == null ? KNOWN : view.flags(neighbor);
            if ((flags & KNOWN) == 0) return -1;
            if ((flags & OPAQUE) == 0) exposed |= 1 << side;
        }
        return exposed;
    }
}
