package com.miaokatze.miaokatzecore.api.animation.runtime;

/** Owner-thread publication fence. Owns no assets, world objects, executors or device context. */
public final class RuntimeGenerations implements AutoCloseable {

    public enum Purpose { CPU_DECODE, GPU_UPLOAD, PRESENTATION }

    private final Thread owner = Thread.currentThread();
    private long definition, asset, instance, context;
    private boolean closed;

    public static final class Ticket {

        public final Purpose purpose;
        public final long definitionGeneration, assetGeneration, instanceGeneration, contextGeneration;
        private final RuntimeGenerations source;

        private Ticket(RuntimeGenerations source, Purpose purpose) {
            this.source = source;
            this.purpose = purpose;
            definitionGeneration = source.definition;
            assetGeneration = source.asset;
            instanceGeneration = source.instance;
            contextGeneration = source.context;
        }
    }

    public Ticket ticket(Purpose purpose) {
        checkOpen();
        if (purpose == null) throw new IllegalArgumentException("purpose");
        return new Ticket(this, purpose);
    }

    public boolean accepts(Ticket ticket) {
        check();
        if (closed || ticket == null || ticket.source != this || ticket.definitionGeneration != definition
            || ticket.assetGeneration != asset) return false;
        if (ticket.purpose != Purpose.CPU_DECODE && ticket.contextGeneration != context) return false;
        return ticket.purpose != Purpose.PRESENTATION || ticket.instanceGeneration == instance;
    }

    public void changedDefinition() {
        checkOpen();
        definition = next(definition);
    }

    public void reloadedAssets() {
        checkOpen();
        asset = next(asset);
    }

    /** Explicit replacement/replay, never a loop or resource reload. */
    public void replacedInstance() {
        checkOpen();
        instance = next(instance);
    }

    public void changedContext() {
        checkOpen();
        context = next(context);
    }

    @Override
    public void close() {
        check();
        closed = true;
    }

    private static long next(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("generation exhausted; replace owner");
        return value + 1;
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }

    private void checkOpen() {
        check();
        if (closed) throw new IllegalStateException("closed");
    }
}
