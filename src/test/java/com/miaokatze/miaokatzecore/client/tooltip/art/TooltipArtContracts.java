// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip.art;

/** Pure author-mask and continuous palette checks; no GL context or shader creation. */
public final class TooltipArtContracts {
    public static void run() {
        for (ArtPlate plate : ArtPlate.values()) {
            long mask = 0;
            boolean changed = false;
            for (int y = 0; y < 8; y++) {
                mask = mask << 8 | plate.rows[y];
                for (int x = 0; x < 8; x++) {
                    int first = plate.pixelColor(x, y, 0);
                    if (first != plate.pixelColor(x, y, 1)) throw new AssertionError("Seal period changed");
                    changed |= first != plate.pixelColor(x, y, .25f);
                    for (int shift : new int[] { 0, 8, 16 }) {
                        int channel = first >> shift & 255;
                        int a = plate.ink >> shift & 255, b = plate.light >> shift & 255;
                        if (channel < Math.min(a, b) || channel > Math.max(a, b))
                            throw new AssertionError("Seal palette escaped the authored colors");
                    }
                }
            }
            if (mask != plate.mask || !changed) throw new AssertionError("Author mask or animation was lost");
        }
        System.out.println("TooltipArtContracts: PASS (11 author masks, palette bounds, continuous periodic animation)");
    }
}
