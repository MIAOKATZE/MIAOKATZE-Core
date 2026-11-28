// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.miaokatze.miaokatzecore.api.tooltip.MachineTooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;

/** Opt-in authored GT description. Page construction is read-only; only a matching item can install its controls. */
public final class NativeTooltipPage {

    public enum Tier {
        QUICK,
        COMMON,
        DETAIL
    }

    public enum Action {
        TIER,
        SELECT,
        VARIANT
    }

    private static final Map<String, State> states = new HashMap<>();
    // Compatibility lookup for key-based public APIs. Window actions use their own immutable document.
    private static final Map<String, TooltipPageBody> documents = new LinkedHashMap<String, TooltipPageBody>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TooltipPageBody> entry) {
            return size() > 128;
        }
    };

    private static final class State {

        Tier tier = Tier.COMMON;
        String selected = "";
        final Map<String, Integer> variants = new HashMap<>();
    }

    private static final Tier[] ALL_HINTS = { Tier.COMMON, Tier.DETAIL, Tier.QUICK };
    private static final Tier[] TWO_HINTS = { Tier.COMMON, Tier.QUICK };
    private boolean compact;
    private boolean single;
    private final String key;
    private final List<Object> blocks = new ArrayList<>();
    private final Map<String, Integer> visible = new LinkedHashMap<>();

    NativeTooltipPage(String key) {
        this.key = key;
    }

    public static NativeTooltipPage forItem(ItemStack stack) {
        return new NativeTooltipPage(TooltipPages.identity(stack));
    }

    /** Full copy is COMMON; no hidden DETAIL stop in the keyboard cycle. */
    public NativeTooltipPage twoTiers() {
        compact = true;
        return this;
    }

    /** Fixed authored page without a tier hint or tier shortcut. */
    public NativeTooltipPage singleTier() {
        single = true;
        return this;
    }

    public Tier tier() {
        if (single) return Tier.COMMON;
        synchronized (states) {
            State s = states.get(key);
            return s == null || (compact && s.tier == Tier.DETAIL) ? Tier.COMMON : s.tier;
        }
    }

    public int variant(String id, int count) {
        if (count < 1) throw new IllegalArgumentException("No chart variants");
        synchronized (states) {
            State s = states.get(key);
            return s == null ? 0 : Math.floorMod(s.variants.getOrDefault(id, 0), count);
        }
    }

    public void line(String text) {
        blocks.add(TooltipInput.hasMaterial(text) ? TooltipInput.materials(text) : Objects.requireNonNull(text));
    }

    public void block(TooltipBlock block) {
        blocks.add(Objects.requireNonNull(block));
    }

    /** Input hints use explicit [right:label], [key:label], or [key-right:label] markers. */
    public void input(String text) {
        block(TooltipInput.of(text));
    }

    public void qr(String prefix, String value) {
        block(TooltipQr.of(prefix, value));
    }

    public void warning(String text) {
        block(new TooltipGuide(TooltipGuide.Kind.WARNING, text));
    }

    public void structure(String text) {
        block(new TooltipGuide(TooltipGuide.Kind.STRUCTURE, text));
    }

    public void divider() {
        line("§8§m--------------------------------");
    } // Native GT/Chromatic divider.

    public void chart(String id, List<String> labels, TooltipChart chart) {
        if (labels.size() > 1 && visible.put(id, labels.size()) != null)
            throw new IllegalArgumentException("Duplicate chart: " + id);
        block(chart.variants(id, labels, variant(id, labels.size())));
    }

    private TooltipGuide hint() {
        Tier active = tier();
        String tiers = "";
        for (Tier t : compact ? TWO_HINTS : ALL_HINTS) {
            if (!tiers.isEmpty()) tiers += "§7/";
            tiers += (t == active ? "§b" : "§7") + StatCollector.translateToLocal(
                "miaokatzecore.page." + t.name()
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return new TooltipGuide(
            TooltipGuide.Kind.TIER,
            StatCollector.translateToLocalFormatted("miaokatzecore.page.tier", TooltipPages.effectKeyLabel(), tiers));
    }

    TooltipPageBody body() {
        List<Object> parts = new ArrayList<>();
        if (!single) parts.add(hint());
        parts.addAll(blocks);
        return new TooltipPageBody(parts, key, visible, compact, single, tier());
    }

    /** Append only this item's own lines; never replace the existing vanilla or third-party prefix. */
    public boolean appendTo(ItemStack stack, List<String> lines) {
        return finish(stack, lines, false);
    }

    public boolean apply(ItemStack stack, List<String> eventLines) {
        return finish(stack, eventLines, true);
    }

    private boolean finish(ItemStack stack, List<String> lines, boolean replaceMachineDescription) {
        if (key.isEmpty() || !key.equals(TooltipPages.identity(stack))) return false;
        TooltipPageBody body = body();
        List<String> strings = body.fallbackLines();
        if (replaceMachineDescription) {
            if (!MachineTooltipPages.replaceDescription(stack, lines, strings)) return false;
        } else lines.addAll(strings);
        remember(body);
        NativeTooltips.replace(stack, lines, strings, body);
        return true;
    }

    static void remember(TooltipPageBody document) {
        synchronized (states) {
            documents.put(document.key, document);
        }
    }

    public static TooltipPageBody document(String key) {
        synchronized (states) {
            return documents.get(key);
        }
    }

    public static boolean supports(String key) {
        return document(key) != null;
    }

    public static boolean selected(String key, String chartId) {
        return selected(document(key), chartId);
    }

    public static boolean selected(TooltipPageBody document, String chartId) {
        return document != null && document.charts.containsKey(chartId) && chartId.equals(selected(document));
    }

    public static String selected(String key) {
        return selected(document(key));
    }

    public static String selected(TooltipPageBody document) {
        if (document == null || document.charts.isEmpty()) return "";
        synchronized (states) {
            State state = states.get(document.key);
            String selected = state == null ? "" : state.selected;
            return document.charts.containsKey(selected) ? selected : document.charts.keySet().iterator().next();
        }
    }

    public static boolean multiple(String key) {
        return multiple(document(key));
    }

    public static boolean multiple(TooltipPageBody document) {
        return document != null && document.charts.size() > 1;
    }

    public static boolean selectVariant(String key, String chartId, int index) {
        return selectVariant(document(key), chartId, index);
    }

    public static boolean selectVariant(TooltipPageBody document, String chartId, int index) {
        if (document == null) return false;
        synchronized (states) {
            Integer count = document.charts.get(chartId);
            if (count == null || index < 0 || index >= count || !currentTier(document)) return false;
            State state = states.computeIfAbsent(document.key, k -> new State());
            state.selected = chartId;
            state.variants.put(chartId, index);
            return true;
        }
    }

    public static boolean act(String key, Action action) {
        return act(document(key), action);
    }

    /** The caller supplies the rendered document, not the latest page authored for the same item. */
    public static boolean act(TooltipPageBody document, Action action) {
        if (document == null) return false;
        synchronized (states) {
            State state = states.computeIfAbsent(document.key, k -> new State());
            if (action == Action.TIER) {
                if (document.single) return false;
                state.tier = document.compact ? (document.tier == Tier.COMMON ? Tier.QUICK : Tier.COMMON)
                    : Tier.values()[(document.tier.ordinal() + 1) % Tier.values().length];
            } else {
                if (!currentTier(document)) return false;
                String selected = selected(document);
                if (action == Action.SELECT) {
                    if (document.charts.size() < 2) return false;
                    List<String> ids = new ArrayList<>(document.charts.keySet());
                    state.selected = ids.get((ids.indexOf(selected) + 1) % ids.size());
                } else {
                    Integer count = document.charts.get(selected);
                    if (count == null) return false;
                    state.selected = selected;
                    state.variants.put(selected, (state.variants.getOrDefault(selected, 0) + 1) % count);
                }
            }
            return true;
        }
    }

    private static boolean currentTier(TooltipPageBody document) {
        State state = states.get(document.key);
        Tier tier = state == null ? Tier.COMMON : state.tier;
        if (document.single || document.compact && tier == Tier.DETAIL) tier = Tier.COMMON;
        return tier == document.tier;
    }
}
