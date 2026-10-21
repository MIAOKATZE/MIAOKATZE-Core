package com.miaokatze.miaokatzecore.api.camera;

/**
 * Immutable GUI rectangle with a top-left origin; NDC Y points upward. No graphics state is read.
 * Mapping arguments must be finite; invalid arguments throw IllegalArgumentException.
 */
public final class ViewportCoordinates {

    private final double x, y, width, height;

    /** Finite positions and nonnegative finite sizes are required, including finite right/bottom edges. */
    public ViewportCoordinates(double x, double y, double width, double height) {
        this.x = CameraNumbers.finite(x, "x");
        this.y = CameraNumbers.finite(y, "y");
        this.width = nonnegative(width, "width");
        this.height = nonnegative(height, "height");
        CameraNumbers.finite(x + width, "right");
        CameraNumbers.finite(y + height, "bottom");
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** Left/top inclusive, right/bottom exclusive. Empty rectangles and nonfinite points never contain a point. */
    public boolean contains(double screenX, double screenY) {
        return screenX >= x && screenX < x + width && screenY >= y && screenY < y + height;
    }

    /** Does not clamp outside points; throws IllegalStateException when width is zero. */
    public double ndcX(double screenX) {
        CameraNumbers.finite(screenX, "screenX");
        if (width == 0) throw new IllegalStateException("Cannot normalize a zero-width viewport");
        return 2 * ((screenX - x) / width) - 1;
    }

    /** Does not clamp outside points; throws IllegalStateException when height is zero. */
    public double ndcY(double screenY) {
        CameraNumbers.finite(screenY, "screenY");
        if (height == 0) throw new IllegalStateException("Cannot normalize a zero-height viewport");
        return 1 - 2 * ((screenY - y) / height);
    }

    /** Maps NDC -1/+1 to the left/right edges, even when they coincide. */
    public double screenX(double ndcX) {
        return x + (CameraNumbers.finite(ndcX, "ndcX") + 1) * .5 * width;
    }

    /** Maps NDC +1/-1 to the top/bottom edges, even when they coincide. */
    public double screenY(double ndcY) {
        return y + (1 - CameraNumbers.finite(ndcY, "ndcY")) * .5 * height;
    }

    /**
     * Clips to the GUI bounds, maps to framebuffer pixels with a bottom-left origin, and rounds outward
     * (floor left/bottom, ceil right/top). Empty axes stay empty, including fractional edges. GUI sizes must
     * be positive and finite; display sizes may be zero for a minimized window, but never negative.
     */
    public FramebufferRect framebufferRect(double guiWidth, double guiHeight, int displayWidth, int displayHeight) {
        CameraNumbers.positive(guiWidth, "guiWidth");
        CameraNumbers.positive(guiHeight, "guiHeight");
        if (displayWidth < 0 || displayHeight < 0) {
            throw new IllegalArgumentException("Display sizes must be nonnegative");
        }
        double left = clamp(x, guiWidth), right = clamp(x + width, guiWidth);
        double top = clamp(y, guiHeight), bottom = clamp(y + height, guiHeight);
        int pixelLeft = (int) Math.floor(pixelCoordinate(left, guiWidth, displayWidth));
        int pixelBottom = (int) Math.floor(pixelCoordinate(guiHeight - bottom, guiHeight, displayHeight));
        int pixelRight = right <= left ? pixelLeft : (int) Math.ceil(pixelCoordinate(right, guiWidth, displayWidth));
        int pixelTop = bottom <= top ? pixelBottom
            : (int) Math.ceil(pixelCoordinate(guiHeight - top, guiHeight, displayHeight));
        return new FramebufferRect(pixelLeft, pixelBottom, pixelRight - pixelLeft, pixelTop - pixelBottom);
    }

    private static double nonnegative(double value, String name) {
        CameraNumbers.finite(value, name);
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    private static double clamp(double value, double maximum) {
        return Math.max(0, Math.min(value, maximum));
    }

    private static double pixelCoordinate(double distance, double guiSize, int pixels) {
        // Multiplication first preserves exact integral edges at scales such as 3/3 and 100/100.
        double product = distance * pixels;
        double result = Double.isFinite(product) ? product / guiSize : distance / guiSize * pixels;
        return Math.max(0, Math.min(result, pixels));
    }

    /** Integer OpenGL viewport/scissor bounds, always contained in the supplied framebuffer. */
    public static final class FramebufferRect {

        private final int left, bottom, width, height;

        private FramebufferRect(int left, int bottom, int width, int height) {
            this.left = left;
            this.bottom = bottom;
            this.width = width;
            this.height = height;
        }

        public int left() {
            return left;
        }

        public int bottom() {
            return bottom;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }
}
