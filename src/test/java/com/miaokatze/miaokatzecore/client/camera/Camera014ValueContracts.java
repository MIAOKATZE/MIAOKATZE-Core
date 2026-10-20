package com.miaokatze.miaokatzecore.client.camera;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.CameraRay;
import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Pure JVM contracts for immutable settings, legacy defaults, controls and published value snapshots. */
public final class Camera014ValueContracts {

    private Camera014ValueContracts() {}

    public static void main(String[] args) {
        settings();
        frame();
        System.out.println("Camera014ValueContracts: PASS (settings bridge, builders, controls, frame values)");
    }

    private static void settings() {
        WorldCameras.Options old = new WorldCameras.Options(
            true,
            false,
            true,
            false,
            true,
            false,
            WorldCameras.MovementPolicy.HORIZONTAL,
            true);
        CameraSettings legacy = CameraSettings.legacy(old, WorldCameras.GuiPolicy.RELEASE);
        check(
            legacy.render()
                .hideHand()
                && !legacy.render()
                    .disableFog()
                && legacy.render()
                    .disableCull()
                && !legacy.render()
                    .disableVanillaFrustum(),
            "legacy render bridge");
        check(
            legacy.input()
                .consumeHotbarScroll()
                && !legacy.input()
                    .blockDebugReloads()
                && legacy.input()
                    .movement() == WorldCameras.MovementPolicy.HORIZONTAL
                && legacy.input()
                    .freeCursor(),
            "legacy input bridge");
        check(legacy.guiPolicy() == WorldCameras.GuiPolicy.RELEASE, "legacy GUI bridge");
        check(
            legacy.hud()
                .crosshair() == CameraSettings.Hud.Crosshair.KEEP
                && !legacy.hud()
                    .panelVisible()
                && !legacy.navigation()
                    .pan()
                && !legacy.navigation()
                    .zoom()
                && !legacy.slice()
                    .enabled(),
            "legacy sources retain passive HUD and navigation");
        CameraSettings.Hud.Builder hudBuilder = CameraSettings.Hud.builder()
            .panelVisible(true)
            .crosshair(CameraSettings.Hud.Crosshair.HIDDEN)
            .panelPosition(4, 8)
            .panelWidth(190)
            .accentArgb(0xFFABCDEF);
        CameraSettings.Hud hud = hudBuilder.build();
        hudBuilder.panelPosition(40, 80)
            .panelVisible(false);
        check(
            hud.panelX() == 4 && hud.panelY() == 8 && hud.panelVisible() && hud.accentArgb() == 0xFFABCDEF,
            "built HUD snapshot independent of builder");
        HeightSlice slice = HeightSlice.below(70, .25);
        CameraSettings.Builder settingsBuilder = CameraSettings.builder()
            .hud(hud)
            .slice(slice)
            .navigation(
                CameraSettings.Navigation.builder()
                    .pan(true)
                    .yaw(true)
                    .pitch(true)
                    .zoom(true)
                    .reset(true)
                    .pitchRange(5, 85)
                    .scaleRange(3, 64)
                    .basePixelsPerWorldUnit(12)
                    .sliceRange(-64, 320)
                    .build());
        CameraSettings settings = settingsBuilder.build();
        settingsBuilder.slice(HeightSlice.disabled());
        check(
            settings.slice() == slice && settings.hud() == hud
                && settings.navigation()
                    .minScale() == 3
                && settings.navigation()
                    .sliceMinimumY() == -64
                && settings.navigation()
                    .basePixelsPerWorldUnit() == 12,
            "immutable named settings snapshot");
        for (CameraControl control : CameraControl.values())
            check(!CameraControls.NONE.supports(control), "NONE passive");
        CameraControls.NONE.panWorld(1, 2, 3);
        CameraControls.NONE.rotate(90, 5);
        CameraControls.NONE.zoom(1.1);
        CameraControls.NONE.resetView();
        CameraControls.NONE.requestSlice(slice);
        expect(
            () -> CameraSettings.Navigation.builder()
                .scaleRange(3, 3));
        expect(
            () -> CameraSettings.Navigation.builder()
                .pitchRange(10, 5));
        expect(
            () -> CameraSettings.Navigation.builder()
                .basePixelsPerWorldUnit(Double.NaN));
        expect(
            () -> CameraSettings.Navigation.builder()
                .sliceRange(10, 10));
        expect(
            () -> CameraSettings.Hud.builder()
                .panelWidth(0));
    }

    private static void frame() {
        CameraPose pose = new CameraPose(10, 20, 30, 0, 0);
        OrthographicProjection projection = new OrthographicProjection(20, 10, 100, .05, 500);
        CameraBasis basis = new CameraBasis(pose);
        HeightSlice requested = HeightSlice.below(70, .25), committed = HeightSlice.below(71, .25);
        CameraFrame frame = new CameraFrame(
            "test:owner",
            4,
            pose,
            projection,
            basis,
            800,
            400,
            400,
            200,
            10,
            11,
            12,
            13,
            14,
            requested,
            requested,
            committed,
            SliceState.APPLYING,
            false,
            true,
            "");
        check(
            frame.ownerEpoch() == 4 && frame.transformRevision() == 10
                && frame.geometryRevision() == 11
                && frame.requestedRevision() == 12
                && frame.displayedRevision() == 13
                && frame.committedRevision() == 14
                && frame.sliceRevision() == 13,
            "frame revisions remain separate");
        check(
            frame.basis() == basis && frame.requestedSlice() == requested
                && frame.committedSlice() == committed
                && frame.sliceState() == SliceState.APPLYING
                && !frame.pickReady(),
            "read-only staged slice snapshot");
        CameraRay ray = frame.ray(400, 200);
        check(
            Math.abs(ray.originZ() - 129.95) < 1e-9 && ray.originX() == 10 && ray.directionZ() == -1,
            "frame ray remains a geometric near-plane ray even when pick publication is gated");
        for (Field field : CameraFrame.class.getDeclaredFields()) {
            check(Modifier.isFinal(field.getModifiers()), "published frame fields final");
            check(
                field.getType() != Object.class && !WorldCameras.Source.class.isAssignableFrom(field.getType()),
                "frame contains no world token or Source");
        }
        expect(
            () -> new CameraFrame(
                "test:owner",
                1,
                pose,
                projection,
                new CameraBasis(new CameraPose(11, 20, 30, 0, 0)),
                800,
                400,
                400,
                200,
                1,
                0,
                0,
                0,
                0,
                requested,
                requested,
                committed,
                SliceState.READY,
                true,
                true,
                ""));
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
