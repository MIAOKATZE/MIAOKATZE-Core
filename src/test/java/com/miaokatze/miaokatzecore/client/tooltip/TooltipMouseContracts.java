// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure layout assertions exercise the same tab window used for painting and mouse regions. */
public final class TooltipMouseContracts {

    public static void run() {
        TooltipTabLayout first = new TooltipTabLayout(47, 0, 88, i -> "[" + i + "]", "[0]", String::length);
        check(!first.before && first.after && first.labels.get(0).index == 0);
        TooltipTabLayout middle = new TooltipTabLayout(47, 23, 88, i -> "[" + i + "]", "[23]", String::length);
        check(middle.before && middle.after && middle.labels.get(0).index > 0);
        TooltipTabLayout last = new TooltipTabLayout(47, 46, 88, i -> "[" + i + "]", "[46]", String::length);
        check(last.before && !last.after && last.labels.get(last.labels.size() - 1).index == 46);
        for (TooltipTabLayout layout : new TooltipTabLayout[] { first, middle, last }) {
            int previous = -1;
            for (TooltipTabLayout.Label label : layout.labels) {
                check(label.index > previous && label.width == label.text.length());
                check(label.x >= 3 && label.x + label.width <= 88);
                previous = label.index;
            }
        }
        String text = "§7蓝图站点：§bhttps://miaokatze.com/blueprints§r。 旁文";
        List<TooltipTextLinks.Span> spans = TooltipTextLinks.spans(text);
        check(spans.size() == 1);
        TooltipTextLinks.Span link = spans.get(0);
        check(link.url.equals("https://miaokatze.com/blueprints") && link.from == 5 && link.to == 37);
        check(
            TooltipTextLinks.plain(TooltipTextLinks.prefix(text, link.from))
                .equals("蓝图站点："));
        check(
            TooltipTextLinks.spans("javascript:evil() file:///tmp/a https:///missing-host")
                .isEmpty());
        check(
            TooltipTextLinks.spans("http://example.org/a, https://example.org/b。")
                .size() == 2);
        check(!TooltipTextLinks.valid("ftp://example.org/a") && !TooltipTextLinks.valid("https:///bad"));
        List<Object> plainLines = new ArrayList<>(Arrays.asList("说明", text, "file:///tmp/a"));
        NativeLinkComponent.convert(plainLines);
        check(
            plainLines.get(0)
                .equals("说明") && plainLines.get(1) instanceof NativeLinkComponent
                && plainLines.get(2)
                    .equals("file:///tmp/a"));
        System.out.println("TooltipMouseContracts: PASS (drawn tab windows, original indices, HTTP(S) glyph spans)");
    }

    private static void check(boolean valid) {
        if (!valid) throw new AssertionError("Tooltip mouse layout contract");
    }
}
