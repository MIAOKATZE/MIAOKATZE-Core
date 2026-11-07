package com.miaokatze.miaokatzecore.client.render;

import java.nio.ByteBuffer;

/** CPU-only encoding for Angelica's POSITION_TEXTURE_COLOR streaming format. */
final class PackedVertexData {

    private PackedVertexData() {}

    static void texturedVertex(ByteBuffer target, double x, double y, double z, double u, double v, double offsetX,
        double offsetY, double offsetZ, int color) {
        target.putFloat((float) (x + offsetX));
        target.putFloat((float) (y + offsetY));
        target.putFloat((float) (z + offsetZ));
        target.putFloat((float) u);
        target.putFloat((float) v);
        target.putInt(color);
    }

    static void requireComplete(int count, boolean quads) {
        if (count < 0 || (count != 0 && (quads ? count % 4 != 0 : count < 3)))
            throw new IllegalStateException("Packed batches require complete quads or a complete triangle strip");
    }
}
