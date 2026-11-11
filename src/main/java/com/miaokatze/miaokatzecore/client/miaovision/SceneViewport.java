// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovision;

/** Framebuffer pixels, bottom-left origin. The MUI consumer converts its actual transformed widget rectangle. */
public final class SceneViewport {

    public final int x, y, width, height;

    public SceneViewport(int x, int y, int width, int height) {
        if (x < 0 || y < 0
            || width <= 0
            || height <= 0
            || width > 32768
            || height > 32768
            || (long) x + width > Integer.MAX_VALUE
            || (long) y + height > Integer.MAX_VALUE) throw new IllegalArgumentException("viewport");
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
