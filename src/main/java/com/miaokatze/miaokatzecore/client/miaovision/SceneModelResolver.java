package com.miaokatze.miaokatzecore.client.miaovision;

import net.minecraft.item.ItemStack;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;

/** Client-owned identity/art bridge. All callbacks run inside a restored native scene rendering scope. */
public interface SceneModelResolver {

    /** Return a display stack; Core copies it before invoking any item/model renderer. Null means no model. */
    ItemStack resolve(String id);

    /**
     * Optional audited model, local normalized cube [-.5,.5]. Return true when drawn; false uses native fallback.
     * Opacity is 1 during native capture; scene fades are composited afterwards, independently of material state.
     */
    default boolean render(SceneBody body, ItemStack stack, float opacity) {
        return false;
    }

    /** Room art in scene world coordinates. No callbacks may mutate gameplay inventories/worlds. */
    default void beforeBodies(SceneSnapshot snapshot) {}

    default void afterBodies(SceneSnapshot snapshot) {}
}
