// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** Session regressions, independent of Minecraft, LWJGL or an installed optional tooltip mod. */
public final class TooltipPinStateContracts {

    public static void main(String[] args) {
        TooltipPinState<String> state = new TooltipPinState<>();
        Object inventory = new Object(), confirmation = new Object(), reopened = new Object();
        check(!state.toggle(inventory), "Cannot pin without an actually rendered item");
        state.hover(inventory, "first stack, NBT and page snapshot");
        check(state.toggle(inventory), "Pin first rendered snapshot");
        String frozen = state.fixed();
        state.beginFrame(inventory);
        state.hover(inventory, "different item");
        check(state.fixed() == frozen && state.active(inventory), "Another hovered item cannot replace a pin");
        check(
            state.toggle(inventory) && state.fixed() == null && "different item".equals(state.hovered()),
            "Unpin restores current hover, not the old pinned target");
        check(state.toggle(inventory) && "different item".equals(state.fixed()), "Only one pin is retained");
        state.screen(confirmation);
        check(!state.active(confirmation) && state.fixed() != null, "Confirmation suspends the inventory overlay");
        check(!state.toggle(confirmation), "Confirmation input cannot change the hidden inventory pin");
        state.screen(inventory);
        check(state.active(inventory), "Canceling confirmation restores the same pin");
        state.beginFrame(inventory);
        check(
            state.toggle(inventory) && state.fixed() == null && state.hovered() == null,
            "Unpin over empty space leaves no tooltip target");
        check(!state.toggle(inventory), "Stale hover cannot create a new pin");
        state.hover(inventory, "last item");
        state.toggle(inventory);
        state.screen(null);
        check(state.fixed() == null && state.hovered() == null, "Returning to play clears all item snapshots");
        state.beginFrame(reopened);
        check(!state.active(reopened) && !state.toggle(reopened), "Reopening an inventory cannot resurrect a pin");
        state.hover(reopened, "new session");
        check(state.toggle(reopened) && state.active(reopened), "A new inventory can create its own pin");
        Object otherInventory = new Object();
        state.beginFrame(otherInventory);
        check(
            state.fixed() != null && !state.active(otherInventory),
            "Changing GUI alone preserves a suspended pin, including confirmations");
        state.hover(otherInventory, null);
        check(state.fixed() != null, "A non-item tooltip cannot retire a suspended pin");
        state.hover(otherInventory, "actually rendered item in another inventory");
        check(
            state.fixed() == null && state.hovered()
                .startsWith("actually rendered"),
            "A visible item in another GUI retires the unreachable old pin");
        check(
            state.toggle(otherInventory) && state.active(otherInventory),
            "The advertised pin key can fix the newly hovered item immediately");
        state.screen(reopened);
        check(
            !state.active(reopened) && !"new session".equals(state.fixed()),
            "Returning to an earlier GUI cannot resurrect its replaced pin");
        state.screen(null);
        System.out.println("Tooltip pin session contracts passed (18 lifecycle assertions)");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
