package com.miaokatze.miaokatzecore.testkit;

/** Passive monotonic time budget. The consumer owns scheduling, cancellation and world state. */
public final class Deadline {

    private final long startedNanos;
    private final long budgetNanos;

    private Deadline(long startedNanos, long budgetNanos) {
        this.startedNanos = startedNanos;
        this.budgetNanos = budgetNanos;
    }

    public static Deadline afterNanos(long budgetNanos) {
        return startingAt(System.nanoTime(), budgetNanos);
    }

    public static Deadline startingAt(long startedNanos, long budgetNanos) {
        return new Deadline(startedNanos, budgetNanos);
    }

    public boolean hasTimeRemaining() {
        return hasTimeRemaining(System.nanoTime());
    }

    /** Supports clock wraparound for elapsed intervals shorter than 2^63 nanoseconds. */
    public boolean hasTimeRemaining(long nowNanos) {
        return budgetNanos > 0L && nowNanos - startedNanos < budgetNanos;
    }

    public boolean isExpired() {
        return isExpired(System.nanoTime());
    }

    public boolean isExpired(long nowNanos) {
        return !hasTimeRemaining(nowNanos);
    }
}
