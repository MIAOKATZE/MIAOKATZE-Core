package com.miaokatze.miaokatzecore.client.camera;

/** Real ownership and automatic-menu lifecycle, without a native window or synthetic focus. */
final class CameraPauseLifecycleContracts {

    static void run() {
        CameraPauseLifecycle pause = new CameraPauseLifecycle();
        Object world = new Object();
        Object[] screen = { null };
        int[] menus = { 0 }, revoked = { 0 };
        for (WorldCameras.GuiPolicy policy : WorldCameras.GuiPolicy.values()) {
            WorldCameras.Lease lease = WorldCameras
                .tryAcquire("focus", world, 30, (t, w, h) -> null, () -> revoked[0]++);
            pause.pause(world, () -> {
                require(pause.automatic(), "GUI event identifies the automatic source");
                screen[0] = new Object();
                menus[0]++;
                require(pause.screen(screen[0], world), "automatic menu is recognized during installation");
            }, () -> screen[0]);
            require(
                lease.active() && menus[0] == policy.ordinal() + 1,
                "lost-focus pause opens vanilla menu and retains ownership");
            require(
                !pause.automatic() && pause.screen(screen[0], world),
                "installed automatic menu retains its own identity");
            require(
                !CameraActivity.releasesForGui(true, policy, pause.screen(screen[0], world)),
                "automatic menu preserves every GUI policy");
            require(
                !CameraActivity.interactive(true, true, true, true),
                "focus gain while the pause menu remains open cannot acquire world input");
            Object oldMenu = screen[0];
            screen[0] = null;
            pause.observe(screen[0], world);
            require(!pause.screen(oldMenu, world), "return to game retires the automatic menu identity");
            require(
                CameraActivity.interactive(true, false, true, true),
                "return to game restores input without reacquiring the lease");
            require(
                CameraActivity.releasesForGui(true, policy, pause.screen(new Object(), world))
                    == (policy == WorldCameras.GuiPolicy.RELEASE),
                "a later explicit GUI keeps the original release policy");
            lease.close();
            require(!lease.active(), "explicit exit remains an immediate revocation");
        }
        require(revoked[0] == 3, "each explicit exit revokes exactly once");
        try {
            pause.pause(world, () -> { throw new IllegalStateException("menu failure"); }, () -> null);
            throw new AssertionError("missing menu failure");
        } catch (IllegalStateException expected) {
            require(!pause.automatic(), "failed menu dispatch cannot mark a later real Escape as automatic");
        }
        WorldCameras.Lease lease = WorldCameras.tryAcquire("focus-world", world, 30, (t, w, h) -> null, null);
        pause.pause(world, () -> screen[0] = new Object(), () -> screen[0]);
        Object oldMenu = screen[0];
        Object nextWorld = new Object();
        WorldCameras.worldChanged(nextWorld);
        pause.observe(oldMenu, nextWorld);
        require(
            !lease.active() && !pause.screen(oldMenu, world) && !pause.screen(oldMenu, nextWorld),
            "world replacement clears both ownership and the automatic menu identity");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
