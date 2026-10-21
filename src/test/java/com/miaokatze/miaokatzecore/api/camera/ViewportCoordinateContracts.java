package com.miaokatze.miaokatzecore.api.camera;

import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;

/** Pure Java contracts for nested canvases, GUI/NDC coordinates and clipped framebuffer coverage. */
public final class ViewportCoordinateContracts {

    private ViewportCoordinateContracts() {}

    public static void main(String[] args) {
        canvas();
        viewport();
        framebuffer();
        validation();
        System.out.println("ViewportCoordinateContracts: PASS (canvas, NDC, framebuffer, validation)");
    }

    private static void canvas() {
        CanvasTransform canvas = new CanvasTransform(-20, 15, 40, -10, .75);
        near(40, canvas.toScreenX(-20), "source anchor reaches screen X");
        near(-10, canvas.toScreenY(15), "source anchor reaches screen Y");
        near(46, canvas.toScreenX(-12), "scaled right movement");
        near(-16, canvas.toScreenY(7), "scaled up movement");
        for (double local : new double[] { -100, -.5, 0, 2.25, 1000 }) {
            near(local, canvas.toLocalX(canvas.toScreenX(local)), "X round trip with negative origin");
            near(local, canvas.toLocalY(canvas.toScreenY(local)), "Y round trip with negative origin");
        }
        near(
            7.25,
            CanvasTransform.identity()
                .toScreenX(7.25),
            "identity screen");
        near(
            -3,
            CanvasTransform.identity()
                .toLocalY(-3),
            "identity local");
        CanvasTransform parent = new CanvasTransform(10, -5, 100, 200, 2);
        CanvasTransform composed = canvas.compose(parent);
        near(172, composed.toScreenX(-12), "child position converted to parent screen X");
        near(178, composed.toScreenY(7), "child position converted to parent screen Y");
        near(1.5, composed.scale(), "composed scale");
        near(-12, composed.toLocalX(172), "composed inverse");
    }

    private static void viewport() {
        ViewportCoordinates viewport = new ViewportCoordinates(-20, 10, 80, 40);
        check(viewport.contains(-20, 10), "top-left included");
        check(!viewport.contains(60, 10) && !viewport.contains(-20, 50), "right and bottom excluded");
        check(!viewport.contains(-20.01, 10) && !viewport.contains(0, Double.NaN), "outside points rejected");
        near(-1, viewport.ndcX(-20), "left NDC edge");
        near(1, viewport.ndcY(10), "top NDC edge points up");
        near(1, viewport.ndcX(60), "right edge remains projectable");
        near(-1, viewport.ndcY(50), "bottom NDC edge");
        near(0, viewport.ndcX(20), "center X");
        near(0, viewport.ndcY(30), "center Y");
        near(2, viewport.ndcX(100), "outside NDC not clamped");
        for (double ndc : new double[] { -2, -1, -.25, 0, 1, 3 }) {
            near(ndc, viewport.ndcX(viewport.screenX(ndc)), "screen/NDC X inverse");
            near(ndc, viewport.ndcY(viewport.screenY(ndc)), "screen/NDC Y inverse");
        }
        ViewportCoordinates empty = new ViewportCoordinates(1.25, 2.75, 0, 0);
        check(!empty.contains(1.25, 2.75), "zero area contains nothing");
        near(1.25, empty.screenX(.5), "zero width maps to anchor");
        near(2.75, empty.screenY(-.5), "zero height maps to anchor");
        expect(IllegalStateException.class, () -> empty.ndcX(1.25));
        expect(IllegalStateException.class, () -> empty.ndcY(2.75));
    }

    private static void framebuffer() {
        rect(new ViewportCoordinates(10, 20, 30, 40), 100, 100, 200, 300, 20, 120, 60, 120);
        // 101/80 and 77/60 are independent nonintegral GUI-to-display scales.
        rect(new ViewportCoordinates(10, 10, 20, 20), 80, 60, 101, 77, 12, 38, 26, 27);
        rect(new ViewportCoordinates(-10, -5, 40, 25), 100, 50, 200, 100, 0, 60, 60, 40);
        rect(new ViewportCoordinates(90, 40, 40, 30), 100, 50, 200, 100, 180, 0, 20, 20);
        rect(new ViewportCoordinates(-30, 0, 10, 50), 100, 50, 200, 100, 0, 0, 0, 100);
        rect(new ViewportCoordinates(0, 70, 100, 10), 100, 50, 200, 100, 0, 0, 200, 0);
        rect(new ViewportCoordinates(1.25, 2.75, 0, 0), 10, 10, 13, 17, 1, 12, 0, 0);
        rect(new ViewportCoordinates(0, 0, 100, 50), 100, 50, 0, 0, 0, 0, 0, 0);
        // Exact boundaries must not acquire an extra pixel through 1 - 1/3 cancellation.
        rect(new ViewportCoordinates(1, 1, 1, 1), 3, 3, 3, 3, 1, 1, 1, 1);
        rect(new ViewportCoordinates(29, 29, 1, 1), 100, 100, 100, 100, 29, 70, 1, 1);
        rect(
            new ViewportCoordinates(0, 0, Double.MAX_VALUE, Double.MAX_VALUE),
            Double.MAX_VALUE,
            Double.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            0,
            0,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE);
    }

    private static void validation() {
        expect(IllegalArgumentException.class, () -> new CanvasTransform(0, 0, 0, 0, 0));
        expect(IllegalArgumentException.class, () -> new CanvasTransform(0, 0, 0, 0, -1));
        expect(IllegalArgumentException.class, () -> new CanvasTransform(0, 0, 0, 0, Double.NaN));
        expect(IllegalArgumentException.class, () -> new CanvasTransform(0, 0, 0, 0, Double.POSITIVE_INFINITY));
        expect(IllegalArgumentException.class, () -> new CanvasTransform(Double.NaN, 0, 0, 0, 1));
        expect(IllegalArgumentException.class, () -> new ViewportCoordinates(0, 0, -1, 1));
        expect(IllegalArgumentException.class, () -> new ViewportCoordinates(0, 0, 1, Double.POSITIVE_INFINITY));
        expect(IllegalArgumentException.class, () -> new ViewportCoordinates(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
        ViewportCoordinates viewport = new ViewportCoordinates(0, 0, 1, 1);
        expect(IllegalArgumentException.class, () -> viewport.framebufferRect(0, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> viewport.framebufferRect(1, Double.NaN, 1, 1));
        expect(IllegalArgumentException.class, () -> viewport.framebufferRect(1, 1, -1, 1));
        expect(IllegalArgumentException.class, () -> viewport.ndcX(Double.NaN));
    }

    private static void rect(ViewportCoordinates viewport, double guiWidth, double guiHeight, int displayWidth,
        int displayHeight, int left, int bottom, int width, int height) {
        ViewportCoordinates.FramebufferRect rect = viewport
            .framebufferRect(guiWidth, guiHeight, displayWidth, displayHeight);
        check(
            rect.left() == left && rect.bottom() == bottom && rect.width() == width && rect.height() == height,
            "framebuffer expected " + left
                + ","
                + bottom
                + " "
                + width
                + "x"
                + height
                + " but got "
                + rect.left()
                + ","
                + rect.bottom()
                + " "
                + rect.width()
                + "x"
                + rect.height());
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void near(double expected, double actual, String label) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= 1e-9, label + ": " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
