package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @WrapMethod(method = "renderWorld")
    private void core$renderWorld(float partialTicks, long time, Operation<Void> original) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean success = false;
        try {
            WorldCameraHooks.beginWorldRender(mc, partialTicks);
            original.call(partialTicks, time);
            success = true;
        } catch (RuntimeException | Error failure) {
            WorldCameraHooks.abortWorldRender();
            throw failure;
        } finally {
            try {
                WorldCameraHooks.endWorldRender(mc);
            } catch (RuntimeException | Error failure) {
                WorldCameraHooks.abortWorldRender();
                throw failure;
            }
            if (success) WorldCameraHooks.completeWorldRender(mc);
        }
    }

    @Inject(method = "setupCameraTransform", at = @At("RETURN"))
    private void core$projection(float partialTicks, int pass, CallbackInfo ci) {
        WorldCameraHooks.applyProjection(Minecraft.getMinecraft());
    }

    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V",
            shift = At.Shift.BEFORE,
            remap = false))
    private void core$restoreViewBeforeWorldLast(float partialTicks, long time, CallbackInfo ci) {
        WorldCameraHooks.beforeWorldLast(Minecraft.getMinecraft());
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void core$hideHand(float partialTicks, int pass, CallbackInfo ci) {
        if (WorldCameraHooks.hideHand()) ci.cancel();
    }

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void core$fog(int mode, float partialTicks, CallbackInfo ci) {
        if (WorldCameraHooks.setupFog()) ci.cancel();
    }

    @Inject(method = "renderCloudsCheck", at = @At("HEAD"), cancellable = true)
    private void core$clouds(RenderGlobal renderer, float partialTicks, CallbackInfo ci) {
        // The orthographic eye may be above the cloud plane while the anchor is on the factory floor.
        if (WorldCameraHooks.orthographicActive()) ci.cancel();
    }

    @WrapOperation(
        method = "renderWorld",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void core$enable(int capability, Operation<Void> original) {
        if (capability != GL11.GL_CULL_FACE || !WorldCameraHooks.skipCullFaceEnable()) original.call(capability);
    }
}
