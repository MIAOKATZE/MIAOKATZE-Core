// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipGuide;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipPageBody;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;

public final class NativePageAdapterContracts {

    public static void run(TooltipPageBody body) {
        NativeTooltipClient register = new NativeTooltipClient();
        NativeTooltipClient.registerRenderer(TooltipChart.class, NativeChartComponent::new);
        NativeTooltipClient.registerRenderer(TooltipGuide.class, NativeGuideComponent::new);
        if (!register.prepare("solar", body.fallbackLines(), body)) throw new AssertionError();
        List<Object> raw = new ArrayList<>();
        raw.add("safe gap");
        raw.addAll(body.fallbackLines());
        raw.add("other mod");
        TextLinesConverterEvent event = new TextLinesConverterEvent(TooltipTarget.EMPTY, raw);
        register.convert("solar", event.list);
        if (!event.list.get(0)
            .equals("safe gap")
            || !event.list.get(1)
                .equals("first")
            || !(event.list.get(2) instanceof NativeGuideComponent)
            || !(event.list.get(3) instanceof NativeChartComponent)
            || !event.list.subList(4, 6)
                .equals(Arrays.asList("last", "other mod")))
            throw new AssertionError("Native order/ownership");
        List<Object> changed = new ArrayList<>(body.fallbackLines());
        changed.set(0, "structure");
        List<Object> copy = new ArrayList<>(changed);
        register.convert("solar", changed);
        if (!copy.equals(changed)) throw new AssertionError("Partial match converted");
    }
}
