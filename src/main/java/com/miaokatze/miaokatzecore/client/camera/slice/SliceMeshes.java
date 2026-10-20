package com.miaokatze.miaokatzecore.client.camera.slice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** RC2 boundary-only native remeshing. All scheduler methods run on the client render thread. */
public final class SliceMeshes {

    private static final int REBUILDS_PER_BUDGET = 4;
    private static final long BUDGET_INTERVAL_NANOS = 16_000_000L;
    private static final Set<RenderSection> pending = identitySet();
    private static final Set<RenderSection> touched = identitySet();
    private static final Set<RenderSection> enqueued = identitySet();
    private static final ArrayDeque<RenderSection> queue = new ArrayDeque<>();
    private static final IdentityHashMap<RenderSection, Snapshot> installed = new IdentityHashMap<>();
    private static final IdentityHashMap<RenderSection, Snapshot> building = new IdentityHashMap<>();
    private static final ThreadLocal<FaceScope> FACE = new ThreadLocal<>();
    private static RenderSectionManager manager;
    private static World managerWorld;
    private static Snapshot target;
    private static HeightSlice displayTarget = HeightSlice.disabled();
    private static long displayRevision = -1;
    private static HeightSlice committed = HeightSlice.disabled();
    private static long committedRevision = -1, geometryRevision, lastBudget, managerGeneration;
    private static boolean released = true;
    private static String failure;
    private static Status lastStatus = new Status(
        State.DISABLED,
        true,
        true,
        false,
        null,
        -1,
        HeightSlice.disabled(),
        0);

    private SliceMeshes() {}

    public enum State {
        DISABLED,
        PREVIEW,
        APPLYING,
        READY,
        RESTORING,
        UNSUPPORTED
    }

    public static final class Status {

        private final State state;
        private final boolean pickReady, supported, retryableUnavailable;
        private final String reason;
        private final long committedRevision, geometryRevision;
        private final HeightSlice committed;

        private Status(State state, boolean pickReady, boolean supported, boolean retryableUnavailable, String reason,
            long revision, HeightSlice committed, long geometryRevision) {
            this.state = state;
            this.pickReady = pickReady;
            this.supported = supported;
            this.retryableUnavailable = retryableUnavailable;
            this.reason = reason;
            this.committedRevision = revision;
            this.committed = committed;
            this.geometryRevision = geometryRevision;
        }

        public State state() {
            return state;
        }

        public boolean pickReady() {
            return pickReady;
        }

        public boolean supported() {
            return supported;
        }

        public boolean retryableUnavailable() {
            return retryableUnavailable;
        }

        public String unavailableReason() {
            return reason;
        }

        public long committedRevision() {
            return committedRevision;
        }

        public HeightSlice committed() {
            return committed;
        }

        public long geometryRevision() {
            return geometryRevision;
        }
    }

    /** This exact immutable object follows a task through worker and deferred main-thread completion. */
    public static final class Snapshot {

        private final RenderSectionManager manager;
        private final World world;
        private final long ownerEpoch, revision;
        private final HeightSlice slice;

        private Snapshot(RenderSectionManager manager, World world, long epoch, long revision, HeightSlice slice) {
            this.manager = manager;
            this.world = world;
            this.ownerEpoch = epoch;
            this.revision = revision;
            this.slice = slice;
        }
    }

