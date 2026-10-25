package com.miaokatze.miaokatzecore.client.camera;

import java.util.function.Supplier;

/** Identifies only vanilla's automatic lost-focus menu, never an explicit Escape or another GUI. */
final class CameraPauseLifecycle {

    private int automaticDepth;
    private Object automaticScreen, automaticWorld;

    void pause(Object world, Runnable openMenu, Supplier<Object> installedMenu) {
        automaticDepth++;
        try {
            openMenu.run();
        } finally {
            automaticDepth--;
            automaticScreen = installedMenu.get();
            automaticWorld = automaticScreen == null ? null : world;
        }
    }

    boolean automatic() {
        return automaticDepth != 0;
    }

    boolean screen(Object screen, Object world) {
        return screen != null && world != null && (automatic() || screen == automaticScreen && world == automaticWorld);
    }

    void observe(Object screen, Object world) {
        if (!automatic() && (screen != automaticScreen || world != automaticWorld)) {
            automaticScreen = null;
            automaticWorld = null;
        }
    }
}
