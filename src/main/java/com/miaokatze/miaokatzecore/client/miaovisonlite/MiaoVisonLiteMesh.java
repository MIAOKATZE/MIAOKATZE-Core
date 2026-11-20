// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.BaseVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexedVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.miaokatze.miaokatzecore.client.miaovisonlite.MiaoVisonLiteMaterialBatch.Frame;
import com.miaokatze.miaokatzecore.client.render.internal.AngelicaFfpBridge;
import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/**
 * A directly uploaded, ordered atlas mesh. Positions use the caller's model space; the active modelview and
 * projection, texture, blend and depth state remain the caller's. No world or block renderer is needed.
 */
public final class MiaoVisonLiteMesh implements AutoCloseable {

    public static final int MAX_VERTICES = 16368;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    public enum Quads {

        /** Angelica's live last-vertex quad conversion, with its renderer defaults. */
        LIVE,
        /** Preserves indexed vertex order and format when migrating existing GTNHLib recorded quad batches. */
        RECORDED
    }

    private final Thread owner = Thread.currentThread();
    private final Frame frame;
    private final VertexBuffer buffer;
    private final IVertexArrayObject array;
    private final Quads quads;
    private final double x, y, z, u, v;
    private final int color;
    private boolean closed;

    private MiaoVisonLiteMesh(Frame frame, VertexBuffer buffer, IVertexArrayObject array, Quads quads, Builder data) {
        this.frame = frame;
        this.buffer = buffer;
        this.array = array;
        this.quads = quads;
        x = data.x;
        y = data.y;
        z = data.z;
        u = data.u;
        v = data.v;
        color = data.color;
    }

    /**
     * Admits the measured fixed-function backend. The supplied Tessellator is checked only to avoid consuming a
     * capture owned by another renderer. This method submits no draw.
     */
    public static Frame currentFrame(Tessellator tess) {
        Frame frame = MiaoVisonLiteMaterialBatch.currentFrame(tess);
        return supported(frame) ? frame : null;
    }

    public static boolean supported(Frame frame) {
        if (frame == null || !frame.angelica
            || !frame.context.OpenGL30
            || !VAOManager.isVaoEnabled()
            || AngelicaFfpBridge.isSDLGPU()) return false;
        try {
            return AngelicaFfpBridge.supported();
        } catch (LinkageError unavailable) {
            return false;
        }
    }

    /** Checks a complete, caller-owned batch before appending its first vertex. */
    public static Builder liveBuilder(Tessellator tess, int vertices) {
        TessellatorAccessor access = (TessellatorAccessor) tess;
        return new Builder(
            vertices,
            access.miaokatze$getXOffset(),
            access.miaokatze$getYOffset(),
            access.miaokatze$getZOffset());
    }

