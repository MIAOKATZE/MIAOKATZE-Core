// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.BitSet;

/** Only accepted button presses own releases. Paint changes do not reset the gesture. */
final class TooltipButtonState {
    private Object owner;
    private final BitSet accepted = new BitSet();

    void screen(Object screen) {
        if (owner != screen) {
            owner = screen;
            accepted.clear();
        }
    }

    void claim(int button) { if (button >= 0) accepted.set(button); }

    boolean release(int button) {
        if (button < 0) return false;
        boolean owned = accepted.get(button);
        accepted.clear(button);
        return owned;
    }
}
