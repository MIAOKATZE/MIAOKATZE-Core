package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

import cpw.mods.fml.common.Loader;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow
    private WorldRenderer[] worldRenderers;

    @Inject(method = "clipRenderersByFrustum", at = @At("HEAD"), cancellable = true)
    private void core$frustum(ICamera camera, float partialTicks, CallbackInfo ci) {
        if (!WorldCameraHooks.disableVanillaFrustum() || Loader.isModLoaded("angelica")) return;
        if (worldRenderers != null) for (WorldRenderer renderer : worldRenderers) {
            if (renderer != null && !renderer.skipAllRenderPasses()) renderer.isInFrustum = true;
        }
        ci.cancel();
    }
}
