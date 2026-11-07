package com.miaokatze.miaokatzecore.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.miaokatze.miaokatzecore.client.render.internal.TessellatorCapture;
import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

import cpw.mods.fml.common.Loader;

/**
 * Bounded packed vertices submitted through Angelica's public streaming drawer. Construction is GL-free.
 *
 * <p>
 * Call begin before emitting a complete pass. False requires the caller's original complete pass. Once begin
 * returns true, exceptions propagate: never retry that pass through Tessellator. Always call end in a finally
 * block. The stream never writes to, starts, resets or draws the supplied Tessellator.
 *
 * <p>
 * The caller owns matrices, textures, depth, blend, alpha and all other server rendering state. Only Angelica's
 * logical fixed-function rendering without external shader packs, lighting or extended vertex attributes is
 * admitted. The stream restores the VAO, array buffer, client texture and client/FFP attribute state.
 *
 * <p>
 * At most 392832 native staging bytes are lazily allocated and reused. There is no automatic flushing: call flush
 * at complete source boundaries and use remainingVertices before emitting each source. A strip must be flushed
 * separately from the next strip; strips are never linked or split automatically. Close on the construction
 * thread before its GL context is destroyed. A closed stream cannot reopen.
 */
public final class PackedVertexStream implements AutoCloseable {

    public static final int MAX_VERTICES = 16368;

    public enum Layout {

        TEXTURED_QUADS(GL11.GL_QUADS, 3, 24, true),
        COLOR_QUADS(GL11.GL_QUADS, 2, 16, false),
        COLOR_TRIANGLE_STRIP(GL11.GL_TRIANGLE_STRIP, 2, 16, false);

        private final int mode, flags, stride;
        private final boolean textured;

        Layout(int mode, int flags, int stride, boolean textured) {
            this.mode = mode;
            this.flags = flags;
            this.stride = stride;
            this.textured = textured;
        }
    }

    public enum Rejection {
        NONE,
        FOREIGN_TESSELLATOR,
        MISSING_BRIDGE,
        PENDING_BATCH,
        CAPTURE,
        NOT_RENDER_THREAD,
        UNSUPPORTED_BACKEND,
        SHADER_OR_EXTENDED_FORMAT,
        TEXTURE_OR_LIGHTING
    }

    private final Thread owner = Thread.currentThread();
    private final double[] offsets = new double[3];
    private ByteBuffer staging;
    private Layout layout;
    private boolean active, failed, closed;
    private int color, savedArray, savedBuffer, savedClientTexture;
    private Rejection rejection = Rejection.NONE;
    private long passes, batches, vertices;

    public PackedVertexStream() {}

    /** All refusals precede allocation, client-state changes and vertex submission. */
    public boolean begin(Tessellator tessellator, Layout requestedLayout) {
        checkOwner();
        if (active) throw new IllegalStateException("A packed vertex pass is already active");
        if (requestedLayout == null) throw new IllegalArgumentException("A packed vertex layout is required");
        if (tessellator == null || tessellator.getClass() != Tessellator.class)
            return reject(Rejection.FOREIGN_TESSELLATOR);
        if (!TessellatorManager.isOnMainThread()) return reject(Rejection.NOT_RENDER_THREAD);
        if (!(tessellator instanceof TessellatorAccessor)) return reject(Rejection.MISSING_BRIDGE);
        TessellatorAccessor input = (TessellatorAccessor) tessellator;
        if (!ScreenLineData.idle(
            input.miaokatze$getIsDrawing(),
            input.miaokatze$getVertexCount(),
            input.miaokatze$getRawBufferIndex(),
            input.miaokatze$getAddedVertices())) return reject(Rejection.PENDING_BATCH);
        if (TessellatorManager.get() != tessellator || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.getDirectCaptureDepth() != 0
            || TessellatorCapture.active(tessellator)) return reject(Rejection.CAPTURE);
        if (!Loader.isModLoaded("angelica")) return reject(Rejection.UNSUPPORTED_BACKEND);
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL30 || !VAOManager.isVaoEnabled() || PackedDrawer.DRAW == null)
            return reject(Rejection.UNSUPPORTED_BACKEND);
        try {
            if (!ScreenLineStream.AngelicaFfp.supported() || !PackedFfp.standardFormat())
                return reject(Rejection.SHADER_OR_EXTENDED_FORMAT);
            if (GL11.glIsEnabled(GL11.GL_LIGHTING) || !PackedFfp.textureMatches(requestedLayout.textured))
                return reject(Rejection.TEXTURE_OR_LIGHTING);
        } catch (LinkageError unavailable) {
            return reject(Rejection.UNSUPPORTED_BACKEND);
        }
        if (GL11.glGetInteger(GL11.GL_LIST_INDEX) != 0) return reject(Rejection.CAPTURE);

