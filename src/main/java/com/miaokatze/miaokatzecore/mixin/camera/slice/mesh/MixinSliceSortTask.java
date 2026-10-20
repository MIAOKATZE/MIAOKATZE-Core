package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshTask;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderSortTask", remap = false)
public abstract class MixinSliceSortTask implements SliceMeshTask {

    @Unique
    private SliceMeshes.Snapshot core$sliceSnapshot;

    @Override
    public void core$setSliceSnapshot(SliceMeshes.Snapshot snapshot) {
        core$sliceSnapshot = snapshot;
    }

    @ModifyExpressionValue(
        method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkSortOutput;",
        at = @At(
            value = "NEW",
            target = "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;ILit/unimi/dsi/fastutil/objects/Reference2ReferenceMap;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkSortOutput;"),
        remap = false)
    private ChunkSortOutput core$stampSortedGeometry(ChunkSortOutput output) {
        return SliceMeshes.tag(output, core$sliceSnapshot);
    }
}
