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
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;

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
        if (!renderers.containsKey(snapshot.getClass())) return false;
        // Group title is caller-owned and stable across runtime value updates.
        bindings.put(item + "\u0000" + expected.get(0), new Binding(item, expected, snapshot));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void convert(TextLinesConverterEvent event) {
        if (Thread.currentThread() != clientThread) return;
        String item = TooltipPages.identity(event.target.getItem());
        if (item.isEmpty()) return;
        for (Binding binding : new ArrayList<>(bindings.values())) {
            if (!binding.item.equals(item)) continue;
            int start = Collections.indexOfSubList(event.list, binding.expected);
            if (start < 0) continue;
            BiFunction<String, TooltipBlock, ITooltipComponent> renderer = renderers.get(binding.snapshot.getClass());
            ITooltipComponent component;
            try {
                component = renderer.apply(item, binding.snapshot);
            } catch (RuntimeException | LinkageError unavailable) {
                continue;
            }
            if (component == null) continue;
            event.list.subList(start, start + binding.expected.size())
                .clear();
            event.list.add(start, component);
        }
    }
}
