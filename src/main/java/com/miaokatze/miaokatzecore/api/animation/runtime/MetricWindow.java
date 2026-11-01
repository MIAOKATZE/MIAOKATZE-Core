package com.miaokatze.miaokatzecore.api.animation.runtime;

import java.util.Arrays;

/** One metric, one elapsed-time clock, one owner thread. Recording is bounded; snapshots sort a copy. */
public final class MetricWindow {

    private final Thread owner = Thread.currentThread();
    private final long windowNanos;
    private final long[] times, values;
    private int head, count;
    private long clock = -1, observations, capacityEvictions, timeExpirations, upstreamDropped;

    public MetricWindow(long windowNanos, int capacity) {
        if (windowNanos <= 0 || capacity < 1 || capacity > 65536) throw new IllegalArgumentException("window/capacity");
        this.windowNanos = windowNanos;
        times = new long[capacity];
        values = new long[capacity];
    }

    /** Nonnegative time since a host-defined monotonic origin, not arbitrary raw nanoTime. */
    public void record(long elapsedNanos, long durationNanos) {
        check();
        if (durationNanos < 0) throw new IllegalArgumentException("negative duration");
        advance(elapsedNanos);
        observations = increment(observations, 1);
        if (count == times.length) {
            head = (head + 1) % times.length;
            count--;
            capacityEvictions = increment(capacityEvictions, 1);
        }
        int slot = (head + count) % times.length;
        times[slot] = elapsedNanos;
        values[slot] = durationNanos;
        count++;
    }

    /** Cumulative owner loss; this is not a claim about loss within any particular snapshot. */
    public void noteUpstreamDropped(long amount) {
        check();
        if (amount < 0) throw new IllegalArgumentException("negative loss");
        upstreamDropped = increment(upstreamDropped, amount);
    }

    /** Keeps (elapsedNanos - windowNanos, elapsedNanos]. Historical UI must retain the returned snapshot. */
    public Snapshot snapshot(long elapsedNanos) {
        check();
        advance(elapsedNanos);
        long[] sorted = new long[count];
        for (int i = 0; i < count; i++) sorted[i] = values[(head + i) % times.length];
        Arrays.sort(sorted);
        long span = count < 2 ? 0 : times[(head + count - 1) % times.length] - times[head];
        return new Snapshot(
            elapsedNanos,
            windowNanos,
            sorted,
            span,
            observations,
            capacityEvictions,
            timeExpirations,
            upstreamDropped);
    }

    private void advance(long now) {
        if (now < 0 || now < clock) throw new IllegalArgumentException("regressing elapsed clock");
        clock = now;
        while (count > 0 && now - times[head] >= windowNanos) {
            head = (head + 1) % times.length;
            count--;
            timeExpirations = increment(timeExpirations, 1);
        }
    }

    public static final class Snapshot {

        public final long endNanos, windowNanos, coveredSpanNanos;
        public final long observations, capacityEvictions, timeExpirations, upstreamDropped;
        public final int samples;
        /** -1 means no sample; a measured zero remains zero. */
        public final long p50, p95, p99, max;

        private Snapshot(long end, long window, long[] sorted, long span, long observed, long evicted, long expired,
            long dropped) {
            endNanos = end;
            windowNanos = window;
            coveredSpanNanos = span;
            samples = sorted.length;
            observations = observed;
            capacityEvictions = evicted;
            timeExpirations = expired;
            upstreamDropped = dropped;
            p50 = rank(sorted, 50);
            p95 = rank(sorted, 95);
            p99 = rank(sorted, 99);
            max = samples == 0 ? -1 : sorted[samples - 1];
        }

        private static long rank(long[] sorted, int percent) {
            return sorted.length == 0 ? -1 : sorted[(sorted.length * percent + 99) / 100 - 1];
        }
    }

    private static long increment(long current, long amount) {
        return amount > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + amount;
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }
}
