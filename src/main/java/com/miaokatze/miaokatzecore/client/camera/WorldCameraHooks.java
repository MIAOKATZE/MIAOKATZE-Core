package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.camera.CameraProjection;
import com.miaokatze.miaokatzecore.api.camera.CameraRay;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;
import com.miaokatze.miaokatzecore.api.camera.ProjectedPoint;

/** The only world-camera hook implementation. All methods run on the Minecraft client thread. */
public final class WorldCameraHooks {

    private static volatile Frame frame;
    private static Entity savedEntity;
    private static Object savedWorld;
    private static double x, y, z, lastX, lastY, lastZ, prevX, prevY, prevZ;
    private static GraphicsState graphics;
    private static RenderOrigins renderOrigins;
    private static boolean cursorSaved, cursorWasGrabbed;

    static {
        WorldCameras.onCleanup(WorldCameraHooks::invalidate);
    }

    private WorldCameraHooks() {}

    /** Called by Core's client lifecycle hooks, including on disconnect and GUI opening. */
    public static void lifecycle(Minecraft mc) {
        WorldCameras.worldChanged(mc.theWorld);
        WorldCameras.Entry owner = WorldCameras.entry();
        if (owner == null) {
            restoreCursor();
            return;
        }
        if (mc.currentScreen != null) guiOpened();
        owner = usable(mc);
        if (owner != null && owner.source.options().freeCursor) {
            if (!cursorSaved) {
                cursorWasGrabbed = Mouse.isGrabbed();
                cursorSaved = true;
            }
            Mouse.setGrabbed(false);
        } else restoreCursor();
    }

    public static void guiOpened() {
        WorldCameras.Entry owner = WorldCameras.entry();
        if (owner == null) return;
        switch (owner.source.guiPolicy()) {
            case RELEASE:
                owner.close();
                break;
            case SUSPEND:
                invalidate();
                break;
            default:
                break;
        }
    }

    public static void beginWorldRender(Minecraft mc, float partialTicks) {
        // Recovery is supplemental; the Mixin's finally also restores in the throwing invocation itself.
        endWorldRender(mc);
        frame = null;
        lifecycle(mc);
        WorldCameras.Entry owner = usable(mc);
        if (owner == null || mc.renderViewEntity == null) return;
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        WorldCameras.View view = owner.source.sample(partialTicks, scaled.getScaledWidth(), scaled.getScaledHeight());
        if (view == null || !owner.active() || usable(mc) != owner) return;
        WorldCameras.Options options = owner.source.options();
        Entity entity = mc.renderViewEntity;
        double originX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double originY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double originZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        double realX = originX, realY = originY, realZ = originZ;
        if (view.anchorDriven) {
            renderOrigins = new RenderOrigins();
            savedEntity = entity;
            savedWorld = entity.worldObj;
            x = entity.posX;
            y = entity.posY;
            z = entity.posZ;
            lastX = entity.lastTickPosX;
            lastY = entity.lastTickPosY;
            lastZ = entity.lastTickPosZ;
            prevX = entity.prevPosX;
            prevY = entity.prevPosY;
            prevZ = entity.prevPosZ;
            originX = view.pose.anchorX();
            originY = view.pose.anchorY();
            originZ = view.pose.anchorZ();
            entity.posX = entity.lastTickPosX = originX;
            entity.posY = entity.lastTickPosY = originY;
            entity.posZ = entity.lastTickPosZ = originZ;
            entity.prevPosX = originX;
            entity.prevPosY = originY;
            entity.prevPosZ = originZ;
        }
        frame = new Frame(
            owner,
            view,
            options,
            mc.displayWidth,
            mc.displayHeight,
            originX,
            originY,
            originZ,
            realX,
            realY,
            realZ);
        graphics = new GraphicsState();
    }

