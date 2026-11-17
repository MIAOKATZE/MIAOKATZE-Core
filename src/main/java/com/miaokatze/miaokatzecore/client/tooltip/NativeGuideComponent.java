// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipGuide;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Frozen 12x9 pixel geometry; only the selected cel changes while a key is held. */
public final class NativeGuideComponent implements ITooltipComponent {

    private static final ResourceLocation SHEET = new ResourceLocation("miaokatzecore", "textures/tooltip/guides.png");
    private static final ResourceLocation MOUSE = new ResourceLocation(
        "miaokatzecore",
        "textures/tooltip/mouse_clicks.png");
    private static final int[] BEATS = { 0, 0, 1, 2, 3, 3, 5, 6, 0, 0 };
    private final String item;
    private final TooltipGuide guide;
    private final String[] runs;
    private final ITooltipComponent content;
    private final boolean showIcon;

    NativeGuideComponent(String item, TooltipGuide guide) {
        this(item, guide, new NativeInputComponent(item, guide.line), true);
    }

    private NativeGuideComponent(String item, TooltipGuide guide, ITooltipComponent content, boolean showIcon) {
        this.content = content;
        this.showIcon = showIcon;
        this.item = item;
        this.guide = guide;
        this.runs = guide.kind == TooltipGuide.Kind.TIER ? guide.text.split("(?=§[7b])") : null;
    }

    public TooltipGuide guide() {
        return guide;
    }

    @Override
    public int getWidth() {
        return 15 + content.getWidth();
    }

    @Override
    public int getHeight() {
        return Math.max(9, content.getHeight());
    }

    @Override
    public int getSpacing() {
        return 1;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext c, int w, int h) {
        if (getWidth() <= w && getHeight() <= h) return new ITooltipComponent[] { this };
        if (guide.kind == TooltipGuide.Kind.TIER || w <= 15)
            return new TextComponent(guide.text).paginate(c, Math.max(1, w), h);
        ITooltipComponent[] pieces = content.paginate(c, w - 15, h);
        for (int i = 0; i < pieces.length; i++)
            pieces[i] = new NativeGuideComponent(item, guide, pieces[i], i == 0 && showIcon);
        return pieces;
    }

    static void sprite(String item, int row, int x, int y, boolean held) {
        long ms = TooltipAnimationClock.nowNanos() / 1000000L;
        int frame = TooltipPages.animationEnabled(item)
            ? (row == 0 ? held ? 3 : BEATS[(int) Math.floorMod(ms / 150, 10L)] : (int) Math.floorMod(ms / 300, 8L))
            : 0;
        drawSprite(SHEET, frame, row, x, y);
    }

    static void mouse(String item, TooltipInput.Mouse button, int x, int y) {
        long ms = TooltipAnimationClock.nowNanos() / 1000000L;
        int frame = TooltipPages.animationEnabled(item) ? BEATS[(int) Math.floorMod(ms / 150, 10L)] : 0;
        drawSprite(MOUSE, frame, button.ordinal(), x, y);
    }

    private static void drawSprite(ResourceLocation sheet, int frame, int row, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(sheet);
            Gui.func_146110_a(x, y, 12 * frame, 9 * row, 12, 9, 96, 27);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        int row = guide.kind == TooltipGuide.Kind.TIER ? 0 : guide.kind == TooltipGuide.Kind.WARNING ? 1 : 2;
        if (showIcon) sprite(item, row, x, y, guide.kind == TooltipGuide.Kind.TIER && TooltipController.tierHeld());
        if (guide.kind != TooltipGuide.Kind.TIER) {
            content.draw(x + 15, y, width - 15, context);
            return;
        }
        double brightness = TooltipPages.animationEnabled(item)
            ? .90 + .075 * Math.sin(TooltipAnimationClock.phase(TooltipAnimationClock.nowNanos(), 3200) * 2 * Math.PI)
            : 1;
        int pen = x + 15, tone = 0xb6b9c6;
        // Hint copies use only the neutral and active inks. Keep all glyphs and advances fixed.
        for (String run : runs) {
            if (run.startsWith("§7") || run.startsWith("§b")) {
                tone = run.charAt(1) == 'b' ? 0xa6e9ef : 0xb6b9c6;
                run = run.substring(2);
            }
            int rgb = (int) ((tone >> 16) * brightness) << 16 | (int) (((tone >> 8) & 255) * brightness) << 8
                | (int) ((tone & 255) * brightness);
            TooltipFontContext.drawString(run, pen, y, 0xff000000 | rgb);
            pen += TooltipFontContext.getStringWidth(run);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, guide);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NativeGuideComponent && ((NativeGuideComponent) o).guide.equals(guide)
            && ((NativeGuideComponent) o).item.equals(item);
    }
}
