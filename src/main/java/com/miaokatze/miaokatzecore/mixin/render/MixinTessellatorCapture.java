package com.miaokatze.miaokatzecore.mixin.render;

import net.minecraft.client.renderer.Tessellator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.render.internal.TessellatorCapture;

/** Records only which vanilla raw slots addVertex actually writes; inactive outside a capture scope. */
@Mixin(Tessellator.class)
public abstract class MixinTessellatorCapture {

    @Inject(method = "addVertex", at = @At("HEAD"))
    private void miaokatze$captureVertex(double x, double y, double z, CallbackInfo ci) {
        TessellatorCapture.vertex((Tessellator) (Object) this);
    }

    @Inject(method = { "reset", "startDrawing" }, at = @At("HEAD"))
    private void miaokatze$invalidateRestart(CallbackInfo ci) {
        TessellatorCapture.invalidate((Tessellator) (Object) this);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void miaokatze$invalidateDraw(CallbackInfoReturnable<Integer> cir) {
        TessellatorCapture.invalidate((Tessellator) (Object) this);
    }
}
