// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.render;

import org.lwjgl.opengl.GL11;

/** Lightweight scope for a caller-owned current matrix stack and an explicit attribute mask. */
public final class MatrixAttributeScope implements AutoCloseable {

    private boolean closed;

    public MatrixAttributeScope(int mask) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(mask);
    }

    @Override
    public void close() {
        if (closed) return;
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        closed = true;
    }
}
