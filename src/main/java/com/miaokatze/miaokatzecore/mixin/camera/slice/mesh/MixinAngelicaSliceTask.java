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
import com.gtnewhorizons.angelica.rendering.celeritas.iris.ContextAwareChunkVertexEncoder;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
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
        method = "drawBlock",
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
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask;encodeTessellated([III[I[ILnet/minecraft/block/Block;Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZLcom/gtnewhorizons/angelica/rendering/celeritas/iris/ContextAwareChunkVertexEncoder;)V"),
        remap = false)
    private void core$nativeLightingScope(int[] raw, int vertexOffset, int count, int[] states, int[] ids, Block block,
        ChunkBuildBuffers buffers, AngelicaChunkBuildContext context, Material material, boolean override,
        ContextAwareChunkVertexEncoder encoder, Operation<Void> original, @Local(argsOnly = true) RenderBlocks renderer,
        @Local(argsOnly = true, ordinal = 1) int x, @Local(argsOnly = true, ordinal = 2) int y,
        @Local(argsOnly = true, ordinal = 3) int z) {
        try (SliceMeshes.FaceScope ignored = SliceMeshes.enter(core$sliceSnapshot, renderer.blockAccess, x, y, z)) {
            original.call(raw, vertexOffset, count, states, ids, block, buffers, context, material, override, encoder);
        }
    }

    @WrapOperation(
        method = "finishDeferred",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask;encodeTessellated([III[I[ILnet/minecraft/block/Block;Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZLcom/gtnewhorizons/angelica/rendering/celeritas/iris/ContextAwareChunkVertexEncoder;)V"),
        remap = false)
    private void core$deferredLightingScope(int[] raw, int vertexOffset, int count, int[] states, int[] ids,
        Block block, ChunkBuildBuffers buffers, AngelicaChunkBuildContext context, Material material, boolean override,
        ContextAwareChunkVertexEncoder encoder, Operation<Void> original, @Local(name = "slice") WorldSlice slice,
        @Local(name = "x") int x, @Local(name = "y") int y, @Local(name = "z") int z) {
        try (SliceMeshes.FaceScope ignored = SliceMeshes.enter(core$sliceSnapshot, slice, x, y, z)) {
            original.call(raw, vertexOffset, count, states, ids, block, buffers, context, material, override, encoder);
        }
    }

    /** Both immediate builds and deferred completion publish through this finalization point. */
    @ModifyExpressionValue(
        method = "finalizeSection",
        at = @At(
            value = "NEW",
            target = "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;Lorg/embeddedt/embeddium/impl/render/chunk/data/BuiltRenderSectionData;Lit/unimi/dsi/fastutil/objects/Reference2ReferenceMap;I)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;"),
        remap = false)
    private ChunkBuildOutput core$stampSliceOutput(ChunkBuildOutput output) {
        return SliceMeshes.tag(output, core$sliceSnapshot);
    }

}
