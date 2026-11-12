// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.tooltip;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.GuiContext;

/** Building or measuring a control must not use up its first visible highlight. */
public final class IntroductoryControlTooltip extends RichTooltip {

    private final String id;

    public IntroductoryControlTooltip(String id, Supplier<String> label, Supplier<String> detail) {
        this.id = id;
        setAutoUpdate(true);
        tooltipBuilder(page -> {
            page.addLine(TooltipPages.controlLabel(id, label.get()));
            page.addLine(detail.get());
        });
    }

    @Override
    public void draw(GuiContext context, ItemStack stack) {
        TooltipPages.controlRendered(id);
        super.draw(context, stack);
    }
}
