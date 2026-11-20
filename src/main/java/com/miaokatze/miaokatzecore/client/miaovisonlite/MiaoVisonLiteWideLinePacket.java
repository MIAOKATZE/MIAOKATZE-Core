// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
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

    static MiaoVisonLiteWideLinePacket upload(OutlineFrame frame, int[] raw, int vertices, Staging staging) {
        VertexFormat wide = frame.wideFormat;
        int triangles = vertices / 2 * LineQuads.VERTICES_PER_SEGMENT;
        int arrayBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        IVertexArrayObject buffer = null;
        try {
            ByteBuffer packed = staging.pack(frame.lineFormat, wide, raw, vertices);
            buffer = VAOManager.allocateStorageVAO(wide, GL11.GL_TRIANGLES, packed, triangles, 0);
            return new MiaoVisonLiteWideLinePacket(frame, buffer, wide.getVertexFlags(), triangles);
        } catch (RuntimeException | Error failure) {
            try {
                if (buffer != null) buffer.delete();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBinding);
        }
    }

    /** One render-thread workspace. A storage upload consumes the bytes before the next pack. */
    static final class Staging implements AutoCloseable {

        private ByteBuffer bytes;

        ByteBuffer pack(VertexFormat source, VertexFormat wide, int[] raw, int vertices) {
            int wideBytes = vertices / 2 * LineQuads.VERTICES_PER_SEGMENT * wide.getVertexSize();
            boolean standard = standardLayout(source, wide);
            int needed = standard ? wideBytes : wideBytes + vertices * source.getVertexSize();
            if (bytes == null || bytes.capacity() < needed) {
                ByteBuffer replacement = MemoryUtilities.memAlloc(needed);
                close();
                bytes = replacement;
            }
            long expanded = MemoryUtilities.memAddress0(bytes);
            if (standard) packStandard(raw, vertices, expanded, wide.getVertexSize());
            else {
                long original = expanded + wideBytes;
                source.writeToBuffer0(original, raw, vertices * 8);
                LineQuads.expandWithOtherEnd(original, source, GL11.GL_LINES, vertices, expanded);
            }
            bytes.clear();
            bytes.limit(wideBytes);
            return bytes;
        }

        @Override
        public void close() {
            if (bytes != null) MemoryUtilities.memFree(bytes);
            bytes = null;
        }
    }

    private static boolean standardLayout(VertexFormat source, VertexFormat wide) {
        boolean expanded = source == DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        if (!expanded && source != DefaultVertexFormat.POSITION_COLOR) return false;
        VertexFormatElement[] input = source.elementsArray, output = wide.elementsArray;
        int normal = expanded ? 4 : 2;
        if (output.length != normal + 1 || wide.getVertexSize() != (expanded ? 40 : 28)) return false;
        for (int at = 0; at < normal; at++) if (input[at] != output[at]) return false;
        return output[normal].getUsage() == VertexFormatElement.Usage.NORMAL
            && output[normal].getType() == VertexFormatElement.Type.FLOAT
            && output[normal].getCount() == 3;
    }

    private static void packStandard(int[] raw, int vertices, long output, int stride) {
        // Angelica's vertex shader uses gl_VertexID modulo six. Preserve A A B A B B and the opposite endpoints.
        for (int first = 0; first < vertices * 8; first += 16) {
            long second = output + 2L * stride;
            writeEndpoint(output, raw, first, first + 8, stride == 40);
            writeEndpoint(second, raw, first + 8, first, stride == 40);
            memCopy(output, output + stride, stride);
            memCopy(output, output + 3L * stride, stride);
            memCopy(second, output + 4L * stride, stride);
            memCopy(second, output + 5L * stride, stride);
            output += 6L * stride;
        }
    }

    private static void writeEndpoint(long address, int[] raw, int vertex, int other, boolean expanded) {
        memPutInt(address, raw[vertex]);
        memPutInt(address + 4, raw[vertex + 1]);
        memPutInt(address + 8, raw[vertex + 2]);
        memPutInt(address + 12, raw[vertex + 5]);
        address += 16;
        if (expanded) {
            memPutInt(address, raw[vertex + 3]);
            memPutInt(address + 4, raw[vertex + 4]);
            memPutInt(address + 8, raw[vertex + 7]);
            address += 12;
        }
        memPutInt(address, raw[other]);
        memPutInt(address + 4, raw[other + 1]);
        memPutInt(address + 8, raw[other + 2]);
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
