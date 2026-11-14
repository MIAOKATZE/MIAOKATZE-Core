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
        if (!animated || !SealShader.draw(plate, x, y, phase)) drawStatic(plate, x, y);
        drawText.accept(plate.labelColor(phase));
    }

    /** Draw exactly the same silhouette on backends without the seal program. Does not change any texture binding. */
    private static void drawStatic(ArtPlate plate, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setColorOpaque_I(plate.ink);
            for (int r = 0; r < 8; r++) {
                int row = plate.rows[r], c = 0;
                while (c < 8) {
                    if ((row & 128 >> c) == 0) {
                        c++;
                        continue;
                    }
                    int start = c++;
                    while (c < 8 && (row & 128 >> c) != 0) c++;
                    tess.addVertex(x + start, y + r, 0);
                    tess.addVertex(x + start, y + r + 1, 0);
                    tess.addVertex(x + c, y + r + 1, 0);
                    tess.addVertex(x + c, y + r, 0);
                }
            }
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
