// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.items;

import net.minecraft.nbt.NBTTagCompound;

/** Pure numeric ItemStack headers used by GTNH's EndlessIDs native reader/writer; no registry or asset policy. */
public final class NativeItemIds {

    private NativeItemIds() {}

    /** Exact native priority. Shorts remain signed; an unrecognized/missing header reads as native zero. */
    public static int read(NBTTagCompound tag) {
        if (tag.hasKey("idExt", 3)) return tag.getInteger("idExt");
        if (tag.hasKey("id", 3)) return tag.getInteger("id");
        if (tag.hasKey("id", 2)) return tag.getShort("id");
        return 0;
    }

    /** Write the native 31999 cutoff and retire stale extended IDs when remapping back to a low ID. */
    public static void write(NBTTagCompound tag, int id) {
        if (id > 31999) {
            tag.setShort("id", (short) 0);
            tag.setInteger("idExt", id);
        } else {
            tag.setShort("id", (short) id);
            tag.removeTag("idExt");
        }
    }
}
