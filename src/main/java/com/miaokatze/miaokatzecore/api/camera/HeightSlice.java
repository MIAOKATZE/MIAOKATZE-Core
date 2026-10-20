package com.miaokatze.miaokatzecore.api.camera;

/** Immutable integer block-layer band [minimumY, maximumY), with optional unbounded ends. */
public final class HeightSlice {

    private static final HeightSlice DISABLED = new HeightSlice(false, false, false, 0, 0, 0);
    private final boolean enabled, hasLower, hasUpper;
    private final int minimumY, maximumY;
    private final double featherWidth;
    private final boolean featheredCaps;

    private HeightSlice(boolean enabled, boolean hasLower, boolean hasUpper, int minimumY, int maximumY,
        double featherWidth) {
        this(enabled, hasLower, hasUpper, minimumY, maximumY, featherWidth, false);
    }

    private HeightSlice(boolean enabled, boolean hasLower, boolean hasUpper, int minimumY, int maximumY,
        double featherWidth, boolean featheredCaps) {
        CameraNumbers.finite(featherWidth, "featherWidth");
        if (featherWidth < 0) throw new IllegalArgumentException("featherWidth must not be negative");
        if (hasLower && hasUpper && minimumY >= maximumY) {
            throw new IllegalArgumentException("minimumY must be below maximumY");
        }
        this.enabled = enabled;
        this.hasLower = hasLower;
        this.hasUpper = hasUpper;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.featherWidth = featherWidth == 0 ? 0 : featherWidth;
        this.featheredCaps = featheredCaps;
    }

    public static HeightSlice disabled() {
        return DISABLED;
    }

    public static HeightSlice above(int minInclusive, double featherWidth) {
        return new HeightSlice(true, true, false, minInclusive, 0, featherWidth);
    }

    public static HeightSlice below(int maxExclusive, double featherWidth) {
        return new HeightSlice(true, false, true, 0, maxExclusive, featherWidth);
    }

    public static HeightSlice between(int minInclusive, int maxExclusive, double featherWidth) {
        return new HeightSlice(true, true, true, minInclusive, maxExclusive, featherWidth);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hasLower() {
        return hasLower;
    }

    public boolean hasUpper() {
        return hasUpper;
    }

    /** Only meaningful when hasLower() is true. */
    public int minimumY() {
        return minimumY;
    }

    /** Only meaningful when hasUpper() is true. */
    public int maximumY() {
        return maximumY;
    }

    public double featherWidth() {
        return featherWidth;
    }

    /** Opt in to fading native cut faces too, with native faces exposed behind the feather band. */
    public HeightSlice withFeatheredCaps(boolean value) {
        return featheredCaps == value ? this
            : new HeightSlice(enabled, hasLower, hasUpper, minimumY, maximumY, featherWidth, value);
    }

    public boolean featheredCaps() {
        return featheredCaps;
    }

    public boolean containsBlockY(int blockY) {
        return !enabled || (!hasLower || blockY >= minimumY) && (!hasUpper || blockY < maximumY);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof HeightSlice)) return false;
        HeightSlice other = (HeightSlice) object;
        return enabled == other.enabled && hasLower == other.hasLower
            && hasUpper == other.hasUpper
            && minimumY == other.minimumY
            && maximumY == other.maximumY
            && featheredCaps == other.featheredCaps
            && Double.doubleToLongBits(featherWidth) == Double.doubleToLongBits(other.featherWidth);
    }

    @Override
    public int hashCode() {
        int result = enabled ? 1 : 0;
        result = 31 * result + (hasLower ? 1 : 0);
        result = 31 * result + (hasUpper ? 1 : 0);
        result = 31 * result + minimumY;
        result = 31 * result + maximumY;
        result = 31 * result + (featheredCaps ? 1 : 0);
        long bits = Double.doubleToLongBits(featherWidth);
        return 31 * result + (int) (bits ^ bits >>> 32);
    }
}
