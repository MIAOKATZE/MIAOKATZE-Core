package com.miaokatze.miaokatzecore.api.animation;

import java.math.BigInteger;

/**
 * Explicit time conversion; scalar V0 sampling continues to use integer
 * milliseconds.
 */
public final class TimelineTimebase {
    public static final long UNITS_PER_SECOND = 120000L;
    public static final long UNITS_PER_MILLISECOND = 120L;
    public static final long UNITS_PER_LEGACY_TICK_20HZ = 6000L;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private TimelineTimebase() {
    }

    public static long fromMilliseconds(long milliseconds) {
        return multiply(milliseconds, UNITS_PER_MILLISECOND);
    }

    /**
     * Converts nominal 20 Hz ticks, not elapsed wall time when a server is lagging.
     */
    public static long fromLegacyTicks20(long ticks) {
        return multiply(ticks, UNITS_PER_LEGACY_TICK_20HZ);
    }

    public static long toMillisecondsFloor(long units) {
        nonnegative(units);
        return units / UNITS_PER_MILLISECOND;
    }

    public static long toMillisecondsExact(long units) {
        nonnegative(units);
        if (units % UNITS_PER_MILLISECOND != 0)
            throw new IllegalArgumentException("sub-millisecond value");
        return units / UNITS_PER_MILLISECOND;
    }

    /**
     * floor(n * U * Q / P), calculated from absolute frame number, never
     * accumulated.
     */
    public static long frameTimeFloor(long frameIndex, long fpsNumerator, long fpsDenominator) {
        nonnegative(frameIndex);
        rate(fpsNumerator, fpsDenominator);
        return checked(BigInteger.valueOf(frameIndex).multiply(BigInteger.valueOf(UNITS_PER_SECOND))
                .multiply(BigInteger.valueOf(fpsDenominator)).divide(BigInteger.valueOf(fpsNumerator)));
    }

    /**
     * Number of frame samples in [0,duration); the terminal pose is not an extra
     * video frame.
     */
    public static long frameCountCeiling(long durationUnits, long fpsNumerator, long fpsDenominator) {
        nonnegative(durationUnits);
        rate(fpsNumerator, fpsDenominator);
        BigInteger divisor = BigInteger.valueOf(UNITS_PER_SECOND).multiply(BigInteger.valueOf(fpsDenominator));
        BigInteger numerator = BigInteger.valueOf(durationUnits).multiply(BigInteger.valueOf(fpsNumerator));
        return checked(numerator.add(divisor).subtract(BigInteger.ONE).divide(divisor));
    }

    private static long multiply(long value, long scale) {
        nonnegative(value);
        if (value > Long.MAX_VALUE / scale)
            throw new IllegalArgumentException("time overflow");
        return value * scale;
    }

    private static long checked(BigInteger value) {
        if (value.signum() < 0 || value.compareTo(LONG_MAX) > 0)
            throw new IllegalArgumentException("time/count overflow");
        return value.longValue();
    }

    private static void rate(long p, long q) {
        if (p <= 0 || q <= 0)
            throw new IllegalArgumentException("positive rational frame rate required");
    }

    private static void nonnegative(long value) {
        if (value < 0)
            throw new IllegalArgumentException("nonnegative time/index required");
    }
}
