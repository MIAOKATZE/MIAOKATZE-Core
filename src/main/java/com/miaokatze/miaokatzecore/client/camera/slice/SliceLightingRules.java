package com.miaokatze.miaokatzecore.client.camera.slice;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Pure, local render-light rules; stored world light and its propagation remain untouched. */
public final class SliceLightingRules {

    private SliceLightingRules() {}

    public static boolean compensates(HeightSlice slice, int sampleY) {
        if (!slice.enabled() || !slice.lightCompensation()) return false;
        double radius = Math.ceil(slice.featherWidth()) + 2;
        return slice.hasUpper() && Math.abs(sampleY + .5 - slice.maximumY()) <= radius
            || slice.hasLower() && Math.abs(sampleY + .5 - slice.minimumY()) <= radius;
    }

    public static boolean removedSample(HeightSlice slice, int sampleY) {
        return !slice.containsBlockY(sampleY) || slice.featheredCaps() && slice.featherWidth() > 0
            && (slice.hasUpper() && sampleY >= slice.maximumY() - Math.ceil(slice.featherWidth())
                || slice.hasLower() && sampleY < slice.minimumY() + Math.ceil(slice.featherWidth()));
    }

    /** Full environmental light at the exposed band, preserving block light and the current dimension's lightmap. */
    public static int brightness(HeightSlice slice, int sampleY, int original) {
        return compensates(slice, sampleY) ? original & ~0xF00000 | 0xF00000 : original;
    }
}
