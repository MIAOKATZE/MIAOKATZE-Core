package com.miaokatze.miaokatzecore.client.camera.hud;

/** Shared, Minecraft-free drawing and hit geometry, including tiny/resized canvases. */
public final class CameraHudLayout {

    public static final double WIDTH = 192;
    public static final double HEIGHT = 166;
    public static final double HEADER_HEIGHT = 18;

    public enum Control {
        NONE,
        PANEL,
        HEADER,
        COLLAPSE,
        YAW_MINUS,
        YAW_PLUS,
        PITCH_MINUS,
        PITCH_PLUS,
        ZOOM_MINUS,
        ZOOM_PLUS,
        RESET,
        SLICE_TOGGLE,
        SLICE_DIRECTION,
        HEIGHT,
        FEATHER
    }

    public final double x, y, scale, width, height;
    public final boolean collapsed;
    public final boolean sliceBar;
    private final boolean directionVisible;

    public CameraHudLayout(int canvasWidth, int canvasHeight, double requestedX, double requestedY,
        double requestedWidth, boolean collapsed) {
        this(canvasWidth, canvasHeight, requestedX, requestedY, requestedWidth, collapsed, false, true);
    }

    public CameraHudLayout(int canvasWidth, int canvasHeight, double requestedX, double requestedY,
        double requestedWidth, boolean collapsed, boolean sliceBar, boolean directionVisible) {
        this.collapsed = collapsed && !sliceBar;
        this.sliceBar = sliceBar;
        this.directionVisible = directionVisible;
        double availableWidth = Math.max(1, canvasWidth);
        double availableHeight = Math.max(1, canvasHeight);
        double logicalWidth = sliceBar ? 72 : WIDTH;
        double logicalHeight = sliceBar ? 220 : collapsed ? HEADER_HEIGHT : HEIGHT;
        scale = Math.min(
            Math.max(1, requestedWidth) / WIDTH,
            Math.min(availableWidth / logicalWidth, availableHeight / logicalHeight));
        width = logicalWidth * scale;
        height = logicalHeight * scale;
        x = clamp(requestedX, 0, availableWidth - width);
        y = clamp(requestedY, 0, availableHeight - height);
    }

    public double localX(double screenX) {
        return (screenX - x) / scale;
    }

    public double localY(double screenY) {
        return (screenY - y) / scale;
    }

    public Control hit(double screenX, double screenY) {
        double px = localX(screenX), py = localY(screenY);
        if (sliceBar) {
            if (px < 0 || px >= 72 || py < 0 || py >= 220) return Control.NONE;
            if (py < 18) return Control.HEADER;
            if (directionVisible && px >= 4 && px < 68 && py >= 22 && py < 38) return Control.SLICE_DIRECTION;
            if (px >= 20 && px <= 52 && py >= 60 && py <= 208) return Control.HEIGHT;
            return Control.PANEL;
        }
        if (px < 0 || px >= WIDTH || py < 0 || py >= (collapsed ? HEADER_HEIGHT : HEIGHT)) return Control.NONE;
        if (py < HEADER_HEIGHT) return px >= WIDTH - 20 ? Control.COLLAPSE : Control.HEADER;
        if (py >= 23 && py < 39) return threeButtons(px, Control.YAW_MINUS, Control.YAW_PLUS, Control.RESET);
        if (py >= 43 && py < 59) return twoButtons(px, Control.PITCH_MINUS, Control.PITCH_PLUS);
        if (py >= 63 && py < 79) return twoButtons(px, Control.ZOOM_MINUS, Control.ZOOM_PLUS);
        if (py >= 84 && py < 100) return px >= 8 && px < 78 ? Control.SLICE_TOGGLE
            : px >= 82 && px < 184 ? Control.SLICE_DIRECTION : Control.PANEL;
        if (px >= 10 && px <= 182 && py >= 116 && py < 131) return Control.HEIGHT;
        if (px >= 10 && px <= 182 && py >= 147 && py < 160) return Control.FEATHER;
        return Control.PANEL;
    }

    /** Slider endpoints are inclusive: left=min and right=max. */
    public double sliderFraction(double screenX) {
        return clamp((localX(screenX) - 10) / 172, 0, 1);
    }

    /** Vertical slice slider: top=max, bottom=min. */
    public double verticalFraction(double screenY) {
        return clamp((204 - localY(screenY)) / 140, 0, 1);
    }

    private static Control threeButtons(double x, Control a, Control b, Control c) {
        return x >= 8 && x < 64 ? a : x >= 68 && x < 124 ? b : x >= 128 && x < 184 ? c : Control.PANEL;
    }

    private static Control twoButtons(double x, Control a, Control b) {
        return x >= 8 && x < 94 ? a : x >= 98 && x < 184 ? b : Control.PANEL;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
