package com.miaokatze.miaokatzecore.client.camera;

import java.util.Objects;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.Display;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Sole bridge from the ownership token to Minecraft. Frame values never retain this session. */
final class CameraSession {

    private WorldCameras.Entry owner;
    private CameraSettings settings;
    private CameraControls controls = CameraControls.NONE;

    void sample(Minecraft mc) {
        WorldCameras.worldChanged(mc.theWorld);
        WorldCameras.Entry next = WorldCameras.entry();
        if (next == null || mc.theWorld == null || next.world != mc.theWorld) {
            clear();
            return;
        }
        CameraSettings sampled = Objects.requireNonNull(next.source.settings(), "Source.settings()");
        CameraControls sampledControls = Objects.requireNonNull(next.source.controls(), "Source.controls()");
        // A callback may close/preempt the lease or replace the world. Never install a stale sample.
        if (!next.active() || next.world != mc.theWorld) {
            clear();
            return;
        }
        owner = next;
        settings = sampled;
        controls = controlsFor(mc, next, sampledControls);
        if (mc.currentScreen != null && sampled.guiPolicy() == WorldCameras.GuiPolicy.RELEASE) next.close();
    }

    void clear() {
        owner = null;
        settings = null;
        controls = CameraControls.NONE;
    }

    private CameraControls controlsFor(Minecraft mc, WorldCameras.Entry sampledOwner, CameraControls delegate) {
        if (delegate == CameraControls.NONE) return delegate;
        return new CameraControls() {

            @Override
            public boolean supports(CameraControl control) {
                return delegate.supports(control);
            }

            @Override
            public void panWorld(double x, double y, double z) {
                delegate.panWorld(x, y, z);
            }

            @Override
            public void rotate(double yaw, double pitch) {
                delegate.rotate(yaw, pitch);
            }

            @Override
            public void zoom(double factor) {
                delegate.zoom(factor);
            }

            @Override
            public void resetView() {
                delegate.resetView();
            }

            @Override
            public void requestSlice(HeightSlice slice) {
                if (!sampledOwner.active() || owner != sampledOwner) return;
                delegate.requestSlice(slice);
                // A HUD request is an explicit policy boundary. Later events in this tick must see the new request.
                sample(mc);
            }
        };
    }

    boolean valid(Minecraft mc) {
        return owner != null && owner.active() && owner.world == mc.theWorld && mc.theWorld != null;
    }

    boolean visible(Minecraft mc) {
        return CameraActivity.visible(
            valid(mc),
            mc.currentScreen != null,
            mc.inGameHasFocus,
            windowActive(),
            settings == null ? null : settings.guiPolicy());
    }

    boolean interactive(Minecraft mc) {
        return CameraActivity.interactive(valid(mc), mc.currentScreen != null, mc.inGameHasFocus, windowActive());
    }

    static boolean windowActive() {
        return Display.isCreated() && Display.isActive();
    }

    WorldCameras.Entry owner() {
        return owner;
    }

    CameraSettings settings() {
        return settings;
    }

    CameraControls controls() {
        return controls;
    }

    boolean matches(CameraFrame frame, Minecraft mc) {
        return visible(mc) && frame.ownerEpoch() == owner.epoch
            && frame.ownerId()
                .equals(owner.owner)
            && frame.displayWidth() == mc.displayWidth
            && frame.displayHeight() == mc.displayHeight;
    }

    boolean matches(String ownerId, Object world, Minecraft mc) {
        return valid(mc) && owner.world == world && owner.owner.equals(ownerId);
    }
}
