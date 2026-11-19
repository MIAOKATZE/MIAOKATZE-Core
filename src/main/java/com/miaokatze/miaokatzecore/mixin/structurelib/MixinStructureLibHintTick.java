// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.structurelib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.structurelib.ClientProxy.FMLEventHandler;
import com.miaokatze.miaokatzecore.client.preview.StructureLibHintLifecycle;

import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;

@Mixin(value = FMLEventHandler.class, remap = false)
public abstract class MixinStructureLibHintTick {

    @Inject(
        method = "onClientTick",
        at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V", shift = At.Shift.AFTER))
    private void core$markRenderListCurrent(ClientTickEvent event, CallbackInfo ci) {
        // The native tick just rebuilt the dirty list, if needed, and sorted it. Future movement still re-sorts.
        StructureLibHintLifecycle.listSorted();
    }
}
