package com.miaokatze.miaokatzecore.client.miaovision;

/** Native 1.7.10 equipped/extruded sprite coordinates normalized to the exact SceneBody half extents. */
public final class SceneSpriteTransform {

    public static final float NATIVE_DEPTH = 1f / 16f;
    public static final double SCALE_Z = 1.0 / NATIVE_DEPTH;
    public static final double OFFSET_XY = -.5, OFFSET_Z = NATIVE_DEPTH / 2;

    private SceneSpriteTransform() {}

    /** Native [0,1] sprite axis -> centered [-.5,.5] body axis. */
    public static double planar(double value) {
        return value + OFFSET_XY;
    }

    /** Native [-1/16,0] extrusion -> centered [-.5,.5] body depth; apply translation after scaling. */
    public static double depth(double value) {
        return (value + OFFSET_Z) * SCALE_Z;
    }
}
