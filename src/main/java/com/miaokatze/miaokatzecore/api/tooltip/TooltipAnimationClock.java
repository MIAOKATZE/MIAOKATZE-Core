// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.tooltip;

/** Monotonic presentation time in the local process; never use it as a machine tick or network timestamp. */
public final class TooltipAnimationClock {

    private TooltipAnimationClock() {}

    public static long nowNanos() {
        return System.nanoTime();
    }

    public static float phase(long nowNanos, int periodMillis) {
        if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis");
        long period = periodMillis * 1_000_000L;
        return (float) (Math.floorMod(nowNanos, period) / (double) period);
    }
}
