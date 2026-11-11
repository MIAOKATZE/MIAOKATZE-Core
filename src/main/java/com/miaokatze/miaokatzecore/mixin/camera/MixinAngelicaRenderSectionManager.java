// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.mixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager", remap = false)
public abstract class MixinAngelicaRenderSectionManager {

    @Inject(
        method = { "shouldUseOcclusionCulling", "useFogOcclusion" },
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void core$orthographicOcclusion(CallbackInfoReturnable<Boolean> cir) {
        if (WorldCameraHooks.disableVanillaFrustum()) cir.setReturnValue(false);
    }
}
