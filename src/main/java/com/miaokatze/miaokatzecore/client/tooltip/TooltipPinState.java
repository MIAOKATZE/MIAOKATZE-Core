// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** One local inventory-session pin. The caller owns the immutable rendered snapshot. */
final class TooltipPinState<T> {

    private Object owner;
    private T hover;
    private T fixed;

    void screen(Object current) {
        // A confirmation screen may temporarily cover the inventory. Only returning to play ends the session.
        if (current == null) {
            owner = null;
            hover = null;
            fixed = null;
        }
    }

    void beginFrame(Object current) {
        screen(current);
        hover = null;
    }

    void hover(Object current, T snapshot) {
        if (current == null || snapshot == null) return;
        // Keep a temporary confirmation overlay suspended, but never let a hidden pin lock another item GUI.
        // Only an actually rendered item in the other GUI retires the old owner and its unique fixed target.
        if (fixed != null && current != owner) fixed = null;
        if (fixed == null) owner = current;
        hover = snapshot;
    }

    boolean toggle(Object current) {
        if (current == null || current != owner) return false;
        if (fixed != null) {
            fixed = null;
            return true;
        }
        if (hover == null) return false;
        fixed = hover;
        return true;
    }

    boolean active(Object current) {
        return fixed != null && current == owner;
    }

    T fixed() {
        return fixed;
    }

    T hovered() {
        return hover;
    }
}
