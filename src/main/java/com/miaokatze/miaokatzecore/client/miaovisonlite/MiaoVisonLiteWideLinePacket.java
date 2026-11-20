// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;
import com.miaokatze.miaokatzecore.client.miaovisonlite.MiaoVisonLiteMaterialBatch.OutlineFrame;
import com.miaokatze.miaokatzecore.client.render.internal.AngelicaFfpBridge;

/** The expanded triangle buffer used by Angelica's native fixed-function wide-line shader. */
final class MiaoVisonLiteWideLinePacket implements AutoCloseable {

    private final OutlineFrame frame;
    private final IVertexArrayObject array;
    private final int flags, triangles;

    private MiaoVisonLiteWideLinePacket(OutlineFrame frame, IVertexArrayObject array, int flags, int triangles) {
        this.frame = frame;
        this.array = array;
        this.flags = flags;
        this.triangles = triangles;
    }

    static MiaoVisonLiteWideLinePacket upload(OutlineFrame frame, int[] raw, int vertices) {
        VertexFormat source = frame.lineFormat;
        VertexFormat wide = frame.wideFormat;
        int triangles = vertices / 2 * LineQuads.VERTICES_PER_SEGMENT;
        int wideBytes = triangles * wide.getVertexSize();
        int arrayBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        ByteBuffer staging = MemoryUtilities.memAlloc(wideBytes + vertices * source.getVertexSize());
        IVertexArrayObject buffer = null;
        try {
            long expanded = MemoryUtilities.memAddress0(staging);
            long original = expanded + wideBytes;
            source.writeToBuffer0(original, raw, vertices * 8);
            LineQuads.expandWithOtherEnd(original, source, GL11.GL_LINES, vertices, expanded);
            staging.limit(wideBytes);
            buffer = VAOManager.allocateStorageVAO(wide, GL11.GL_TRIANGLES, staging, triangles, 0);
            return new MiaoVisonLiteWideLinePacket(frame, buffer, wide.getVertexFlags(), triangles);
        } catch (RuntimeException | Error failure) {
            try {
                if (buffer != null) buffer.delete();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            MemoryUtilities.memFree(staging);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBinding);
        }
    }

    void draw() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousTexture = AngelicaFfpBridge.clientTexture();
        try {
            array.bind();
            boolean culled = LineQuads.beginDraw(flags);
            FfpExtendedAttribs.beginInternalDraw();
            try {
                array.draw(GL11.GL_TRIANGLES, 0, triangles);
            } finally {
                FfpExtendedAttribs.endInternalDraw();
                LineQuads.endDraw(culled);
            }
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL13.glClientActiveTexture(previousTexture);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    @Override
    public void close() {
        if (frame.currentContext()) array.delete();
    }
}
