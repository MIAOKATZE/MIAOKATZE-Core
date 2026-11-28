// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.tooltip.art;

import java.util.function.IntConsumer;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;

/**
 * Draws an 8x8 signature immediately before a heading. The caller reserves 10 GUI px, measures the real font,
 * and draws the heading at x+10 using the supplied ARGB. Body text, quantities and all other tooltips stay untouched.
 */
public final class TooltipArtRenderer {

    private TooltipArtRenderer() {}

    public static void drawLabel(TooltipArt art, int x, int y, int textWidth, boolean animated, IntConsumer drawText) {
        if (art == TooltipArt.PLAIN) {
            drawText.accept(0xffffffff);
            return;
        }
        ArtPlate plate = ArtPlate.of(art);
        float phase = animated ? TooltipAnimationClock.phase(TooltipAnimationClock.nowNanos(), plate.period) : 0.25f;
        drawSeal(plate, x, y, phase, animated);
        drawText.accept(plate.labelColor(phase));
    }

    /** Draw the authored mask and continuous glint in one vanilla batch. No GPU program or matrix readback. */
    private static void drawSeal(ArtPlate plate, int x, int y, float phase, boolean animated) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if ((plate.rows[r] & 128 >> c) == 0) continue;
                    tess.setColorOpaque_I(animated ? plate.pixelColor(c, r, phase) : plate.ink);
                    tess.addVertex(x + c, y + r, 0);
                    tess.addVertex(x + c, y + r + 1, 0);
                    tess.addVertex(x + c + 1, y + r + 1, 0);
                    tess.addVertex(x + c + 1, y + r, 0);
                }
            }
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
