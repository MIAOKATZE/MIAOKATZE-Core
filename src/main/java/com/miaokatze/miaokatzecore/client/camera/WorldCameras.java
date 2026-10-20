package com.miaokatze.miaokatzecore.client.camera;

import java.util.Objects;

import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Client-thread ownership. This contract can load without Minecraft or OpenGL. */
public final class WorldCameras {

    public enum GuiPolicy {
        KEEP,
        SUSPEND,
        RELEASE
    }

    public enum MovementPolicy {
        NONE,
        HORIZONTAL,
        ALL
    }

    public static final class Options {

        public static final Options PASSIVE = new Options(
            false,
            false,
            false,
            false,
            false,
            false,
            MovementPolicy.NONE);
        public final boolean hideHand, disableFog, disableCull, disableVanillaFrustum, consumeHotbarScroll,
            blockDebugReloads;
        public final MovementPolicy movement;
        public final boolean freeCursor;

        public Options(boolean hideHand, boolean disableFog, boolean disableCull, boolean disableVanillaFrustum,
            boolean consumeHotbarScroll, boolean blockDebugReloads, MovementPolicy movement) {
            this(
                hideHand,
                disableFog,
                disableCull,
                disableVanillaFrustum,
                consumeHotbarScroll,
                blockDebugReloads,
                movement,
                false);
        }

        public Options(boolean hideHand, boolean disableFog, boolean disableCull, boolean disableVanillaFrustum,
            boolean consumeHotbarScroll, boolean blockDebugReloads, MovementPolicy movement, boolean freeCursor) {
            this.hideHand = hideHand;
            this.disableFog = disableFog;
            this.disableCull = disableCull;
            this.disableVanillaFrustum = disableVanillaFrustum;
            this.consumeHotbarScroll = consumeHotbarScroll;
            this.blockDebugReloads = blockDebugReloads;
            this.movement = Objects.requireNonNull(movement, "movement");
            this.freeCursor = freeCursor;
        }
    }

    public static final class View {

        public final CameraPose pose;
        public final OrthographicProjection projection;
        public final boolean anchorDriven;

        public View(CameraPose pose, OrthographicProjection projection, boolean anchorDriven) {
            this.pose = Objects.requireNonNull(pose, "pose");
            this.projection = Objects.requireNonNull(projection, "projection");
            this.anchorDriven = anchorDriven;
        }
    }

    public interface Source {

        /** Once per renderWorld invocation. Null reserves ownership without applying orthographic projection. */
        View sample(float partialTicks, int scaledWidth, int scaledHeight);

        default Options options() {
            return Options.PASSIVE;
        }

        default GuiPolicy guiPolicy() {
            return GuiPolicy.SUSPEND;
        }

        /** Immutable policy sampled by Core at tick/render boundaries. Legacy sources remain passive HUD users. */
        default CameraSettings settings() {
            return CameraSettings.legacy(options(), guiPolicy());
        }

        default CameraControls controls() {
            return CameraControls.NONE;
        }

        default boolean allowKey(int keyCode) {
            return true;
        }
    }

    public interface Lease extends AutoCloseable {

        boolean active();

        String ownerId();

        @Override
        void close();
    }

    private static volatile Entry current;
    private static boolean revoking;
    private static long epoch;
    private static Runnable cleanup;

    private WorldCameras() {}

    /** Equal/lower priority is refused. Tokens use world object identity, never dimension numbers. */
    public static Lease tryAcquire(String ownerId, Object worldToken, int priority, Source source, Runnable onRevoked) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.isEmpty()) throw new IllegalArgumentException("ownerId must not be empty");
        Objects.requireNonNull(worldToken, "worldToken");
        Objects.requireNonNull(source, "source");
        if (revoking || current != null && priority <= current.priority) return null;
        revoke();
        Entry entry = new Entry(ownerId, worldToken, priority, source, onRevoked, ++epoch);
        current = entry;
        return entry;
    }

    public static boolean ownedBy(String ownerId) {
        return current != null && current.owner.equals(ownerId);
    }

    public static String currentOwnerId() {
        return current == null ? null : current.owner;
    }

    public static void worldChanged(Object worldToken) {
        if (current != null && current.world != worldToken) revoke();
    }

    static Entry entry() {
        return current;
    }

    static void onCleanup(Runnable action) {
        cleanup = action;
    }

    private static void revoke() {
        Entry old = current;
        if (old == null) return;
        current = null;
        revoking = true;
        try {
            if (cleanup != null) cleanup.run();
        } finally {
            try {
                if (old.onRevoked != null) old.onRevoked.run();
            } finally {
                revoking = false;
            }
        }
    }

    static final class Entry implements Lease {

        final String owner;
        final Object world;
        final int priority;
        final Source source;
        final Runnable onRevoked;
        final long epoch;

        Entry(String owner, Object world, int priority, Source source, Runnable onRevoked, long epoch) {
            this.owner = owner;
            this.world = world;
            this.priority = priority;
            this.source = source;
            this.onRevoked = onRevoked;
            this.epoch = epoch;
        }

        @Override
        public boolean active() {
            return current == this;
        }

        @Override
        public String ownerId() {
            return owner;
        }

        @Override
        public void close() {
            if (current == this) revoke();
        }
    }
}
