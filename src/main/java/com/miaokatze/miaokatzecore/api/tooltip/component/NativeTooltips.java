// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;

/** Opt-in component bridge. No client or optional library types enter the common API. */
public final class NativeTooltips {

    public interface Backend {

        /** Register an immutable snapshot for actual rendering only; never retain the ItemStack. */
        boolean prepare(String itemKey, List<String> expected, TooltipBlock snapshot);
    }

    private static volatile Backend backend;

    private NativeTooltips() {}

    public static void install(Backend clientBackend) {
        backend = clientBackend;
    }

    /**
     * Schedule replacement of the caller-owned consecutive lines at render time. The source list stays intact for
     * search, plain-text UI and optional-library fallback.
     */
    public static boolean replace(ItemStack item, List<String> lines, List<String> expected, TooltipBlock snapshot) {
        if (backend == null || snapshot == null || expected == null || expected.isEmpty()) return false;
        int start = Collections.indexOfSubList(lines, expected);
        if (start < 0) return false;
        String key = TooltipPages.identity(item);
        if (key.isEmpty()) return false;
        try {
            return backend.prepare(key, Collections.unmodifiableList(new java.util.ArrayList<>(expected)), snapshot);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
}
