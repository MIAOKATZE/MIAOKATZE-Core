package com.miaokatze.miaokatzecore;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Common entry point deliberately does not reference client or optional adapter classes. */
@Mod(
    modid = MiaokatzeCore.MODID,
    name = "MIAOKATZE Core",
    version = Tags.RELEASE_VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-before:gregtech;required-after:modularui2;required-after:gtnhlib;after:etfuturum")
public final class MiaokatzeCore {

    public static final String MODID = "miaokatzecore";

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
