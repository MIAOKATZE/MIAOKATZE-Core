// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.client.preview;

/** Internal view of BlockRenderer's dummy world, added by the optional client mixin. */
public interface TrackedPreviewRevision {

    long miaokatze$previewRevision();
}
