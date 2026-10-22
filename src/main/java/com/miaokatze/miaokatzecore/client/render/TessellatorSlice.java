package com.miaokatze.miaokatzecore.client.render;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.client.renderer.Tessellator;

import com.miaokatze.miaokatzecore.client.render.internal.TessellatorCapture;
import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/**
 * An immutable CPU copy of vertices appended to an existing vanilla GL_QUADS batch. Never draws, resets, or owns a GL
 * resource. Callers must exclude renderer extensions that keep additional per-vertex state.
 *
 * <p>
 * The callback must only append vertices and update vanilla vertex attributes: it must not draw, restart the batch,
 * edit existing vertices, inspect batch counts, or perform other side effects that need repeating on replay.
 */
public final class TessellatorSlice {

    private final int[] vertices;
    private final byte[] masks;
    private final State input;
    private final State output;

    private TessellatorSlice(int[] vertices, byte[] masks, State input, State output) {
        this.vertices = vertices;
        this.masks = masks;
        this.input = input;
        this.output = output;
    }

    /**
     * Executes commands exactly once in the existing batch, returning null if the batch or emitted layout is
     * unsupported.
     * Callback exceptions propagate without retry or rollback.
     */
    public static TessellatorSlice capture(Tessellator tessellator, Runnable commands) {
        Objects.requireNonNull(tessellator, "tessellator");
        Objects.requireNonNull(commands, "commands");
        TessellatorAccessor tess = supported(tessellator);
        if (tess == null) {
            commands.run();
            return null;
        }
        State input = new State(tess);
        int start = tess.miaokatze$getRawBufferIndex();
        int count = tess.miaokatze$getVertexCount();
        int added = tess.miaokatze$getAddedVertices();
        TessellatorCapture.Session session = TessellatorCapture.begin(tessellator, start);
        try {
            commands.run();
        } finally {
            session.close();
        }
        if (supported(tessellator) == null) return null;
        int vertexDelta = tess.miaokatze$getVertexCount() - count;
        int rawDelta = tess.miaokatze$getRawBufferIndex() - start;
        int addedDelta = tess.miaokatze$getAddedVertices() - added;
        if (!TessellatorSliceData.validDeltas(rawDelta, vertexDelta, addedDelta)) return null;
        byte[] masks = session.finish(vertexDelta);
        if (masks == null) return null;
        int[] raw = tess.miaokatze$getRawBuffer();
        return new TessellatorSlice(
            rawDelta == 0 ? new int[0] : Arrays.copyOfRange(raw, start, start + rawDelta),
            masks,
            input,
            new State(tess));
    }

    /** Appends into a matching input state; returns false without modifying an unsupported or mismatched batch. */
    public boolean replay(Tessellator tessellator) {
        TessellatorAccessor tess = supported(tessellator);
        if (tess == null || !input.matches(tess)) return false;
        int start = tess.miaokatze$getRawBufferIndex();
        long end = (long) start + vertices.length;
        long count = (long) tess.miaokatze$getVertexCount() + masks.length;
        long added = (long) tess.miaokatze$getAddedVertices() + masks.length;
        if (end > Integer.MAX_VALUE - 32 || count > Integer.MAX_VALUE || added > Integer.MAX_VALUE) return false;

        int[] raw = tess.miaokatze$getRawBuffer();
        int capacity = tess.miaokatze$getRawBufferSize();
        if (vertices.length > 0 && end > capacity - 32L) {
            int grown = capacity == 0 ? 0x10000 : capacity;
            while (end > grown - 32L) {
                if (grown > (Integer.MAX_VALUE - 8) / 2) {
                    grown = (int) end + 32;
                    break;
                }
                grown *= 2;
            }
            raw = raw == null ? new int[grown] : Arrays.copyOf(raw, grown);
            tess.miaokatze$setRawBuffer(raw);
            tess.miaokatze$setRawBufferSize(grown);
        }
        TessellatorSliceData.copy(vertices, masks, raw, start);
        tess.miaokatze$setRawBufferIndex((int) end);
        tess.miaokatze$setVertexCount((int) count);
        tess.miaokatze$setAddedVertices((int) added);
        output.apply(tess);
        return true;
    }

    /** Number of copied raw ints; also retains one write-mask byte per vertex and two scalar snapshots. */
    public int retainedInts() {
        return vertices.length;
    }

