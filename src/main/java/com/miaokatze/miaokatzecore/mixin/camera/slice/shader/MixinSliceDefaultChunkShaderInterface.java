package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface", remap = false)
public abstract class MixinSliceDefaultChunkShaderInterface {

    @Inject(method = "setupState", at = @At("RETURN"))
    private void core$sliceUniforms(CallbackInfo ci) {
        SliceShaders.uploadCurrentProgram();
    }
}