    public static Status update(Minecraft mc, long ownerEpoch, HeightSlice requested, long revision) {
        if (requested == null) throw new NullPointerException("requested");
        if (mc.theWorld != managerWorld) {
            // Old sections belong to the old renderer; never schedule them into a new world.
            forgetTarget();
            return publish(
                requested.enabled() ? State.UNSUPPORTED : State.DISABLED,
                !requested.enabled(),
                !requested.enabled(),
                requested.enabled() ? "RC2 section manager is not attached to this world" : null);
        }
        released = false;
        if (manager == null) {
            return publish(
                requested.enabled() ? State.UNSUPPORTED : State.DISABLED,
                !requested.enabled(),
                !requested.enabled(),
                requested.enabled() ? "RC2 boundary mesher is unavailable" : null);
        }
        if (target == null && !requested.enabled()) {
            displayTarget = requested;
            displayRevision = revision;
            committed = requested;
            committedRevision = revision;
            return publish(State.DISABLED, true, true, null);
        }
        if (target == null || target.ownerEpoch != ownerEpoch
            || !SliceMeshRules.sameGeometry(target.slice, requested)) {
            changeTarget(ownerEpoch, requested, revision, mc);
        } else {
            // Geometry-equivalent requests keep the exact stamp, queues and in-flight tasks alive.
            displayTarget = requested;
            displayRevision = revision;
        }
        return tick(mc);
    }

    public static Status tick(Minecraft mc) {
        if (mc.theWorld != managerWorld) {
            forgetTarget();
            return publish(State.DISABLED, true, true, null);
        }
        if (target == null) return publish(State.DISABLED, true, true, null);
        pending.removeIf(RenderSection::isDisposed);
        touched.removeIf(RenderSection::isDisposed);
        installed.keySet()
            .removeIf(RenderSection::isDisposed);
        building.keySet()
            .removeIf(RenderSection::isDisposed);
        long now = System.nanoTime();
        if (now - lastBudget >= BUDGET_INTERVAL_NANOS) {
            lastBudget = now;
            for (int i = 0; i < REBUILDS_PER_BUDGET && !queue.isEmpty(); i++) {
                RenderSection section = queue.removeFirst();
                enqueued.remove(section);
                if (!pending.contains(section) || section.isDisposed()) continue;
                CancellationToken token = section.getBuildCancellationToken();
                if (token != null) {
                    token.setCancelled();
                    section.setBuildCancellationToken(null);
                }
                manager.scheduleRebuild(section.getChunkX(), section.getChunkY(), section.getChunkZ(), true);
            }
        }
        return refreshStatus();
    }

    public static Status status() {
        return lastStatus;
    }

    public static long managerGeneration() {
        return managerGeneration;
    }

    public static void release() {
        if (released) return;
        released = true;
        if (target != null && (target.slice.enabled() || !pending.isEmpty() || !touched.isEmpty())) {
            // Do not read Minecraft here; lifecycle tick drains the asynchronous restoration.
            changeTarget(0, HeightSlice.disabled(), -1, null);
            refreshStatus();
        } else {
            displayTarget = HeightSlice.disabled();
            displayRevision = -1;
            committed = displayTarget;
            committedRevision = -1;
            refreshStatus();
        }
    }

    public static void managerCreated(World world, RenderSectionManager created) {
        if (manager == created) return;
        managerGeneration++;
        forgetTarget();
        manager = created;
        managerWorld = world;
    }

    public static void managerDestroyed(RenderSectionManager destroyed) {
        if (manager != destroyed) return;
        managerGeneration++;
        forgetTarget();
        manager = null;
        managerWorld = null;
    }

    public static RenderSection sectionAdded(RenderSectionManager source, RenderSection section) {
        if (source == manager && target != null
            && target.slice.enabled()
            && SliceMeshRules.affectsSection(target.slice, section.getOriginY())) {
            pending.add(section);
            enqueue(section);
            refreshStatus();
        }
        return section;
    }

    /** Called only from the render-thread factory, before a task is submitted to any executor. */
    public static void taskCreated(RenderSectionManager source, RenderSection render, Object task) {
        if (source != manager) return;
        Snapshot snapshot = target;
        if (task instanceof SliceMeshTask) {
            ((SliceMeshTask) task).core$setSliceSnapshot(snapshot);
            building.put(render, snapshot);
            if (snapshot != null && snapshot.slice.enabled()
                && SliceMeshRules.affectsSection(snapshot.slice, render.getOriginY())) {
                pending.add(render);
                refreshStatus();
            }
        } else if (task != null && snapshot != null && snapshot.slice.enabled()) {
            failure = "Active RC2 task provider does not use the Angelica native mesher";
            refreshStatus();
        }
    }

