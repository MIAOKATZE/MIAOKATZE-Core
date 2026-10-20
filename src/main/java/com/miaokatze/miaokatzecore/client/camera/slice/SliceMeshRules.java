package com.miaokatze.miaokatzecore.client.camera.slice;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Pure integer boundary rules, independent of the renderer and world height. */
public final class SliceMeshRules {

    private SliceMeshRules() {}

    /** Legacy feather is shader-only; layer-fading caps also need native faces across ceil(featherWidth). */
    public static boolean sameGeometry(HeightSlice first, HeightSlice second) {
        return first.enabled() == second.enabled() && first.hasLower() == second.hasLower()
            && first.hasUpper() == second.hasUpper()
            && first.featheredCaps() == second.featheredCaps()
            && first.lightCompensation() == second.lightCompensation()
            && (!(first.featheredCaps() || first.lightCompensation())
                || Math.ceil(first.featherWidth()) == Math.ceil(second.featherWidth()))
            && (!first.hasLower() || first.minimumY() == second.minimumY())
            && (!first.hasUpper() || first.maximumY() == second.maximumY());
    }

    public static boolean opensFace(HeightSlice slice, int blockY, int neighborY, int side) {
        return slice.enabled()
            && (side == 0 && (long) neighborY == (long) blockY - 1
                || side == 1 && (long) neighborY == (long) blockY + 1)
            && slice.containsBlockY(blockY)
            && (!slice.containsBlockY(neighborY) || slice.featheredCaps() && slice.featherWidth() > 0
                && (side == 1 && slice.hasUpper()
                    && (double) neighborY >= slice.maximumY() - Math.ceil(slice.featherWidth())
                    || side == 0 && slice.hasLower()
                        && (double) blockY <= slice.minimumY() + Math.ceil(slice.featherWidth())));
    }

    /** Exclude removed/fading layers from neighboring AO tests while preserving the rendered block itself. */
    public static boolean hidesNeighbor(HeightSlice slice, int renderedY, int queriedY) {
        if (!slice.enabled() || queriedY == renderedY) return false;
        if (!slice.containsBlockY(queriedY)) return true;
        return slice.featheredCaps() && slice.featherWidth() > 0
            && (queriedY > renderedY && slice.hasUpper()
                && queriedY >= slice.maximumY() - Math.ceil(slice.featherWidth())
                || queriedY < renderedY && slice.hasLower()
                    && queriedY < slice.minimumY() + Math.ceil(slice.featherWidth()));
    }

    public static boolean affectsSection(HeightSlice slice, int originY) {
        if (!slice.enabled()) return false;
        double radius = Math.ceil(slice.featherWidth()) + 2;
        return slice.hasLower() && intersects(originY, slice.minimumY(), radius)
            || slice.hasUpper() && intersects(originY, slice.maximumY(), radius);
    }

    private static boolean intersects(int originY, int boundary, double radius) {
        return (double) originY + 16 > (double) boundary - radius && (double) originY < (double) boundary + radius;
    }
}
