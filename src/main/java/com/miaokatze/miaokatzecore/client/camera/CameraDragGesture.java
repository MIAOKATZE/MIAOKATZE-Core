package com.miaokatze.miaokatzecore.client.camera;

/** Captured rotation gesture; crossing the threshold latches drag even when the pointer returns to its origin. */
final class CameraDragGesture {

    private int originX, originY, previousX, previousY, deltaX, deltaY;
    private double threshold;
    private boolean dragging;

    void begin(int x, int y, double pixelThreshold) {
        originX = previousX = x;
        originY = previousY = y;
        threshold = pixelThreshold;
        dragging = pixelThreshold == 0;
        deltaX = deltaY = 0;
    }

    boolean move(int x, int y) {
        double dx = (double) x - originX, dy = (double) y - originY;
        if (!dragging && dx * dx + dy * dy > threshold * threshold) dragging = true;
        if (!dragging) return false;
        deltaX = x - previousX;
        deltaY = y - previousY;
        previousX = x;
        previousY = y;
        return true;
    }

    int deltaX() {
        return deltaX;
    }

    int deltaY() {
        return deltaY;
    }

    boolean click() {
        return threshold > 0 && !dragging;
    }
}
