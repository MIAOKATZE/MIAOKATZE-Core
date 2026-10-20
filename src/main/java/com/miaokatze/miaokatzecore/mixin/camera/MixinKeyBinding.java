package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.settings.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Mixin(KeyBinding.class)
public abstract class MixinKeyBinding {

    @Inject(method = "setKeyBindState", at = @At("HEAD"), cancellable = true)
    private static void core$keyState(int keyCode, boolean pressed, CallbackInfo ci) {
        // Always let releases through, so pre-existing held keys cannot become stuck.
        if (pressed && !WorldCameraHooks.allowKey(keyCode)) ci.cancel();
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private static void core$keyTick(int keyCode, CallbackInfo ci) {
        if (!WorldCameraHooks.allowKey(keyCode)) ci.cancel();
    }
}
