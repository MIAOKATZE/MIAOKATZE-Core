package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraRay;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;
import com.miaokatze.miaokatzecore.api.camera.ProjectedPoint;
import com.miaokatze.miaokatzecore.api.camera.RayInterval;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceBackend;

/** Client facade; ownership, frame values, reversible rendering and pointer capture have separate lifetimes. */
public final class WorldCameraHooks {

    private static final CameraSession SESSION = new CameraSession();
    private static final CameraFrameStore FRAMES = new CameraFrameStore();
    private static final CameraInputState INPUT = new CameraInputState();
    private static CameraRenderScope renderScope;

    static {
        WorldCameras.onCleanup(WorldCameraHooks::invalidate);
    }

    private WorldCameraHooks() {}

    /** Tick policy boundary, before vanilla/Forge receives any mouse event. */
    public static void lifecycle(Minecraft mc) {
        CameraClientEvents.install();
        SliceBackend.tick(mc);
        SESSION.sample(mc);
        validate(mc);
        INPUT.maintain(mc, SESSION, displayed(mc));
    }

    public static void endTick() {
        INPUT.endTick();
    }

    public static void guiOpened() {
        Minecraft mc = Minecraft.getMinecraft();
        SESSION.sample(mc);
        // KEEP preserves the picture. A real GUI always revokes world-pointer ownership.
        INPUT.cancel();
        validate(mc);
    }

    private static void validate(Minecraft mc) {
        CameraFrame old = FRAMES.displayed();
        if (!SESSION.visible(mc) || !mc.inGameHasFocus || old != null && !SESSION.matches(old, mc)) {
            FRAMES.clear();
            INPUT.cancel();
            SliceBackend.release();
        }
    }

    public static void beginWorldRender(Minecraft mc, float partialTicks) {
        endWorldRender(mc);
        SESSION.sample(mc);
        validate(mc);
        if (!SESSION.visible(mc) || !mc.inGameHasFocus || mc.renderViewEntity == null) return;
        WorldCameras.Entry owner = SESSION.owner();
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        WorldCameras.View view = owner.source.sample(partialTicks, scaled.getScaledWidth(), scaled.getScaledHeight());
        if (view == null || !owner.active() || !SESSION.visible(mc) || SESSION.owner() != owner) {
            FRAMES.clear();
            INPUT.cancel();
            SliceBackend.release();
            return;
        }
        CameraSettings settings = SESSION.settings();
        SliceBackend.Snapshot slice = SliceBackend.freeze(mc, owner.epoch, settings.slice());
        long revision = FRAMES.transformRevision(
            view,
            mc.displayWidth,
            mc.displayHeight,
            scaled.getScaledWidth(),
            scaled.getScaledHeight());
        CameraBasis basis = FRAMES.basis(view, revision);
        CameraFrame snapshot = new CameraFrame(
            owner.owner,
            owner.epoch,
            view.pose,
            view.projection,
            basis,
            mc.displayWidth,
            mc.displayHeight,
            scaled.getScaledWidth(),
            scaled.getScaledHeight(),
            revision,
            slice.geometryRevision,
            slice.requestedRevision,
            slice.displayedRevision,
            slice.committedRevision,
            slice.requested,
            slice.displayed,
            slice.committed,
            slice.state,
            slice.pickReady,
            slice.supported,
            slice.unavailableReason);
        // Source callbacks have completed; only now may this invocation mutate the real view entity.
        if (!owner.active() || !SESSION.visible(mc) || SESSION.owner() != owner) return;
        Entity entity = mc.renderViewEntity;
        double realX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double realY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double realZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        double originX = view.anchorDriven ? view.pose.anchorX() : realX;
        double originY = view.anchorDriven ? view.pose.anchorY() : realY;
        double originZ = view.anchorDriven ? view.pose.anchorZ() : realZ;
        FRAMES.begin(snapshot, view, settings, originX, originY, originZ, realX, realY, realZ);
        renderScope = new CameraRenderScope(
            settings.render()
                .disableCull(),
            settings.render()
                .disableFog());
        if (view.anchorDriven) renderScope.anchor(entity, originX, originY, originZ);
        SliceBackend.beginWorldFrame(snapshot);
    }

