package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.miaokatzecore.client.camera.hud.CameraHud;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Installed only from the client-only Minecraft Mixin; common mod loading never resolves this class. */
public final class CameraClientEvents {

    private static boolean installed;

    static void install() {
        if (installed) return;
        installed = true;
        MinecraftForge.EVENT_BUS.register(new CameraClientEvents());
    }

    @SubscribeEvent
    public void beforeOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        CameraFrame frame = WorldCameraHooks.frame();
        CameraSettings settings = WorldCameraHooks.settings();
        if (frame != null && settings != null
            && settings.hud()
                .crosshair() == CameraSettings.Hud.Crosshair.HIDDEN) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void afterOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) return;
        CameraFrame frame = WorldCameraHooks.frame();
        CameraSettings settings = WorldCameraHooks.settings();
        if (frame != null && settings != null) CameraHud.render(frame, settings, WorldCameraHooks.controls());
    }
}
