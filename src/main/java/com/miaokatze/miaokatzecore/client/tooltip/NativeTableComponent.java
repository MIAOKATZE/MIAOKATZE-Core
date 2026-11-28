// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.List;
import java.util.Objects;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTableLayout;
import com.miaokatze.miaokatzecore.client.tooltip.art.TooltipArtRenderer;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Native coordinates are shared by row cells; resource/font changes are measured at layout time. */
public final class NativeTableComponent implements ITooltipComponent {

    private static final int INSET = 2; // Same grid edges as NativeChartComponent.
    private final String item;
    private final TooltipTable table;

    private final int limit;
    private TooltipTableLayout geometry;
    private net.minecraft.client.gui.FontRenderer measuredFont;
    private boolean unicode;
    private int lastGridWidth;
    private final java.util.Map<String, TooltipInput> cells = new java.util.HashMap<>();

    public NativeTableComponent(String item, TooltipTable table) {
        this(item, table, Integer.MAX_VALUE);
    }

    private NativeTableComponent(String item, TooltipTable table, int limit) {
        this.limit = limit;
        this.item = item;
        this.table = table;
    }

    private TooltipTableLayout layout() {
        net.minecraft.client.gui.FontRenderer font = TooltipFontContext.getFontRenderer();
        if (geometry == null || font != measuredFont || unicode != font.getUnicodeFlag()) {
            geometry = new TooltipTableLayout(table, TooltipFontContext::getStringWidth, limit - 2 * INSET);
            measuredFont = font;
            unicode = font.getUnicodeFlag();
        }
        return geometry;
    }

    public TooltipTable table() {
        return table;
    }

    /** Observational diagnostics for real render tests. No context, ItemStack or world is retained. */
    public java.util.Map<String, Object> layoutSnapshot() {
        TooltipTableLayout measured = layout();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<Integer> widths = new java.util.ArrayList<>(), starts = new java.util.ArrayList<>();
        int start = INSET;
        for (int c = 0; c < table.headers()
            .size(); c++) {
            starts.add(start);
            int width = measured.columnWidth(c, lastGridWidth);
            widths.add(width);
            start += width + TooltipTableLayout.GAP;
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

    private TooltipInput cell(String value, boolean header) {
        TooltipInput input = header ? null : table.input(value);
        return input != null ? input : cells.computeIfAbsent(header ? "§7" + value : value, TooltipInput::materials);
    }

    private int rowHeight(List<String> cells, boolean header) {
        if (limit == Integer.MAX_VALUE) return TooltipFontContext.getFontHeight();
        int count = 1;
        for (int i = 0; i < cells.size(); i++) {
            TooltipInput cell = cell(cells.get(i), header);
            if (cell.width(TooltipFontContext::getStringWidth) > layout().columnWidth(i)) count = Math.max(
                count,
                cell.wrap(layout().columnWidth(i), TooltipFontContext::getStringWidth)
                    .size());
        }
        return count * TooltipFontContext.getFontHeight();
    }

    private int titleHeight() {
        return table.title()
            .isEmpty() ? 0 : TooltipFontContext.getFontHeight() + 3;
    }

    @Override
    public int getWidth() {
        return Math.max(
            layout().width() + 2 * INSET,
            NativeInputComponent.width(table.title()) + (table.art() == TooltipArt.PLAIN ? 0 : 10));
    }

    @Override
    public int getHeight() {
        int height = titleHeight() + rowHeight(table.headers(), true) + 3;
        for (List<String> cells : table.rows()) height += rowHeight(cells, false);
        return height;
    }

    @Override
    public int getSpacing() {
        return 3;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int maxWidth, int maxHeight) {
        int line = TooltipFontContext.getFontHeight();
        NativeTableComponent fit = limit <= maxWidth ? this : new NativeTableComponent(item, table, maxWidth);
        if (fit.getWidth() > maxWidth) return new TextComponent(table.fallbackLines())
            .paginate(context, Math.max(1, maxWidth), Math.max(line, maxHeight));
        int height = fit.titleHeight() + fit.rowHeight(table.headers(), true) + 3;
        int rows = 0;
        for (List<String> row : table.rows()) {
            int next = fit.rowHeight(row, false);
            if (rows > 0 && height + next > maxHeight) break;
            height += next;
            rows++;
        }
        if (rows == table.rows()
            .size()) return new ITooltipComponent[] { fit };
        return new ITooltipComponent[] { new NativeTableComponent(item, table.slice(0, rows), maxWidth),
            new NativeTableComponent(
                item,
                table.slice(
                    rows,
                    table.rows()
                        .size()),
                maxWidth) };
    }

    @Override
    public void draw(int x, int y, int availableWidth, TooltipContext context) {
        NativeTooltipMouse.Drawing drawing = new NativeTooltipMouse.Drawing(context, TooltipPinning.interactive(context));
        TooltipTableLayout layout = layout();
        // Chromatic supplies one content width for every drawn component in this page.
        int width = Math.max(getWidth(), Math.min(availableWidth, limit));
        int gridWidth = width - 2 * INSET;
        lastGridWidth = gridWidth;
        if (!table.title()
            .isEmpty()) {
            if (table.art() == TooltipArt.PLAIN)
                NativeInputComponent.text(item, table.title(), x, y, 0xffffffff, drawing);
            else {
                final int titleY = y;
                final String label = EnumChatFormatting.getTextWithoutFormattingCodes(table.title());
                TooltipArtRenderer.drawLabel(
                    table.art(),
                    x,
                    y,
                    NativeInputComponent.width(label),
                    TooltipPages.animationEnabled(item),
                    (java.util.function.IntConsumer) color -> NativeInputComponent
                        .text(item, label, x + 10, titleY, color, drawing));
            }
            y += titleHeight();
        }
        drawRow(table.headers(), layout, x + INSET, y, true, gridWidth, drawing);
        y += rowHeight(table.headers(), true) + 1;
        net.minecraft.client.gui.Gui.drawRect(x + INSET, y, x + width - INSET, y + 1, 0xff674071);
        y += 2;
        for (List<String> row : table.rows()) {
            drawRow(row, layout, x + INSET, y, false, gridWidth, drawing);
            y += rowHeight(row, false);
        }
    }

    private void drawRow(List<String> cells, TooltipTableLayout layout, int x, int y, boolean header, int gridWidth,
        NativeTooltipMouse.Drawing drawing) {
        int left = x;
        for (int c = 0; c < cells.size(); c++) {
            TooltipInput cell = cell(cells.get(c), header);
            int w = layout.columnWidth(c, gridWidth), yy = y;
            List<List<TooltipInput.Run>> lines = cell.width(TooltipFontContext::getStringWidth) <= w
                ? java.util.Collections.singletonList(cell.runs())
                : cell.wrap(w, TooltipFontContext::getStringWidth);
            for (List<TooltipInput.Run> runs : lines) {
                int offset = 0;
                if (table.alignment(c) == TooltipTable.Alignment.RIGHT) {
                    offset = w;
                    for (TooltipInput.Run run : runs) offset -= run.width(TooltipFontContext::getStringWidth);
                }
                NativeInputComponent.drawRuns(item, runs, left + offset, yy, 0xffffffff, drawing);
                yy += TooltipFontContext.getFontHeight();
            }
            left += w + TooltipTableLayout.GAP;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, table, limit);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeTableComponent)) return false;
        NativeTableComponent t = (NativeTableComponent) other;
        return item.equals(t.item) && table.equals(t.table) && limit == t.limit;
    }
}
