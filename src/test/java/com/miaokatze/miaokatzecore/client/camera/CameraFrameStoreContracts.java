package com.miaokatze.miaokatzecore.client.camera;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Display publication and transform-cache contracts, without Minecraft or OpenGL. */
public final class CameraFrameStoreContracts {

    public static void main(String[] args) {
        CameraFrameStore store = new CameraFrameStore();
        WorldCameras.View view = view(1);
        long revision = store.transformRevision(view, 1280, 720, 640, 360);
        CameraBasis basis = store.basis(view, revision);
        CameraSettings settings = CameraSettings.builder()
            .build();
        CameraFrame first = frame(view, basis, revision);
        store.begin(first, view, settings, 0, 0, 0, 0, 0, 0);
        require(store.displayed() == null, "unapplied candidate is private");
        store.complete();
        require(store.displayed() == null, "unapplied invocation cannot publish");
        store.begin(first, view, settings, 0, 0, 0, 0, 0, 0);
        store.candidate.applied = true;
        require(store.displayed() == null, "applied invocation is still private before success");
        store.complete();
        require(store.displayed() == first && store.displayedSettings() == settings, "successful frozen snapshot");
        WorldCameras.View equal = view(1);
        require(
            store.transformRevision(equal, 1280, 720, 640, 360) == revision,
            "equal contents in fresh objects do not change revision");
        require(store.basis(equal, revision) == basis, "equal transform reuses trigonometry");
        WorldCameras.View moved = view(2);
        long movedRevision = store.transformRevision(moved, 1280, 720, 640, 360);
        require(
            movedRevision > revision && store.basis(moved, movedRevision) != basis,
            "anchor change invalidates basis");
        store.begin(frame(moved, store.basis(moved, movedRevision), movedRevision), moved, settings, 0, 0, 0, 0, 0, 0);
        store.candidate.applied = true;
        store.abort();
        require(
            store.displayed() == null && store.candidate == null && store.displayedSettings() == null,
            "throwing render clears all public display state");
        System.out.println("Camera frame-store contracts passed (Minecraft/OpenGL not required).");
    }

    private static WorldCameras.View view(double x) {
        return new WorldCameras.View(
            new CameraPose(x, 20, 30, 45, 35),
            new OrthographicProjection(20, 15, 256, .05, 1024),
            true);
    }

    private static CameraFrame frame(WorldCameras.View view, CameraBasis basis, long revision) {
        return new CameraFrame(
            "test:camera",
            1,
            view.pose,
            view.projection,
            1280,
            720,
            640,
            360,
            revision,
            0,
            HeightSlice.disabled(),
            HeightSlice.disabled(),
            HeightSlice.disabled(),
            SliceState.DISABLED,
            true,
            basis);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
