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

    private static final class State {

        Tier tier = Tier.COMMON;
        String selected = "";
        Map<String, Integer> visible = Collections.emptyMap();
        final Map<String, Integer> variants = new HashMap<>();
    }

    private final String key;
    private final List<Object> blocks = new ArrayList<>();
    private final Map<String, Integer> visible = new LinkedHashMap<>();

    NativeTooltipPage(String key) {
        this.key = key;
    }

    public static NativeTooltipPage forItem(ItemStack stack) {
        return new NativeTooltipPage(TooltipPages.identity(stack));
    }

    public Tier tier() {
        synchronized (states) {
            State s = states.get(key);
            return s == null ? Tier.COMMON : s.tier;
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
        blocks.add(Objects.requireNonNull(text));
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
        for (Tier t : new Tier[] { Tier.DETAIL, Tier.COMMON, Tier.QUICK }) {
            if (!tiers.isEmpty()) tiers += "§7/";
            tiers += (t == active ? "§b" : "§7") + StatCollector.translateToLocal(
                "miaokatzecore.page." + t.name()
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return new TooltipGuide(
            TooltipGuide.Kind.TIER,
            StatCollector.translateToLocalFormatted("miaokatzecore.page.tier", TooltipPages.effectKeyLabel(), tiers));
    }

    public boolean apply(ItemStack stack, List<String> eventLines) {
        if (key.isEmpty() || !key.equals(TooltipPages.identity(stack))) return false;
        List<Object> parts = new ArrayList<>();
        parts.add(hint());
        parts.addAll(blocks);
        TooltipPageBody body = new TooltipPageBody(parts);
        List<String> strings = body.fallbackLines();
        if (!MachineTooltipPages.replaceDescription(stack, eventLines, strings)) return false;
        bind(key, visible);
        NativeTooltips.replace(stack, eventLines, strings, body);
        return true;
    }

    static void bind(String key, Map<String, Integer> visible) {
        synchronized (states) {
            State s = states.computeIfAbsent(key, k -> new State());
            s.visible = new LinkedHashMap<>(visible);
            if (!s.visible.isEmpty() && !s.visible.containsKey(s.selected)) s.selected = s.visible.keySet()
                .iterator()
                .next();
        }
    }

    public static boolean supports(String key) {
        synchronized (states) {
            return states.containsKey(key);
        }
    }

    public static boolean selected(String key, String chartId) {
        synchronized (states) {
            State s = states.get(key);
            return s != null && s.visible.containsKey(chartId) && chartId.equals(s.selected);
        }
    }

    public static String selected(String key) {
        synchronized (states) {
            State s = states.get(key);
            return s != null && s.visible.containsKey(s.selected) ? s.selected : "";
        }
    }

    public static boolean multiple(String key) {
        synchronized (states) {
            State s = states.get(key);
            return s != null && s.visible.size() > 1;
        }
    }

    /** Dispatch only after the local client has verified hover, modifiers and search-field focus. */
    public static boolean act(String key, Action action) {
        synchronized (states) {
            State s = states.get(key);
            if (s == null) return false;
            if (action == Action.TIER) {
                s.tier = Tier.values()[(s.tier.ordinal() + 1) % Tier.values().length];
                s.visible = Collections.emptyMap(); // Don't switch a stale chart before its new page is built.
            } else if (action == Action.SELECT) {
                if (s.visible.size() < 2) return false;
                List<String> ids = new ArrayList<>(s.visible.keySet());
                s.selected = ids.get((ids.indexOf(s.selected) + 1) % ids.size());
            } else {
                Integer count = s.visible.get(s.selected);
                if (count == null) return false;
                s.variants.put(s.selected, (s.variants.getOrDefault(s.selected, 0) + 1) % count);
            }
            return true;
        }
    }
}
