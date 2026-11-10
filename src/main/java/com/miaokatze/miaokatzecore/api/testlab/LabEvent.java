package com.miaokatze.miaokatzecore.api.testlab;

/** Immutable bounded text snapshot. Never retains worlds, players, senders, exceptions or business objects. */
public final class LabEvent {

    private final long sequence, timestampMillis;
    private final String module, run, task, phase, check, cleanup;
    private final LabResult result;

    LabEvent(long sequence, String module, String run, String task, String phase, String check, LabResult result,
        String cleanup) {
        this.sequence = sequence;
        this.timestampMillis = System.currentTimeMillis();
        this.module = bound(module);
        this.run = bound(run);
        this.task = bound(task);
        this.phase = bound(phase);
        this.check = bound(check);
        this.result = result;
        this.cleanup = bound(cleanup);
    }

    private static String bound(String text) {
        return text == null ? "unknown" : text.substring(0, Math.min(1024, text.length()));
    }

    public long sequence() {
        return sequence;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public String module() {
        return module;
    }

    public String run() {
        return run;
    }

    public String task() {
        return task;
    }

    public String phase() {
        return phase;
    }

    public String check() {
        return check;
    }

    public LabResult result() {
        return result;
    }

    public String cleanup() {
        return cleanup;
    }
}
