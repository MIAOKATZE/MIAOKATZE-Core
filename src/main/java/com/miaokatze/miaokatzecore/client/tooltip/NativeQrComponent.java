// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipQr;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

final class NativeQrComponent implements ITooltipComponent {

    private final TooltipQr qr;

    NativeQrComponent(String item, TooltipQr qr) {
        this.qr = qr;
    }

    @Override
    public int getWidth() {
        return Math.max(qr.side(), TooltipFontContext.getStringWidth(qr.text));
    }

    @Override
    public int getHeight() {
        return TooltipFontContext.getFontHeight() + 3 + qr.side();
    }

    @Override
    public int getSpacing() {
        return 2;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        if (width >= getWidth() && height >= getHeight()) return new ITooltipComponent[] { this };
        java.util.List<Object> lines = new java.util.ArrayList<>();
        lines.add(qr.text);
        NativeLinkComponent.convert(lines);
        Object line = lines.get(0);
        return (line instanceof ITooltipComponent ? (ITooltipComponent) line : new TextComponent(qr.text))
            .paginate(context, Math.max(1, width), height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        TooltipFontContext.drawString(qr.text, x, y);
        NativeTooltipMouse.textLinks(context, qr.text, x, y);
        y += TooltipFontContext.getFontHeight() + 3;
        int side = qr.side(), scale = qr.scale(), inset = TooltipQr.BORDER + TooltipQr.QUIET * scale;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Tessellator mesh = Tessellator.instance;
            mesh.startDrawingQuads();
            mesh.setColorOpaque_I(0x997aa6);
            rect(mesh, x, y, side, side);
            mesh.setColorOpaque_I(0xffffff);
            rect(mesh, x + 1, y + 1, side - 2, side - 2);
            mesh.setColorOpaque_I(0x111111);
            for (int row = 0; row < qr.modules(); row++) {
                int col = 0;
                while (col < qr.modules()) {
                    if (!qr.dark(col, row)) {
                        col++;
                        continue;
                    }
                    int start = col++;
                    while (col < qr.modules() && qr.dark(col, row)) col++;
                    rect(mesh, x + inset + start * scale, y + inset + row * scale, (col - start) * scale, scale);
                }
            }
            mesh.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void rect(Tessellator mesh, int x, int y, int w, int h) {
        mesh.addVertex(x, y + h, 0);
        mesh.addVertex(x + w, y + h, 0);
        mesh.addVertex(x + w, y, 0);
        mesh.addVertex(x, y, 0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NativeQrComponent && qr.equals(((NativeQrComponent) other).qr);
    }

    @Override
    public int hashCode() {
        return qr.hashCode();
    }
}
