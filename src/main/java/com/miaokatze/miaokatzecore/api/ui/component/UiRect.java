package com.miaokatze.miaokatzecore.api.ui.component;

/** Immutable local GUI rectangle. Right and bottom edges are exclusive. */
public final class UiRect {

    public final double x, y, width, height;

    public UiRect(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(width)
            || !Double.isFinite(height)
            || width < 0
            || height < 0
            || !Double.isFinite(x + width)
            || !Double.isFinite(y + height)) {
            throw new IllegalArgumentException("Invalid rectangle");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    public boolean empty() {
        return width == 0 || height == 0;
    }

    public UiRect intersect(UiRect other) {
        double left = Math.max(x, other.x), top = Math.max(y, other.y);
        return new UiRect(
            left,
            top,
            Math.max(0, Math.min(right(), other.right()) - left),
            Math.max(0, Math.min(bottom(), other.bottom()) - top));
    }
}
