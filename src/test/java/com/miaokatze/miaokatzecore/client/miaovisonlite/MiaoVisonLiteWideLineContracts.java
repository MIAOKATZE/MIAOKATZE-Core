// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;

/** Compares the resident upload bytes with Angelica's captured GL_LINES expansion. */
public final class MiaoVisonLiteWideLineContracts {

    private MiaoVisonLiteWideLineContracts() {}

    public static void main(String[] ignored) {
        int[] raw = new int[64];
        for (int at = 0; at < raw.length; at++) raw[at] = Float.floatToRawIntBits((at - 28) * .125f);
        raw[0] = Float.floatToRawIntBits(-0.0f);
        raw[5] = 0xdeadbeef;
        raw[7] = 0x01020304;
        try (MiaoVisonLiteWideLinePacket.Staging staging = new MiaoVisonLiteWideLinePacket.Staging()) {
            compare(staging, DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, raw, 8);
            compare(staging, DefaultVertexFormat.POSITION_COLOR, raw, 4);
            compare(staging, DefaultVertexFormat.POSITION_COLOR_LIGHT, raw, 6);
        }
        System.out.println("MiaoVisonLiteWideLineContracts PASS (full, color-only and fallback byte parity)");
    }

    private static void compare(MiaoVisonLiteWideLinePacket.Staging staging, VertexFormat source, int[] raw,
        int vertices) {
        VertexFormat wide = LineQuads.quadFormat(source);
        int expanded = vertices / 2 * LineQuads.VERTICES_PER_SEGMENT * wide.getVertexSize();
        ByteBuffer reference = ByteBuffer.allocateDirect(expanded + vertices * source.getVertexSize());
        long address = MemoryUtilities.memAddress0(reference);
        source.writeToBuffer0(address + expanded, raw, vertices * 8);
        LineQuads.expandWithOtherEnd(address + expanded, source, GL11.GL_LINES, vertices, address);
        reference.limit(expanded);
        ByteBuffer actual = staging.pack(source, wide, raw, vertices);
        if (!actual.equals(reference)) throw new AssertionError("Packed wide-line bytes differ: " + source);
    }
}
