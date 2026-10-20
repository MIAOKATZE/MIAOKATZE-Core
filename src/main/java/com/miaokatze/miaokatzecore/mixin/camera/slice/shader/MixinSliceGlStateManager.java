package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.GLStateManager", remap = false)
public abstract class MixinSliceGlStateManager {

    @Inject(method = "glDeleteProgram(I)V", at = @At("HEAD"))
    private static void core$sliceProgramDeleted(int program, CallbackInfo ci) {
        SliceShaders.forgetProgram(program);
    }
}
