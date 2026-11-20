// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;

/** Owns a compiled compatibility draw, including Angelica's emulated display lists. */
public final class MiaoVisonLiteDisplayList implements AutoCloseable {

    private final Thread owner = Thread.currentThread();
    private final ContextCapabilities context;
    private int name;
    private boolean recording, compiled;

    private MiaoVisonLiteDisplayList(int name, ContextCapabilities context) {
        this.name = name;
        this.context = context;
    }

    public static MiaoVisonLiteDisplayList allocate() {
        ContextCapabilities context = MiaoVisonLiteMaterialBatch.boundContext();
        if (context == null) return null;
        int name = GL11.glGenLists(1);
        return name == 0 ? null : new MiaoVisonLiteDisplayList(name, context);
    }

    public void begin() {
        requireContext();
        if (name == 0 || recording) throw new IllegalStateException("Display list is not available for recording");
        GL11.glNewList(name, GL11.GL_COMPILE);
        compiled = false;
        recording = true;
    }

    public void finish() {
        if (recording) {
            requireContext();
            recording = false;
            GL11.glEndList();
            compiled = true;
        }
    }

    public void draw() {
        requireContext();
        if (!compiled) throw new IllegalStateException("Display list was not compiled");
        GL11.glCallList(name);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Display list belongs to another thread");
    }

    private void requireContext() {
        requireOwner();
        if (context != MiaoVisonLiteMaterialBatch.boundContext())
            throw new IllegalStateException("Display list belongs to another GL context");
    }

    @Override
    public void close() {
        if (name == 0) return;
        requireOwner();
        try {
            if (context == MiaoVisonLiteMaterialBatch.boundContext()) {
                try {
                    finish();
                } finally {
                    GL11.glDeleteLists(name, 1);
                }
            }
        } finally {
            recording = false;
            compiled = false;
            name = 0;
        }
    }
}
