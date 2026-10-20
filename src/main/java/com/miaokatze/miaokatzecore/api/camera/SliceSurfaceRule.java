package com.miaokatze.miaokatzecore.api.camera;

import java.util.Objects;

/** Shared CPU/shader surface coverage and ordered dithering. Inputs are actual surface position and normal. */
public final class SliceSurfaceRule {

    public static final double CAP_EPSILON = .002;
    public static final double NORMAL_Y_THRESHOLD = .999;
    private static final int[] BAYER_4 = { 0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5 };

    private SliceSurfaceRule() {}

    /**
     * Real top (+Y) and bottom (-Y) boundary faces stay fully covered, including narrow native liquid offsets.
     * Other surfaces fade with smoothstep inside the kept band; exterior surfaces have zero coverage.
     * This function never creates geometry or a synthetic collision face.
     */
    public static double coverageAt(HeightSlice slice, double worldY, double normalY) {
        Objects.requireNonNull(slice, "slice");
        CameraNumbers.finite(worldY, "worldY");
        CameraNumbers.finite(normalY, "normalY");
        if (normalY < -1 || normalY > 1) throw new IllegalArgumentException("normalY must be in [-1, 1]");
        if (!slice.enabled()) return 1;
        if (slice.hasUpper() && normalY >= NORMAL_Y_THRESHOLD && Math.abs(worldY - slice.maximumY()) <= CAP_EPSILON)
            return 1;
        if (slice.hasLower() && normalY <= -NORMAL_Y_THRESHOLD && Math.abs(worldY - slice.minimumY()) <= CAP_EPSILON)
            return 1;
        if (slice.hasLower() && worldY < slice.minimumY() || slice.hasUpper() && worldY >= slice.maximumY()) return 0;
        double feather = slice.featherWidth();
        if (feather == 0) return 1;
        double coverage = 1;
        if (slice.hasLower()) coverage = smooth((worldY - slice.minimumY()) / feather);
        if (slice.hasUpper()) coverage = Math.min(coverage, smooth((slice.maximumY() - worldY) / feather));
        return coverage;
    }

    /** Bottom-left display-pixel coordinates, matching floor(gl_FragCoord.xy). */
    public static double thresholdAt(int pixelX, int pixelY) {
        return (BAYER_4[((pixelY & 3) << 2) | (pixelX & 3)] + .5) / 16;
    }

    public static boolean keeps(HeightSlice slice, double worldY, double normalY, int pixelX, int pixelY) {
        return coverageAt(slice, worldY, normalY) >= thresholdAt(pixelX, pixelY);
    }

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
