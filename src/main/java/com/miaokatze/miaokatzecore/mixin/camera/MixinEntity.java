package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void core$freeCursorLook(float yaw, float pitch, CallbackInfo ci) {
        if (WorldCameraHooks.freezeMouseLook((Entity) (Object) this)) ci.cancel();
    }
}
