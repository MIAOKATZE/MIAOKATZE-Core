package com.miaokatze.miaokatzecore;

import cpw.mods.fml.common.Mod;

/** Common entry point deliberately does not reference client or optional adapter classes. */
@Mod(
    modid = MiaokatzeCore.MODID,
    name = "MIAOKATZE Core",
    version = Tags.RELEASE_VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-before:gregtech@[5.09.54.205];required-after:modularui2@[2.3.92-1.7.10];required-after:gtnhlib@[0.11.52]")
public final class MiaokatzeCore {

    public static final String MODID = "miaokatzecore";
}
