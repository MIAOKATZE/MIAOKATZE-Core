// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
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
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.component.InlineComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;
import com.slprime.chromatictooltips.event.TooltipEnricherEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Optional rendering backend. Authored documents and actual rendered targets have separate lifetimes. */
public final class NativeTooltipClient implements NativeTooltips.Backend {

    private static final Map<Class<?>, BiFunction<String, TooltipBlock, ITooltipComponent>> renderers = new LinkedHashMap<>();
    // Weak, identity-based targets are owned by Chromatic contexts; ordinary rendering cannot retain a GUI/world.
    private static final Map<TooltipTarget, TooltipPageBody> renderedDocuments = new WeakHashMap<>();
    private final Thread clientThread = Thread.currentThread();
    private final Map<String, Map<String, Binding>> bindings = new LinkedHashMap<String, Map<String, Binding>>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Map<String, Binding>> eldest) {
            return size() > 128;
        }
    };

    private static final class Binding {
        final List<String> expected;
        final TooltipBlock snapshot;
        Binding(List<String> expected, TooltipBlock snapshot) {
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
        if (Minecraft.getMinecraft().getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(manager -> {
                adapter.bindings.clear();
                renderedDocuments.clear();
                TooltipPinning.clear();
            });
        }
    }

    public static <T extends TooltipBlock> void registerRenderer(Class<T> type,
        BiFunction<String, T, ITooltipComponent> renderer) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(renderer);
        if (renderers.containsKey(type)) throw new IllegalArgumentException("Duplicate tooltip renderer: " + type.getName());
        renderers.put(type, (item, block) -> renderer.apply(item, type.cast(block)));
    }

    @Override
    public boolean prepare(String item, List<String> expected, TooltipBlock snapshot) {
        if (Thread.currentThread() != clientThread || expected.isEmpty()) return false;
        boolean page = snapshot instanceof TooltipPageBody;
        if (!page && !renderers.containsKey(snapshot.getClass())) return false;
        String id = page ? "\u0000page" : expected.get(0);
        Map<String, Binding> current = bindings.get(item);
        Binding prior = current == null ? null : current.get(id);
        if (prior != null && prior.snapshot == snapshot && prior.expected.equals(expected)) return true;
        // Publish on authoring changes. Converters can iterate a stable per-item snapshot without allocating or
        // exposing an iterator to a reentrant third-party renderer's prepare() call.
        Map<String, Binding> updated = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
        updated.put(id, new Binding(expected, snapshot));
        if (updated.size() > 16) updated.remove(updated.keySet().iterator().next());
        bindings.put(item, Collections.unmodifiableMap(updated));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void convert(TextLinesConverterEvent event) {
        if (Thread.currentThread() != clientThread) return;
        String item = TooltipPages.identity(event.target.getItem());
        if (item.isEmpty()) return;
        TooltipPageBody document = convertDocument(item, event.list);
        if (document != null) renderedDocuments.put(event.target, document);
    }

    static TooltipPageBody document(TooltipContext context) {
        return context == null ? null : renderedDocuments.get(context.getTarget());
    }

    /** Public section insertion preserves upstream title/modifier trees and their revision tracking. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void positionHeader(TooltipEnricherEvent event) {
        if (Thread.currentThread() != clientThread) return;
        String item = TooltipPages.identity(event.context.getItem());
        if (item.isEmpty() && !event.context.getTarget().isFluid()) return;
        List<ITooltipComponent> controls = new ArrayList<>();
        controls.add(new NativePinComponent(item));
        TooltipPageBody document = document(event.context);
        if (document != null && event.context.getActiveModifier() == TooltipModifier.NONE) {
            for (TooltipPageBody.Span span : document.spans) {
                if (span.block instanceof TooltipGuide && ((TooltipGuide) span.block).kind == TooltipGuide.Kind.TIER)
                    controls.add(new NativeGuideComponent(item, (TooltipGuide) span.block));
            }
        }
        addControls(event.context, controls);
    }

    static void addControls(TooltipContext context, List<ITooltipComponent> controls) {
        int afterHeader = context.getSections().indexOf(context.getSection("header")) + 1;
        context.addSection(afterHeader, "miaokatzecore-controls",
            Collections.singletonList(new InlineComponent(Collections.singletonList(controls), 4, 0)));
    }

    void convert(String item, List<Object> lines) { convertDocument(item, lines); }

    private TooltipPageBody convertDocument(String item, List<Object> lines) {
        Map<String, Binding> itemBindings = bindings.get(item);
        TooltipPageBody document = null;
        if (itemBindings != null) for (Binding binding : itemBindings.values()) {
            int start = Collections.indexOfSubList(lines, binding.expected);
            if (start < 0) continue;
            if (binding.snapshot instanceof TooltipPageBody) {
                if (Collections.lastIndexOfSubList(lines, binding.expected) != start) continue;
                document = (TooltipPageBody) binding.snapshot;
                List<TooltipPageBody.Span> spans = document.spans;
                for (int i = spans.size() - 1; i >= 0; i--) {
                    TooltipPageBody.Span span = spans.get(i);
                    BiFunction<String, TooltipBlock, ITooltipComponent> factory = renderers.get(span.block.getClass());
                    if (factory == null) continue;
                    boolean tier = span.block instanceof TooltipGuide && ((TooltipGuide) span.block).kind == TooltipGuide.Kind.TIER;
                    ITooltipComponent part = tier ? null : render(factory, item, span.block);
                    if (!tier && part == null) continue;
                    lines.subList(start + span.from, start + span.to).clear();
                    if (part != null) lines.add(start + span.from, part);
                }
            } else {
                BiFunction<String, TooltipBlock, ITooltipComponent> factory = renderers.get(binding.snapshot.getClass());
                if (factory == null) continue;
                ITooltipComponent part = render(factory, item, binding.snapshot);
                if (part == null) continue;
                lines.subList(start, start + binding.expected.size()).clear();
                lines.add(start, part);
            }
        }
        NativeLinkComponent.convert(lines);
        return document;
    }
    private static ITooltipComponent render(BiFunction<String, TooltipBlock, ITooltipComponent> factory,
        String item, TooltipBlock block) {
        try {
            return factory.apply(item, block);
        } catch (RuntimeException | LinkageError unavailable) {
            // Keep the complete original lines if a renderer or an optional client API is unavailable.
            return null;
        }
    }

}
