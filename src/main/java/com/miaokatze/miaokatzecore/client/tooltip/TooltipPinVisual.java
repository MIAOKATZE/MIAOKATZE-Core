// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** The reviewed 12x12 pin. Geometry never changes between the six pinned highlight cels. */
final class TooltipPinVisual {

    private static final int[] PALETTE = { 0x783545, 0xaa4553, 0xdf695f, 0xffd19d, 0xd0e1e0, 0x8597a8, 0xeaf6ee,
        0xffd9a7, 0xffe1b1, 0xffe9bb, 0xfff1c5, 0xfff9cf };
    private static final String[] FLOATING = { ".......11...", "......1441..", "......34331.", "......123321",
        "...113321121", "..1433321.1.", "...133221...", "....122221..", "....56111...", "...56.......", "..7.........",
        "............" };
    private static final String[] PINNED = { "............", "....1111....", "...144331...", "...133321...",
        "....1221....", "....1321....", "...143321...", "..13333221..", "...122221...", ".....56.....", ".....76.....",
        "............" };
    private static final int[] BODY = { 0xe6b83d, 0xebc64c, 0xf0d45c, 0xf5e36b, 0xfaf17b, 0xffff8a };
    private static final int[] BINDING = { 0xffff55, 0xffff67, 0xffff79, 0xffff8c, 0xffff9e, 0xffffb0 };

    private TooltipPinVisual() {}

    static int frame(boolean pinned, long nanos, boolean reduced) {
        if (reduced) return 0;
        if (!pinned) return (int) Math.floorMod(nanos / 1200000000L, 2L);
        int beat = (int) Math.floorMod(nanos / 500000000L, 10L);
        return Math.min(beat, 10 - beat);
    }

    static int pixel(boolean pinned, int frame, int x, int y) {
        if (x < 0 || x >= 12 || y < 0 || y >= 12) return 0;
        if (!pinned && frame == 1) y--;
        if (y < 0) return 0;
        char pixel = (pinned ? PINNED : FLOATING)[y].charAt(x);
        if (pixel == '.') return 0;
        int index = Character.digit(pixel, 16) - 1;
        // Only the three authored cap highlights change ink; red fill and silver needle stay fixed.
        if (pinned && index == 3 && frame > 0) index = 6 + frame;
        return 0xff000000 | PALETTE[index];
    }

    static int body(boolean pinned, int frame, boolean reduced) {
        return 0xff000000 | (pinned ? reduced ? 0xffff55 : BODY[frame] : 0xb6b9c6);
    }

    static int binding(boolean pinned, int frame, boolean reduced) {
        return 0xff000000 | (pinned && !reduced ? BINDING[frame] : 0xffff55);
    }
}