    /** Empty sections have no task; their real empty output is still acknowledged at upload. */
    public static ChunkBuildOutput tagEmpty(RenderSectionManager source, ChunkBuildOutput output) {
        if (source == manager) {
            tag(output, target);
            building.put(output.render, target);
        }
        return output;
    }

    /** A sort operates on installed geometry, so it must retain that geometry's stamp, not the slider's stamp. */
    public static void sortTaskCreated(RenderSectionManager source, RenderSection render, Object task) {
        if (source == manager && task instanceof SliceMeshTask) {
            ((SliceMeshTask) task).core$setSliceSnapshot(installed.get(render));
        }
    }

    /** Worker-safe: only the task's frozen value is copied, with no world or UI reads. */
    public static <T extends ChunkTaskOutput> T tag(T output, Snapshot snapshot) {
        ((SliceMeshOutput) output).core$setSliceSnapshot(snapshot);
        return output;
    }

    /** Before native deduplication and GPU upload. Removed results are deleted exactly once here. */
    public static void filter(RenderSectionManager source,
        ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        Iterator<ChunkJobResult.Success<? extends ChunkTaskOutput>> iterator = results.iterator();
        while (iterator.hasNext()) {
            ChunkTaskOutput output = iterator.next()
                .output();
            Snapshot stamp = ((SliceMeshOutput) output).core$getSliceSnapshot();
            boolean required = source == manager && (pending.contains(output.render) || touched.contains(output.render)
                || target != null && SliceMeshRules.affectsSection(target.slice, output.render.getOriginY()));
            boolean obsolete = stamp != null && (stamp.manager != source || stamp.world != managerWorld
                || source != manager
                || stamp != target
                    && (required || SliceMeshRules.affectsSection(stamp.slice, output.render.getOriginY())));
            // Untagged work can predate ownership. It cannot overwrite a required boundary rebuild.
            if (stamp == null && required) obsolete = true;
            if (!obsolete) continue;
            iterator.remove();
            output.delete();
            if (source == manager && target != null
                && !output.render.isDisposed()
                && installed.get(output.render) != target) {
                pending.add(output.render);
                if (building.get(output.render) != target) enqueue(output.render);
            }
        }
        refreshStatus();
    }

    /** Only called after the actual native uploadMeshes call returns successfully. */
    public static void uploaded(RenderSectionManager source,
        Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        if (source != manager) return;
        boolean changed = false;
        for (ChunkJobResult.Success<? extends ChunkTaskOutput> result : results) {
            ChunkTaskOutput output = result.output();
            if (!(output instanceof ChunkBuildOutput)) continue;
            if (output.render.isDisposed()) continue;
            changed = true;
            Snapshot stamp = ((SliceMeshOutput) output).core$getSliceSnapshot();
            if (building.get(output.render) == stamp) building.remove(output.render);
            if (stamp == null) installed.remove(output.render);
            else installed.put(output.render, stamp);
            if (stamp == null || stamp != target) continue;
            pending.remove(output.render);
            if (SliceMeshRules.affectsSection(stamp.slice, output.render.getOriginY())) touched.add(output.render);
            else touched.remove(output.render);
        }
        if (changed) geometryRevision++;
        refreshStatus();
    }

    public static FaceScope enter(Snapshot snapshot, IBlockAccess access, int x, int y, int z) {
        FaceScope scope = new FaceScope(FACE.get(), snapshot, access, x, y, z);
        FACE.set(scope);
        return scope;
    }

    /** Only native rendering sees this view; no world state, light engine or block collision is changed. */
    public static IBlockAccess renderAccess(Snapshot snapshot, IBlockAccess original, int y) {
        return snapshot != null && snapshot.slice.enabled()
            && snapshot.slice.containsBlockY(y)
            && SliceMeshRules.affectsSection(snapshot.slice, y) ? new SliceBlockAccess(original, snapshot.slice, y)
                : original;
    }

