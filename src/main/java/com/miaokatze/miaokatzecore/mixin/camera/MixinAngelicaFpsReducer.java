package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

/** Angelica also opens the automatic menu when minimizing skips EntityRenderer entirely. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.FpsReducer", remap = false)
public abstract class MixinAngelicaFpsReducer {

    @WrapOperation(
        method = "onRenderSkipped",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayInGameMenu()V", remap = true),
        remap = false,
        require = 0)
    private static void core$automaticPause(Minecraft mc, Operation<Void> original) {
        WorldCameraHooks.pauseOnLostFocus(mc, () -> original.call(mc));
    }
}
