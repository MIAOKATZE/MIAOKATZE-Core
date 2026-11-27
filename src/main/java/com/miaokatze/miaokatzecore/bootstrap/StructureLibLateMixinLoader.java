// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.bootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Select the client hint optimizations independently of Core's other integrations. */
@LateMixin
public final class StructureLibLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.miaokatzecore.structurelib.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!FMLLaunchHandler.side().isClient() || !loadedMods.contains("structurelib")) return Collections.emptyList();
        ModContainer structureLib = Loader.instance().getIndexedModList().get("structurelib");
        // These hooks depend on the 1.4.47 renderer's exact loop and decorative particle tail.
        if (structureLib == null || !"1.4.47".equals(structureLib.getVersion())) return Collections.emptyList();
        return Arrays.asList(
            "structurelib.MixinStructureLibHintTick",
            "structurelib.MixinStructureLibHintRender",
            "structurelib.MixinStructureLibHintEffects");
    }
}
