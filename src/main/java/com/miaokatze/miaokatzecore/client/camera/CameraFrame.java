package com.miaokatze.miaokatzecore.client.camera;

import java.util.Objects;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.CameraRay;
import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;
import com.miaokatze.miaokatzecore.api.camera.ProjectedPoint;

/** Published immutable display snapshot. It retains no world, owner callback or mutable session reference. */
public final class CameraFrame {

    private final String ownerId;
    private final long ownerEpoch, transformRevision, geometryRevision;
    private final long requestedRevision, displayedRevision, committedRevision;
    private final CameraPose pose;
    private final OrthographicProjection projection;
    private final CameraBasis basis;
    private final int displayWidth, displayHeight, scaledWidth, scaledHeight;
    private final HeightSlice requestedSlice, displayedSlice, committedSlice;
    private final SliceState sliceState;
    private final boolean pickReady, sliceSupported;
    private final String sliceUnavailableReason;

    CameraFrame(String ownerId, long ownerEpoch, CameraPose pose, OrthographicProjection projection, int displayWidth,
        int displayHeight, int scaledWidth, int scaledHeight, long transformRevision, long sliceRevision,
        HeightSlice requestedSlice, HeightSlice displayedSlice, HeightSlice committedSlice, SliceState sliceState,
        boolean pickReady) {
        this(
            ownerId,
            ownerEpoch,
            pose,
            projection,
            new CameraBasis(pose),
            displayWidth,
            displayHeight,
            scaledWidth,
            scaledHeight,
            transformRevision,
            0,
            sliceRevision,
            sliceRevision,
            sliceRevision,
            requestedSlice,
            displayedSlice,
            committedSlice,
            sliceState,
            pickReady,
            true,
            "");
    }

    CameraFrame(String ownerId, long ownerEpoch, CameraPose pose, OrthographicProjection projection, int displayWidth,
        int displayHeight, int scaledWidth, int scaledHeight, long transformRevision, long sliceRevision,
        HeightSlice requestedSlice, HeightSlice displayedSlice, HeightSlice committedSlice, SliceState sliceState,
        boolean pickReady, CameraBasis basis) {
        this(
            ownerId,
            ownerEpoch,
            pose,
            projection,
            basis,
            displayWidth,
            displayHeight,
            scaledWidth,
            scaledHeight,
            transformRevision,
            0,
            sliceRevision,
            sliceRevision,
            sliceRevision,
            requestedSlice,
            displayedSlice,
            committedSlice,
            sliceState,
            pickReady,
            true,
            "");
    }

    CameraFrame(String ownerId, long ownerEpoch, CameraPose pose, OrthographicProjection projection, CameraBasis basis,
        int displayWidth, int displayHeight, int scaledWidth, int scaledHeight, long transformRevision,
        long geometryRevision, long requestedRevision, long displayedRevision, long committedRevision,
        HeightSlice requestedSlice, HeightSlice displayedSlice, HeightSlice committedSlice, SliceState sliceState,
        boolean pickReady, boolean sliceSupported, String sliceUnavailableReason) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.isEmpty()) throw new IllegalArgumentException("ownerId must not be empty");
        this.ownerEpoch = ownerEpoch;
        this.pose = Objects.requireNonNull(pose, "pose");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.basis = Objects.requireNonNull(basis, "basis");
        CameraPose basisPose = basis.pose();
        if (basisPose.anchorX() != pose.anchorX() || basisPose.anchorY() != pose.anchorY()
            || basisPose.anchorZ() != pose.anchorZ()
            || basisPose.yawDegrees() != pose.yawDegrees()
            || basisPose.pitchDegrees() != pose.pitchDegrees()) {
            throw new IllegalArgumentException("basis must match pose");
        }
        if (displayWidth <= 0 || displayHeight <= 0 || scaledWidth <= 0 || scaledHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        this.transformRevision = transformRevision;
        this.geometryRevision = geometryRevision;
        this.requestedRevision = requestedRevision;
        this.displayedRevision = displayedRevision;
        this.committedRevision = committedRevision;
        this.requestedSlice = Objects.requireNonNull(requestedSlice, "requestedSlice");
        this.displayedSlice = Objects.requireNonNull(displayedSlice, "displayedSlice");
        this.committedSlice = Objects.requireNonNull(committedSlice, "committedSlice");
        this.sliceState = Objects.requireNonNull(sliceState, "sliceState");
        this.pickReady = pickReady;
        this.sliceSupported = sliceSupported;
        this.sliceUnavailableReason = Objects.requireNonNull(sliceUnavailableReason, "sliceUnavailableReason");
    }

    public String ownerId() {
        return ownerId;
    }

    public long ownerEpoch() {
        return ownerEpoch;
    }

    public CameraPose pose() {
        return pose;
    }

    public OrthographicProjection projection() {
        return projection;
    }

    public CameraBasis basis() {
        return basis;
    }

    public int displayWidth() {
        return displayWidth;
    }

    public int displayHeight() {
        return displayHeight;
    }

    public int scaledWidth() {
        return scaledWidth;
    }

    public int scaledHeight() {
        return scaledHeight;
    }

    public long transformRevision() {
        return transformRevision;
    }

    public long geometryRevision() {
        return geometryRevision;
    }

    public long requestedRevision() {
        return requestedRevision;
    }

    public long displayedRevision() {
        return displayedRevision;
    }

    public long committedRevision() {
        return committedRevision;
    }

    /** Alias for the slice revision used by the actual displayed frame. */
    public long sliceRevision() {
        return displayedRevision;
    }

    public HeightSlice requestedSlice() {
        return requestedSlice;
    }

    public HeightSlice displayedSlice() {
        return displayedSlice;
    }

    public HeightSlice committedSlice() {
        return committedSlice;
    }

    public SliceState sliceState() {
        return sliceState;
    }

    /** The displayed clipping policy can be picked even while boundary geometry is APPLYING or RESTORING. */
    public boolean pickReady() {
        return pickReady;
    }

    public boolean sliceSupported() {
        return sliceSupported;
    }

    public String sliceUnavailableReason() {
        return sliceUnavailableReason;
    }

    /** Pure geometry, with bottom-left display-pixel coordinates. The caller checks publication/pick readiness. */
    public CameraRay ray(double displayX, double displayY) {
        return basis.ray(projection, 2 * displayX / displayWidth - 1, 2 * displayY / displayHeight - 1);
    }

    public ProjectedPoint project(double worldX, double worldY, double worldZ) {
        return basis.project(projection, worldX, worldY, worldZ);
    }
}