    private static TessellatorAccessor supported(Tessellator tessellator) {
        if (tessellator == null || tessellator.getClass() != Tessellator.class
            || !(tessellator instanceof TessellatorAccessor)) return null;
        TessellatorAccessor tess = (TessellatorAccessor) tessellator;
        int index = tess.miaokatze$getRawBufferIndex();
        int count = tess.miaokatze$getVertexCount();
        int capacity = tess.miaokatze$getRawBufferSize();
        int[] raw = tess.miaokatze$getRawBuffer();
        if (!tess.miaokatze$getIsDrawing() || tess.miaokatze$getDrawMode() != 7
            || index < 0
            || count < 0
            || tess.miaokatze$getAddedVertices() < 0
            || index != count * 8L
            || capacity < index
            || (raw == null ? capacity != 0 : raw.length != capacity)) return null;
        return tess;
    }

    static final class State {

        private final double textureU;
        private final double textureV;
        private final int brightness;
        private final int color;
        private final int normal;
        private final boolean hasTexture;
        private final boolean hasColor;
        private final boolean hasBrightness;
        private final boolean hasNormals;
        private final boolean isColorDisabled;
        private final double xOffset;
        private final double yOffset;
        private final double zOffset;
        private final int drawMode;
        private final boolean isDrawing;

        State(TessellatorAccessor tess) {
            textureU = tess.miaokatze$getTextureU();
            textureV = tess.miaokatze$getTextureV();
            brightness = tess.miaokatze$getBrightness();
            color = tess.miaokatze$getColor();
            normal = tess.miaokatze$getNormal();
            hasTexture = tess.miaokatze$getHasTexture();
            hasColor = tess.miaokatze$getHasColor();
            hasBrightness = tess.miaokatze$getHasBrightness();
            hasNormals = tess.miaokatze$getHasNormals();
            isColorDisabled = tess.miaokatze$getIsColorDisabled();
            xOffset = tess.miaokatze$getXOffset();
            yOffset = tess.miaokatze$getYOffset();
            zOffset = tess.miaokatze$getZOffset();
            drawMode = tess.miaokatze$getDrawMode();
            isDrawing = tess.miaokatze$getIsDrawing();
        }

        boolean matches(TessellatorAccessor tess) {
            return Double.doubleToRawLongBits(textureU) == Double.doubleToRawLongBits(tess.miaokatze$getTextureU())
                && Double.doubleToRawLongBits(textureV) == Double.doubleToRawLongBits(tess.miaokatze$getTextureV())
                && brightness == tess.miaokatze$getBrightness()
                && color == tess.miaokatze$getColor()
                && normal == tess.miaokatze$getNormal()
                && hasTexture == tess.miaokatze$getHasTexture()
                && hasColor == tess.miaokatze$getHasColor()
                && hasBrightness == tess.miaokatze$getHasBrightness()
                && hasNormals == tess.miaokatze$getHasNormals()
                && isColorDisabled == tess.miaokatze$getIsColorDisabled()
                && Double.doubleToRawLongBits(xOffset) == Double.doubleToRawLongBits(tess.miaokatze$getXOffset())
                && Double.doubleToRawLongBits(yOffset) == Double.doubleToRawLongBits(tess.miaokatze$getYOffset())
                && Double.doubleToRawLongBits(zOffset) == Double.doubleToRawLongBits(tess.miaokatze$getZOffset())
                && drawMode == tess.miaokatze$getDrawMode()
                && isDrawing == tess.miaokatze$getIsDrawing();
        }

        void apply(TessellatorAccessor tess) {
            tess.miaokatze$setTextureU(textureU);
            tess.miaokatze$setTextureV(textureV);
            tess.miaokatze$setBrightness(brightness);
            tess.miaokatze$setColor(color);
            tess.miaokatze$setNormal(normal);
            tess.miaokatze$setHasTexture(hasTexture);
            tess.miaokatze$setHasColor(hasColor);
            tess.miaokatze$setHasBrightness(hasBrightness);
            tess.miaokatze$setHasNormals(hasNormals);
            tess.miaokatze$setIsColorDisabled(isColorDisabled);
            tess.miaokatze$setXOffset(xOffset);
            tess.miaokatze$setYOffset(yOffset);
            tess.miaokatze$setZOffset(zOffset);
            tess.miaokatze$setDrawMode(drawMode);
            tess.miaokatze$setIsDrawing(isDrawing);
        }
    }

}
