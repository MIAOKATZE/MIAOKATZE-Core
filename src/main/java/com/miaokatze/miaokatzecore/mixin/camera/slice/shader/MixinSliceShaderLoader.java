package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.shader.ShaderLoader", remap = false)
public abstract class MixinSliceShaderLoader {

    @Inject(method = "getShaderSource(Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
    private static void core$sliceSource(String resource, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SliceShaders.terrainSource(resource, cir.getReturnValue()));
    }
}
