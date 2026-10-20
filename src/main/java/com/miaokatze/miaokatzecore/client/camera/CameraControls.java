package com.miaokatze.miaokatzecore.client.camera;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Narrow requests to the consumer's existing camera state. Core retains no second navigation state. */
public interface CameraControls {

    CameraControls NONE = new CameraControls() {};

    default boolean supports(CameraControl control) {
        return false;
    }

    default void panWorld(double dx, double dy, double dz) {}

    default void rotate(double yawDeltaDegrees, double pitchDeltaDegrees) {}

    default void zoom(double factor) {}

    default void resetView() {}

    default void requestSlice(HeightSlice slice) {}
}
