// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.camera.slice;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

/** Forces an already-pending native rebuild back into Angelica's interim submission list when a task was lost. */
public interface SliceMeshScheduler {

    void core$scheduleSliceRebuild(RenderSection section);
}
