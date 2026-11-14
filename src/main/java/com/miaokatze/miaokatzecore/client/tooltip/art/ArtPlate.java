// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.tooltip.art;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;

/** Original 8x8 lead seals. Bit 7 is the left edge. No glyph masks or assets from other mods. */
enum ArtPlate {

    ACCENT(0xb6c6ce, 0xe0ebed, 4300, 0, 0x001c222222221c00L),
    GRID(0x64c7b0, 0xc4ffe6, 2600, 1, 0x00606018187e0606L),
    DATA_STREAM(0x7bc5ef, 0xd2f6ff, 3100, 2, 0x005f003e007a0000L),
    QUANTUM(0xa79bdd, 0xe5daff, 3800, 3, 0x1824428181422418L),
    STEAM(0x88c5cb, 0xe4faf2, 4000, 4, 0x0024482424007e3cL),
    RIFT(0xc397dc, 0xf3cef2, 4900, 5, 0x0c183016682c1800L),
    SHARD(0xa8bacd, 0xebdee7, 4100, 6, 0x1030385513222400L),
    CAT(0xe5ab92, 0xffe5ce, 5600, 7, 0x0042667e5a7e3c00L),
    COIN(0xe9bd69, 0xfff2bd, 4500, 8, 0x183c664a52663c18L),
    HEARTH(0xd99999, 0xffe6cc, 6200, 9, 0x0066ffff7e3c1800L),
    BLUEPRINT(0x77bce3, 0xd2edff, 3900, 10, 0x00fe829286a2be80L);

    final int ink, light, period, path;
    final long mask;
    final int[] rows = new int[8];

    ArtPlate(int ink, int light, int period, int path, long mask) {
        this.ink = ink;
        this.light = light;
        this.period = period;
        this.path = path;
        this.mask = mask;
        for (int y = 0; y < 8; y++) rows[y] = (int) (mask >>> (56 - 8 * y)) & 255;
    }

    static ArtPlate of(TooltipArt art) {
        return valueOf(art.name());
    }

    int labelColor(float phase) {
        // Low excursion: a heading remains readable beside white quantities, even at minimum luminance.
        double breath = 0.42 + 0.17 * Math.cos(phase * 2 * Math.PI);
        int r = mix(ink >> 16 & 255, light >> 16 & 255, breath);
        int g = mix(ink >> 8 & 255, light >> 8 & 255, breath);
        int b = mix(ink & 255, light & 255, breath);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
