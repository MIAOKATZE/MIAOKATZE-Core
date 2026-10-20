package com.miaokatze.miaokatzecore.mixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

/** Optional Angelica/Celeritas compatibility, with no hard class linkage. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.viewport.Viewport", remap = false)
public abstract class MixinCeleritasViewport {

    @Inject(
        method = { "isBoxVisible(DDDDDD)Z", "isBoxVisible(IIIF)Z", "isBoxVisible(IIIFFF)Z" },
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void core$orthographicFrustum(CallbackInfoReturnable<Boolean> cir) {
        if (WorldCameraHooks.disableVanillaFrustum()) cir.setReturnValue(true);
    }
}
