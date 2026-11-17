// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Shared item-title status. The key label and pin state are read at paint time, including cached tooltips. */
final class NativePinComponent implements ITooltipComponent {

    private final String item;
    private final int wrapWidth;

    NativePinComponent(String item) {
        this(item, Integer.MAX_VALUE);
    }

    private NativePinComponent(String item, int wrapWidth) {
        this.item = item;
        this.wrapWidth = wrapWidth;
    }

    private String[] parts() {
        return parts(TooltipController.isPinned(item));
    }

    private String[] parts(boolean pinned) {
        String key = TooltipController.pinKeyLabel();
        String text = StatCollector
            .translateToLocalFormatted("miaokatzecore.tooltip.pin." + (pinned ? "pinned" : "floating"), "\u0001");
        int at = text.indexOf('\u0001');
        return at < 0 ? new String[] { "", "[" + key + "]", " " + text }
            : new String[] { text.substring(0, at), "[" + key + "]", text.substring(at + 1) };
    }

    private int headerWidth(String[] text) {
        return 15 + TooltipFontContext.getStringWidth(text[0]) + 14 + TooltipFontContext.getStringWidth(text[1]);
    }

    private boolean wrapped(String[] text) {
        return headerWidth(text) + TooltipFontContext.getStringWidth(text[2]) > wrapWidth;
    }

    private List<List<TooltipInput.Run>> actionRows(String[] text) {
        return TooltipInput.of(text[2].trim())
            .wrap(Math.max(1, wrapWidth - 15), TooltipFontContext::getStringWidth);
    }

    @Override
    public int getWidth() {
        // A cached context can switch state without rebuilding its title section. Reserve both captions.
        return Math.max(width(parts(false)), width(parts(true)));
    }

    private int width(String[] text) {
        int width = headerWidth(text);
        if (!wrapped(text)) return width + TooltipFontContext.getStringWidth(text[2]);
        for (List<TooltipInput.Run> row : actionRows(text)) {
            int measured = 15;
            for (TooltipInput.Run run : row) measured += run.width(TooltipFontContext::getStringWidth);
            width = Math.max(width, measured);
        }
        return width;
    }

    @Override
    public int getHeight() {
        return Math.max(height(parts(false)), height(parts(true)));
    }

    private int height(String[] text) {
        return lineHeight() * (wrapped(text) ? 1 + actionRows(text).size() : 1);
    }

    private static int lineHeight() {
        return Math.max(12, TooltipFontContext.getFontHeight());
    }

    @Override
    public int getSpacing() {
        return 1;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        NativePinComponent fit = new NativePinComponent(item, Math.max(1, width));
        if (fit.getWidth() <= width && fit.getHeight() <= height) return new ITooltipComponent[] { fit };
        String[] text = parts();
        return new TextComponent(text[0] + text[1] + text[2]).paginate(context, Math.max(1, width), height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        boolean pinned = TooltipController.isPinned(item), reduced = !TooltipPages.animationEnabled(item);
        // Sample once so the cap highlight, text and binding all change on the same 0.5-second edge.
        int frame = TooltipPinVisual.frame(pinned, TooltipAnimationClock.nowNanos(), reduced);
        int body = TooltipPinVisual.body(pinned, frame, reduced),
            binding = TooltipPinVisual.binding(pinned, frame, reduced);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            for (int py = 0; py < 12; py++) for (int px = 0; px < 12; px++) {
                int color = TooltipPinVisual.pixel(pinned, frame, px, py);
                if (color != 0) Gui.drawRect(x + px, y + py, x + px + 1, y + py + 1, color);
            }
            String[] text = parts();
            int textY = y + (lineHeight() - TooltipFontContext.getFontHeight()) / 2;
            int pen = x + 15;
            TooltipFontContext.drawString(text[0], pen, textY, body);
            pen += TooltipFontContext.getStringWidth(text[0]);
            NativeGuideComponent.sprite(item, 0, pen, y + (lineHeight() - 9) / 2, false);
            pen += 14;
            TooltipFontContext.drawString(text[1], pen, textY, binding);
            pen += TooltipFontContext.getStringWidth(text[1]);
            if (!wrapped(text)) TooltipFontContext.drawString(text[2], pen, textY, body);
            else {
                for (List<TooltipInput.Run> row : actionRows(text)) {
                    textY += lineHeight();
                    NativeInputComponent.drawRuns(item, row, x + 15, textY, body);
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, wrapWidth);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NativePinComponent && item.equals(((NativePinComponent) other).item)
            && wrapWidth == ((NativePinComponent) other).wrapWidth;
    }
}
