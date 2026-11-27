// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.structurelib;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.structurelib.ClientProxy.ForgeEventHandler;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

@Mixin(value = ForgeEventHandler.class, remap = false)
public abstract class MixinStructureLibHintRender {

    @Inject(
        method = "onRenderWorldLast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;startDrawing(I)V",
            ordinal = 2,
            shift = At.Shift.AFTER,
            remap = true))
    private void core$resetHintBatchSize(RenderWorldLastEvent event, CallbackInfo ci,
        @Local(name = "cubeDrawn") LocalIntRef cubeDrawn) {
        // The initial batch and depth-mode switch already initialize this counter. Reset only the capacity flush.
        cubeDrawn.set(0);
    }
}
