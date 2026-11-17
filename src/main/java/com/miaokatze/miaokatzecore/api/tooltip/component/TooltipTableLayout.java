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
        this(table, measure, Integer.MAX_VALUE);
    }

    /** Reduce the widest column first; no column can split a key chord or a labeled material sprite. */
    public TooltipTableLayout(TooltipTable table, ToIntFunction<String> measure, int maxWidth) {
        this.table = table;
        this.measure = measure;
        widths = new int[table.headers()
            .size()];
        int total = 0;
        for (int c = 0; c < widths.length; c++) {
            widths[c] = headerWidth(
                table.headers()
                    .get(c));
            for (java.util.List<String> row : table.measurementRows())
                widths[c] = Math.max(widths[c], bodyWidth(row.get(c)));
            total += widths[c] + (c > 0 ? GAP : 0);
        }
        if (total > maxWidth) {
            int[] floor = new int[widths.length];
            for (int c = 0; c < floor.length; c++) {
                floor[c] = minimum(
                    table.headers()
                        .get(c),
                    true);
                for (java.util.List<String> row : table.measurementRows())
                    floor[c] = Math.max(floor[c], minimum(row.get(c), false));
            }
            while (total > maxWidth) {
                int widest = -1;
                for (int c = 0; c < widths.length; c++)
                    if (widths[c] > floor[c] && (widest < 0 || widths[c] > widths[widest])) widest = c;
                if (widest < 0) break;
                widths[widest]--;
                total--;
            }
        }
        width = total;
    }

    private int minimum(String text, boolean header) {
        TooltipInput input = header ? null : table.input(text);
        if (input == null) input = TooltipInput.materials(text);
        int min = 0;
        for (TooltipInput.Run run : input.runs()) {
            if (run.key || run.mouse != null || run.itemId != null || run.fluidId != null)
                min = Math.max(min, run.width(measure));
            else {
                for (int i = 0; i < run.text.length();) {
                    int cp = run.text.codePointAt(i);
                    i += Character.charCount(cp);
                    if (cp == '§' && i < run.text.length()) i++;
                    else min = Math.max(min, measure.applyAsInt(new String(Character.toChars(cp))) + 1);
                }
            }
        }
        return min;
    }

    public int width() {
        return width;
    }

    public int columnWidth(int c) {
        return widths[c];
    }

    /** Allocate the page's remaining pixels across all columns; keep the measured content widths as their basis. */
    public int columnWidth(int c, int availableWidth) {
        int extra = Math.max(0, availableWidth - width);
        return widths[c] + (int) ((long) extra * (c + 1) / widths.length - (long) extra * c / widths.length);
    }

    private int headerWidth(String text) {
        return TooltipInput.hasMaterial(text) ? TooltipInput.materials(text)
            .width(measure) : measure.applyAsInt(text);
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
        return x + (table.alignment(c) == TooltipTable.Alignment.RIGHT
            ? widths[c] - (header ? headerWidth(text) : bodyWidth(text))
            : 0);
    }
}
