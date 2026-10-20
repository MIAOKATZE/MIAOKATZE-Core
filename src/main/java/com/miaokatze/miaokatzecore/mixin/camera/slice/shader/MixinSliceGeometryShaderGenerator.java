package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.ffp.GeometryShaderGenerator", remap = false)
public abstract class MixinSliceGeometryShaderGenerator {

    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private static void core$sliceGeometry(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SliceShaders.geometrySource(cir.getReturnValue()));
    }
}
