package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
        try (SliceMeshes.FaceScope ignored = SliceMeshes.enter(core$sliceSnapshot, renderer.blockAccess, x, y, z)) {
            return original.call(renderer, block, x, y, z);
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
