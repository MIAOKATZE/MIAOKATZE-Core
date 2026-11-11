// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.bq;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Internal adapter for BQ 3.8.92's process-wide databases, shared by an integrated host's two sides. */
public final class SharedQuestDatabase {

    private SharedQuestDatabase() {}

    public static boolean isLocalHost() {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient handler = minecraft.getNetHandler();
        // The channel check also excludes a remote connection if an integrated-server flag is stale.
        return minecraft.isIntegratedServerRunning() && handler != null
            && handler.getNetworkManager()
                .isLocalChannel();
    }
}
