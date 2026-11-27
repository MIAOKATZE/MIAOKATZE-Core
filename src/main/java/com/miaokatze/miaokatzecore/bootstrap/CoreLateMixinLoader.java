// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.bootstrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Optional mod targets must be selected after FML discovers the installed mods. */
@LateMixin
public final class CoreLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.miaokatzecore.bq.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!FMLLaunchHandler.side()
            .isClient()) {
            return Collections.emptyList();
        }
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("betterquesting"))
            mixins.addAll(Arrays.asList("bq.MixinNetQuestSync", "bq.MixinNetChapterSync"));
        if (loadedMods.contains("chromatictooltips"))
            mixins.addAll(Arrays.asList("tooltip.MixinTooltipHandler", "tooltip.TooltipContextAccessor"));
        if (loadedMods.contains("structurelib")) {
            ModContainer structureLib = Loader.instance().getIndexedModList().get("structurelib");
            // These hooks depend on the 1.4.47 renderer's exact loop and decorative particle tail.
            if (structureLib != null && "1.4.47".equals(structureLib.getVersion())) {
                mixins.addAll(Arrays.asList(
                    "structurelib.StructureLibHintAccessor",
                    "structurelib.MixinStructureLibHintTick",
                    "structurelib.MixinStructureLibHintRender",
                    "structurelib.MixinStructureLibHintEffects"));
            }
        }
        return mixins;
    }
}
