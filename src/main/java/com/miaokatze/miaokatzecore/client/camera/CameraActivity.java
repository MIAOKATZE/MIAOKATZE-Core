package com.miaokatze.miaokatzecore.client.camera;

/** Rendering may survive a GUI; world input always requires game focus and an active native window. */
final class CameraActivity {

    private CameraActivity() {}

    static boolean visible(boolean valid, boolean guiOpen, boolean gameFocus, boolean windowActive,
        WorldCameras.GuiPolicy policy) {
        return valid && windowActive && (guiOpen ? policy == WorldCameras.GuiPolicy.KEEP : gameFocus);
    }

    static boolean interactive(boolean valid, boolean guiOpen, boolean gameFocus, boolean windowActive) {
        return valid && windowActive && !guiOpen && gameFocus;
    }

    static boolean releasesForGui(boolean guiOpen, WorldCameras.GuiPolicy policy, boolean automaticPause) {
        return guiOpen && policy == WorldCameras.GuiPolicy.RELEASE && !automaticPause;
    }
}
