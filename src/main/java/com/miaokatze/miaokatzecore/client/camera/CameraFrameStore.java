package com.miaokatze.miaokatzecore.client.camera;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Candidate render state is private until the entire world invocation succeeds. */
final class CameraFrameStore {

    private CameraFrame displayed;
    private CameraFrame transform;
    private CameraSettings displayedSettings;
    private long transformRevision;
    Candidate candidate;

    long transformRevision(WorldCameras.View view, int width, int height, int scaledWidth, int scaledHeight) {
        if (transform == null || !samePose(transform.pose(), view.pose)
            || !sameProjection(transform.projection(), view.projection)
            || transform.displayWidth() != width
            || transform.displayHeight() != height
            || transform.scaledWidth() != scaledWidth
            || transform.scaledHeight() != scaledHeight) {
            ++transformRevision;
        }
        return transformRevision;
    }

    void begin(CameraFrame frame, WorldCameras.View view, CameraSettings settings, double originX, double originY,
        double originZ, double realX, double realY, double realZ) {
        transform = frame;
        candidate = new Candidate(frame, view, settings, originX, originY, originZ, realX, realY, realZ);
    }

    CameraBasis basis(WorldCameras.View view, long revision) {
        return transform != null && transform.transformRevision() == revision ? transform.basis()
            : new CameraBasis(view.pose);
    }

    void complete() {
        if (candidate != null && candidate.applied) {
            displayed = candidate.frame;
            displayedSettings = candidate.settings;
        } else {
            displayed = null;
            displayedSettings = null;
        }
        candidate = null;
    }

    void abort() {
        displayed = null;
        displayedSettings = null;
        candidate = null;
    }

    void clear() {
        abort();
        transform = null;
    }

    CameraFrame displayed() {
        return displayed;
    }

    CameraSettings displayedSettings() {
        return displayedSettings;
    }

    private static boolean samePose(CameraPose a, CameraPose b) {
        return a.anchorX() == b.anchorX() && a.anchorY() == b.anchorY()
            && a.anchorZ() == b.anchorZ()
            && a.yawDegrees() == b.yawDegrees()
            && a.pitchDegrees() == b.pitchDegrees();
    }

    private static boolean sameProjection(OrthographicProjection a, OrthographicProjection b) {
        return a.halfWidth() == b.halfWidth() && a.halfHeight() == b.halfHeight()
            && a.depthOffset() == b.depthOffset()
            && a.near() == b.near()
            && a.far() == b.far();
    }

    static final class Candidate {

        final CameraFrame frame;
        final WorldCameras.View view;
        final CameraSettings settings;
        final double originX, originY, originZ, realX, realY, realZ;
        boolean applied, worldLast;

        Candidate(CameraFrame frame, WorldCameras.View view, CameraSettings settings, double originX, double originY,
            double originZ, double realX, double realY, double realZ) {
            this.frame = frame;
            this.view = view;
            this.settings = settings;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.realX = realX;
            this.realY = realY;
            this.realZ = realZ;
        }
    }
}
