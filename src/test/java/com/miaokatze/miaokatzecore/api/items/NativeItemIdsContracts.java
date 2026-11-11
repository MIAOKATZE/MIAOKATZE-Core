// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.items;

import net.minecraft.nbt.NBTTagCompound;

/** Numeric native headers only: no Item registry, game bootstrap, world or graphics context. */
public final class NativeItemIdsContracts {

    private NativeItemIdsContracts() {}

    public static void main(String[] args) {
        NBTTagCompound tag = new NBTTagCompound();
        require(NativeItemIds.read(tag) == 0, "missing native header");
        tag.setInteger("id", 23);
        tag.setInteger("idExt", 70000);
        require(NativeItemIds.read(tag) == 70000, "extended integer has precedence");
        tag.setString("idExt", "not the native integer type");
        require(NativeItemIds.read(tag) == 23, "integer id is the next native form");
        tag.removeTag("idExt");
        tag.setShort("id", (short) -123);
        require(NativeItemIds.read(tag) == -123, "native short remains signed");
        tag.setByte("id", (byte) 7);
        require(NativeItemIds.read(tag) == 0, "byte header is not a native item-id form");
        tag.setLong("id", 70000);
        require(NativeItemIds.read(tag) == 0, "long header is not a native item-id form");
        tag.setInteger("id", 70000);
        require(NativeItemIds.read(tag) == 70000, "old integer high ID is readable");
        tag.setByte("Count", (byte) 9);
        tag.setShort("Damage", (short) 4);
        NBTTagCompound body = new NBTTagCompound();
        body.setString("variant", "opaque native body");
        tag.setTag("tag", body.copy());
        NativeItemIds.write(tag, 31999);
        require(tag.hasKey("id", 2) && tag.getShort("id") == 31999 && !tag.hasKey("idExt"), "last low native ID");
        NativeItemIds.write(tag, 32000);
        require(
            tag.getShort("id") == 0 && tag.hasKey("idExt", 3) && NativeItemIds.read(tag) == 32000,
            "first extended ID uses the actual 31999 cutoff");
        NativeItemIds.write(tag, Integer.MAX_VALUE);
        require(NativeItemIds.read(tag) == Integer.MAX_VALUE, "extended integer remains exact");
        NativeItemIds.write(tag, 42);
        require(
            tag.hasKey("id", 2) && tag.getShort("id") == 42 && !tag.hasKey("idExt"),
            "low remap clears stale idExt");
        require(
            tag.getByte("Count") == 9 && tag.getShort("Damage") == 4 && body.equals(tag.getCompoundTag("tag")),
            "numeric header writing never edits count, damage or opaque payload");
        NativeItemIds.write(tag, -1);
        require(NativeItemIds.read(tag) == -1 && !tag.hasKey("idExt"), "native signed sentinel is not masked");
        System.out.println("Native item-ID contracts passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
