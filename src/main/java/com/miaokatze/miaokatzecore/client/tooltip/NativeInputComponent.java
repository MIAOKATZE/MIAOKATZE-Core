// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Objects;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

final class NativeInputComponent implements ITooltipComponent {

    private final String item;
    private final TooltipInput line;

    NativeInputComponent(String item, TooltipInput line) {
        this.item = item;
        this.line = line;
    }

    static void drawLine(String item, TooltipInput line, int x, int y) {
        for (TooltipInput.Run run : line.runs()) {
            if (run.key) {
                NativeGuideComponent.sprite(item, 0, x, y, false);
                x += TooltipInput.ADVANCE;
            }
            if (run.mouse != null) {
                NativeGuideComponent.mouse(item, run.mouse, x, y);
                x += TooltipInput.ADVANCE;
            }
            TooltipFontContext.drawString(run.text, x, y);
            x += TooltipFontContext.getStringWidth(run.text);
        }
    }

    @Override
    public int getWidth() {
        return line.width(TooltipFontContext::getStringWidth);
    }

    @Override
    public int getHeight() {
        return Math.max(9, TooltipFontContext.getFontHeight());
    }

    @Override
    public int getSpacing() {
        return 1;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        return getWidth() <= width && getHeight() <= height ? new ITooltipComponent[] { this }
            : new TextComponent(line.plain()).paginate(context, Math.max(1, width), height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        drawLine(item, line, x, y);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeInputComponent)) return false;
        NativeInputComponent input = (NativeInputComponent) other;
        return item.equals(input.item) && line.equals(input.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, line);
    }
}
