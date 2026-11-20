// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.awt.Point;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipGuide;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipPageBody;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.api.TooltipStyle;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.component.InlineComponent;
import com.slprime.chromatictooltips.component.KeyboardModifierComponent;
import com.slprime.chromatictooltips.component.SectionComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

public final class NativePageAdapterContracts {

    public static void run(TooltipPageBody body, TooltipPageBody tierPage) {
        TooltipPinVisualContracts.run();
        NativeTooltipClient register = new NativeTooltipClient();
        NativeTooltipClient.registerRenderer(TooltipChart.class, NativeChartComponent::new);
        List<String> draws = new ArrayList<>();
        ITooltipComponent tier = fixedLine((x, y) -> draws.add("tier " + x + "," + y));
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
        headerOrder(register, tierPage, draws);
        unregisteredTarget(register);
        pinKeyDuringMouseMotion();
    }

    private static void unregisteredTarget(NativeTooltipClient adapter) {
        TooltipTarget target = testTarget(new Item());
        List<Object> text = Arrays.asList("NEI usage tooltip", "https://example.com/");
        TextLinesConverterEvent event = new TextLinesConverterEvent(target, new ArrayList<>(text));
        adapter.convert(event);
        if (!event.list.equals(text)) throw new AssertionError("Unregistered item tooltip was modified");
    }

    private static TooltipTarget testTarget(Item item) {
        try {
            // Build the real Chromatic target without loading the vanilla fluid-container registry in this JVM.
            Constructor<TooltipTarget> constructor = TooltipTarget.class
                .getDeclaredConstructor(ItemStack.class, FluidStack.class, long.class, Fluid.class, long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new ItemStack(item), null, 1L, null, 0L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create an unregistered item target", exception);
        }
    }

    private static void pinKeyDuringMouseMotion() {
        Point cursor = new Point(10, 20), moved = new Point(12, 20);
        if (!TooltipPinning.canPinHover(cursor, cursor, 100, false, false, false)
            || !TooltipPinning.canPinHover(cursor, moved, 100, false, true, true)
            || TooltipPinning.canPinHover(cursor, moved, 100, false, false, false)
            || TooltipPinning.canPinHover(cursor, moved, 100, true, true, false)
            || TooltipPinning.canPinHover(cursor, moved, 300_000_001L, false, true, true)
            || !TooltipPinning.canPinHover(cursor, moved, 300_000_001L, true, false, false))
            throw new AssertionError(
                "The explicit pin key may still pin the same item or tooltip, but not an overlapping new slot");
    }

    private static final TooltipStyle STYLE = new TooltipStyle(new JsonObject());

    private static void headerOrder(NativeTooltipClient register, TooltipPageBody body, List<String> draws) {
        register.prepare("solar", body.fallbackLines(), body);
        List<Object> rows = new ArrayList<>(body.fallbackLines());
        register.convert("solar", rows);
        ITooltipComponent converted = (ITooltipComponent) rows.get(0);
        ITooltipComponent name = fixedLine((x, y) -> {}), third = fixedLine((x, y) -> {}),
            warning = fixedLine((x, y) -> {}), pin = fixedLine((x, y) -> draws.add("pin " + x + "," + y));
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

            @Override
            public void draw(int x, int y, int w, TooltipContext context) {
                draws.add("native " + x + "," + y);
            }
        };
        SectionComponent header = section(
            "header",
            section("title", name),
            section("keyboard-modifier", keys),
            section("third-party", third));
        SectionComponent info = section("itemInfo", converted, warning), content = section("body", info);
        SectionComponent composed = TierHeaderSlot.compose(header, content, pin);
        List<ITooltipComponent> leaves = pagedLeaves(section("page", composed, content));
        if (leaves.size() != 4 || leaves.get(0) != name || leaves.get(2) != third || leaves.get(3) != warning)
            throw new AssertionError("Unrelated header and body order changed: " + leaves);
        assertInline(leaves.get(1), draws, "native 10,20", "pin 44,20", "tier 88,20");

        SectionComponent structure = section("body", section("itemInfo", warning));
        leaves = pagedLeaves(section("page", TierHeaderSlot.compose(header, null, pin), structure));
        assertInline(leaves.get(1), draws, "native 10,20", "pin 44,20");
        if (leaves.get(3) != warning) throw new AssertionError("Structure content was moved");

        SectionComponent withoutKeys = section("header", section("title", name), section("third-party", third));
        leaves = pagedLeaves(section("page", TierHeaderSlot.compose(withoutKeys, null, pin), structure));
        assertInline(leaves.get(1), draws, "pin 10,20");
        if (leaves.get(2) != third || TierHeaderSlot.compose(null, null, pin) != null)
            throw new AssertionError("Missing keyboard section fallback changed neighboring content");
    }

    private static void assertInline(ITooltipComponent row, List<String> draws, String... expected) {
        if (!(row instanceof InlineComponent)) throw new AssertionError("Controls do not share an inline row");
        draws.clear();
        row.draw(10, 20, 400, null);
        if (!draws.equals(Arrays.asList(expected))) throw new AssertionError("Wrong key order or baseline: " + draws);
    }

    private static ITooltipComponent fixedLine(BiConsumer<Integer, Integer> drawAt) {
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
            public void draw(int x, int y, int w, TooltipContext c) {
                drawAt.accept(x, y);
            }
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

    private static List<ITooltipComponent> pagedLeaves(SectionComponent root) {
        List<ITooltipComponent> actual = new ArrayList<>();
        for (ITooltipComponent page : root.paginate(null, 400, 300)) flatten(page, actual);
        return actual;
    }

    private static void flatten(ITooltipComponent part, List<ITooltipComponent> leaves) {
        if (part instanceof SectionComponent) {
            for (ITooltipComponent child : ((SectionComponent) part).getComponents()) flatten(child, leaves);
        } else leaves.add(part);
    }
}
