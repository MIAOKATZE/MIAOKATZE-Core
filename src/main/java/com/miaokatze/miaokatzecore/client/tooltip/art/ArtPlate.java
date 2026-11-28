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
    private final double[] travel = new double[64];

    ArtPlate(int ink, int light, int period, int path, long mask) {
        this.ink = ink;
        this.light = light;
        this.period = period;
        this.path = path;
        this.mask = mask;
        for (int y = 0; y < 8; y++) {
            rows[y] = (int) (mask >>> (56 - 8 * y)) & 255;
            for (int x = 0; x < 8; x++) travel[y * 8 + x] = travel(path, x, y);
        }
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

    /** The original seal shader's spatial phase, precomputed once for the author's 8x8 mask. */
    private static double travel(int motif, int px, int py) {
        double x = (px + .5) / 8, y = (py + .5) / 8;
        switch (motif) {
            case 0: return x + y;
            case 1: return y < .49 ? y * .55 : x * .55 + .35;
            case 2: return x + Math.floor(y * 4) * .19;
            case 3: return px + py < 8 ? .25 : .75;
            case 4: return 1 - y + x * .18;
            case 5: return y + (px < 4 ? .20 : -.20);
            case 6: return Math.floor((x * .62 + y) * 3) / 3;
            case 7: return py < 3 ? x * .3 : .65;
            case 8: return (x - y) * .65;
            case 9: return Math.hypot(x - .5, y - .65);
            case 10: return (py < 4 ? x : 1 - y) * .65;
            default: throw new IllegalArgumentException("Unknown seal motif");
        }
    }

    int pixelColor(int x, int y, float phase) {
        double t = travel[y * 8 + x] - phase + .5;
        double distance = Math.abs(t - Math.floor(t) - .5);
        double fade = Math.max(0, Math.min(1, (distance - .03) / .22));
        double amount = .15 + .80 * (1 - fade * fade * (3 - 2 * fade));
        return mix(ink >> 16 & 255, light >> 16 & 255, amount) << 16
            | mix(ink >> 8 & 255, light >> 8 & 255, amount) << 8
            | mix(ink & 255, light & 255, amount);
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
