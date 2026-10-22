package com.miaokatze.miaokatzecore.client.camera;

/** GUI focus differs from native window focus. These contracts need neither Minecraft nor OpenGL. */
final class CameraActivityContracts {

    private CameraActivityContracts() {}

    static void run() {
        for (WorldCameras.GuiPolicy policy : WorldCameras.GuiPolicy.values()) {
            require(CameraActivity.visible(true, false, true, true, policy), "focused world renders for " + policy);
            require(CameraActivity.interactive(true, false, true, true), "focused world remains interactive");
            require(
                CameraActivity.visible(true, true, false, true, policy) == (policy == WorldCameras.GuiPolicy.KEEP),
                "real GUI preserves only KEEP after vanilla clears game focus: " + policy);
            require(
                CameraActivity.visible(true, true, true, true, policy) == (policy == WorldCameras.GuiPolicy.KEEP),
                "stale game focus cannot bypass SUSPEND or RELEASE: " + policy);
            require(!CameraActivity.interactive(true, true, false, true), "GUI owns input even while KEEP renders");
            require(!CameraActivity.interactive(true, true, true, true), "a GUI cannot inherit stale game focus");
            for (boolean guiOpen : new boolean[] { false, true }) {
                require(
                    !CameraActivity.visible(true, guiOpen, true, false, policy),
                    "native focus loss revokes picture even before game focus updates: " + policy);
                require(
                    !CameraActivity.interactive(true, guiOpen, true, false),
                    "native focus loss revokes navigation and picking");
                require(
                    !CameraActivity.visible(false, guiOpen, true, true, policy),
                    "replaced world, disconnected world or stale lease never renders: " + policy);
                require(
                    !CameraActivity.interactive(false, guiOpen, true, true),
                    "invalid world/lease cannot acquire input");
            }
            require(
                !CameraActivity.visible(true, false, false, true, policy),
                "KEEP cannot bypass missing game focus without an actual GUI: " + policy);
            require(!CameraActivity.interactive(true, false, false, true), "closing GUI waits for game focus");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
