// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.List;

/** Immutable presentation snapshot. New block types (sprites, charts) supply their own client renderer. */
public interface TooltipBlock {

    /** A complete, independently readable representation for non-component consumers. */
    List<String> fallbackLines();
}
