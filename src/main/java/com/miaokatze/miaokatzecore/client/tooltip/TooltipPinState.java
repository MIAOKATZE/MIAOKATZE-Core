// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

/** The root and one child can be fixed in the same GUI session. */
final class TooltipPinState<T> {

    private Object owner;
    private T hover;
    private T fixed;
    private T child;

    boolean screen(Object current) {
        if (owner == current) return false;
        owner = current;
        hover = null;
        fixed = null;
        child = null;
        return true;
    }

    void beginFrame(Object current) {
        screen(current);
        hover = null;
    }

    void hover(Object current, T snapshot) {
        if (current != null && current == owner) hover = snapshot;
    }

    boolean toggle(Object current, T childHover) {
        if (current == null || current != owner) return false;
        if (child != null) {
            child = null;
        } else if (fixed != null) {
            if (childHover != null) child = childHover;
            else fixed = null;
        } else {
            if (hover == null) return false;
            fixed = hover;
        }
        return true;
    }

    boolean active(Object current) {
        return pinnedRoot(current) != null;
    }

    /** Hover snapshots are candidates for the key only, never roots for the fixed render pass. */
    T pinnedRoot(Object current) {
        return current != null && current == owner ? fixed : null;
    }

    T fixed() {
        return fixed;
    }

    T child() {
        return child;
    }

    T hovered() {
        return hover;
    }
}
