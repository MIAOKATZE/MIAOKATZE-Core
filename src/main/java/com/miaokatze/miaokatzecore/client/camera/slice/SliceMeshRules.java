package com.miaokatze.miaokatzecore.client.camera.slice;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Pure integer boundary rules, independent of the renderer and world height. */
public final class SliceMeshRules {

    private SliceMeshRules() {}

    /** Only integer membership affects native face emission; feather width belongs exclusively to the shader. */
    public static boolean sameGeometry(HeightSlice first, HeightSlice second) {
        return first.enabled() == second.enabled() && first.hasLower() == second.hasLower()
            && first.hasUpper() == second.hasUpper()
            && (!first.hasLower() || first.minimumY() == second.minimumY())
            && (!first.hasUpper() || first.maximumY() == second.maximumY());
    }

    public static boolean opensFace(HeightSlice slice, int blockY, int neighborY, int side) {
        return slice.enabled()
            && (side == 0 && (long) neighborY == (long) blockY - 1
                || side == 1 && (long) neighborY == (long) blockY + 1)
            && slice.containsBlockY(blockY)
            && !slice.containsBlockY(neighborY);
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
