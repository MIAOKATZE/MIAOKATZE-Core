// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** Exercises the production painter's pixel and color policy without Minecraft or OpenGL. */
public final class TooltipPinVisualContracts {

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        int[] beats = { 0, 1, 2, 3, 4, 5, 4, 3, 2, 1 };
        for (int tick = 0; tick <= 200; tick++) {
            long nanos = tick * 50000000L;
            int expected = beats[tick / 10 % 10];
            check(TooltipPinVisual.frame(true, nanos, false) == expected, "0.5s hold / 5s round trip at " + tick);
            check(TooltipPinVisual.frame(true, nanos, true) == 0, "Reduced motion frame");
            check(TooltipPinVisual.body(true, expected, true) == 0xffffff55, "Reduced motion text");
            check(TooltipPinVisual.binding(true, expected, true) == 0xffffff55, "Reduced motion binding");
            check(TooltipPinVisual.body(false, expected, false) == 0xffb6b9c6, "Floating gray text");
            check(TooltipPinVisual.binding(false, expected, false) == 0xffffff55, "Floating yellow binding");
        }
        int[] body = { 0xffe6b83d, 0xffebc64c, 0xfff0d45c, 0xfff5e36b, 0xfffaf17b, 0xffffff8a };
        int[] key = { 0xffffff55, 0xffffff67, 0xffffff79, 0xffffff8c, 0xffffff9e, 0xffffffb0 };
        int[] cap = { 0xffffd19d, 0xffffd9a7, 0xffffe1b1, 0xffffe9bb, 0xfffff1c5, 0xfffff9cf };
        for (int frame = 0; frame < 6; frame++) {
            int highlights = 0, filled = 0;
            check(TooltipPinVisual.body(true, frame, false) == body[frame], "Pinned text palette");
            check(TooltipPinVisual.binding(true, frame, false) == key[frame], "Pinned key palette");
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int baseline = TooltipPinVisual.pixel(true, 0, x, y);
                    int actual = TooltipPinVisual.pixel(true, frame, x, y);
                    check((baseline == 0) == (actual == 0), "Pinned silhouette drift");
                    if (actual != 0) filled++;
                    if (baseline == cap[0]) {
                        highlights++;
                        check(actual == cap[frame], "Highlight frame differs from text frame");
                    } else check(actual == baseline, "Solid red cap / silver needle changed");
                }
            }
            check(highlights == 3 && filled > 45, "Solid pin occupancy");
            check(TooltipPinVisual.pixel(true, frame, 5, 9) == 0xffd0e1e0, "Needle remains silver");
            check(TooltipPinVisual.pixel(true, frame, 5, 3) == 0xffdf695f, "Cap remains filled red");
        }
        for (int y = 1; y < 12; y++) for (int x = 0; x < 12; x++) check(
            TooltipPinVisual.pixel(false, 1, x, y) == TooltipPinVisual.pixel(false, 0, x, y - 1),
            "Floating cel is the reviewed one-pixel drop");
        System.out
            .println("TooltipPinVisualContracts: PASS (201 ticks, six shared ink frames, fixed red cap/silver needle)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
