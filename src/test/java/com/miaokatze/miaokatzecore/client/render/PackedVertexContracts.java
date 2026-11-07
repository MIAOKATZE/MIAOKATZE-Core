package com.miaokatze.miaokatzecore.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

/** Literal layout, precision, source-boundary and pre-GL lifecycle contracts. */
public final class PackedVertexContracts {

    public static void main(String[] args) throws Exception {
        for (ByteOrder order : new ByteOrder[] { ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN }) {
            ByteBuffer bytes = ByteBuffer.allocate(48)
                .order(order);
            int color = ScreenLineData.color(.2F, .7F, .8F, .45F, order);
            PackedVertexData.texturedVertex(bytes, 16777217D, -0D, 4, .25, -.5, -16777216D, -0D, .5, color);
            require(bytes.position() == 24, "textured stride is 24 bytes");
            require(bytes.getInt(0) == 0x3f800000, "double translation precedes float conversion");
            require(bytes.getInt(4) == 0x80000000, "position preserves negative zero");
            require(bytes.getInt(8) == 0x40900000, "translated z is 4.5");
            require(bytes.getInt(12) == 0x3e800000 && bytes.getInt(16) == 0xbf000000, "UV fields precede color");
            require(
                bytes.get(20) == 0x33 && bytes.get(21) == (byte) 0xb2
                    && bytes.get(22) == (byte) 0xcc
                    && bytes.get(23) == 0x72,
                "literal RGBA bytes retain vanilla truncation across byte orders");
            PackedVertexData.texturedVertex(bytes, 2, 3, 4, .75, 1, 0, 0, 0, color);
            require(bytes.getInt(24) == 0x40000000 && bytes.getInt(36) == 0x3f400000, "source vertex order retained");
        }
        PackedVertexData.requireComplete(0, true);
        PackedVertexData.requireComplete(0, false);
        PackedVertexData.requireComplete(4, true);
        PackedVertexData.requireComplete(PackedVertexStream.MAX_VERTICES, true);
        PackedVertexData.requireComplete(3, false);
        PackedVertexData.requireComplete(7, false);
        fails(() -> PackedVertexData.requireComplete(3, true), "never truncate an incomplete quad");
        fails(() -> PackedVertexData.requireComplete(2, false), "never submit a partial strip");
        fails(() -> PackedVertexData.requireComplete(-1, false), "invalid count rejected");
        require(PackedVertexStream.MAX_VERTICES * 24 == 392832, "bounded maximum native allocation");
        lifecycle();
        System.out.println("Packed vertex CPU contracts passed (no Minecraft initialization or GL).");
    }

    private static void lifecycle() throws Exception {
        PackedVertexStream stream = new PackedVertexStream();
        for (PackedVertexStream.Layout layout : PackedVertexStream.Layout.values()) {
            require(!stream.begin(null, layout), "null/foreign input refuses before optional backend or GL");
            require(
                stream.lastRejection() == PackedVertexStream.Rejection.FOREIGN_TESSELLATOR,
                "refusal is distinct from an admitted empty pass");
        }
        require(
            stream.acceptedPasses() == 0 && stream.submittedVertices() == 0
                && stream.submittedBatches() == 0
                && stream.retainedBytes() == 0,
            "a refused pass never submits or increments counters");
        fails(() -> stream.color(1, 1, 1, 1), "unadmitted color");
        fails(() -> stream.vertex(0, 0, 0), "unadmitted color vertex");
        fails(() -> stream.vertex(0, 0, 0, 0, 0), "unadmitted textured vertex");
        fails(stream::flush, "unadmitted submission");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread wrong = new Thread(() -> {
            try {
                stream.close();
                failure.set(new AssertionError("wrong owner freed native memory"));
            } catch (IllegalStateException expected) {
                // Rejected before native allocation or GL access.
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });
        wrong.start();
        wrong.join();
        if (failure.get() != null) throw new AssertionError("owner-thread cleanup", failure.get());
        stream.close();
        stream.close();
        fails(() -> stream.begin(null, PackedVertexStream.Layout.COLOR_QUADS), "closed stream stays closed");
    }

    private static void fails(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
