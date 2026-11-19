// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.mixin.tooltip;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.SectionComponent;

/** Replace the assembled header before the first pagination pass. */
@Mixin(value = TooltipContext.class, remap = false)
public interface TooltipContextAccessor {

    @Accessor("sections")
    List<SectionComponent> miaokatze$getSections();
}
