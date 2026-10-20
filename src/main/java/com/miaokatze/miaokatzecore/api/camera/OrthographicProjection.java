package com.miaokatze.miaokatzecore.api.camera;

/** Symmetric orthographic extents and positive view-space clipping distances. */
public final class OrthographicProjection {

    private final double halfWidth, halfHeight, depthOffset, near, far;

    public OrthographicProjection(double halfWidth, double halfHeight, double depthOffset, double near, double far) {
        this.halfWidth = CameraNumbers.positive(halfWidth, "halfWidth");
        this.halfHeight = CameraNumbers.positive(halfHeight, "halfHeight");
        this.depthOffset = CameraNumbers.positive(depthOffset, "depthOffset");
        this.near = CameraNumbers.positive(near, "near");
        this.far = CameraNumbers.positive(far, "far");
        if (far <= near) throw new IllegalArgumentException("far must exceed near");
    }

    /**
     * Outpost-compatible dynamic depth. The caller supplies its own scale and minimum depth policy.
     * Rejects scales whose 8000-unit far cap would clip the anchor itself.
     */
    public static OrthographicProjection scaled(double scaledWidth, double scaledHeight, double pixelsPerWorldUnit,
        double minimumDepth) {
        CameraNumbers.positive(scaledWidth, "scaledWidth");
        CameraNumbers.positive(scaledHeight, "scaledHeight");
        CameraNumbers.positive(pixelsPerWorldUnit, "pixelsPerWorldUnit");
        CameraNumbers.positive(minimumDepth, "minimumDepth");
        double halfW = scaledWidth / pixelsPerWorldUnit / 2;
        double halfH = scaledHeight / pixelsPerWorldUnit / 2;
        double depth = Math.max(minimumDepth, Math.ceil(3.2 * halfH));
        double far = Math.min(8000, depth + 3.6 * halfH + 200);
        if (depth <= .05 || depth >= far) {
            throw new IllegalArgumentException(
                "Scale/depth places the anchor outside the [.05, far] clipping interval");
        }
        return new OrthographicProjection(halfW, halfH, depth, .05, far);
    }

    public double halfWidth() {
        return halfWidth;
    }

    public double halfHeight() {
        return halfHeight;
    }

    public double depthOffset() {
        return depthOffset;
    }

    public double near() {
        return near;
    }

    public double far() {
        return far;
    }
}
