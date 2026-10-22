package com.miaokatze.miaokatzecore.client.render.internal;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.renderer.Tessellator;

import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/** Internal Mixin bridge, not a supported consumer API. Does not draw or reset the observed batch. */
public final class TessellatorCapture {

    private static final ThreadLocal<Session> ACTIVE = new ThreadLocal<>();
    private static final AtomicInteger CAPTURES = new AtomicInteger();

    private TessellatorCapture() {}

    public static Session begin(Tessellator tessellator, int startIndex) {
        Session session = new Session(tessellator, startIndex, ACTIVE.get());
        ACTIVE.set(session);
        CAPTURES.incrementAndGet();
        return session;
    }

    /** Invoked at vanilla addVertex HEAD, before any raw slots are written. */
    public static void vertex(Tessellator tessellator) {
        if (CAPTURES.get() == 0) return;
        Session session = ACTIVE.get();
        while (session != null) {
            if (session.tessellator == tessellator) session.record((TessellatorAccessor) tessellator);
            session = session.parent;
        }
    }

    public static void invalidate(Tessellator tessellator) {
        if (CAPTURES.get() == 0) return;
        Session session = ACTIVE.get();
        while (session != null) {
            if (session.tessellator == tessellator) session.valid = false;
            session = session.parent;
        }
    }

    public static final class Session implements AutoCloseable {

        private final Tessellator tessellator;
        private final int startIndex;
        private final Session parent;
        private byte[] masks = new byte[64];
        private int count;
        private boolean valid = true;
        private boolean closed;

        private Session(Tessellator tessellator, int startIndex, Session parent) {
            this.tessellator = tessellator;
            this.startIndex = startIndex;
            this.parent = parent;
        }

        private void record(TessellatorAccessor tess) {
            if (!tess.miaokatze$getIsDrawing() || tess.miaokatze$getDrawMode() != 7
                || tess.miaokatze$getRawBufferIndex() != (long) startIndex + count * 8L) valid = false;
            if (count == masks.length) masks = Arrays.copyOf(masks, masks.length * 2);
            masks[count++] = (byte) ((tess.miaokatze$getHasTexture() ? 1 : 0) | (tess.miaokatze$getHasColor() ? 2 : 0)
                | (tess.miaokatze$getHasBrightness() ? 4 : 0)
                | (tess.miaokatze$getHasNormals() ? 8 : 0));
        }

        public byte[] finish(int vertices) {
            return valid && vertices == count ? Arrays.copyOf(masks, count) : null;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (parent == null) ACTIVE.remove();
            else ACTIVE.set(parent);
            CAPTURES.decrementAndGet();
        }
    }
}
