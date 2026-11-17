// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

final class NativeInputComponent implements ITooltipComponent {

    private final String item;
    private final TooltipInput line;

    private final List<List<TooltipInput.Run>> rows;

    NativeInputComponent(String item, TooltipInput line) {
        this(item, line, Collections.singletonList(line.runs()));
    }

    private NativeInputComponent(String item, TooltipInput line, List<List<TooltipInput.Run>> rows) {
        this.rows = rows;
        this.item = item;
        this.line = line;
    }

    static int width(String value) {
        return TooltipInput.hasMaterial(value) ? TooltipInput.materials(value)
            .width(TooltipFontContext::getStringWidth) : TooltipFontContext.getStringWidth(value);
    }

    static void text(String item, String value, int x, int y, int color) {
        if (TooltipInput.hasMaterial(value)) drawLine(item, TooltipInput.materials(value), x, y, color);
        else TooltipFontContext.drawString(value, x, y, color);
    }

    static void drawLine(String item, TooltipInput line, int x, int y) {
        drawLine(item, line, x, y, 0xffffffff);
    }

    static void drawLine(String item, TooltipInput line, int x, int y, int color) {
        drawRuns(item, line.runs(), x, y, color);
    }

    static void drawRuns(String item, List<TooltipInput.Run> runs, int x, int y, int color) {
        for (TooltipInput.Run run : runs) {
            if (run.key) {
                NativeGuideComponent.sprite(item, 0, x, y, false);
                x += TooltipInput.ADVANCE;
            }
            if (run.mouse != null) {
                NativeGuideComponent.mouse(item, run.mouse, x, y);
                x += TooltipInput.ADVANCE;
            }
            if (run.itemId != null || run.fluidId != null) {
                MaterialSprite.draw(run, x, y - 1);
                x += TooltipInput.MATERIAL_ADVANCE;
            }
            TooltipFontContext.drawString(run.text, x, y, color);
            x += TooltipFontContext.getStringWidth(run.text);
        }
    }

    @Override
    public int getWidth() {
        int max = 0;
        for (List<TooltipInput.Run> row : rows) {
            int w = 0;
            for (TooltipInput.Run run : row) w += run.width(TooltipFontContext::getStringWidth);
            max = Math.max(w, max);
        }
        return max;
    }

    @Override
    public int getHeight() {
        return rows.size() * TooltipFontContext.getFontHeight();
    }

    @Override
    public int getSpacing() {
        return 1;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        List<List<TooltipInput.Run>> wrapped = rows;
        if (getWidth() > width) {
            wrapped = line.wrap(Math.max(1, width), TooltipFontContext::getStringWidth);
            // An entire labeled icon may exceed this viewport. Keep its complete readable fallback.
            for (List<TooltipInput.Run> row : wrapped)
                for (TooltipInput.Run run : row) if (run.width(TooltipFontContext::getStringWidth) > width)
                    return new TextComponent(line.plain()).paginate(context, Math.max(1, width), height);
        }
        int count = Math.max(1, height / TooltipFontContext.getFontHeight());
        if (count < wrapped.size())
            return new ITooltipComponent[] { new NativeInputComponent(item, line, wrapped.subList(0, count)),
                new NativeInputComponent(item, line, wrapped.subList(count, wrapped.size())) };
        return new ITooltipComponent[] { wrapped == rows ? this : new NativeInputComponent(item, line, wrapped) };
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        for (List<TooltipInput.Run> row : rows) {
            drawRuns(item, row, x, y, 0xffffffff);
            int pen = x;
            for (TooltipInput.Run run : row) {
                if (run.key) pen += TooltipInput.ADVANCE;
                if (run.mouse != null) pen += TooltipInput.ADVANCE;
                if (run.itemId != null || run.fluidId != null) pen += TooltipInput.MATERIAL_ADVANCE;
                NativeTooltipMouse.textLinks(context, run.text, pen, y);
                pen += TooltipFontContext.getStringWidth(run.text);
            }
            y += TooltipFontContext.getFontHeight();
        }
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
