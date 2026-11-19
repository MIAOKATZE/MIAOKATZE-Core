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

/** Select the optional GUI preview optimization after the client mods are discovered. */
@LateMixin
public final class BlockRendererLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.miaokatzecore.blockrenderer.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!FMLLaunchHandler.side()
            .isClient() || !loadedMods.contains("blockrenderer6343")) return Collections.emptyList();
        ModContainer blockRenderer = Loader.instance()
            .getIndexedModList()
            .get("blockrenderer6343");
        // Reuse the cache only for the inspected world and renderer contracts.
        if (blockRenderer == null || !"1.4.25".equals(blockRenderer.getVersion())) return Collections.emptyList();
        return Arrays.asList("blockrenderer.MixinTrackedPreviewWorld", "blockrenderer.MixinPreviewSceneRenderer");
    }
}
