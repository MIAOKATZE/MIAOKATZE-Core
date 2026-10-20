package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.glsm.ffp.Program;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.ffp.ShaderManager", remap = false)
public abstract class MixinSliceShaderManager {

    @Shadow
    private Program currentProgram;

    @Inject(method = "uploadUniforms", at = @At("RETURN"))
    private void core$sliceUniforms(CallbackInfo ci) {
        if (currentProgram != null) SliceShaders.upload(currentProgram.getProgramId());
    }
}
