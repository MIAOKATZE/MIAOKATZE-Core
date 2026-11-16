// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Optional adapter for localized authoring data. Item state, URLs and function values are supplied by the owner. */
public final class NativeTooltipBlocks {

    private NativeTooltipBlocks() {}

    public static boolean append(NativeTooltipPage page, JsonObject b) {
        String title = str(b, "title");
        switch (str(b, "type")) {
            case "line":
                String text = str(b, "text");
                if (text.indexOf('[') >= 0) page.input(text);
                else page.line(text);
                return true;
            case "warning":
                page.warning(str(b, "text"));
                return true;
            case "divider":
                page.divider();
                return true;
            case "link":
                if (!b.has("value")) return false;
                if (b.has("qr") && b.get("qr")
                    .getAsBoolean()) page.qr(str(b, "prefix"), str(b, "value"));
                else page.line(str(b, "prefix") + str(b, "value"));
                return true;
            case "table":
            case "bars":
            case "heat":
                break;
            default:
                return false;
        }

        List<String> columns = strings(b.getAsJsonArray("columns"));
        List<List<String>> rows = new ArrayList<>();
        JsonArray source = b.getAsJsonArray("rows");
        String type = str(b, "type");
        double[][] values = new double[source.size()][];
        double max = 1;
        if ("heat".equals(type)) {
            columns.add(0, str(b, "rowTitle"));
            for (String field : new String[] { "columnTitle", "unit" }) {
                if (!str(b, field).isEmpty()) title += (title.isEmpty() ? "" : " · ") + str(b, field);
            }
            for (int r = 0; r < source.size(); r++) {
                List<String> row = strings(
                    b.getAsJsonArray("values")
                        .get(r)
                        .getAsJsonArray());
                values[r] = new double[row.size()];
                for (int c = 0; c < row.size(); c++) max = Math.max(max, values[r][c] = Double.parseDouble(row.get(c)));
                row.add(
                    0,
                    source.get(r)
                        .getAsString());
                rows.add(row);
            }
        } else {
            for (JsonElement row : source) rows.add(strings(row.getAsJsonArray()));
        }

        TooltipTable table = TooltipTable.of(title, columns, rows)
            .withInputIcons();
        for (int c = 1; c < columns.size(); c++) table = table.align(c, TooltipTable.Alignment.LEFT);
        if ("table".equals(type)) page.block(table);
        else if ("heat".equals(type)) page.block(
            TooltipChart.heat(
                title,
                table,
                values,
                max,
                Collections.nCopies(rows.size(), ""),
                Collections.nCopies(columns.size() - 1, "")));
        else {
            double[] bars = new double[rows.size()];
            for (int i = 0; i < bars.length; i++) max = Math.max(
                max,
                bars[i] = b.getAsJsonArray("values")
                    .get(i)
                    .getAsDouble());
            page.block(
                TooltipChart.bars(title, table, bars, null, max)
                    .barColumn(
                        b.get("barColumn")
                            .getAsInt()));
        }
        return true;
    }

    private static String str(JsonObject b, String key) {
        return b.has(key) ? b.get(key)
            .getAsString() : "";
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }
}
