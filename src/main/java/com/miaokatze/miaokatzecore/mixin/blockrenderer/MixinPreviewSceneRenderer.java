// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.blockrenderer;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.preview.BlockRendererPreviewCache;
import com.miaokatze.miaokatzecore.client.preview.PreviewCacheHooks;
import com.miaokatze.miaokatzecore.client.preview.TrackedPreviewRevision;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongSet;

/** Optional, domain-scoped substitution. Camera, raytrace, overlays and all TESR callbacks stay upstream. */
@Pseudo
@Mixin(targets = "blockrenderer6343.client.renderer.WorldSceneRenderer", remap = false)
public abstract class MixinPreviewSceneRenderer {

    @Shadow
    @Final
    private RenderBlocks bufferBuilder;
    @Shadow
    private boolean renderAllFaces;
    @Shadow
    private Consumer<Object> onRender;
    @Shadow
    @Final
    public LongSet renderedBlocks;
    @Unique
    private World core$previewWorld;
    @Unique
    private BlockRendererPreviewCache core$previewCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void core$rememberWorld(@Coerce World world, CallbackInfo callback) {
        core$previewWorld = world;
    }

    @Inject(method = { "resetRenderedBlocks", "setRenderAllFaces" }, at = @At("RETURN"))
    private void core$selectionChanged(CallbackInfo callback) {
        if (core$previewCache != null) core$previewCache.invalidate();
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true)
    private void core$appendCachedGeometry(Tessellator tessellator, LongCollection positions, boolean faded,
        CallbackInfo callback) {
        if (!PreviewCacheHooks.isReady() || faded
            || positions != renderedBlocks
            || positions.isEmpty()
            || !(core$previewWorld instanceof TrackedPreviewRevision)
            || !BlockRendererPreviewCache.supports(tessellator, bufferBuilder)) return;
        TrackedPreviewRevision revision = (TrackedPreviewRevision) core$previewWorld;
        if (core$previewCache == null)
            core$previewCache = new BlockRendererPreviewCache(core$previewWorld, revision::miaokatze$previewRevision);
        if (!core$previewCache.prepare(positions)) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int ambientOcclusion = minecraft.gameSettings.ambientOcclusion;
        minecraft.gameSettings.ambientOcclusion = 0;
        tessellator.startDrawingQuads();
        try {
            tessellator.setBrightness(0x00f000f0);
            core$previewCache.append(tessellator, bufferBuilder, renderAllFaces);
            if (onRender != null) onRender.accept(this);
        } finally {
            minecraft.gameSettings.ambientOcclusion = ambientOcclusion;
            tessellator.draw();
            tessellator.setTranslation(0, 0, 0);
        }
        callback.cancel();
    }

    // BlockRenderer 1.4.25 traverses tileMap directly, including native pass and selection checks.
}
