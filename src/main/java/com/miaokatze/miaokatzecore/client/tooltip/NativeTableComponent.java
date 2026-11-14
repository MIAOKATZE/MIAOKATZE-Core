// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.List;
import java.util.Objects;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTableLayout;
import com.miaokatze.miaokatzecore.client.tooltip.art.TooltipArtRenderer;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Native coordinates are shared by row cells; resource/font changes are measured at layout time. */
public final class NativeTableComponent implements ITooltipComponent {

    private final String item;
    private final TooltipTable table;

    public NativeTableComponent(String item, TooltipTable table) {
        this.item = item;
        this.table = table;
    }

    private TooltipTableLayout layout() {
        return new TooltipTableLayout(table, TooltipFontContext::getStringWidth);
    }

    public TooltipTable table() {
        return table;
    }

    /** Observational diagnostics for real render tests. No context, ItemStack or world is retained. */
    public java.util.Map<String, Object> layoutSnapshot() {
        TooltipTableLayout measured = layout();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<Integer> widths = new java.util.ArrayList<>(), starts = new java.util.ArrayList<>();
        int start = 0;
        for (int c = 0; c < table.headers()
            .size(); c++) {
            starts.add(start);
            widths.add(measured.columnWidth(c));
            start += measured.columnWidth(c) + TooltipTableLayout.GAP;
        }
        result.put("title", table.title());
        result.put("headers", table.headers());
        result.put("rows", table.rows());
        result.put("columnWidths", widths);
        result.put("columnStarts", starts);
        result.put("width", getWidth());
        result.put("height", getHeight());
        result.put(
            "font",
            TooltipFontContext.getFontRenderer()
                .getClass()
                .getName());
        result.put(
            "unicode",
            TooltipFontContext.getFontRenderer()
                .getUnicodeFlag());
        return java.util.Collections.unmodifiableMap(result);
    }

    private int titleHeight() {
        return table.title()
            .isEmpty() ? 0 : TooltipFontContext.getFontHeight() + 3;
    }

    @Override
    public int getWidth() {
        return Math.max(
            layout().width(),
            TooltipFontContext.getStringWidth(table.title()) + (table.art() == TooltipArt.PLAIN ? 0 : 10));
    }

    @Override
    public int getHeight() {
        return titleHeight() + (table.rows()
            .size() + 1) * TooltipFontContext.getFontHeight() + 3;
    }

    @Override
    public int getSpacing() {
        return 3;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int maxWidth, int maxHeight) {
        int line = TooltipFontContext.getFontHeight();
        int rows = (maxHeight - titleHeight() - line - 3) / line;
        if (getWidth() > maxWidth || rows < 1) {
            // Keep complete values/units and repeat labels when a three-column view will not fit.
            return new TextComponent(table.fallbackLines())
                .paginate(context, Math.max(1, maxWidth), Math.max(line, maxHeight));
        }
        if (rows >= table.rows()
            .size()) return new ITooltipComponent[] { this };
        return new ITooltipComponent[] { new NativeTableComponent(item, table.slice(0, rows)),
            new NativeTableComponent(
                item,
                table.slice(
                    rows,
                    table.rows()
                        .size())) };
    }

    @Override
    public void draw(int x, int y, int availableWidth, TooltipContext context) {
        TooltipTableLayout layout = layout();
        if (!table.title()
            .isEmpty()) {
            if (table.art() == TooltipArt.PLAIN) TooltipFontContext.drawString(table.title(), x, y);
            else {
                final int titleY = y;
                final String label = EnumChatFormatting.getTextWithoutFormattingCodes(table.title());
                TooltipArtRenderer.drawLabel(
                    table.art(),
                    x,
                    y,
                    TooltipFontContext.getStringWidth(label),
                    TooltipPages.animationEnabled(item),
                    (java.util.function.IntConsumer) color -> TooltipFontContext
                        .drawString(label, x + 10, titleY, color));
            }
            y += titleHeight();
        }
        drawRow(table.headers(), layout, x, y, true);
        y += TooltipFontContext.getFontHeight() + 3;
        for (List<String> row : table.rows()) {
            drawRow(row, layout, x, y, false);
            y += TooltipFontContext.getFontHeight();
        }
    }

    private static void drawRow(List<String> cells, TooltipTableLayout layout, int x, int y, boolean header) {
        for (int c = 0; c < cells.size(); c++) {
            String value = cells.get(c);
            TooltipFontContext.drawString((header ? "§7" : "") + value, x + layout.cellX(value, c), y);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, table);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeTableComponent)) return false;
        NativeTableComponent t = (NativeTableComponent) other;
        return item.equals(t.item) && table.equals(t.table);
    }
}
