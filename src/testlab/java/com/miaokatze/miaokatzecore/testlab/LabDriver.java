package com.miaokatze.miaokatzecore.testlab;

import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.miaokatzecore.api.testlab.LabRegistry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Replacement-jar-only driver. Its event policy always rechecks an enabled exact-world owner. */
public final class LabDriver {

    private static final LabWorldPolicy POLICY = new LabWorldPolicy();
    private static boolean installed;

    private LabDriver() {}

    public static synchronized void install(FMLServerStartingEvent event) {
        if (LabRegistry.modules()
            .isEmpty()) return;
        event.registerServerCommand(new MiaoTestCommand());
        if (!installed) {
            MinecraftForge.EVENT_BUS.register(POLICY);
            FMLCommonHandler.instance()
                .bus()
                .register(POLICY);
            installed = true;
        }
    }
}
