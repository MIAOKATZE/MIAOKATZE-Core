package com.miaokatze.miaokatzecore.client.camera;

/** Executable without Minecraft/GL on the classpath. */
public final class CameraLeaseContracts {

    private static final WorldCameras.Source SOURCE = (pt, width, height) -> null;

    private CameraLeaseContracts() {}

    public static void main(String[] args) {
        Object world = new Object();
        int[] revoked = { 0 };
        WorldCameras.Lease first = WorldCameras.tryAcquire("first", world, 20, SOURCE, () -> revoked[0]++);
        require(first != null && first.active() && WorldCameras.ownedBy("first"), "first owner");
        require(WorldCameras.tryAcquire("equal", world, 20, SOURCE, null) == null, "equal priority refused");
        require(WorldCameras.tryAcquire("lower", world, 19, SOURCE, null) == null, "lower priority refused");
        WorldCameras.Lease second = WorldCameras.tryAcquire("second", world, 30, SOURCE, () -> revoked[0]++);
        require(
            second != null && second.active() && !first.active() && revoked[0] == 1,
            "higher priority revokes once");
        first.close();
        require(second.active(), "stale close cannot release new owner");
        WorldCameras.worldChanged(world);
        require(second.active(), "same world identity retained");
        WorldCameras.worldChanged(new Object());
        require(!second.active() && revoked[0] == 2 && WorldCameras.currentOwnerId() == null, "new identity releases");
        second.close();
        require(revoked[0] == 2, "close idempotent");
        WorldCameras.Lease[] reentrant = { null };
        WorldCameras.Lease third = WorldCameras.tryAcquire(
            "third",
            world,
            1,
            SOURCE,
            () -> reentrant[0] = WorldCameras.tryAcquire("reentrant", world, 999, SOURCE, null));
        third.close();
        require(
            reentrant[0] == null && WorldCameras.currentOwnerId() == null,
            "revoke callback cannot steal in-flight handoff");
        require(SOURCE.options() == WorldCameras.Options.PASSIVE && SOURCE.allowKey(1), "default input passive");
        require(
            !SOURCE.options().freeCursor && SOURCE.guiPolicy() == WorldCameras.GuiPolicy.SUSPEND,
            "default cursor and GUI");
        System.out.println("Camera lease contracts passed (Minecraft/OpenGL not required).");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
