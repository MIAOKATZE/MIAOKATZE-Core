package com.miaokatze.miaokatzecore.api.animation.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Pre-allocation accounting only. Neither a device allocator nor a measurement of driver memory. */
public final class ResourceLedger implements AutoCloseable {

    public enum Dimension { CPU_BYTES, GPU_BYTES, HANDLES, DECODE_PIXELS, VERTICES, JOBS }

    public static final int MAX_LEASES = 4096;
    private final Thread owner = Thread.currentThread();
    private final long[] limits, used;
    private final Set<Lease> leases = Collections.newSetFromMap(new IdentityHashMap<Lease, Boolean>());
    private boolean closed;

    public ResourceLedger(long... limits) {
        if (limits == null || limits.length != Dimension.values().length)
            throw new IllegalArgumentException("dimension count");
        this.limits = limits.clone();
        used = new long[limits.length];
        for (long limit : this.limits)
            if (limit < 0) throw new IllegalArgumentException("negative limit");
    }

    /** Nonblocking, all dimensions or none. Null means capacity refusal, not malformed input. */
    public Lease reserve(long... amounts) {
        check();
        if (closed) throw new IllegalStateException("ledger closed");
        if (amounts == null || amounts.length != limits.length) throw new IllegalArgumentException("dimension count");
        for (long amount : amounts)
            if (amount < 0) throw new IllegalArgumentException("negative reservation");
        if (leases.size() >= MAX_LEASES) return null;
        for (int i = 0; i < amounts.length; i++)
            if (amounts[i] > limits[i] - used[i]) return null;
        Lease lease = new Lease(amounts.clone());
        for (int i = 0; i < amounts.length; i++) used[i] += amounts[i];
        leases.add(lease);
        return lease;
    }

    public long used(Dimension dimension) {
        check();
        if (dimension == null) throw new IllegalArgumentException("dimension");
        return used[dimension.ordinal()];
    }

    public int activeLeases() {
        check();
        return leases.size();
    }

    public final class Lease implements AutoCloseable {

        private final long[] amounts;
        private boolean released;

        private Lease(long[] amounts) {
            this.amounts = amounts;
        }

        public boolean active() {
            check();
            return !released;
        }

        /** Return the reservation only after its real resource has been released. */
        @Override
        public void close() {
            check();
            if (released) return;
            released = true;
            for (int i = 0; i < amounts.length; i++) used[i] -= amounts[i];
            leases.remove(this);
        }
    }

    /** Accounting teardown only; callers must release actual resources first. */
    @Override
    public void close() {
        check();
        if (closed) return;
        closed = true;
        for (Lease lease : new ArrayList<>(leases)) lease.close();
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }
}
