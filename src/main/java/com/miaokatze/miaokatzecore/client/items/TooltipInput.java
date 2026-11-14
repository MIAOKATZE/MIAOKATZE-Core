// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.miaokatzecore.client.items;

/** Compatibility bootstrap; Tooltip controls and render integration now live in the dedicated module. */
public final class TooltipInput {

    private TooltipInput() {}

    public static void install() {
        com.miaokatze.miaokatzecore.client.tooltip.TooltipController.install();
    }
}
