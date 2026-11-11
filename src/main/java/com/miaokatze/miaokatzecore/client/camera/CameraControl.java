// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.camera;

/** Actions the current source can elect to receive from Core's controls. */
public enum CameraControl {
    PAN,
    YAW,
    PITCH,
    ZOOM,
    RESET,
    SLICE
}
