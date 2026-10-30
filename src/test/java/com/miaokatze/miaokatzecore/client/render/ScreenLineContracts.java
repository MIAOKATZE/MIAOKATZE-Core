package com.miaokatze.miaokatzecore.client.render;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/** Independent byte oracles and owner/rejection contracts. Does not create Minecraft, a GL context or a GPU buffer. */
public final class ScreenLineContracts {

    public static void main(String[] args) throws Exception {
        packedBytes();
        translationOrder();
        inputIsReadOnly();
        lifecycle();
        require(ScreenLineData.idle(false, 0, 0, 0), "idle input");
        require(!ScreenLineData.idle(true, 0, 0, 0), "active batch rejected");
        require(!ScreenLineData.idle(false, 1, 0, 0), "pending vertices rejected");
        require(!ScreenLineData.idle(false, 0, 8, 0), "pending raw words rejected");
        require(!ScreenLineData.idle(false, 0, 0, 1), "pending converted vertices rejected");
        require(!ScreenLineData.idle(false, -1, -8, -1), "invalid counts rejected");
        require(ScreenLineStream.MAX_VERTICES % 24 == 0, "whole box and pair boundary");
        require(ScreenLineStream.MAX_VERTICES * ScreenLineStream.BYTES_PER_VERTEX == 261888, "fixed staging capacity");
        System.out.println("Screen line CPU contracts passed (no Minecraft initialization or GL).");
    }

    private static void packedBytes() {
        byte[] little = { 0, 0, 0x40, 0x3f, 0, 0, (byte) 0xe0, (byte) 0xbf, 0, 0, (byte) 0x90, 0x40, 0x33, (byte) 0xb2,
            (byte) 0xcc, 0x72 };
        byte[] big = { 0x3f, 0x40, 0, 0, (byte) 0xbf, (byte) 0xe0, 0, 0, 0x40, (byte) 0x90, 0, 0, 0x33, (byte) 0xb2,
            (byte) 0xcc, 0x72 };
        for (ByteOrder order : new ByteOrder[] { ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN }) {
            ByteBuffer bytes = ByteBuffer.allocate(32)
                .order(order);
            int color = ScreenLineData.color(.2F, .7F, .8F, .45F, order);
            ScreenLineData.vertex(bytes, 1.25, -2, 4, -.5, .25, .5, color);
            require(
                Arrays.equals(Arrays.copyOf(bytes.array(), 16), order == ByteOrder.LITTLE_ENDIAN ? little : big),
                "literal IEEE754 positions and vanilla RGBA truncation bytes");
            color = ScreenLineData.color(-1, 2, Float.NaN, Float.POSITIVE_INFINITY, order);
            ScreenLineData.vertex(bytes, 0, 0, 0, 0, 0, 0, color);
            require(bytes.position() == 32, "two endpoints remain adjacent and ordered");
            require(
                bytes.get(28) == 0 && bytes.get(29) == (byte) 255 && bytes.get(30) == 0 && bytes.get(31) == (byte) 255,
                "literal clamp and non-finite channel oracle");
        }
    }

    private static void translationOrder() {
        ByteBuffer bytes = ByteBuffer.allocate(16)
            .order(ByteOrder.nativeOrder());
        ScreenLineData.vertex(bytes, 16777217D, -0D, 1e16 + .15D, -16777216D, -0D, -1e16, 0);
        require(bytes.getInt(0) == 0x3f800000, "add double offset before casting: exact one, not zero");
        require(bytes.getInt(4) == 0x80000000, "negative zero is preserved");
        require(bytes.getInt(8) == 0, "caller depth bias precedes offset and final float conversion");
    }

    private static void inputIsReadOnly() {
        final double[] original = { .125, -7.25, 123456789.5 };
        TessellatorAccessor input = (TessellatorAccessor) Proxy.newProxyInstance(
            TessellatorAccessor.class.getClassLoader(),
            new Class<?>[] { TessellatorAccessor.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("miaokatze$getXOffset")) return original[0];
                if (method.getName()
                    .equals("miaokatze$getYOffset")) return original[1];
                if (method.getName()
                    .equals("miaokatze$getZOffset")) return original[2];
                throw new AssertionError("Screen lines must not write/reset the Tessellator: " + method.getName());
            });
        double[] offsets = new double[3];
        ScreenLineData.offsets(input, offsets);
        ByteBuffer bytes = ByteBuffer.allocate(16)
            .order(ByteOrder.nativeOrder());
        ScreenLineData.vertex(
            bytes,
            0,
            0,
            0,
            offsets[0],
            offsets[1],
            offsets[2],
            ScreenLineData.color(1, 1, 1, 1, ByteOrder.nativeOrder()));
        require(Arrays.equals(offsets, original), "only existing double offsets are read");
        require(input.miaokatze$getXOffset() == original[0], "packing leaves the source untouched");
    }

    private static void lifecycle() throws Exception {
        final ScreenLineStream stream = new ScreenLineStream();
        require(!stream.begin(null), "foreign/null input refuses before GL");
        require(
            stream.lastRejection() == ScreenLineStream.Rejection.FOREIGN_TESSELLATOR,
            "rejection is observable, not a successful empty fast pass");
        require(
            stream.acceptedPasses() == 0 && stream.submittedVertices() == 0 && stream.submittedBatches() == 0,
            "refusal cannot submit or increment fast counters");
        fails(() -> stream.color(1, 1, 1, 1), "unadmitted color");
        fails(stream::flush, "unadmitted flush");
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread wrong = new Thread(() -> {
            try {
                stream.close();
                failure.set(new AssertionError("wrong owner close succeeded"));
            } catch (IllegalStateException expected) {
                // Must fail before any GL/resource action.
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });
        wrong.start();
        wrong.join();
        if (failure.get() != null) throw new AssertionError("owner close", failure.get());
        stream.close();
        stream.close();
        fails(() -> stream.begin(null), "closed stream cannot reopen");
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
