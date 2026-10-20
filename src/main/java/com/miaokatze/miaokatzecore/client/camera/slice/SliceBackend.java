package com.miaokatze.miaokatzecore.client.camera.slice;

import java.util.Objects;

import net.minecraft.client.Minecraft;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.client.camera.CameraFrame;
import com.miaokatze.miaokatzecore.client.camera.SliceState;

/**
 * Client-thread coordinator for the slice renderer. Requested GPU preview and uploaded boundary geometry have
 * separate revisions. Picking follows the displayed clipping policy while boundary meshes rebuild in the background.
 */
public final class SliceBackend {

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }

    public static final class Snapshot {

        public final HeightSlice requested, displayed, committed;
        public final long requestedRevision, displayedRevision, committedRevision, geometryRevision;
        public final SliceState state;
        public final boolean pickReady, supported;
        public final String unavailableReason;

        private Snapshot(HeightSlice requested, HeightSlice displayed, HeightSlice committed, long requestedRevision,
            long displayedRevision, long committedRevision, long geometryRevision, SliceState state, boolean pickReady,
            boolean supported, String unavailableReason) {
            this.requested = requested;
            this.displayed = displayed;
            this.committed = committed;
            this.requestedRevision = requestedRevision;
            this.displayedRevision = displayedRevision;
            this.committedRevision = committedRevision;
            this.geometryRevision = geometryRevision;
            this.state = state;
            this.pickReady = pickReady;
            this.supported = supported;
            this.unavailableReason = unavailableReason == null ? "" : unavailableReason;
        }
    }

    private static final Scope NO_SCOPE = () -> {};
    private static Object world;
    private static long epoch, nextRevision, requestedRevision, displayedRevision;
    private static HeightSlice requested = HeightSlice.disabled();
    private static HeightSlice lastDisplayed = HeightSlice.disabled();
    private static boolean session;
    private static String meshUnavailableReason;
    private static long meshCapabilityGeneration = -1;
    private static SliceShaders.FrameData renderData;
    private static long renderEpoch, renderTransformRevision, renderSliceRevision;

    private SliceBackend() {}

    /** Called once while assembling a rendered camera frame; never from a mesh worker or picker. */
    public static Snapshot freeze(Minecraft mc, long ownerEpoch, HeightSlice target) {
        Objects.requireNonNull(mc, "mc");
        Objects.requireNonNull(target, "target");
        long managerGeneration = SliceMeshes.managerGeneration();
        if (!session || world != mc.theWorld || epoch != ownerEpoch || meshCapabilityGeneration != managerGeneration)
            meshUnavailableReason = null;
        meshCapabilityGeneration = managerGeneration;
        boolean changed = !session || world != mc.theWorld || epoch != ownerEpoch || !requested.equals(target);
        if (changed) {
            world = mc.theWorld;
            epoch = ownerEpoch;
            requested = target;
            requestedRevision = ++nextRevision;
            displayedRevision = requestedRevision;
            session = true;
        }

        String unavailable = meshUnavailableReason != null ? meshUnavailableReason : SliceShaders.unsupportedReason();
        boolean supported = unavailable == null;
        HeightSlice displayed = supported ? target : HeightSlice.disabled();
        if (!changed && !lastDisplayed.equals(displayed)) displayedRevision = ++nextRevision;
        lastDisplayed = displayed;
        SliceMeshes.Status status = SliceMeshes.update(mc, ownerEpoch, displayed, displayedRevision);
        if (status.state() == SliceMeshes.State.UNSUPPORTED) {
            supported = false;
            String reason = status.unavailableReason() == null ? "RC2 boundary mesher is unavailable"
                : status.unavailableReason();
            // Initial world attachment is transient. Only a confirmed unsupported native provider is latched.
            if (!status.retryableUnavailable()) meshUnavailableReason = reason;
            unavailable = reason;
            displayed = HeightSlice.disabled();
            if (!lastDisplayed.equals(displayed)) displayedRevision = ++nextRevision;
            lastDisplayed = displayed;
            // Existing caps must be restored even when a native task provider proves unsupported after activation.
            SliceMeshes.release();
            status = SliceMeshes.tick(mc);
        }
        SliceState state = state(status.state());
        // Missing renderer capability does not disable the ordinary camera; restoration still fences picking.
        return new Snapshot(
            target,
            displayed,
            status.committed(),
            requestedRevision,
            displayedRevision,
            status.committedRevision(),
            status.geometryRevision(),
            state,
            supported || status.pickReady(),
            supported,
            unavailable);
    }

    /** The frame value already contains the displayed policy; pending HUD changes cannot mutate this draw. */
    public static void beginWorldFrame(CameraFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (renderData == null || renderEpoch != frame.ownerEpoch()
            || renderTransformRevision != frame.transformRevision()
            || renderSliceRevision != frame.sliceRevision()) {
            HeightSlice slice = frame.displayedSlice();
            renderData = new SliceShaders.FrameData(
                slice.enabled(),
                slice.hasLower() ? slice.minimumY() : -1.0E20,
                slice.hasUpper() ? slice.maximumY() : 1.0E20,
                slice.featherWidth(),
                frame.pose(),
                frame.projection(),
                0,
                0,
                frame.displayWidth(),
                frame.displayHeight(),
                slice.featheredCaps());
            renderEpoch = frame.ownerEpoch();
            renderTransformRevision = frame.transformRevision();
            renderSliceRevision = frame.sliceRevision();
        }
        SliceShaders.beginFrame(renderData);
    }

    public static void endWorldFrame() {
        SliceShaders.endFrame();
    }

    /**
     * Read-only click fence for the displayed clipping policy. Mesh uploads change visual boundary completion,
     * not the world ray or its clipped interval; a slice change still invalidates the previous displayed frame.
     */
    public static boolean canPick(CameraFrame frame) {
        if (frame == null || !session
            || !frame.pickReady()
            || frame.ownerEpoch() != epoch
            || frame.requestedRevision() != requestedRevision
            || frame.displayedRevision() != displayedRevision
            || meshCapabilityGeneration != SliceMeshes.managerGeneration()
            || !frame.requestedSlice()
                .equals(requested)
            || !frame.displayedSlice()
                .equals(lastDisplayed))
            return false;
        if (frame.sliceSupported()) return true;
        // An unsupported provider must finish restoring its old boundary meshes before ordinary camera picking.
        SliceMeshes.Status status = SliceMeshes.status();
        return !frame.displayedSlice()
            .enabled() && status.pickReady()
            && status.state() != SliceMeshes.State.APPLYING
            && status.state() != SliceMeshes.State.RESTORING;
    }

    /** Explicit opt-in for world-space mod previews. GUI renderers must not enter this scope. */
    public static Scope worldPreview() {
        SliceShaders.Scope scope = SliceShaders.domain(SliceShaders.Domain.WORLD_PREVIEW);
        return scope == null ? NO_SCOPE : scope::close;
    }

    /** Continues bounded cleanup even after the camera lease has gone away. */
    public static void tick(Minecraft mc) {
        SliceMeshes.tick(mc);
    }

    /** Releases GL state immediately and schedules normal boundary geometry restoration without blocking the client. */
    public static void release() {
        try {
            SliceShaders.endFrame();
        } finally {
            SliceMeshes.release();
            session = false;
            world = null;
            epoch = 0;
            requested = HeightSlice.disabled();
            lastDisplayed = HeightSlice.disabled();
            meshUnavailableReason = null;
            renderData = null;
        }
    }

    private static SliceState state(SliceMeshes.State state) {
        switch (state) {
            case PREVIEW:
                return SliceState.PREVIEW;
            case APPLYING:
                return SliceState.APPLYING;
            case READY:
                return SliceState.READY;
            case RESTORING:
                return SliceState.RESTORING;
            default:
                return SliceState.DISABLED;
        }
    }
}
