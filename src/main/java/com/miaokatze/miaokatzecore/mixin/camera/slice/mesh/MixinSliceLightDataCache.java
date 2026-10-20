package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceLightingRules;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

/** Celeritas recalculates light after RenderBlocks; apply the same frozen slice to its separate cache. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.light.LightDataCache", remap = false)
public abstract class MixinSliceLightDataCache {

    @Inject(method = "compute", at = @At("RETURN"), cancellable = true, remap = false)
    private void core$cutLight(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        HeightSlice slice = SliceMeshes.lightingSlice();
        if (!SliceLightingRules.compensates(slice, y)) return;
        int original = cir.getReturnValue();
        if (SliceLightingRules.removedSample(slice, y)) {
            // Removed or fading layers neither block the sky nor occlude native smooth AO.
            cir.setReturnValue(
                LightDataAccess.packAO(1) | LightDataAccess.packSL(15)
                    | LightDataAccess.packBL(LightDataAccess.unpackBL(original))
                    | LightDataAccess.packLU(LightDataAccess.unpackLU(original)));
        } else cir.setReturnValue(original & ~LightDataAccess.packSL(15) | LightDataAccess.packSL(15));
    }
}