    public static void applyProjection(Minecraft mc) {
        CameraFrameStore.Candidate f = candidate(mc);
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
        GL11.glTranslated(
            f.originX - f.view.pose.anchorX(),
            f.originY - f.view.pose.anchorY(),
            f.originZ - f.view.pose.anchorZ());
        if (f.settings.render()
            .disableCull()) GL11.glDisable(GL11.GL_CULL_FACE);
        f.applied = true;
    }

    public static void beforeWorldLast(Minecraft mc) {
        CameraFrameStore.Candidate f = candidate(mc);
        boolean original = renderScope != null && renderScope.restoreEntity(mc.renderViewEntity);
        if (f == null || !f.applied || f.worldLast || !f.view.anchorDriven || !original) return;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glTranslated(f.realX - f.originX, f.realY - f.originY, f.realZ - f.originZ);
        renderScope.worldLastOrigin(f.realX, f.realY, f.realZ);
        f.worldLast = true;
    }

    /** Promote only after original rendering and all restoration have succeeded. */
    public static void completeWorldRender(Minecraft mc) {
        if (candidate(mc) == null) FRAMES.abort();
        else FRAMES.complete();
    }

    public static void abortWorldRender() {
        FRAMES.abort();
    }

    public static void endWorldRender(Minecraft mc) {
        try {
            SliceBackend.endWorldFrame();
        } finally {
            CameraRenderScope old = renderScope;
            renderScope = null;
            if (old != null) old.close();
        }
    }

    private static void invalidate() {
        FRAMES.clear();
        SESSION.clear();
        INPUT.cancel();
        try {
            endWorldRender(null);
        } finally {
            try {
                SliceBackend.release();
            } finally {
                INPUT.restoreCursor(Minecraft.getMinecraft());
            }
        }
    }

    private static CameraFrameStore.Candidate candidate(Minecraft mc) {
        CameraFrameStore.Candidate f = FRAMES.candidate;
        return f != null && mc.inGameHasFocus && SESSION.matches(f.frame, mc) ? f : null;
    }

    private static CameraFrame displayed(Minecraft mc) {
        CameraFrame f = FRAMES.displayed();
        return f != null && mc.inGameHasFocus && SESSION.matches(f, mc) ? f : null;
    }

    /** Last successfully displayed values; retaining the value grants no input authority. */
    public static CameraFrame frame() {
        return displayed(Minecraft.getMinecraft());
    }

    public static CameraFrame frame(String ownerId, Object worldToken) {
        Minecraft mc = Minecraft.getMinecraft();
        return SESSION.matches(ownerId, worldToken, mc) ? displayed(mc) : null;
    }

    public static CameraSettings settings() {
        return displayed(Minecraft.getMinecraft()) != null ? FRAMES.displayedSettings() : null;
    }

    public static CameraControls controls() {
        return SESSION.valid(Minecraft.getMinecraft()) ? SESSION.controls() : CameraControls.NONE;
    }

    public static boolean pointerCaptured() {
        return INPUT.captured();
    }

    /** Called before ForgeHooksClient.postMouseEvent, independent of event-subscriber order. */
    public static boolean rawMouseEvent(Minecraft mc) {
        return INPUT.event(mc, SESSION, displayed(mc), FRAMES.displayedSettings());
    }

    public static boolean orthographicActive() {
        return candidate(Minecraft.getMinecraft()) != null;
    }

    public static boolean skipCullFaceEnable() {
        CameraFrameStore.Candidate f = candidate(Minecraft.getMinecraft());
        return f != null && f.settings.render()
            .disableCull();
    }

    public static boolean disableVanillaFrustum() {
        CameraFrameStore.Candidate f = candidate(Minecraft.getMinecraft());
        return f != null && f.settings.render()
            .disableVanillaFrustum();
    }

    public static boolean hideHand() {
        Minecraft mc = Minecraft.getMinecraft();
        CameraFrameStore.Candidate f = candidate(mc);
        return f != null ? f.settings.render()
            .hideHand()
            : SESSION.visible(mc) && SESSION.settings()
                .render()
                .hideHand();
    }

