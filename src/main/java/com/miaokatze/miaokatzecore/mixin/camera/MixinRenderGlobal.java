package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

import cpw.mods.fml.common.Loader;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow
    private WorldRenderer[] worldRenderers;

    @WrapOperation(
        method = "renderEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean core$deferPlayerBody(RenderManager manager, Entity entity, float partialTicks,
        Operation<Boolean> original) {
        return WorldCameraHooks.deferPlayerBody(entity) || original.call(manager, entity, partialTicks);
    }

    @Inject(method = "clipRenderersByFrustum", at = @At("HEAD"), cancellable = true)
    private void core$frustum(ICamera camera, float partialTicks, CallbackInfo ci) {
        if (!WorldCameraHooks.disableVanillaFrustum() || Loader.isModLoaded("angelica")) return;
        if (worldRenderers != null) for (WorldRenderer renderer : worldRenderers) {
            if (renderer != null && !renderer.skipAllRenderPasses()) renderer.isInFrustum = true;
        }
        ci.cancel();
    }
}
