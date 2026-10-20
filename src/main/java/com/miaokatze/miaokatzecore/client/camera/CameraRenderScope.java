package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

/** One render invocation's reversible writes. No ownership or policy sampling lives here. */
final class CameraRenderScope implements AutoCloseable {

    private final int matrixMode;
    private boolean cull, fog;
    private boolean cullCaptured, fogCaptured;
    private int fogMode;
    private float fogStart, fogEnd;
    private boolean projectionPushed, modelviewPushed, closed;
    private Entity entity;
    private Object entityWorld;
    private double x, y, z, lastX, lastY, lastZ, prevX, prevY, prevZ;
    private RenderOrigins origins;

    CameraRenderScope(boolean changeCull, boolean changeFog) {
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try {
            if (changeCull) {
                cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                cullCaptured = true;
            }
            if (changeFog) {
                fog = GL11.glIsEnabled(GL11.GL_FOG);
                fogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
                fogStart = GL11.glGetFloat(GL11.GL_FOG_START);
                fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
                fogCaptured = true;
            }
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelviewPushed = true;
            GL11.glMatrixMode(matrixMode);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    void anchor(Entity original, double anchorX, double anchorY, double anchorZ) {
        origins = new RenderOrigins();
        entity = original;
        entityWorld = original.worldObj;
        x = original.posX;
        y = original.posY;
        z = original.posZ;
        lastX = original.lastTickPosX;
        lastY = original.lastTickPosY;
        lastZ = original.lastTickPosZ;
        prevX = original.prevPosX;
        prevY = original.prevPosY;
        prevZ = original.prevPosZ;
        original.posX = original.lastTickPosX = original.prevPosX = anchorX;
        original.posY = original.lastTickPosY = original.prevPosY = anchorY;
        original.posZ = original.lastTickPosZ = original.prevPosZ = anchorZ;
    }

    boolean restoreEntity(Entity expected) {
        Entity original = entity;
        entity = null;
        if (original == null) return false;
        boolean same = original == expected && original.worldObj == entityWorld;
        if (original.worldObj == entityWorld) {
            original.posX = x;
            original.posY = y;
            original.posZ = z;
            original.lastTickPosX = lastX;
            original.lastTickPosY = lastY;
            original.lastTickPosZ = lastZ;
            original.prevPosX = prevX;
            original.prevPosY = prevY;
            original.prevPosZ = prevZ;
        }
        entityWorld = null;
        return same;
    }

    void worldLastOrigin(double x, double y, double z) {
        RenderManager.renderPosX = RenderManager.instance.viewerPosX = x;
        RenderManager.renderPosY = RenderManager.instance.viewerPosY = y;
        RenderManager.renderPosZ = RenderManager.instance.viewerPosZ = z;
        TileEntityRendererDispatcher.staticPlayerX = TileEntityRendererDispatcher.instance.field_147560_j = x;
        TileEntityRendererDispatcher.staticPlayerY = TileEntityRendererDispatcher.instance.field_147561_k = y;
        TileEntityRendererDispatcher.staticPlayerZ = TileEntityRendererDispatcher.instance.field_147558_l = z;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            restoreEntity(null);
        } finally {
            try {
                if (origins != null) origins.restore();
            } finally {
                origins = null;
                try {
                    if (modelviewPushed) {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPopMatrix();
                    }
                } finally {
                    try {
                        if (projectionPushed) {
                            GL11.glMatrixMode(GL11.GL_PROJECTION);
                            GL11.glPopMatrix();
                        }
                    } finally {
                        GL11.glMatrixMode(matrixMode);
                        if (fogCaptured) {
                            GL11.glFogi(GL11.GL_FOG_MODE, fogMode);
                            GL11.glFogf(GL11.GL_FOG_START, fogStart);
                            GL11.glFogf(GL11.GL_FOG_END, fogEnd);
                            enable(GL11.GL_FOG, fog);
                        }
                        if (cullCaptured) enable(GL11.GL_CULL_FACE, cull);
                    }
                }
            }
        }
    }

    private static void enable(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    private static final class RenderOrigins {

        final double rx = RenderManager.renderPosX, ry = RenderManager.renderPosY, rz = RenderManager.renderPosZ;
        final double vx = RenderManager.instance.viewerPosX, vy = RenderManager.instance.viewerPosY,
            vz = RenderManager.instance.viewerPosZ;
        final double tx = TileEntityRendererDispatcher.staticPlayerX, ty = TileEntityRendererDispatcher.staticPlayerY,
            tz = TileEntityRendererDispatcher.staticPlayerZ;
        final double cx = TileEntityRendererDispatcher.instance.field_147560_j,
            cy = TileEntityRendererDispatcher.instance.field_147561_k,
            cz = TileEntityRendererDispatcher.instance.field_147558_l;

        void restore() {
            RenderManager.renderPosX = rx;
            RenderManager.renderPosY = ry;
            RenderManager.renderPosZ = rz;
            RenderManager.instance.viewerPosX = vx;
            RenderManager.instance.viewerPosY = vy;
            RenderManager.instance.viewerPosZ = vz;
            TileEntityRendererDispatcher.staticPlayerX = tx;
            TileEntityRendererDispatcher.staticPlayerY = ty;
            TileEntityRendererDispatcher.staticPlayerZ = tz;
            TileEntityRendererDispatcher.instance.field_147560_j = cx;
            TileEntityRendererDispatcher.instance.field_147561_k = cy;
            TileEntityRendererDispatcher.instance.field_147558_l = cz;
        }
    }
}