    /** Uploads complete quads without invoking or recording any Tessellator draw. */
    public static MiaoVisonLiteMesh upload(Frame frame, Builder data, Quads quads) {
        if (frame == null || frame.context != MiaoVisonLiteMaterialBatch.boundContext()
            || data.vertices() == 0
            || (data.vertices() & 3) != 0)
            throw new IllegalArgumentException("A supported frame and complete textured quads are required");
        long started = MiaoVisonLiteMaterialBatch.beginStage();
        VertexFormat format = quads == Quads.LIVE ? frame.materialFormat() : DefaultVertexFormat.POSITION_TEXTURE_COLOR;
        int binding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int elementsBinding = quads == Quads.RECORDED ? GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) : 0;
        ByteBuffer packed = MemoryUtilities.memAlloc(data.vertices() * format.getVertexSize());
        VertexBuffer buffer = null;
        IndexBuffer elements = null;
        IVertexArrayObject array = null;
        try {
            if (quads == Quads.LIVE) data.defaults(frame.packedDefaultNormal(), frame.packedDefaultBrightness());
            format.writeToBuffer(packed, data.raw, data.length);
            packed.flip();
            buffer = new VertexBuffer(format, quads == Quads.LIVE ? GL11.GL_QUADS : GL11.GL_TRIANGLES);
            buffer.allocate(packed, data.vertices());
            if (quads == Quads.LIVE) array = new BaseVAO(buffer);
            else {
                elements = IndexBuffer.convertQuadsToTrigs(data.vertices());
                array = new IndexedVAO(buffer, elements);
            }
            return new MiaoVisonLiteMesh(frame, buffer, array, quads, data);
        } catch (RuntimeException | Error failure) {
            try {
                if (array != null) array.delete();
                else try {
                    if (buffer != null) buffer.delete();
                } finally {
                    if (elements != null) elements.delete();
                }
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            MemoryUtilities.memFree(packed);
            GL30.glBindVertexArray(vao);
            // Creating a GTNHLib index buffer changes the element binding of the currently bound VAO.
            if (quads == Quads.RECORDED) GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementsBinding);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, binding);
            MiaoVisonLiteMaterialBatch.endStage("mesh-upload-ns", started);
        }
    }

    public boolean matches(Frame current) {
        return belongsTo(current) && (quads == Quads.RECORDED || frame.sameDefaults(current));
    }

    public boolean belongsTo(Frame current) {
        return current != null && !closed && frame.context == current.context;
    }

    /** Draws a resident mesh through the live camera, while restoring the caller's client array state. */
    public void draw() {
        requireOwner();
        if (closed) throw new IllegalStateException("Mesh has already been closed");
        if (frame.context != MiaoVisonLiteMaterialBatch.boundContext())
            throw new IllegalStateException("Mesh belongs to another GL context");
        long started = MiaoVisonLiteMaterialBatch.beginStage();
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousTexture = AngelicaFfpBridge.clientTexture();
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            array.bind();
            buffer.setupState();
            if (quads == Quads.LIVE) array.draw();
            else array.draw(GL11.GL_TRIANGLES, 0, buffer.getVertexCount() / 4 * 6);
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL11.glPopClientAttrib();
            GL13.glClientActiveTexture(previousTexture);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            AngelicaFfpBridge.restore();
            MiaoVisonLiteMaterialBatch.endStage("mesh-draw-ns", started);
        }
    }

    /**
     * Compatibility for a viewport that still shares the vanilla Tessellator with native adapters. Only terminal
     * scalar attributes and the empty batch lifecycle are mirrored; the mesh supplies all submitted vertices.
     */
    public boolean drawInEmptyBatch(Tessellator tess, Frame current) {
        if (!matches(current)) return false;
        TessellatorAccessor access = (TessellatorAccessor) tess;
        if (!access.miaokatze$getIsDrawing() || access.miaokatze$getDrawMode() != GL11.GL_QUADS
            || access.miaokatze$getVertexCount() != 0
            || access.miaokatze$getRawBufferIndex() != 0
            || access.miaokatze$getIsColorDisabled()
            || Double.doubleToRawLongBits(x) != Double.doubleToRawLongBits(access.miaokatze$getXOffset())
            || Double.doubleToRawLongBits(y) != Double.doubleToRawLongBits(access.miaokatze$getYOffset())
            || Double.doubleToRawLongBits(z) != Double.doubleToRawLongBits(access.miaokatze$getZOffset())) return false;
        tess.setTextureUV(u, v);
        access.miaokatze$setColor(color);
        access.miaokatze$setHasColor(true);
        tess.draw();
        draw();
        return true;
    }

    @Override
    public void close() {
        requireOwner();
        if (!closed && frame.context == MiaoVisonLiteMaterialBatch.boundContext()) array.delete();
        closed = true;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Mesh belongs to another thread");
    }

    /** CPU-only model-space input in the caller's face and layer order. */
    public static final class Builder {

        private final int[] raw;
        private final double x, y, z;
        private int length, color = -1;
        private double u, v;

        public Builder(int vertices) {
            this(vertices, 0, 0, 0);
        }

        private Builder(int vertices, double x, double y, double z) {
            if (vertices <= 0 || vertices > MAX_VERTICES) throw new IllegalArgumentException("Mesh vertex capacity");
            raw = new int[vertices * 8];
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void color(float red, float green, float blue, float alpha) {
            int r = channel(red), g = channel(green), b = channel(blue), a = channel(alpha);
            color = LITTLE_ENDIAN ? r | g << 8 | b << 16 | a << 24 : r << 24 | g << 16 | b << 8 | a;
        }

        public void vertex(double x, double y, double z, double u, double v) {
            if (length == raw.length) throw new IllegalStateException("Mesh is full");
            raw[length] = bits(x + this.x);
            raw[length + 1] = bits(y + this.y);
            raw[length + 2] = bits(z + this.z);
            raw[length + 3] = bits(u);
            raw[length + 4] = bits(v);
            raw[length + 5] = color;
            this.u = u;
            this.v = v;
            length += 8;
        }

        public int vertices() {
            return length / 8;
        }

        private void defaults(int normal, int brightness) {
            for (int at = 0; at < length; at += 8) {
                raw[at + 6] = normal;
                raw[at + 7] = brightness;
            }
        }

        private static int channel(float value) {
            return Math.max(0, Math.min(255, (int) (value * 255)));
        }

        private static int bits(double value) {
            return Float.floatToRawIntBits((float) value);
        }
    }
}
