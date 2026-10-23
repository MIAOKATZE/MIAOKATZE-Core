package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;

/** Runs after anchor restoration. No player coordinates, camera identity or F5 setting are written. */
final class CameraPlayerBody {

    private CameraPlayerBody() {}

    static void render(Minecraft mc, float partialTicks) {
        Entity player = mc.thePlayer;
        if (player == null || mc.theWorld == null || player.worldObj != mc.theWorld) return;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        // Reuse Core's full native-renderer scope, including matrix depths, textures and lightmap cache.
        // Angelica redirects these attribute calls through its cache-aware state stack.
        PreviewRenderScope.RenderState state = new PreviewRenderScope.RenderState();
        try {
            state.push();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            RenderHelper.enableStandardItemLighting();
            mc.entityRenderer.enableLightmap(partialTicks);
            int brightness = player.getBrightnessForRender(partialTicks);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightness % 65536, brightness / 65536);
            GL11.glColor4f(1, 1, 1, 1);
            RenderManager.instance.renderEntityWithPosYaw(
                player,
                x - RenderManager.renderPosX,
                y - RenderManager.renderPosY,
                z - RenderManager.renderPosZ,
                yaw,
                partialTicks);
        } finally {
            state.restore();
        }
    }
}