    public static void applyProjection(Minecraft mc) {
        Frame f = currentFrame(mc);
        if (f == null) return;
        OrthographicProjection p = f.view.projection;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-p.halfWidth(), p.halfWidth(), -p.halfHeight(), p.halfHeight(), p.near(), p.far());
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslated(0, 0, -p.depthOffset());
        GL11.glRotated(f.view.pose.pitchDegrees(), 1, 0, 0);
        GL11.glRotated(f.view.pose.yawDegrees(), 0, 1, 0);
        // Chunk/entity vertices already subtract the render origin. Apply only the remaining anchor delta.
        GL11.glTranslated(
            f.originX - f.view.pose.anchorX(),
            f.originY - f.view.pose.anchorY(),
            f.originZ - f.view.pose.anchorZ());
        if (f.options.disableCull) GL11.glDisable(GL11.GL_CULL_FACE);
        f.applied = true;
    }

    public static void beforeWorldLast(Minecraft mc) {
        Entity original = savedEntity;
        restoreEntity();
        Frame f = currentFrame(mc);
        if (f == null || !f.applied
            || f.worldLast
            || !f.view.anchorDriven
            || original == null
            || original != mc.renderViewEntity
            || original.worldObj != mc.theWorld) return;
        // WorldLast follows vanilla's real-view-entity origin convention. Restore all four participants together:
        // view entity, modelview translation, entity render origin and tile-entity render origin.
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glTranslated(f.realX - f.originX, f.realY - f.originY, f.realZ - f.originZ);
        RenderManager.renderPosX = RenderManager.instance.viewerPosX = f.realX;
        RenderManager.renderPosY = RenderManager.instance.viewerPosY = f.realY;
        RenderManager.renderPosZ = RenderManager.instance.viewerPosZ = f.realZ;
        TileEntityRendererDispatcher.staticPlayerX = TileEntityRendererDispatcher.instance.field_147560_j = f.realX;
        TileEntityRendererDispatcher.staticPlayerY = TileEntityRendererDispatcher.instance.field_147561_k = f.realY;
        TileEntityRendererDispatcher.staticPlayerZ = TileEntityRendererDispatcher.instance.field_147558_l = f.realZ;
        renderOrigins = null;
        f.worldLast = true;
    }

    public static void abortWorldRender() {
        frame = null;
    }

    public static void endWorldRender(Minecraft mc) {
        try {
            restoreEntity();
        } finally {
            if (renderOrigins != null) {
                renderOrigins.restore();
                renderOrigins = null;
            }
            if (graphics != null) {
                GraphicsState old = graphics;
                graphics = null;
                old.close();
            }
        }
    }

    private static void restoreEntity() {
        Entity original = savedEntity;
        savedEntity = null;
        // Never write into a replacement renderViewEntity or an entity moved to another world.
        if (original != null && original.worldObj == savedWorld) {
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
        savedWorld = null;
    }

    private static void invalidate() {
        frame = null;
        try {
            endWorldRender(null);
        } finally {
            restoreCursor();
        }
    }

    private static void restoreCursor() {
        if (!cursorSaved) return;
        cursorSaved = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (cursorWasGrabbed && mc.currentScreen == null && mc.inGameHasFocus && mc.theWorld != null) {
            mc.mouseHelper.grabMouseCursor();
        }
    }

    public static boolean orthographicActive() {
        return currentFrame(Minecraft.getMinecraft()) != null;
    }

    public static boolean skipCullFaceEnable() {
        Frame f = currentFrame(Minecraft.getMinecraft());
        return f != null && f.options.disableCull;
    }

    public static boolean disableVanillaFrustum() {
        Frame f = currentFrame(Minecraft.getMinecraft());
        return f != null && f.options.disableVanillaFrustum;
    }

    public static boolean hideHand() {
        return options(Minecraft.getMinecraft()).hideHand;
    }

    public static boolean consumeHotbarScroll() {
        return options(Minecraft.getMinecraft()).consumeHotbarScroll;
    }

    public static boolean blockDebugReloads() {
        return options(Minecraft.getMinecraft()).blockDebugReloads;
    }

    public static boolean freezeMouseLook(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return entity == mc.thePlayer && options(mc).freeCursor;
    }

    public static boolean setupFog() {
        Frame f = currentFrame(Minecraft.getMinecraft());
        if (f == null || !f.options.disableFog) return false;
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, 1.0E8F);
        GL11.glFogf(GL11.GL_FOG_END, 2.0E8F);
        GL11.glDisable(GL11.GL_FOG);
        return true;
    }

    public static void filterMovement(MovementInput input) {
        WorldCameras.MovementPolicy policy = options(Minecraft.getMinecraft()).movement;
        if (policy == WorldCameras.MovementPolicy.NONE) return;
        input.moveForward = input.moveStrafe = 0;
        if (policy == WorldCameras.MovementPolicy.ALL) input.jump = input.sneak = false;
    }

    public static boolean allowKey(int keyCode) {
        if (keyCode == 0) return true;
        WorldCameras.Entry owner = usable(Minecraft.getMinecraft());
        return owner == null || owner.source.allowKey(keyCode);
    }

    /** Display pixels, LWJGL convention: origin at bottom left. No frame means null, never an eye ray. */
    public static CameraRay mouseRay(String ownerId, Object worldToken, double displayX, double displayY) {
        Frame f = matchingFrame(ownerId, worldToken);
        return f == null ? null
            : CameraProjection
                .unprojectRay(f.view.pose, f.view.projection, 2 * displayX / f.width - 1, 2 * displayY / f.height - 1);
    }

    /** NDC output: Y upward; GUI Y = (1 - ndcY) * scaledHeight / 2. */
    public static ProjectedPoint projectWorld(String ownerId, Object worldToken, double worldX, double worldY,
        double worldZ) {
        Frame f = matchingFrame(ownerId, worldToken);
        return f == null ? null : CameraProjection.project(f.view.pose, f.view.projection, worldX, worldY, worldZ);
    }

    private static Frame matchingFrame(String ownerId, Object worldToken) {
        Frame f = currentFrame(Minecraft.getMinecraft());
        return f != null && f.applied && f.owner.world == worldToken && f.owner.owner.equals(ownerId) ? f : null;
    }

    private static Frame currentFrame(Minecraft mc) {
        Frame f = frame;
        return f != null && f.owner.active()
            && f.owner.world == mc.theWorld
            && (mc.currentScreen == null || f.owner.source.guiPolicy() == WorldCameras.GuiPolicy.KEEP)
            && f.width == mc.displayWidth
            && f.height == mc.displayHeight ? f : null;
    }

    private static WorldCameras.Entry usable(Minecraft mc) {
        WorldCameras.Entry owner = WorldCameras.entry();
        if (owner == null || mc.theWorld == null || owner.world != mc.theWorld) return null;
        if (mc.currentScreen != null && owner.source.guiPolicy() != WorldCameras.GuiPolicy.KEEP) return null;
        return owner;
    }

    private static WorldCameras.Options options(Minecraft mc) {
        WorldCameras.Entry owner = usable(mc);
        return owner == null ? WorldCameras.Options.PASSIVE : owner.source.options();
    }

    private static final class Frame {

        final WorldCameras.Entry owner;
        final WorldCameras.View view;
        final WorldCameras.Options options;
        final int width, height;
        final double originX, originY, originZ;
        final double realX, realY, realZ;
        boolean applied, worldLast;

        Frame(WorldCameras.Entry owner, WorldCameras.View view, WorldCameras.Options options, int width, int height,
            double originX, double originY, double originZ, double realX, double realY, double realZ) {
            this.owner = owner;
            this.view = view;
            this.options = options;
            this.width = width;
            this.height = height;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.realX = realX;
            this.realY = realY;
            this.realZ = realZ;
        }
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

    /** Explicit restores pass through the same GL calls as writes, including Angelica's state-manager redirects. */
    private static final class GraphicsState implements AutoCloseable {

        final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), fog = GL11.glIsEnabled(GL11.GL_FOG);
        final int fogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
        final float fogStart = GL11.glGetFloat(GL11.GL_FOG_START), fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);

        GraphicsState() {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glMatrixMode(matrixMode);
        }

        @Override
        public void close() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glFogi(GL11.GL_FOG_MODE, fogMode);
            GL11.glFogf(GL11.GL_FOG_START, fogStart);
            GL11.glFogf(GL11.GL_FOG_END, fogEnd);
            enable(GL11.GL_CULL_FACE, cull);
            enable(GL11.GL_FOG, fog);
        }

        private static void enable(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability);
            else GL11.glDisable(capability);
        }
    }
}
