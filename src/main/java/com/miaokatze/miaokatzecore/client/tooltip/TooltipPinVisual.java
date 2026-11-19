// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** The reviewed 12x12 pin. Geometry never changes between the six pinned highlight cels. */
final class TooltipPinVisual {

    private static final int[] BINDING = { 0xffff55, 0xffff67, 0xffff79, 0xffff8c, 0xffff9e, 0xffffb0 };

    private TooltipPinVisual() {}

    static int frame(boolean pinned, long nanos, boolean reduced) {
        if (reduced) return 0;
        if (!pinned) return (int) Math.floorMod(nanos / 1200000000L, 2L);
        int beat = (int) Math.floorMod(nanos / 500000000L, 10L);
        return Math.min(beat, 10 - beat);
    }

    static int cel(boolean pinned, int frame) {
        return pinned ? frame + 2 : frame;
    }

    static int binding(boolean pinned, int frame, boolean reduced) {
        return 0xff000000 | (!pinned ? 0xb6b9c6 : reduced ? 0xffff55 : BINDING[frame]);
    }
}
