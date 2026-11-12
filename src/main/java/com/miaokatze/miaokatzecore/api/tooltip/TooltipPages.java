// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.tooltip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;

import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

import cpw.mods.fml.common.registry.GameRegistry;

/** Opt-in presentation state. No ItemStack, NBT, server state or translated label is used as storage. */
public final class TooltipPages {

    private static final Properties prefs = new Properties();
    private static final Set<String> supported = ConcurrentHashMap.newKeySet();
    private static final Set<String> animated = ConcurrentHashMap.newKeySet();
    private static final Set<String> introductoryItems = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String, Long> firstShown = new ConcurrentHashMap<>();
    private static final String LEARNED_FOLD = "learned.tooltipFold";
    private static Path file;

    private static Supplier<String> detailLabel = () -> "V";
    private static Supplier<String> effectLabel = () -> "Alt+V";

    private TooltipPages() {}

    public static void bindLabels(Supplier<String> details, Supplier<String> effects) {
        detailLabel = details;
        effectLabel = effects;
    }

    public static void load(Path path) {
        file = path;
        firstShown.clear();
        prefs.clear();
        if (Files.isRegularFile(path)) try (java.io.InputStream in = Files.newInputStream(path)) {
            prefs.load(in);
            // Older versions saved only non-default values; a collapsed item proves prior use.
            if (!prefs.containsKey(LEARNED_FOLD) && prefs.stringPropertyNames()
                .stream()
                .anyMatch(k -> k.startsWith("details.") && "false".equals(prefs.getProperty(k)))) {
                prefs.setProperty(LEARNED_FOLD, "true");
            }
        } catch (IOException | IllegalArgumentException e) {
            LogManager.getLogger("MIAOKATZE-Core")
                .warn("Cannot read local tooltip preferences", e);
        }
    }

    public static String identity(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (id == null) return "";
        int meta = stack.getHasSubtypes() || "gregtech:gt.blockmachines".equals(id.toString()) ? stack.getItemDamage()
            : 0;
        return id + "@" + meta;
    }

    public static boolean expanded(ItemStack stack) {
        return expanded(identity(stack));
    }

    static boolean expanded(String key) {
        return !"false".equals(prefs.getProperty("details." + key));
    }

    public static void hint(ItemStack stack, List<String> lines, boolean hasAnimation) {
        String key = identity(stack);
        if (key.isEmpty()) return;
        // Every foldable page supports the introductory key hint; warnings advertise their own animation.
        register(key, true);
        String action = expanded(key) ? "collapse" : "expand";
        String hint = StatCollector.translateToLocalFormatted("miaokatzecore.tooltip." + action, detailLabel.get());
        String arrow = expanded(key) ? "▾" : "▸";
        String line = foldHintColor(key, System.nanoTime()) + arrow + " " + hint + "§r";
        if (hasAnimation || !animation(key)) line += "  §8" + StatCollector.translateToLocalFormatted(
            "miaokatzecore.tooltip.animation." + (animation(key) ? "on" : "off"),
            effectLabel.get()) + "§r";
        lines.add(line);
    }

    static String foldHintColor(String key, long nanos) {
        if (prefs.containsKey(LEARNED_FOLD) || !animation(key)) return "§8";
        // Fixed geometry, no bold or obfuscation: only the key hint brightens and fades.
        long ms = Math.floorMod(nanos / 1000000L, 3200L);
        long level = Math.min(ms, 3200L - ms);
        return level < 450 ? "§8" : level < 1050 ? "§7" : level < 1430 ? "§f" : "§e";
    }

    public static String warning(ItemStack stack, String message) {
        long phase = System.nanoTime() / 1000000L % 2400L;
        String color = animation(identity(stack)) && phase >= 700 && phase < 1700 ? "§e" : "§6";
        return color + "» §l" + message + "§r";
    }

    /** Building item text (including NEI indexing) must never count as seeing it. */
    public static String firstLook(ItemStack stack, String line) {
        String key = identity(stack);
        if (key.isEmpty()) return line;
        introductoryItems.add(key);
        return introductoryColor(key) + line;
    }

    public static void rendered(String key) {
        if (file != null && introductoryItems.contains(key) && animation(key)) beginFirstLook(key);
    }

    static void controlRendered(String id) {
        if (file != null) beginFirstLook("control." + id);
    }

    static String controlLabel(String id, String label) {
        return introductoryColor("control." + id) + label + "§r";
    }

    private static void beginFirstLook(String key) {
        if (prefs.getProperty("seen." + key) != null) return;
        prefs.setProperty("seen." + key, "true");
        firstShown.put(key, System.nanoTime());
        save();
    }

    private static String introductoryColor(String key) {
        Long start = firstShown.get(key);
        if (start == null || !animation(key)) return "";
        long age = System.nanoTime() - start;
        if (age >= 1000000000L) {
            firstShown.remove(key, start);
            return "";
        }
        return age < 600000000L ? "§e" : "§6";
    }

    public static boolean supports(String key) {
        return supported.contains(key);
    }

    static void register(String key, boolean effect) {
        supported.add(key);
        if (effect) animated.add(key);
    }

    static boolean animation(String key) {
        return !"false".equals(prefs.getProperty("animation." + key));
    }

    public static boolean toggle(String key, boolean effect) {
        if (!supported.contains(key) || effect && !animated.contains(key) || file == null) return false;
        String field = (effect ? "animation." : "details.") + key;
        if ("false".equals(prefs.getProperty(field))) prefs.remove(field);
        else prefs.setProperty(field, "false");
        if (!effect) prefs.setProperty(LEARNED_FOLD, "true");
        save();
        return true;
    }

    private static void save() {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            prefs.store(out, "Local tooltip preferences; missing entries are enabled");
            Files.createDirectories(file.getParent());
            AtomicFilePublisher.publishAtomic(file, out.toByteArray());
        } catch (IOException e) {
            LogManager.getLogger("MIAOKATZE-Core")
                .warn("Cannot save local tooltip preferences", e);
        }
    }
}
