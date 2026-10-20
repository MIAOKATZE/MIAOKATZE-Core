package com.miaokatze.miaokatzecore.mixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkRenderer", remap = false)
public abstract class MixinAngelicaChunkRenderer {

    @Inject(method = "useBlockFaceCulling", at = @At("HEAD"), cancellable = true, remap = false)
    private void core$orthographicFaceCulling(CallbackInfoReturnable<Boolean> cir) {
        if (WorldCameraHooks.skipCullFaceEnable()) cir.setReturnValue(false);
    }
}
