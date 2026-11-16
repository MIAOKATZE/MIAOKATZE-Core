// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.ToIntFunction;

/** No Minecraft client required: inline labels remain readable through wrapping and pagination. */
public final class InputHintContracts {

    public static void main(String[] args) {
        String chord = "§e[key-right:Shift+右击]解锁";
        TooltipInput input = TooltipInput.of(chord);
        ToIntFunction<String> font = value -> value.replaceAll("§.", "").length() * 6;
        check(input.plain().equals("§e[Shift+右击]解锁"));
        check(input.runs().get(1).key && input.runs().get(1).mouse == TooltipInput.Mouse.RIGHT);
        check(input.runs().get(1).text.equals("§e[Shift+右击]"));
        check(input.runs().get(2).text.equals("§e解锁"));
        check(input.width(font) == font.applyAsInt(input.plain()) + 28);
        check(TooltipInput.of("[left:左击]勾选 / [middle:中击] [unrelated]").plain().equals("[左击]勾选 / [中击] [unrelated]"));
        TooltipTable legacy = TooltipTable.of("", Arrays.asList("操作", "结果"),
            Arrays.asList(Arrays.asList(chord, "移动"), Arrays.asList("[left:左击]勾选", "选中")));
        TooltipTable icons = legacy.withInputIcons().align(0, TooltipTable.Alignment.RIGHT)
            .withMeasurementRows(Collections.singletonList(Arrays.asList("[key-middle:Ctrl+中击]", "取样")));
        check(legacy.fallbackLines().get(0).contains(chord));
        check(icons.fallbackLines().get(0).equals("操作: §e[Shift+右击]解锁  |  结果: 移动"));
        TooltipTableLayout full = new TooltipTableLayout(icons, font);
        TooltipTableLayout page = new TooltipTableLayout(icons.slice(1, 2), font);
        check(full.width() == page.width());
        check(full.cellX(chord, 0) + input.width(font) == full.columnWidth(0));
        check(full.cellX("操作", 0, true) + font.applyAsInt("操作") == full.columnWidth(0));
    }

    private static void check(boolean ok) {
        if (!ok) throw new AssertionError("Tooltip input layout or fallback");
    }
}
