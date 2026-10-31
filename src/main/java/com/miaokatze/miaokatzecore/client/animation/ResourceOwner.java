package com.miaokatze.miaokatzecore.client.animation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.miaokatze.miaokatzecore.api.animation.runtime.ResourceLedger;
import com.miaokatze.miaokatzecore.api.animation.runtime.RuntimeGenerations;

/**
 * Explicit owner-thread resource bindings. Borrowed resources are never closed; invalid generations cannot be read.
 * This class performs no allocation, resource loading or implicit device-context switching.
 */
public final class ResourceOwner implements AutoCloseable {

    public static final int MAX_HANDLES = 4096;
    private final Thread owner = Thread.currentThread();
    private final RuntimeGenerations generations;
    private final List<Handle<?>> handles = new ArrayList<>();
    private final Map<Object, Handle<?>> identities = new IdentityHashMap<>();
    private final Map<ResourceLedger.Lease, Handle<?>> reservations = new IdentityHashMap<>();
    private boolean closed, retiring;
    private int failedReleases;

    public ResourceOwner(RuntimeGenerations generations) {
        if (generations == null) throw new IllegalArgumentException("generations");
        this.generations = generations;
    }

    /** Ownership transfers only after this method returns successfully. Reserve before allocating the resource. */
    public <T extends AutoCloseable> Handle<T> own(RuntimeGenerations.Ticket ticket, T resource,
        ResourceLedger.Lease accounting) {
        checkAdmission(ticket, resource);
        if (accounting == null || !accounting.active() || reservations.containsKey(accounting))
            throw new IllegalArgumentException("active exclusive reservation required");
        Handle<T> handle = new Handle<>(ticket, resource, resource, accounting);
        register(handle);
        reservations.put(accounting, handle);
        return handle;
    }

    /** Never acquires ownership of the underlying atlas, camera or shared resource. */
    public <T> Handle<T> borrow(RuntimeGenerations.Ticket ticket, T resource) {
        checkAdmission(ticket, resource);
        Handle<T> handle = new Handle<>(ticket, resource, null, null);
        register(handle);
        return handle;
    }

    private void checkAdmission(RuntimeGenerations.Ticket ticket, Object resource) {
        check();
        if (closed || retiring) throw new IllegalStateException("resource owner unavailable");
        if (resource == null || identities.containsKey(resource)) throw new IllegalArgumentException("resource identity");
        if (!generations.accepts(ticket) || ticket.purpose == RuntimeGenerations.Purpose.CPU_DECODE)
            throw new IllegalArgumentException("current device resource ticket required");
        if (handles.size() >= MAX_HANDLES) throw new IllegalStateException("resource handle capacity");
    }

    private void register(Handle<?> handle) {
        handles.add(handle);
        identities.put(handle.resource, handle);
    }

    public int activeHandles() {
        check();
        return handles.size();
    }

    /** Failed owned releases retain their accounting reservation; zero active bindings is not proof of release. */
    public int failedReleases() {
        check();
        return failedReleases;
    }

    /** Host must invoke on the owning resource context; a changed context is not permission to delete old GL IDs. */
    public void retireInvalidated() throws Exception {
        check();
        if (retiring) return;
        retiring = true;
        Throwable failure = null;
        try {
            for (Handle<?> handle : new ArrayList<>(handles)) {
                if (handle.valid()) continue;
                try {
                    handle.close();
                } catch (Exception | Error thrown) {
                    if (failure == null) failure = thrown;
                    else if (failure != thrown) failure.addSuppressed(thrown);
                }
            }
        } finally {
            retiring = false;
        }
        rethrow(failure);
    }

    public final class Handle<T> implements AutoCloseable {

        private final RuntimeGenerations.Ticket ticket;
        private AutoCloseable release;
        private final boolean owned;
        private final ResourceLedger.Lease accounting;
        private T resource;
        private boolean retired;

        private Handle(RuntimeGenerations.Ticket ticket, T resource, AutoCloseable release,
            ResourceLedger.Lease accounting) {
            this.ticket = ticket;
            this.resource = resource;
            this.release = release;
            owned = release != null;
            this.accounting = accounting;
        }

        public boolean owned() {
            check();
            return owned;
        }

        public boolean valid() {
            check();
            return !closed && !retired && generations.accepts(ticket) && (accounting == null || accounting.active());
        }

        /** Invalid borrowed IDs cannot accidentally survive a reload/context change. */
        public T get() {
            if (!valid()) throw new IllegalStateException("resource binding invalid");
            return resource;
        }

        /**
         * Detach first, then close exactly this owned object. Release failure keeps accounting active and is reported;
         * repeated close never repeats a device callback. The host must explicitly resolve any failed retirement.
         */
        @Override
        public void close() throws Exception {
            check();
            if (retired) return;
            retired = true;
            T old = resource;
            resource = null;
            handles.remove(this);
            identities.remove(old);
            AutoCloseable action = release;
            release = null;
            boolean previousRetiring = retiring;
            retiring = true;
            try {
                if (action != null) {
                    action.close();
                    accounting.close();
                    reservations.remove(accounting);
                }
            } catch (Exception | Error failure) {
                failedReleases++;
                throw failure;
            } finally {
                retiring = previousRetiring;
            }
        }
    }

    @Override
    public void close() throws Exception {
        check();
        if (closed) return;
        closed = true;
        Throwable failure = null;
        for (Handle<?> handle : new ArrayList<>(handles)) {
            try {
                handle.close();
            } catch (Exception | Error thrown) {
                if (failure == null) failure = thrown;
                else if (failure != thrown) failure.addSuppressed(thrown);
            }
        }
        rethrow(failure);
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (Exception) failure;
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }
}
