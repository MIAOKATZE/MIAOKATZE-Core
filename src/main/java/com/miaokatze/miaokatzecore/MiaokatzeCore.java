package com.miaokatze.miaokatzecore;

import cpw.mods.fml.common.Mod;

/** Common entry point deliberately does not reference client or optional adapter classes. */
@Mod(
    modid = MiaokatzeCore.MODID,
    name = "MIAOKATZE Core",
    version = Tags.RELEASE_VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public final class MiaokatzeCore {

    public static final String MODID = "miaokatzecore";
}