    private static CameraSettings inputSettings(Minecraft mc) {
        return SESSION.interactive(mc) ? SESSION.settings() : null;
    }

    public static boolean consumeHotbarScroll() {
        CameraSettings s = inputSettings(Minecraft.getMinecraft());
        return s != null && s.input()
            .consumeHotbarScroll();
    }

    public static boolean blockDebugReloads() {
        CameraSettings s = inputSettings(Minecraft.getMinecraft());
        return s != null && s.input()
            .blockDebugReloads();
    }

    public static boolean freezeMouseLook(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        CameraSettings s = inputSettings(mc);
        return entity == mc.thePlayer && s != null
            && s.input()
                .freeCursor();
    }

    public static boolean setupFog() {
        CameraFrameStore.Candidate f = candidate(Minecraft.getMinecraft());
        if (f == null || !f.settings.render()
            .disableFog()) return false;
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, 1.0E8F);
        GL11.glFogf(GL11.GL_FOG_END, 2.0E8F);
        GL11.glDisable(GL11.GL_FOG);
        return true;
    }

    public static void filterMovement(MovementInput input) {
        CameraSettings s = inputSettings(Minecraft.getMinecraft());
        if (s == null || s.input()
            .movement() == WorldCameras.MovementPolicy.NONE) return;
        input.moveForward = input.moveStrafe = 0;
        if (s.input()
            .movement() == WorldCameras.MovementPolicy.ALL) input.jump = input.sneak = false;
    }

    public static boolean allowKey(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        return keyCode == 0 || !SESSION.interactive(mc) || SESSION.owner().source.allowKey(keyCode);
    }

    /** Raw queue filtering also suppresses FML KeyInputEvent and vanilla hard-coded shortcuts. */
    public static boolean allowRawKey(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        CameraSettings settings = inputSettings(mc);
        return settings == null || !settings.input()
            .exclusiveKeyboard() || SESSION.owner().source.allowRawKey(keyCode);
    }

    private static CameraFrame pickingFrame(String ownerId, Object worldToken) {
        Minecraft mc = Minecraft.getMinecraft();
        CameraFrame f = frame(ownerId, worldToken);
        return f != null && SESSION.interactive(mc)
            && !INPUT.captured()
            && f.pickReady()
            && f.requestedSlice()
                .equals(
                    SESSION.settings()
                        .slice())
            && SliceBackend.canPick(f)
            && (f.sliceState() == SliceState.READY || f.sliceState() == SliceState.DISABLED) ? f : null;
    }

    /** Original normalized near-plane ray; the slice never changes its origin. */
    public static CameraRay mouseRay(String ownerId, Object worldToken, double displayX, double displayY) {
        CameraFrame f = pickingFrame(ownerId, worldToken);
        return f == null ? null : f.ray(displayX, displayY);
    }

    /** Continuous bottom-left display coordinates, preserving the original near-plane ray and slice interval. */
    public static RayInterval rayInterval(String ownerId, Object worldToken, double displayX, double displayY) {
        CameraFrame f = pickingFrame(ownerId, worldToken);
        return f == null ? null
            : new RayInterval(
                f.ray(displayX, displayY),
                0,
                f.projection()
                    .far()
                    - f.projection()
                        .near()).clip(f.displayedSlice());
    }

    /** Integer framebuffer pixels sample their fragment center, matching GLSL gl_FragCoord. */
    public static RayInterval pixelRayInterval(String ownerId, Object worldToken, int displayX, int displayY) {
        return rayInterval(ownerId, worldToken, displayX + .5, displayY + .5);
    }

    /** NDC output: Y upward. WorldLast uses that invocation's frozen transform. */
    public static ProjectedPoint projectWorld(String ownerId, Object worldToken, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!SESSION.matches(ownerId, worldToken, mc)) return null;
        CameraFrameStore.Candidate c = candidate(mc);
        CameraFrame f = c != null && c.applied ? c.frame : displayed(mc);
        return f == null ? null : f.project(x, y, z);
    }
}
