package com.miaokatze.miaokatzecore.client.camera.hud;

/** Executed against the production layout/conversion classes, without Minecraft or OpenGL. */
public final class CameraHudContracts {

    private static int checks;

    public static void main(String[] args) {
        near(CameraPointerCapture.guiX(0, 1920, 960), 0, "left physical edge");
        near(CameraPointerCapture.guiX(1919, 1920, 960), 959.5, "right physical edge");
        near(CameraPointerCapture.guiY(1079, 1080, 540), 0, "top physical edge");
        near(CameraPointerCapture.guiY(0, 1080, 540), 539.5, "bottom physical edge");
        near(CameraPointerCapture.guiY(719, 720, 360), 0, "resize uses current dimensions");
        int[] sizes = { 1, 2, 16, 80, 165, 192, 320, 1080 };
        for (int width : sizes) for (int height : sizes) for (boolean collapsed : new boolean[] { false, true }) {
            CameraHudLayout layout = new CameraHudLayout(width, height, 5000, -100, 384, collapsed);
            require(
                layout.x >= 0 && layout.y >= 0
                    && layout.x + layout.width <= width + 1e-9
                    && layout.y + layout.height <= height + 1e-9,
                "all controls stay within canvas");
            double cx = layout.x + layout.width * .5, cy = layout.y + 8 * layout.scale;
            require(layout.hit(cx, cy) == CameraHudLayout.Control.HEADER, "draw/hit share scale and origin");
            require(layout.hit(layout.x - .001, cy) == CameraHudLayout.Control.NONE, "outside does not capture");
            near(layout.sliderFraction(layout.x - 100), 0, "drag clamps outside left");
            near(layout.sliderFraction(layout.x + layout.width + 100), 1, "drag clamps outside right");
        }
        CameraHudLayout normal = new CameraHudLayout(640, 360, 12, 14, 192, false);
        require(normal.hit(12 + 10, 14 + 120) == CameraHudLayout.Control.HEIGHT, "height track hit");
        require(normal.hit(12 + 10, 14 + 150) == CameraHudLayout.Control.FEATHER, "feather track hit");
        require(normal.hit(12 + 130, 14 + 30) == CameraHudLayout.Control.RESET, "reset button hit");
        near(normal.sliderFraction(12 + 10), 0, "inclusive min slider endpoint");
        near(normal.sliderFraction(12 + 182), 1, "inclusive max slider endpoint");
        require(normal.hit(12 + 182, 14 + 120) == CameraHudLayout.Control.HEIGHT, "maximum Y endpoint is clickable");
        require(
            normal.hit(12 + 182, 14 + 150) == CameraHudLayout.Control.FEATHER,
            "maximum feather endpoint is clickable");
        CameraHudLayout folded = new CameraHudLayout(640, 360, 12, 14, 192, true);
        require(folded.hit(20, 50) == CameraHudLayout.Control.NONE, "folded body does not capture world input");
        CameraHudLayout bar = new CameraHudLayout(640, 360, 12, 14, 192, false, true, true);
        near(bar.width, 72, "compact bar leaves room for world");
        require(bar.hit(48, 84) == CameraHudLayout.Control.HEIGHT, "vertical height track hit");
        require(bar.hit(20, 40) == CameraHudLayout.Control.SLICE_DIRECTION, "direction button hit");
        near(bar.verticalFraction(14 + 64), 1, "vertical top is maximum Y");
        near(bar.verticalFraction(14 + 204), 0, "vertical bottom is minimum Y");
        near(bar.verticalFraction(-1000), 1, "vertical drag clamps above canvas");
        near(bar.verticalFraction(1000), 0, "vertical drag clamps below canvas");
        require(bar.hit(90, 84) == CameraHudLayout.Control.NONE, "compact bar does not capture old panel area");
        for (int width : sizes) for (int height : sizes) {
            CameraHudLayout tiny = new CameraHudLayout(width, height, 5000, -100, 192, true, true, false);
            require(
                tiny.x >= 0 && tiny.y >= 0
                    && tiny.x + tiny.width <= width + 1e-9
                    && tiny.y + tiny.height <= height + 1e-9,
                "compact bar fits resized/tiny viewport");
        }
        System.out.println("PASS " + checks + " production HUD geometry/conversion assertions");
    }

    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-9, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
