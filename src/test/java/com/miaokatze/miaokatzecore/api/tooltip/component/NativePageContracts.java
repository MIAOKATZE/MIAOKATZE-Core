// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.miaokatze.miaokatzecore.api.tooltip.MachinePageSpanContracts;
import com.miaokatze.miaokatzecore.client.tooltip.NativePageAdapterContracts;

public final class NativePageContracts {

    public static void main(String[] args) {
        MachinePageSpanContracts.run();
        String item = "test:item@6";
        NativeTooltipPage p = new NativeTooltipPage(item);
        Map<String, Integer> charts = new LinkedHashMap<>();
        charts.put("route", 3);
        charts.put("dimension", 47);
        NativeTooltipPage.bind(item, charts);
        check(p.tier() == NativeTooltipPage.Tier.COMMON && NativeTooltipPage.selected(item, "route"));
        check(NativeTooltipPage.act(item, NativeTooltipPage.Action.VARIANT) && p.variant("route", 3) == 1);
        check(
            NativeTooltipPage.act(item, NativeTooltipPage.Action.SELECT)
                && NativeTooltipPage.selected(item, "dimension"));
        for (int i = 0; i < 48; i++) NativeTooltipPage.act(item, NativeTooltipPage.Action.VARIANT);
        check(p.variant("route", 3) == 1 && p.variant("dimension", 47) == 1);
        NativeTooltipPage.act(item, NativeTooltipPage.Action.TIER);
        NativeTooltipPage.bind(item, charts);
        check(p.tier() == NativeTooltipPage.Tier.DETAIL && NativeTooltipPage.selected(item, "dimension"));
        NativeTooltipPage.act(item, NativeTooltipPage.Action.TIER);
        NativeTooltipPage.bind(item, Collections.emptyMap());
        check(
            p.tier() == NativeTooltipPage.Tier.QUICK && !NativeTooltipPage.act(item, NativeTooltipPage.Action.VARIANT));
        check(!NativeTooltipPage.act(item, NativeTooltipPage.Action.SELECT));
        NativeTooltipPage.act(item, NativeTooltipPage.Action.TIER);
        NativeTooltipPage.bind(item, charts);
        check(p.tier() == NativeTooltipPage.Tier.COMMON && NativeTooltipPage.selected(item, "dimension"));
        check(!NativeTooltipPage.act("test:item@7", NativeTooltipPage.Action.TIER));
        NativeTooltipPage.bind("single", Collections.singletonMap("only", 6));
        check(!NativeTooltipPage.act("single", NativeTooltipPage.Action.SELECT));

        TooltipTable table = TooltipTable.of(
            "Mixtures",
            Arrays.asList("Fluid", "Water"),
            Arrays.asList(Arrays.asList("A", "0.5×"), Arrays.asList("B", "2×")));
        double[][] data = { { .5 }, { 2 } };
        List<String> labels = new ArrayList<>(Arrays.asList("a", "b"));
        TooltipChart heat = TooltipChart.heat("Mixtures", table, data, 4, labels, Collections.singletonList("water"))
            .media(
                Arrays.asList("1×", "4×"),
                Arrays.asList("10 L/s", "15 L/s"),
                Collections.singletonList("0.5×"),
                Collections.singletonList("3 L/s"),
                false);
        data[0][0] = 123;
        labels.set(0, "changed");
        check(
            heat.value(0, 0) == .5 && heat.rowFluids.get(0)
                .equals("a")
                && heat.fallbackLines()
                    .toString()
                    .contains("15 L/s"));
        double[] xs = { 0, 1, 5 }, ys = { 0, .5, .8 };
        TooltipChart curve = TooltipChart
            .lines("Stock", "x", "P/V", xs, Collections.singletonList("f(x)"), new double[][] { ys })
            .range(5, 1)
            .mark(1, .5);
        xs[1] = 4;
        ys[1] = 12;
        check(
            curve.x(1) == 1 && curve.value(0, 1) == .5
                && curve.fallbackLines()
                    .contains("(1, 0.5)"));
        boolean invalid = false;
        try {
            TooltipChart.bars("bad", table, new double[] { 1, 2 }, new double[] { 0, 3 }, 2);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid);
        TooltipChart bars = TooltipChart.bars("Power", table, new double[] { 1, 2 }, null, 4);
        check(bars.previous(1) == 2);
        TooltipPageBody body = new TooltipPageBody(
            Arrays.asList("first", new TooltipGuide(TooltipGuide.Kind.WARNING, "safe gap"), heat, "last"));
        NativePageAdapterContracts.run(body);
        System.out.println(
            "NativePageContracts: PASS (tier/selection, immutable chart data, GT finisher, Chromatic page conversion)");
    }

    private static void check(boolean ok) {
        if (!ok) throw new AssertionError("Authored tooltip contract");
    }
}
