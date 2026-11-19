// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** Session regressions independent of Minecraft, LWJGL and the optional tooltip mod. */
public final class TooltipPinStateContracts {

    public static void main(String[] args) {
        TooltipPinState<String> state = new TooltipPinState<>();
        Object inventory = new Object(), other = new Object();
        state.beginFrame(inventory);
        check(!state.toggle(inventory, null), "Pin requires a rendered root");
        state.hover(inventory, "root");
        check(state.toggle(inventory, null) && "root".equals(state.fixed()), "Pin the rendered root");
        state.beginFrame(inventory);
        state.hover(inventory, "other item");
        check("root".equals(state.fixed()), "Another hovered item cannot replace the root");
        check(state.toggle(inventory, "material") && "material".equals(state.child()), "Pin a rendered material");
        check("root".equals(state.fixed()) && state.active(inventory), "Both pins remain in the same GUI");
        check(state.toggle(inventory, "other child") && state.child() == null, "Unpin the child first");
        check("root".equals(state.fixed()), "Unpinning the child keeps the root");
        check(state.toggle(inventory, null) && !state.active(inventory), "Unpin the root next");
        state.beginFrame(inventory);
        check(!state.toggle(inventory, null), "Old hover cannot start another pin");
        state.hover(inventory, "root");
        state.toggle(inventory, null);
        state.toggle(inventory, "material");
        state.screen(other);
        check(
            state.fixed() == null && state.child() == null && state.hovered() == null,
            "Entering any different GUI removes both pins and hover without requiring another item");
        state.screen(inventory);
        check(!state.toggle(inventory, null), "Returning to the old GUI cannot resurrect a pin");
        state.hover(inventory, "root");
        state.toggle(inventory, null);
        state.toggle(inventory, "material");
        state.screen(null);
        check(
            state.fixed() == null && state.child() == null && !state.active(inventory),
            "Returning to play clears both layers");
        System.out.println("TooltipPinStateContracts: PASS (two pins, ordered unpin, GUI and play cleanup)");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
