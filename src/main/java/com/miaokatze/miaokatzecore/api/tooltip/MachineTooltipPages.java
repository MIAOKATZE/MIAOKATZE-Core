// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTSplit;
import gregtech.common.blocks.ItemMachines;

/** Replaces only the machine's own description, leaving GT parameters, covers and other mods' text in place. */
public final class MachineTooltipPages {

    private static final Pattern PARAMETER = Pattern.compile("%%%(.*?)%%%");

    private MachineTooltipPages() {}

    public static void apply(ItemStack stack, List<String> lines, String summaryKey, String warningKey) {
        IMetaTileEntity meta = ItemMachines.getMetaTileEntity(stack);
        if (meta == null || !StatCollector.canTranslate(summaryKey)) return;
        if (meta instanceof ISecondaryDescribable && ((ISecondaryDescribable) meta).isDisplaySecondaryDescription())
            return;
        String[] raw = meta.getDescription();
        if (raw == null || raw.length == 0) return;
        List<String> description = Arrays.asList(raw);
        int index = Collections.indexOfSubList(lines, description);
        if (index < 0) {
            String key = "gt.blockmachines." + meta.getMetaName() + ".tooltip";
            if (StatCollector.canTranslate(key + "." + stack.getItemDamage())) key += "." + stack.getItemDamage();
            String translated = StatCollector.translateToLocal(key);
            if (translated.contains("%s")) {
                List<String> args = new ArrayList<>();
                for (String line : raw) {
                    if (line == null) continue;
                    Matcher matcher = PARAMETER.matcher(line);
                    while (matcher.find()) args.add(matcher.group(1));
                }
                try {
                    description = Arrays.asList(GTSplit.splitFormatted(translated, args.toArray()));
                } catch (java.util.IllegalFormatException ignored) {
                    // A resource pack can change placeholders. Never remove an unrecognized description.
                    description = Arrays.asList(raw);
                }
            } else description = Arrays.asList(GTSplit.split(translated));
            index = Collections.indexOfSubList(lines, description);
        }
        String warning = warningKey == null ? null : StatCollector.translateToLocal(warningKey);
        if (index >= 0 && !TooltipPages.expanded(stack)) {
            lines.subList(index, index + description.size())
                .clear();
            lines.addAll(
                index,
                Arrays.asList(
                    StatCollector.translateToLocal(summaryKey)
                        .split("\\|")));
        }
        if (warning != null) {
            int at = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (warning.equals(EnumChatFormatting.getTextWithoutFormattingCodes(lines.get(i)))) {
                    at = i;
                    break;
                }
            }
            if (at >= 0) lines.set(at, TooltipPages.warning(stack, warning));
            else lines.add(TooltipPages.warning(stack, warning));
        }
        if (index >= 0 || warning != null) TooltipPages.hint(stack, lines, warning != null);
    }
}