        ScreenLineData.offsets(input, offsets);
        savedArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        savedBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        savedClientTexture = ScreenLineStream.AngelicaFfp.clientTexture();
        if (staging == null) staging = MemoryUtilities.memAlloc(MAX_VERTICES * 24)
            .order(ByteOrder.nativeOrder());
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        layout = requestedLayout;
        staging.clear();
        staging.limit(MAX_VERTICES * layout.stride);
        color = ScreenLineData.color(1, 1, 1, 1, ByteOrder.nativeOrder());
        failed = false;
        active = true;
        rejection = Rejection.NONE;
        passes++;
        return true;
    }

    private boolean reject(Rejection reason) {
        rejection = reason;
        return false;
    }

    /** Same multiply, truncation and channel clamping as vanilla setColorRGBA_F. */
    public void color(float red, float green, float blue, float alpha) {
        checkActive();
        color = ScreenLineData.color(red, green, blue, alpha, ByteOrder.nativeOrder());
    }

    /** Adds the Tessellator's double translation before the final position float conversion. */
    public void vertex(double x, double y, double z, double u, double v) {
        checkActive();
        if (!layout.textured) throw fail("This packed layout has no texture coordinates");
        requireSpace();
        PackedVertexData.texturedVertex(staging, x, y, z, u, v, offsets[0], offsets[1], offsets[2], color);
    }

    public void vertex(double x, double y, double z) {
        checkActive();
        if (layout.textured) throw fail("This packed layout requires texture coordinates");
        requireSpace();
        ScreenLineData.vertex(staging, x, y, z, offsets[0], offsets[1], offsets[2], color);
    }

    private void requireSpace() {
        if (staging.remaining() < layout.stride) throw fail("Flush at a complete source boundary before capacity");
    }

    private IllegalStateException fail(String message) {
        failed = true;
        staging.position(0);
        return new IllegalStateException(message);
    }

    public int remainingVertices() {
        checkActive();
        return staging.remaining() / layout.stride;
    }

    /** Submits complete quads or one complete strip, preserving vertex order. Empty flushes do nothing. */
    public void flush() {
        checkActive();
        int count = staging.position() / layout.stride;
        try {
            PackedVertexData.requireComplete(count, layout.mode == GL11.GL_QUADS);
            if (count == 0) return;
            staging.flip();
            PackedDrawer.draw(staging, layout.mode, layout.flags, count);
            batches++;
            vertices += count;
        } catch (RuntimeException | Error failure) {
            failed = true;
            throw failure;
        } finally {
            // Submission can fail after drawing. Never allow end() to retry any bytes from this batch.
            staging.clear();
            staging.limit(MAX_VERTICES * layout.stride);
        }
    }

    /** Flushes a healthy pass, then restores client and logical FFP state even after submission failure. */
    public void end() {
        checkOwner();
        if (!active) throw new IllegalStateException("No admitted packed vertex pass");
        try {
            if (!failed) flush();
        } finally {
            try {
                restore();
            } finally {
                active = false;
                layout = null;
            }
        }
    }

    private void restore() {
        try {
            GL30.glBindVertexArray(savedArray);
        } finally {
            try {
                GL11.glPopClientAttrib();
            } finally {
                try {
                    GL13.glClientActiveTexture(savedClientTexture);
                } finally {
                    try {
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBuffer);
                    } finally {
                        ScreenLineStream.AngelicaFfp.restore();
                    }
                }
            }
        }
    }

    public Rejection lastRejection() {
        return rejection;
    }

    public long acceptedPasses() {
        return passes;
    }

    public long submittedBatches() {
        return batches;
    }

    public long submittedVertices() {
        return vertices;
    }

    /** Retained native staging bytes; zero before the first admitted pass and after close. */
    public int retainedBytes() {
        return staging == null ? 0 : staging.capacity();
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Packed vertices require their owner thread");
        if (closed) throw new IllegalStateException("Packed vertex stream is closed");
    }

    private void checkActive() {
        checkOwner();
        if (!active) throw new IllegalStateException("No admitted packed vertex pass");
        if (failed) throw new IllegalStateException("Packed vertex pass failed; end it without retrying");
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Packed vertices require their owner thread");
        if (closed) return;
        try {
            if (active) end();
        } finally {
            try {
                if (staging != null) MemoryUtilities.memFree(staging);
            } finally {
                staging = null;
                closed = true;
            }
        }
    }

    /** Runtime lookup admits only the actual public API, without a hard dependency on its MR-JAR class. */
    private static final class PackedDrawer {

        private static final MethodHandle DRAW = find();

        private static MethodHandle find() {
            try {
                Class<?> type = Class.forName(
                    "com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer",
                    false,
                    PackedVertexStream.class.getClassLoader());
                return MethodHandles.publicLookup()
                    .findStatic(
                        type,
                        "drawPacked",
                        MethodType.methodType(void.class, ByteBuffer.class, int.class, int.class, int.class));
            } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
                return null;
            }
        }

        private static void draw(ByteBuffer data, int mode, int flags, int count) {
            try {
                DRAW.invokeExact(data, mode, flags, count);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Angelica packed submission failed; do not retry this pass", failure);
            }
        }
    }

    private static final class PackedFfp {

        private static boolean standardFormat() {
            ImmediateExtendedAttribHandler handler = GLSMHooks.immediateExtendedHandler;
            return handler == null || (!handler.wantsExtended() && !handler.wantsTangent()
                && !handler.wantsEntityIds()
                && !handler.wantsExtendedCapture()
                && !handler.wantsFaceNormals());
        }

        private static boolean textureMatches(boolean textured) {
            com.gtnewhorizons.angelica.glsm.states.TextureUnitArray textures = com.gtnewhorizons.angelica.glsm.GLStateManager
                .ctx().textures;
            return !textures.getTexture1DStates(0)
                .isEnabled()
                && !textures.getTexture3DStates(0)
                    .isEnabled()
                && !textures.getTextureCubeMapStates(0)
                    .isEnabled()
                && textures.getTextureUnitStates(0)
                    .isEnabled() == textured;
        }
    }
}
