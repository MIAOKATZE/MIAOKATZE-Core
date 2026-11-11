// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.camera.slice;

public interface SliceMeshOutput {

    SliceMeshes.Snapshot core$getSliceSnapshot();

    void core$setSliceSnapshot(SliceMeshes.Snapshot snapshot);
}
