package com.miaokatze.miaokatzecore.api.animation.runtime;

/** Explicit decorative-only hysteresis. No listeners, execution callbacks or authority over gameplay. */
public final class QualityGovernor {

    public enum Tier { OFF, LOW, MEDIUM, HIGH }

    public enum Reason { HELD, OVER_BUDGET, HEADROOM, MISSING_OR_LOSSY, TOO_FEW_SAMPLES, COOLDOWN, LOCKED }

    public static final class Decision {

        public final Tier tier;
        public final Reason reason;
        public final boolean changed;

        private Decision(Tier tier, Reason reason, boolean changed) {
            this.tier = tier;
            this.reason = reason;
            this.changed = changed;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final long lowNanos, highNanos, cooldownNanos;
    private final int minimumSamples, downWindows, upWindows;
    private Tier tier;
    private boolean locked;
    private int highRun, lowRun;
    private long lastWindow = -1, lastNow = -1, changedAt = -1;

    public QualityGovernor(Tier initial, long lowNanos, long highNanos, int minimumSamples, int downWindows,
        int upWindows, long cooldownNanos) {
        if (initial == null || lowNanos < 0 || highNanos <= lowNanos || minimumSamples < 1 || downWindows < 1
            || upWindows < 1 || cooldownNanos < 0) throw new IllegalArgumentException("quality policy");
        tier = initial;
        this.lowNanos = lowNanos;
        this.highNanos = highNanos;
        this.minimumSamples = minimumSamples;
        this.downWindows = downWindows;
        this.upWindows = upWindows;
        this.cooldownNanos = cooldownNanos;
    }

    /** Freezes the tier; user hard-disable must additionally prevent the host from submitting effects. */
    public void setLocked(boolean value) {
        check();
        locked = value;
        highRun = lowRun = 0;
    }

    /** Host proves usable coverage; unsupported, pending, lossy or invalid windows must set usable=false. */
    public Decision observe(long windowId, long elapsedNanos, long p95Nanos, int samples, boolean usable) {
        check();
        if (windowId < 0 || windowId <= lastWindow || elapsedNanos < 0 || elapsedNanos < lastNow || samples < 0
            || (usable && (samples == 0 || p95Nanos < 0))) throw new IllegalArgumentException("window identity/data");
        lastWindow = windowId;
        lastNow = elapsedNanos;
        if (locked) return reset(Reason.LOCKED);
        if (!usable) return reset(Reason.MISSING_OR_LOSSY);
        if (samples < minimumSamples) return reset(Reason.TOO_FEW_SAMPLES);
        if (changedAt >= 0 && elapsedNanos - changedAt < cooldownNanos) return reset(Reason.COOLDOWN);
        if (p95Nanos > highNanos) {
            if (highRun < downWindows) highRun++;
            lowRun = 0;
        } else if (p95Nanos < lowNanos) {
            if (lowRun < upWindows) lowRun++;
            highRun = 0;
        } else {
            highRun = lowRun = 0;
        }
        int index = tier.ordinal();
        if (highRun >= downWindows && index > 0)
            return change(Tier.values()[index - 1], Reason.OVER_BUDGET, elapsedNanos);
        if (lowRun >= upWindows && index < Tier.values().length - 1)
            return change(Tier.values()[index + 1], Reason.HEADROOM, elapsedNanos);
        return new Decision(tier, Reason.HELD, false);
    }

    private Decision change(Tier next, Reason reason, long now) {
        tier = next;
        changedAt = now;
        highRun = lowRun = 0;
        return new Decision(tier, reason, true);
    }

    private Decision reset(Reason reason) {
        highRun = lowRun = 0;
        return new Decision(tier, reason, false);
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }
}
