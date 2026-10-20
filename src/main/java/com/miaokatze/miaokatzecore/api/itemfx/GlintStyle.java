package com.miaokatze.miaokatzecore.api.itemfx;

/** Immutable two-pass glint configuration; no Minecraft or client classes. */
public final class GlintStyle {

    public static final int PASS_COUNT = 2;
    private final float red, green, blue, alpha, textureScale;
    private final double[] scrollSpeeds;
    private final float[] rotationDegrees;

    public GlintStyle(float red, float green, float blue, float alpha, float textureScale, double[] scrollSpeeds,
        float[] rotationDegrees) {
        this.red = color(red);
        this.green = color(green);
        this.blue = color(blue);
        this.alpha = color(alpha);
        if (!Float.isFinite(textureScale) || textureScale <= 0) throw new IllegalArgumentException("textureScale");
        this.textureScale = textureScale;
        if (scrollSpeeds == null || scrollSpeeds.length != PASS_COUNT
            || rotationDegrees == null
            || rotationDegrees.length != PASS_COUNT)
            throw new IllegalArgumentException("Exactly two glint passes are required");
        this.scrollSpeeds = scrollSpeeds.clone();
        this.rotationDegrees = rotationDegrees.clone();
        for (double value : this.scrollSpeeds)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("scrollSpeeds");
        for (float value : this.rotationDegrees)
            if (!Float.isFinite(value)) throw new IllegalArgumentException("rotationDegrees");
    }

    private static float color(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("color");
        return value;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public float getAlpha() {
        return alpha;
    }

    public float getTextureScale() {
        return textureScale;
    }

    public double getScrollSpeed(int pass) {
        return scrollSpeeds[pass];
    }

    public float getRotationDegrees(int pass) {
        return rotationDegrees[pass];
    }

    public double[] getScrollSpeeds() {
        return scrollSpeeds.clone();
    }

    public float[] getRotationDegrees() {
        return rotationDegrees.clone();
    }
}
