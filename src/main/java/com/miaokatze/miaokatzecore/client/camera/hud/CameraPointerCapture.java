package com.miaokatze.miaokatzecore.client.camera.hud;

/**
 * Converts LWJGL bottom-left physical pixels to top-left GUI coordinates.
 * Ownership, button capture and stale-release quarantine belong to the camera input router.
 */
public final class CameraPointerCapture {

    private CameraPointerCapture() {}

    public static double guiX(int physicalX, int displayWidth, int guiWidth) {
        return (double) physicalX * Math.max(1, guiWidth) / Math.max(1, displayWidth);
    }

    public static double guiY(int physicalY, int displayHeight, int guiHeight) {
        return (double) (Math.max(1, displayHeight) - 1 - physicalY) * Math.max(1, guiHeight)
            / Math.max(1, displayHeight);
    }
}
