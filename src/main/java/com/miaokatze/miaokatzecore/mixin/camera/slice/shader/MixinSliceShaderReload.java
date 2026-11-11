// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.mixin.camera.slice.shader;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

@Mixin(Minecraft.class)
public abstract class MixinSliceShaderReload {

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void core$sliceResourceReloaded(CallbackInfo ci) {
        SliceShaders.resourceReloaded();
    }
}
