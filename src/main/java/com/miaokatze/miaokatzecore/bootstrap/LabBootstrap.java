// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.bootstrap;

import com.miaokatze.miaokatzecore.api.testlab.LabRegistry;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Discovery boundary only. The driver and world policy exist exclusively in the replacement testlab jar. */
public final class LabBootstrap {

    private LabBootstrap() {}

    public static void serverStarting(FMLServerStartingEvent event) {
        if (LabRegistry.modules()
            .isEmpty()) return;
        try {
            Class.forName("com.miaokatze.miaokatzecore.testlab.LabDriver")
                .getMethod("install", FMLServerStartingEvent.class)
                .invoke(null, event);
        } catch (ClassNotFoundException absent) {
            // Production Core permits passive discovery and metadata without installing a test driver.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install testlab driver", failure);
        }
    }
}
