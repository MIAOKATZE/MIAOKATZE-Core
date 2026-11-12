// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.tooltip;

import java.nio.file.Files;
import java.nio.file.Path;

/** Two GT machine metas share the same item registry ID, but not the same presentation preferences. */
public final class TooltipPreferenceContracts {

    public static void run() throws Exception {
        Path dir = Files.createTempDirectory("tooltip-pages");
        Path file = dir.resolve("preferences.properties");
        String entangler = "gregtech:gt.blockmachines@19001";
        String other = "gregtech:gt.blockmachines@19002";
        try {
            TooltipPages.load(file);
            TooltipPages.register(entangler, true);
            require(
                !TooltipPages.foldHintColor(entangler, 0)
                    .equals(TooltipPages.foldHintColor(entangler, 1550000000L)));
            TooltipPages.register(other, false);
            require(TooltipPages.expanded(entangler) && TooltipPages.animation(entangler));
            require(
                !TooltipPages.controlLabel("refund", "scope")
                    .startsWith("§"));
            TooltipPages.controlRendered("refund");
            require(
                TooltipPages.controlLabel("refund", "scope")
                    .startsWith("§e"));
            TooltipPages.load(file);
            TooltipPages.controlRendered("refund");
            require(
                !TooltipPages.controlLabel("refund", "scope")
                    .startsWith("§"));
            require(!TooltipPages.toggle("missing", false) && !TooltipPages.toggle(other, true));
            require(TooltipPages.toggle(entangler, true));
            require("§8".equals(TooltipPages.foldHintColor(entangler, 1550000000L)));
            require(TooltipPages.toggle(entangler, true));
            require(!"§8".equals(TooltipPages.foldHintColor(entangler, 1550000000L)));
            require(TooltipPages.toggle(entangler, false) && !TooltipPages.expanded(entangler));
            require("§8".equals(TooltipPages.foldHintColor(other, 1550000000L)));
            require(TooltipPages.animation(entangler) && TooltipPages.expanded(other));
            require(TooltipPages.toggle(entangler, true));
            TooltipPages.load(file);
            require(!TooltipPages.expanded(entangler) && !TooltipPages.animation(entangler));
            require(TooltipPages.toggle(entangler, false) && !TooltipPages.animation(entangler));
            require(TooltipPages.toggle(entangler, true));
            TooltipPages.load(file);
            require(TooltipPages.expanded(entangler) && TooltipPages.animation(entangler));
            require("§8".equals(TooltipPages.foldHintColor(entangler, 1550000000L)));
            Files.write(file, java.util.Collections.singletonList("details.legacy=false"));
            TooltipPages.load(file);
            require("§8".equals(TooltipPages.foldHintColor(other, 1550000000L)));
        } finally {
            Files.deleteIfExists(file);
            Files.delete(dir);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("per-item tooltip preference contract");
    }
}
