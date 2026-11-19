// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.client.preview;

/** Internal client-thread bridge between the optional group publication and sort hooks. */
public final class StructureLibHintLifecycle {

    private static Runnable markClean;

    private StructureLibHintLifecycle() {}

    public static void groupPublished(Runnable clearDirtyFlag) {
        markClean = clearDirtyFlag;
    }

    public static void listSorted() {
        // Keep vanilla's dirty flag behavior unless the hook that invalidates on publication is active.
        if (markClean != null) markClean.run();
    }
}
