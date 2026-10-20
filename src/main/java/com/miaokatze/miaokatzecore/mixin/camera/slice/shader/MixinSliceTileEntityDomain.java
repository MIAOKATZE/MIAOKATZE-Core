package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer", remap = false)
public abstract class MixinSliceTileEntityDomain {

    // Includes ModelPartBatcher.flush and TesrBatchRenderer.flush, which execute after individual TESRs.
    @WrapMethod(method = "renderBlockEntities(F)I")
    private int core$sliceTileEntities(float partialTicks, Operation<Integer> original) {
        try (SliceShaders.Scope ignored = SliceShaders.domain(SliceShaders.Domain.TILE_ENTITIES)) {
            return original.call(partialTicks);
        }
    }
}
