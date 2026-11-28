// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.mixin.tooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.tooltip.TooltipController;
import com.slprime.chromatictooltips.TooltipHandler;

/** Chromatic has no page-navigation event. Adapt only its explicit next/previous commands, never global contexts. */
@Mixin(value = TooltipHandler.class, remap = false)
public abstract class MixinTooltipHandler {
    @Inject(method = "nextTooltipPage", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$nextFixedPage(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = TooltipController.navigatePinned(true);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "previousTooltipPage", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$previousFixedPage(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = TooltipController.navigatePinned(false);
        if (result != null) cir.setReturnValue(result);
    }
}
