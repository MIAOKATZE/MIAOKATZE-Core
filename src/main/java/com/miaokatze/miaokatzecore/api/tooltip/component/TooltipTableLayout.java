// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.function.ToIntFunction;

/** Pure layout: all columns are recomputed from the active font, never padded with spaces. */
public final class TooltipTableLayout {

    public static final int GAP = 12;
    private final TooltipTable table;
    private final ToIntFunction<String> measure;
    private final int[] widths;
    private final int width;

    public TooltipTableLayout(TooltipTable table, ToIntFunction<String> measure) {
        this.table = table;
        this.measure = measure;
        widths = new int[table.headers()
            .size()];
        int total = 0;
        for (int c = 0; c < widths.length; c++) {
            widths[c] = measure.applyAsInt(
                table.headers()
                    .get(c));
            for (java.util.List<String> row : table.measurementRows())
                widths[c] = Math.max(widths[c], bodyWidth(row.get(c)));
            total += widths[c] + (c > 0 ? GAP : 0);
        }
        width = total;
    }

    public int width() {
        return width;
    }

    public int columnWidth(int c) {
        return widths[c];
    }

    private int bodyWidth(String text) {
        TooltipInput input = table.input(text);
        return input == null ? measure.applyAsInt(text) : input.width(measure);
    }

    public int cellX(String text, int c) {
        return cellX(text, c, false);
    }

    public int cellX(String text, int c, boolean header) {
        int x = 0;
        for (int i = 0; i < c; i++) x += widths[i] + GAP;
        return x + (table.alignment(c) == TooltipTable.Alignment.RIGHT ? widths[c] - (header ? measure.applyAsInt(text) : bodyWidth(text)) : 0);
    }
}
