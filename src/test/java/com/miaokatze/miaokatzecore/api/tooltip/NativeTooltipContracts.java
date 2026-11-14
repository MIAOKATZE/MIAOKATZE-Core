// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTableLayout;

/** Geometry/semantic contracts exercise variable-width fonts and immutable snapshots without a client. */
public final class NativeTooltipContracts {

    public static void run() {
        List<List<String>> source = new ArrayList<>();
        source.add(new ArrayList<>(Arrays.asList("液氮", "120 L/s", "2×")));
        source.add(new ArrayList<>(Arrays.asList("冷却水", "6 L/s", "1.25×")));
        TooltipTable table = TooltipTable.of("冷却剂", Arrays.asList("材料", "流量", "倍率"), source);
        source.get(0)
            .set(0, "changed");
        require(
            table.rows()
                .get(0)
                .get(0)
                .equals("液氮"));
        for (int font = 1; font <= 3; font++) {
            final int glyph = font;
            java.util.function.ToIntFunction<String> measure = text -> text.chars()
                .map(c -> c > 255 ? glyph * 7 : c == '1' ? glyph * 2 : glyph * 5)
                .sum();
            TooltipTableLayout layout = new TooltipTableLayout(table, measure);
            for (int column = 1; column < 3; column++) {
                String a = table.rows()
                    .get(0)
                    .get(column),
                    b = table.rows()
                        .get(1)
                        .get(column);
                require(
                    layout.cellX(a, column) + measure.applyAsInt(a) == layout.cellX(b, column) + measure.applyAsInt(b));
            }
            require(layout.cellX("液氮", 0) == 0);
            require(layout.cellX("120 L/s", 1) >= layout.columnWidth(0) + TooltipTableLayout.GAP);
        }
        require(
            table.fallbackLines()
                .get(1)
                .contains("流量: 120 L/s"));
        require(
            table.fallbackLines()
                .get(1)
                .contains("倍率: 2×"));
        require(
            table.slice(1, 2)
                .rows()
                .size() == 1
                && table.rows()
                    .size() == 2);
        require(table.equals(TooltipTable.of(table.title(), table.headers(), table.rows())));
        try {
            table.rows()
                .get(0)
                .add("bad");
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {}
        try {
            TooltipTable.of("bad", Arrays.asList("one"), source);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {}
        try {
            TooltipTable.of("bad\nnew", table.headers(), table.rows());
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("Native tooltip table contract");
    }
}
