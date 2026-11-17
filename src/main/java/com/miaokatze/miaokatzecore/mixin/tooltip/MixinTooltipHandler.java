// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.mixin.tooltip;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipRequest;

/** Selected only on clients with the target Chromatic API installed. */
@Mixin(value = TooltipHandler.class, remap = false)
public abstract class MixinTooltipHandler {

    @Inject(
        method = "drawHoveringText(Lcom/slprime/chromatictooltips/api/TooltipRequest;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void miaokatze$holdRequest(TooltipRequest request, CallbackInfo ci) {
        if (TooltipPinning.request(request)) ci.cancel();
    }

    @Inject(method = "drawLastTooltip", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$drawFixed(CallbackInfo ci) {
        if (TooltipPinning.drawPinned()) ci.cancel();
    }

    @Inject(method = "getLastTooltipContext", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$fixedContext(CallbackInfoReturnable<TooltipContext> cir) {
        if (TooltipPinning.active(Minecraft.getMinecraft().currentScreen)) cir.setReturnValue(TooltipPinning.context());
    }

    @Inject(method = "nextTooltipPage", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$nextFixedPage(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipPinning.active(Minecraft.getMinecraft().currentScreen)) cir.setReturnValue(
            TooltipPinning.context()
                .nextTooltipPage());
    }

    @Inject(method = "previousTooltipPage", at = @At("HEAD"), cancellable = true)
    private static void miaokatze$previousFixedPage(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipPinning.active(Minecraft.getMinecraft().currentScreen)) cir.setReturnValue(
            TooltipPinning.context()
                .previousTooltipPage());
    }
}
