// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltips;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipBlock;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipGuide;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipPageBody;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipQr;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.miaokatze.miaokatzecore.mixin.tooltip.TooltipContextAccessor;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.component.SectionComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;
import com.slprime.chromatictooltips.event.TooltipEnricherEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Optional client adapter. Components are introduced only inside Chromatic's native converter event. */
public final class NativeTooltipClient implements NativeTooltips.Backend {

    private static final Map<Class<?>, BiFunction<String, TooltipBlock, ITooltipComponent>> renderers = new LinkedHashMap<>();
    private final Thread clientThread = Thread.currentThread();
    private final Map<String, Binding> bindings = new LinkedHashMap<String, Binding>(128, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Binding> eldest) {
            return size() > 128;
        }
    };

    private static final class Binding {

        final String item;
        final List<String> expected;
        final TooltipBlock snapshot;

        Binding(String item, List<String> expected, TooltipBlock snapshot) {
            this.item = item;
            this.expected = expected;
            this.snapshot = snapshot;
        }
    }

    private static boolean installed;

    public static void install() {
        if (installed) return;
        installed = true;
        registerRenderer(TooltipTable.class, NativeTableComponent::new);
        registerRenderer(TooltipQr.class, NativeQrComponent::new);
        registerRenderer(TooltipInput.class, NativeInputComponent::new);
        registerRenderer(TooltipGuide.class, NativeGuideComponent::new);
        registerRenderer(TooltipChart.class, NativeChartComponent::new);
        NativeTooltipClient adapter = new NativeTooltipClient();
        NativeTooltips.install(adapter);
        MinecraftForge.EVENT_BUS.register(adapter);
    }

    /** Extension slot for images, sprite animation or charts; model code stays graphics-independent. */
    public static <T extends TooltipBlock> void registerRenderer(Class<T> type,
        BiFunction<String, T, ITooltipComponent> renderer) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(renderer);
        if (renderers.containsKey(type))
            throw new IllegalArgumentException("Duplicate tooltip renderer: " + type.getName());
        renderers.put(type, (item, block) -> renderer.apply(item, type.cast(block)));
    }

    @Override
    public boolean prepare(String item, List<String> expected, TooltipBlock snapshot) {
        if (Thread.currentThread() != clientThread) return false;
        boolean page = snapshot instanceof TooltipPageBody;
        if (!page && !renderers.containsKey(snapshot.getClass())) return false;
        // Group title is caller-owned and stable across runtime value updates.
        bindings.put(item + "\u0000" + (page ? "\u0000page" : expected.get(0)), new Binding(item, expected, snapshot));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void convert(TextLinesConverterEvent event) {
        if (Thread.currentThread() != clientThread) return;
        String item = TooltipPages.identity(event.target.getItem());
        if (item.isEmpty()) return;
        convert(item, event.list);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void positionHeader(TooltipEnricherEvent event) {
        if (Thread.currentThread() != clientThread) return;
        String item = TooltipPages.identity(event.context.getItem());
        if (item.isEmpty() && !event.context.getTarget()
            .isFluid()) return;
        SectionComponent header = event.context.getSection("header");
        SectionComponent body = event.context.getActiveModifier() == TooltipModifier.NONE
            ? event.context.getSection("body")
            : null;
        SectionComponent keys = TierHeaderSlot.compose(header, body, new NativePinComponent(item));
        if (keys != header) {
            List<SectionComponent> sections = ((TooltipContextAccessor) event.context).miaokatze$getSections();
            sections.set(sections.indexOf(header), keys);
        }
    }

    void convert(String item, List<Object> lines) {
        for (Binding binding : new ArrayList<>(bindings.values())) {
            if (!binding.item.equals(item)) continue;
            int start = Collections.indexOfSubList(lines, binding.expected);
            if (start < 0) continue;
            if (binding.snapshot instanceof TooltipPageBody) {
                if (Collections.lastIndexOfSubList(lines, binding.expected) != start) continue;
                List<TooltipPageBody.Span> spans = ((TooltipPageBody) binding.snapshot).spans;
                // Reverse order preserves offsets, including equal text in different owned spans.
                for (int i = spans.size() - 1; i >= 0; i--) {
                    TooltipPageBody.Span span = spans.get(i);
                    BiFunction<String, TooltipBlock, ITooltipComponent> factory = renderers.get(span.block.getClass());
                    if (factory == null) continue;
                    ITooltipComponent part;
                    try {
                        part = factory.apply(item, span.block);
                    } catch (RuntimeException | LinkageError unavailable) {
                        continue;
                    }
                    if (part != null) {
                        if (span.block instanceof TooltipGuide
                            && ((TooltipGuide) span.block).kind == TooltipGuide.Kind.TIER)
                            part = new TierHeaderSlot(part);
                        lines.subList(start + span.from, start + span.to)
                            .clear();
                        lines.add(start + span.from, part);
                    }
                }
                continue;
            }
            BiFunction<String, TooltipBlock, ITooltipComponent> renderer = renderers.get(binding.snapshot.getClass());
            ITooltipComponent component;
            try {
                component = renderer.apply(item, binding.snapshot);
            } catch (RuntimeException | LinkageError unavailable) {
                continue;
            }
            if (component == null) continue;
            lines.subList(start, start + binding.expected.size())
                .clear();
            lines.add(start, component);
        }
        NativeLinkComponent.convert(lines);
    }
}
