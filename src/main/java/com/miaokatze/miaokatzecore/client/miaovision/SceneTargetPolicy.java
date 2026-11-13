// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovision;

import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;

/** Pure preflight policy. Unknown draw-buffer state never authorizes native rendering. */
final class SceneTargetPolicy {

    enum Target {

        SINGLE_COLOR(null),
        UNSUPPORTED("MiaoVision requires a single-color GUI target; MRT is unsupported"),
        UNAVAILABLE("MiaoVision scene skipped: SDL GPU does not provide indexed draw-buffer queries");

        final String reason;

        Target(String reason) {
            this.reason = reason;
        }
    }

    private SceneTargetPolicy() {}

    static Target inspect(BooleanSupplier sdlGPU, BooleanSupplier openGL20, IntUnaryOperator integer) {
        // Angelica 2.2.43 fabricates GL capabilities but returns zero for GL_DRAW_BUFFER0..N.
        // Do not mistake that missing query for GL_NONE, or touch GL/Tessellator state to discover it.
        if (sdlGPU.getAsBoolean()) return Target.UNAVAILABLE;
        if (!openGL20.getAsBoolean()) return Target.SINGLE_COLOR;
        if (integer.applyAsInt(0x8825 /* GL_DRAW_BUFFER0 */) == 0 /* GL_NONE */) return Target.UNSUPPORTED;
        int count = integer.applyAsInt(0x8824 /* GL_MAX_DRAW_BUFFERS */);
        for (int index = 1; index < count; index++)
            if (integer.applyAsInt(0x8825 + index) != 0) return Target.UNSUPPORTED;
        return Target.SINGLE_COLOR;
    }
}