    public static boolean opensFace(IBlockAccess access, int x, int y, int z, int side) {
        FaceScope scope = FACE.get();
        return scope != null && scope.snapshot != null
            && scope.access == access
            && scope.x == x
            && scope.z == z
            && SliceMeshRules.opensFace(scope.snapshot.slice, scope.y, y, side);
    }

    public static final class FaceScope implements AutoCloseable {

        private final FaceScope previous;
        private final Snapshot snapshot;
        private final IBlockAccess access;
        private final int x, y, z;

        private FaceScope(FaceScope previous, Snapshot snapshot, IBlockAccess access, int x, int y, int z) {
            this.previous = previous;
            this.snapshot = snapshot;
            this.access = access;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void close() {
            if (previous == null) FACE.remove();
            else FACE.set(previous);
        }
    }

    private static void changeTarget(long epoch, HeightSlice slice, long revision, Minecraft mc) {
        HeightSlice old = target == null ? HeightSlice.disabled() : target.slice;
        target = new Snapshot(manager, managerWorld, epoch, revision, slice);
        displayTarget = slice;
        displayRevision = revision;
        failure = null;
        queue.clear();
        enqueued.clear();
        pending.addAll(touched);
        for (RenderSection section : manager.getAllRenderSections()) {
            if (!section.isDisposed() && (SliceMeshRules.affectsSection(old, section.getOriginY())
                || SliceMeshRules.affectsSection(slice, section.getOriginY()))) pending.add(section);
        }
        ArrayList<RenderSection> ordered = new ArrayList<>(pending);
        final double px = mc != null && mc.renderViewEntity != null ? mc.renderViewEntity.posX : 0;
        final double py = mc != null && mc.renderViewEntity != null ? mc.renderViewEntity.posY : 0;
        final double pz = mc != null && mc.renderViewEntity != null ? mc.renderViewEntity.posZ : 0;
        ordered
            .sort(
                Comparator
                    .<RenderSection>comparingInt(
                        section -> manager
                            .isSectionVisible(section.getChunkX(), section.getChunkY(), section.getChunkZ()) ? 0 : 1)
                    .thenComparingDouble(section -> distance(section, px, py, pz)));
        for (RenderSection section : ordered) enqueue(section);
        refreshStatus();
    }

    private static double distance(RenderSection section, double x, double y, double z) {
        double dx = section.getCenterX() - x, dy = section.getCenterY() - y, dz = section.getCenterZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void enqueue(RenderSection section) {
        if (!section.isDisposed() && enqueued.add(section)) queue.addLast(section);
    }

    private static Status refreshStatus() {
        if (target == null) return publish(State.DISABLED, true, true, null);
        if (failure != null) return publish(State.UNSUPPORTED, pending.isEmpty() && touched.isEmpty(), false, failure);
        if (!pending.isEmpty())
            return publish(target.slice.enabled() ? State.APPLYING : State.RESTORING, false, true, null);
        // A GPU-only update may advance presentation while geometry is pending, but commits only after upload ack.
        committed = displayTarget;
        committedRevision = displayRevision;
        return publish(target.slice.enabled() ? State.READY : State.DISABLED, true, true, null);
    }

    private static Status publish(State state, boolean ready, boolean supported, String reason) {
        lastStatus = new Status(
            state,
            ready,
            supported,
            state == State.UNSUPPORTED && failure == null,
            reason,
            committedRevision,
            committed,
            geometryRevision);
        return lastStatus;
    }

    private static void forgetTarget() {
        target = null;
        displayTarget = HeightSlice.disabled();
        displayRevision = -1;
        pending.clear();
        touched.clear();
        installed.clear();
        building.clear();
        enqueued.clear();
        queue.clear();
        committed = HeightSlice.disabled();
        committedRevision = -1;
        failure = null;
        released = true;
        publish(State.DISABLED, true, true, null);
    }

    private static Set<RenderSection> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<RenderSection, Boolean>());
    }
}
