package com.miaokatze.miaokatzecore;

import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistrationLifecycle;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Common entry point deliberately does not reference client or optional adapter classes.
 * The existing after:etfuturum / required-before:gregtech ordering addresses GT5U 5.09.54.205's preInit
 * read of EFR's blast furnace on the GTNH 2.9 RC baseline; it does not establish a fault's root cause.
 * This is independent of recipe scheduling. On GTNH/GT5U/EFR upgrades, review the call site and dependency
 * graph again. An equivalent upstream fix only makes this constraint redundant and reviewable for removal;
 * do not infer it from version numbers or silently ignore a dependency conflict.
 */
@Mod(
    modid = MiaokatzeCore.MODID,
    name = "MIAOKATZE Core",
    version = Tags.RELEASE_VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-before:gregtech;required-after:modularui2;required-after:gtnhlib;after:etfuturum")
public final class MiaokatzeCore {

    public static final String MODID = "miaokatzecore";

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        RecipeRegistrationLifecycle.install(event.getSide());
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!event.getSide()
            .isClient() || !Loader.isModLoaded("NotEnoughItems")) return;
        try {
            // String lookup prevents optional NEI/client types from entering the common class's signatures.
            Class.forName("com.miaokatze.miaokatzecore.client.items.NEIItemOrdering")
                .getMethod("install")
                .invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot install optional NEI item ordering", exception);
        }
    }
}
