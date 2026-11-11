// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

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
