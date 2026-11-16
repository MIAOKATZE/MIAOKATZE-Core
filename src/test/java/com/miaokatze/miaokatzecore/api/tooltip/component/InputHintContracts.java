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
        ToIntFunction<String> font = value -> value.replaceAll("§.", "")
            .length() * 6;
        check(
            input.plain()
                .equals("§e[Shift+右击]解锁"));
        check(
            input.runs()
                .get(1).key
                && input.runs()
                    .get(1).mouse == TooltipInput.Mouse.RIGHT);
        check(
            input.runs()
                .get(1).text.equals("§e[Shift+右击]"));
        check(
            input.runs()
                .get(2).text.equals("§e解锁"));
        check(input.width(font) == font.applyAsInt(input.plain()) + 28);
        check(
            TooltipInput.of("[left:左击]勾选 / [middle:中击] [unrelated]")
                .plain()
                .equals("[左击]勾选 / [中击] [unrelated]"));
        String dye = "[item:minecraft:dye@4|青金石]", water = "[fluid:water|水]";
        TooltipInput material = TooltipInput.of("§e用" + dye + "§r，加入 " + water + " 和" + dye + water + "×2");
        check(
            material.plain()
                .equals("§e用 [青金石]§r，加入 [水] 和 [青金石] [水]×2"));
        check(
            material.runs()
                .stream()
                .filter(r -> "minecraft:dye".equals(r.itemId) && r.meta == 4)
                .count() == 2);
        check(
            material.runs()
                .stream()
                .filter(r -> "water".equals(r.fluidId))
                .count() == 2);
        check(material.width(font) == font.applyAsInt(material.plain()) + TooltipInput.MATERIAL_ADVANCE * 4);
        check(
            TooltipInput.of("[key:R]" + water + "。")
                .plain()
                .equals("[R] [水]。"));
        check(
            TooltipInput.materials("[key:R]" + water)
                .plain()
                .equals("[key:R] [水] "));
        check(
            TooltipInput.of("水位 [对象] " + water + "§r→")
                .plain()
                .equals("水位 [对象] [水]§r→"));
        String forgeName = "molten.atomic separation catalyst";
        TooltipInput fluid = TooltipInput.materials("[fluid:" + forgeName + "|熔融原子分离催化剂]");
        check(
            fluid.plain()
                .equals(" [熔融原子分离催化剂] ")
                && forgeName.equals(
                    fluid.runs()
                        .get(1).fluidId));
        check(
            TooltipInput.materials("[fluid:ammonium chloride|氯化铵]")
                .plain()
                .equals(" [氯化铵] "));
        TooltipInput badMeta = TooltipInput.materials("[item:test:missing@oops|占位]");
        check(
            badMeta.plain()
                .equals(" [占位] ")
                && badMeta.runs()
                    .get(1).itemId == null);
        int iconCount = 0;
        for (java.util.List<TooltipInput.Run> row : material.wrap(70, font)) {
            int width = 0;
            for (TooltipInput.Run run : row) {
                width += run.width(font);
                if (run.fluidId != null || run.itemId != null) {
                    iconCount++;
                    check(run.text.contains("[") && run.text.contains("]"));
                }
            }
            check(width <= 70);
        }
        check(iconCount == 4);
        TooltipTable materials = TooltipTable
            .of(dye, Arrays.asList("材料", water), Collections.singletonList(Arrays.asList(water, "长段文字需要换行继续显示而不丢失图标")));
        TooltipTableLayout narrow = new TooltipTableLayout(materials, font, 97);
        int widePage = narrow.width() + 81;
        check(narrow.columnWidth(0, widePage) == narrow.columnWidth(0) + 40);
        check(narrow.columnWidth(1, widePage) == narrow.columnWidth(1) + 41);
        check(narrow.columnWidth(0, widePage) + narrow.columnWidth(1, widePage) + TooltipTableLayout.GAP == widePage);
        check(
            narrow.width() == 97 && narrow.columnWidth(0) >= TooltipInput.materials(water)
                .width(font));
        check(
            materials.fallbackLines()
                .get(0)
                .equals(" [青金石] "));
        check(
            materials.fallbackLines()
                .get(1)
                .contains("[水]")
                && !materials.fallbackLines()
                    .get(1)
                    .contains("[fluid:"));
        TooltipTable legacy = TooltipTable.of(
            "",
            Arrays.asList("操作", "结果"),
            Arrays.asList(Arrays.asList(chord, "移动"), Arrays.asList("[left:左击]勾选", "选中")));
        TooltipTable icons = legacy.withInputIcons()
            .align(0, TooltipTable.Alignment.RIGHT)
            .withMeasurementRows(Collections.singletonList(Arrays.asList("[key-middle:Ctrl+中击]", "取样")));
        check(
            legacy.fallbackLines()
                .get(0)
                .contains(chord));
        check(
            icons.fallbackLines()
                .get(0)
                .equals("操作: §e[Shift+右击]解锁  |  结果: 移动"));
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
