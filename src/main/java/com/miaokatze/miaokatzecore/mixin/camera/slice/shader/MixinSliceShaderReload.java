package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Mixin(Minecraft.class)
public abstract class MixinSliceShaderReload {

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void core$sliceResourceReloaded(CallbackInfo ci) {
        SliceShaders.resourceReloaded();
    }
}
