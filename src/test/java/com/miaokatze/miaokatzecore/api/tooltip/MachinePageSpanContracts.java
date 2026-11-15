// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MachinePageSpanContracts {

    public static void run() {
        List<String> desc = Arrays.asList("original", "--", "Hold §l[LSHIFT]§r for structure");
        List<String> replacement = Arrays.asList("Tier", "purpose", "warning", "structure", "brand");
        List<String> lines = new ArrayList<>();
        lines.add("Item name");
        lines.addAll(desc);
        lines.add("Cover");
        if (!MachineTooltipPages.replaceOwned(lines, desc, replacement) || !lines.equals(
            Arrays.asList("Item name", "Tier", "purpose", "warning", "structure", "brand", desc.get(2), "Cover")))
            throw new AssertionError("GT title, finisher or cover moved");
        lines = new ArrayList<>(Arrays.asList("Name", "original", "--", "Cover", "§!shift"));
        if (!MachineTooltipPages.replaceOwned(lines, desc, replacement) || !lines.get(lines.size() - 1)
            .equals("§!shift")) throw new AssertionError("Chromatic finisher");
        List<String> unsupported = new ArrayList<>(Arrays.asList("Name", "changed", "--", "Cover", "§!shift"));
        List<String> copy = new ArrayList<>(unsupported);
        if (MachineTooltipPages.replaceOwned(unsupported, desc, replacement) || !copy.equals(unsupported))
            throw new AssertionError("Unmatched structure/description changed");
    }
}
