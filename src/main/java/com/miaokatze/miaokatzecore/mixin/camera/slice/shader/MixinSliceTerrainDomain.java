package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkRenderer", remap = false)
public abstract class MixinSliceTerrainDomain {

    @WrapMethod(method = "render")
    private void core$sliceTerrain(ChunkRenderMatrices matrices, CommandList commands, ChunkRenderListIterable lists,
        TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera, Operation<Void> original) {
        try (SliceShaders.Scope ignored = SliceShaders.domain(SliceShaders.Domain.TERRAIN)) {
            original.call(matrices, commands, lists, pass, occlusionCamera, camera);
        }
    }
}
