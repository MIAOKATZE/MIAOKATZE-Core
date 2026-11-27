// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.structurelib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.gtnewhorizon.structurelib.ClientProxy;

@Mixin(value = ClientProxy.class, remap = false)
public interface StructureLibHintAccessor {

    @Accessor("allHintsDirty")
    static void core$setAllHintsDirty(boolean dirty) {
        throw new AssertionError("StructureLib hint accessor was not transformed");
    }
}
