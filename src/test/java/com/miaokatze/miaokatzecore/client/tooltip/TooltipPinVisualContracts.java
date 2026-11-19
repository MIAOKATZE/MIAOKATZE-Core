// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/** Exercises the production texture and frame policy without Minecraft or OpenGL. */
public final class TooltipPinVisualContracts {

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        BufferedImage atlas;
        try (InputStream asset = TooltipPinVisual.class
            .getResourceAsStream("/assets/miaokatzecore/textures/tooltip/pin.png")) {
            check(asset != null, "Packaged pin atlas is present");
            atlas = ImageIO.read(asset);
        } catch (IOException e) {
            throw new AssertionError("Read pin atlas", e);
        }
        check(atlas.getWidth() == 96 && atlas.getHeight() == 12, "Eight 12-by-12 cels");
        int[] beats = { 0, 1, 2, 3, 4, 5, 4, 3, 2, 1 };
        for (int tick = 0; tick <= 200; tick++) {
            long nanos = tick * 50000000L;
            int expected = beats[tick / 10 % 10];
            check(TooltipPinVisual.frame(true, nanos, false) == expected, "0.5s hold / 5s round trip at " + tick);
            check(TooltipPinVisual.frame(true, nanos, true) == 0, "Reduced motion frame");
            check(TooltipPinVisual.binding(true, expected, true) == 0xffffff55, "Reduced motion binding");
            check(TooltipPinVisual.binding(false, expected, false) == 0xffb6b9c6, "Floating gray binding");
        }
        int[] key = { 0xffffff55, 0xffffff67, 0xffffff79, 0xffffff8c, 0xffffff9e, 0xffffffb0 };
        int[] cap = { 0xffffd19d, 0xffffd9a7, 0xffffe1b1, 0xffffe9bb, 0xfffff1c5, 0xfffff9cf };
        for (int frame = 0; frame < 6; frame++) {
            int highlights = 0, filled = 0;
            check(TooltipPinVisual.binding(true, frame, false) == key[frame], "Pinned key palette");
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int baseline = pixel(atlas, true, 0, x, y);
                    int actual = pixel(atlas, true, frame, x, y);
                    check((baseline == 0) == (actual == 0), "Pinned silhouette drift");
                    if (actual != 0) filled++;
                    if (baseline == cap[0]) {
                        highlights++;
                        check(actual == cap[frame], "Highlight frame differs from text frame");
                    } else check(actual == baseline, "Solid red cap / silver needle changed");
                }
            }
            check(highlights == 3 && filled > 45, "Solid pin occupancy");
            check(pixel(atlas, true, frame, 5, 9) == 0xffd0e1e0, "Needle remains silver");
            check(pixel(atlas, true, frame, 5, 3) == 0xffdf695f, "Cap remains filled red");
        }
        for (int y = 1; y < 12; y++) for (int x = 0; x < 12; x++) check(
            pixel(atlas, false, 1, x, y) == pixel(atlas, false, 0, x, y - 1),
            "Floating cel is the reviewed one-pixel drop");
        System.out.println("TooltipPinVisualContracts: PASS (packaged atlas, 201 ticks and six fixed cels)");
    }

    private static int pixel(BufferedImage atlas, boolean pinned, int frame, int x, int y) {
        return atlas.getRGB(TooltipPinVisual.cel(pinned, frame) * 12 + x, y);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
