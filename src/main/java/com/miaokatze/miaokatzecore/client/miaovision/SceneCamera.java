package com.miaokatze.miaokatzecore.client.miaovision;

/** Display-only camera. Positive pitch views the room from above; no player/camera lease is acquired. */
public final class SceneCamera {

    public final double yawDegrees, pitchDegrees, zoom;

    public SceneCamera(double yawDegrees, double pitchDegrees, double zoom) {
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)
            || !Double.isFinite(zoom)
            || zoom <= 0
            || zoom > 100) throw new IllegalArgumentException("camera");
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = pitchDegrees;
        this.zoom = zoom;
    }
}
