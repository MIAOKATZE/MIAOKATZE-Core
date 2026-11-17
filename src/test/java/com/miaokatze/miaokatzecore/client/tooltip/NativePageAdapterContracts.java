// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.JsonObject;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipGuide;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipPageBody;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.api.TooltipStyle;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.component.KeyboardModifierComponent;
import com.slprime.chromatictooltips.component.SectionComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

public final class NativePageAdapterContracts {

    public static void run(TooltipPageBody body, TooltipPageBody tierPage) {
        TooltipPinVisualContracts.run();
        NativeTooltipClient register = new NativeTooltipClient();
        NativeTooltipClient.registerRenderer(TooltipChart.class, NativeChartComponent::new);
        ITooltipComponent tier = fixedLine();
        NativeTooltipClient.registerRenderer(
            TooltipGuide.class,
            (item, guide) -> guide.kind == TooltipGuide.Kind.TIER ? tier : new NativeGuideComponent(item, guide));
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
        headerOrder(register, tierPage, tier);
    }

    private static final TooltipStyle STYLE = new TooltipStyle(new JsonObject());

    private static void headerOrder(NativeTooltipClient register, TooltipPageBody body, ITooltipComponent hint) {
        register.prepare("solar", body.fallbackLines(), body);
        List<Object> rows = new ArrayList<>(body.fallbackLines());
        register.convert("solar", rows);
        ITooltipComponent converted = (ITooltipComponent) rows.get(0);
        ITooltipComponent name = fixedLine(), third = fixedLine(), warning = fixedLine(), pin = fixedLine();
        KeyboardModifierComponent keys = new KeyboardModifierComponent(
            EnumSet.noneOf(TooltipModifier.class),
            TooltipModifier.NONE) {

            @Override
            public int getWidth() {
                return 30;
            }

            @Override
            public int getHeight() {
                return 9;
            }
        };
        SectionComponent header = section(
            "header",
            section("title", name),
            section("keyboard-modifier", keys),
            section("third-party", third));
        SectionComponent info = section("itemInfo", converted, warning), content = section("body", info);
        assertOrder(section("page", header, content), name, keys, third, hint, warning); // The observed regression.
        if (!TierHeaderSlot.pin(header, pin) || !TierHeaderSlot.pin(header, pin))
            throw new AssertionError("Pin missing");
        TierHeaderSlot.place(header, content);
        TierHeaderSlot.place(header, content);
        assertOrder(section("page", header, content), name, pin, hint, keys, third, warning);
        // Routes without our tier block must keep the original key bar and structure untouched.
        SectionComponent normalHeader = section("header", section("title", name), section("keyboard-modifier", keys));
        SectionComponent structure = section("body", section("itemInfo", warning));
        TierHeaderSlot.place(normalHeader, structure);
        assertOrder(section("page", normalHeader, structure), name, keys, warning);
        TierHeaderSlot.pin(normalHeader, pin);
        assertOrder(section("page", normalHeader, structure), name, pin, keys, warning);
        if (TierHeaderSlot.pin(section("header", section("other", third)), pin))
            throw new AssertionError("A non-item/headerless tooltip gained a status row");
    }

    private static ITooltipComponent fixedLine() {
        return new ITooltipComponent() {

            @Override
            public int getWidth() {
                return 40;
            }

            @Override
            public int getHeight() {
                return 9;
            }

            @Override
            public int getSpacing() {
                return 1;
            }

            @Override
            public void draw(int x, int y, int w, TooltipContext c) {}
        };
    }

    // The real Chromatic section/pagination code; only font installation is skipped in the no-GL JVM.
    private static SectionComponent section(String id, ITooltipComponent... children) {
        return new SectionComponent(id, STYLE, Arrays.asList(children)) {

            {
                fontContext = new TooltipFontContext(STYLE) {

                    @Override
                    public void pushContext() {}

                    @Override
                    public void popContext() {}
                };
            }
        };
    }

    private static void assertOrder(SectionComponent root, ITooltipComponent... expected) {
        List<ITooltipComponent> actual = new ArrayList<>();
        for (ITooltipComponent page : root.paginate(null, 400, 300)) flatten(page, actual);
        if (actual.size() != expected.length)
            throw new AssertionError("Tier duplicated/lost in paginated sections: " + actual.size());
        for (int i = 0; i < expected.length; i++)
            if (actual.get(i) != expected[i]) throw new AssertionError("Incorrect paged header order at " + i);
    }

    private static void flatten(ITooltipComponent part, List<ITooltipComponent> leaves) {
        if (part instanceof SectionComponent) {
            for (ITooltipComponent child : ((SectionComponent) part).getComponents()) flatten(child, leaves);
        } else leaves.add(part);
    }
}
