package com.miaokatze.miaokatzecore.api.ui;

import java.util.Objects;

/**
 * Immutable uniform scale and translation between local canvas and screen coordinates.
 * All coordinate arguments must be finite; invalid arguments throw IllegalArgumentException.
 */
public final class CanvasTransform {

    private static final CanvasTransform IDENTITY = new CanvasTransform(0, 0, 0, 0, 1);

    private final double sourceX, sourceY, targetX, targetY, scale;

    /** Maps the source anchor to the target anchor. Scale must be finite and strictly positive. */
    public CanvasTransform(double sourceX, double sourceY, double targetX, double targetY, double scale) {
        this.sourceX = finite(sourceX, "sourceX");
        this.sourceY = finite(sourceY, "sourceY");
        this.targetX = finite(targetX, "targetX");
        this.targetY = finite(targetY, "targetY");
        this.scale = finite(scale, "scale");
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
    }

    public static CanvasTransform identity() {
        return IDENTITY;
    }

    public double toScreenX(double localX) {
        return targetX + (finite(localX, "localX") - sourceX) * scale;
    }

    public double toScreenY(double localY) {
        return targetY + (finite(localY, "localY") - sourceY) * scale;
    }

    public double toLocalX(double screenX) {
        return sourceX + (finite(screenX, "screenX") - targetX) / scale;
    }

    public double toLocalY(double screenY) {
        return sourceY + (finite(screenY, "screenY") - targetY) / scale;
    }

    public double scale() {
        return scale;
    }

    /** Applies this transform first, then parent: result(local) = parent(this(local)). */
    public CanvasTransform compose(CanvasTransform parent) {
        Objects.requireNonNull(parent, "parent");
        return new CanvasTransform(
            sourceX,
            sourceY,
            parent.toScreenX(targetX),
            parent.toScreenY(targetY),
            scale * parent.scale);
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
}
