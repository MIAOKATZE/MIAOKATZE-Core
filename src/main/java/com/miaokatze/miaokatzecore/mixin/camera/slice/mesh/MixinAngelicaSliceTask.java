package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshTask;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask", remap = false)
public abstract class MixinAngelicaSliceTask implements SliceMeshTask {

    @Unique
    private SliceMeshes.Snapshot core$sliceSnapshot;

    @Override
    public void core$setSliceSnapshot(SliceMeshes.Snapshot snapshot) {
        this.core$sliceSnapshot = snapshot;
    }

    @WrapOperation(
        method = "renderBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z",
            remap = true),
        remap = false)
    private boolean core$nativeBoundaryScope(RenderBlocks renderer, Block block, int x, int y, int z,
        Operation<Boolean> original) {
        IBlockAccess originalAccess = renderer.blockAccess;
        renderer.blockAccess = SliceMeshes.renderAccess(core$sliceSnapshot, originalAccess, y);
        try (SliceMeshes.FaceScope ignored = SliceMeshes.enter(core$sliceSnapshot, renderer.blockAccess, x, y, z)) {
            return original.call(renderer, block, x, y, z);
        } finally {
            renderer.blockAccess = originalAccess;
        }
    }

    @WrapOperation(
        method = "renderBlock",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;copyRawBuffer([II[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V"),
        remap = false)
    private void core$nativeLightingScope(AngelicaChunkBuildContext context, int[] raw, int count, int[] states,
        int[] ids, ChunkBuildBuffers buffers, Material material, boolean override, boolean smooth,
        Operation<Void> original, @Local(argsOnly = true) RenderBlocks renderer,
        @Local(argsOnly = true, ordinal = 1) int x, @Local(argsOnly = true, ordinal = 2) int y,
        @Local(argsOnly = true, ordinal = 3) int z) {
        try (SliceMeshes.FaceScope ignored = SliceMeshes.enter(core$sliceSnapshot, renderer.blockAccess, x, y, z)) {
            original.call(context, raw, count, states, ids, buffers, material, override, smooth);
        }
    }

    @ModifyExpressionValue(
        method = { "execute", "completeDeferred" },
        at = @At(
            value = "NEW",
            target = "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;Lorg/embeddedt/embeddium/impl/render/chunk/data/BuiltRenderSectionData;Lit/unimi/dsi/fastutil/objects/Reference2ReferenceMap;I)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;"),
        remap = false)
    private ChunkBuildOutput core$stampSliceOutput(ChunkBuildOutput output) {
        return SliceMeshes.tag(output, core$sliceSnapshot);
    }
}
