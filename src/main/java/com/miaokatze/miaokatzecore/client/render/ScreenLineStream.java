package com.miaokatze.miaokatzecore.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.miaokatze.miaokatzecore.client.render.internal.TessellatorCapture;
import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

import cpw.mods.fml.common.Loader;

/**
 * Bounded, owner-thread POSITION_COLOR / GL_LINES uploads for already projected coordinates. Since 0.1.28.
 * Does not transform, sort, deduplicate or retain scene geometry. Never replaces a capture or foreign Tessellator.
 *
 * <p>
 * Call begin before emitting anything. False means the caller must execute its original complete line pass.
 * After true, use color/line/flush and end in a finally block; failures propagate and must never trigger a retry.
 * The caller owns matrices, depth, blend, alpha and line width. Only unlit, untextured fixed-function rendering
 * (including Angelica's logical FFP program zero) is admitted; external shader packs are excluded.
 *
 * <p>
 * Construction is GL-free. The first admitted pass lazily allocates a 261888-byte staging buffer and a reusable
 * GTNHLib VAO/VBO. Close on the same owner thread before its GL context is destroyed. A closed stream cannot reopen.
 */
public final class ScreenLineStream implements AutoCloseable {

    public static final int MAX_VERTICES = 16368;
    public static final int BYTES_PER_VERTEX = 16;

    public enum Rejection {
        NONE,
        FOREIGN_TESSELLATOR,
        MISSING_BRIDGE,
        PENDING_BATCH,
        CAPTURE,
        NOT_RENDER_THREAD,
        UNSUPPORTED_GL,
        SHADER,
        TEXTURED_OR_LIT
    }

    private final Thread owner = Thread.currentThread();
    private ByteBuffer staging;
    private VertexBuffer buffer;
    private IVertexArrayObject array;
    private boolean active, closed, angelica;
    private int savedArray, savedBuffer, savedClientTexture;
    private final double[] offsets = new double[3];
    private int color;
    private Rejection rejection = Rejection.NONE;
    private long passes, batches, vertices;

    /** No GL resource or Minecraft singleton is created here. */
    public ScreenLineStream() {}

    /**
     * Checks all eligibility before any upload/draw, without consuming or modifying the supplied Tessellator.
     * Its existing double translation is added before each final float conversion, exactly like addVertex.
     */
    public boolean begin(Tessellator tessellator) {
        checkOwner();
        if (active) throw new IllegalStateException("A screen line pass is already active");
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

        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL30 || !VAOManager.isVaoEnabled()) return reject(Rejection.UNSUPPORTED_GL);
        angelica = Loader.isModLoaded("angelica");
        if (angelica) {
            if (!AngelicaFfp.supported()) return reject(Rejection.SHADER);
        } else {
            if (caps.OpenGL32
                && (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_CORE_PROFILE_BIT) != 0)
                return reject(Rejection.UNSUPPORTED_GL);
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) return reject(Rejection.SHADER);
        }
        if (GL11.glGetInteger(GL11.GL_LIST_INDEX) != 0) return reject(Rejection.CAPTURE);
        if (GL11.glIsEnabled(GL11.GL_LIGHTING) || (angelica ? AngelicaFfp.textured() : textured()))
            return reject(Rejection.TEXTURED_OR_LIT);

        ScreenLineData.offsets(input, offsets);
        savedArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        savedBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        savedClientTexture = angelica ? AngelicaFfp.clientTexture() : GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        if (staging == null) staging = MemoryUtilities.memAlloc(MAX_VERTICES * BYTES_PER_VERTEX)
            .order(ByteOrder.nativeOrder());
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        active = true;
        rejection = Rejection.NONE;
        passes++;
        staging.clear();
        color = ScreenLineData.color(1, 1, 1, 1, ByteOrder.nativeOrder());
        return true;
    }

    private static boolean textured() {
        int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            int units = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            if (units <= 0) throw new IllegalStateException("Fixed-function texture-unit count is invalid");
            for (int at = 0; at < units; at++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + at);
                if (GL11.glIsEnabled(GL11.GL_TEXTURE_1D) || GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
                    || GL11.glIsEnabled(0x806f)
                    || GL11.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP)) return true;
            }
            return false;
        } finally {
            GL13.glActiveTexture(unit);
        }
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

    /**
     * Appends one complete line, automatically flushing at the fixed bound. Input Z already includes any depth bias.
     */
    public void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        checkActive();
        if (staging.remaining() < 2 * BYTES_PER_VERTEX) flush();
        ScreenLineData.vertex(staging, x1, y1, z1, offsets[0], offsets[1], offsets[2], color);
        ScreenLineData.vertex(staging, x2, y2, z2, offsets[0], offsets[1], offsets[2], color);
    }

    public int remainingVertices() {
        checkActive();
        return staging.remaining() / BYTES_PER_VERTEX;
    }

    /** Explicitly flush before a caller's complete box/other ordered group when desired. Never changes line width. */
    public void flush() {
        checkActive();
        int count = staging.position() / BYTES_PER_VERTEX;
        if (count == 0) return;
        staging.flip();
        try {
            if (buffer == null) {
                buffer = new VertexBuffer(DefaultVertexFormat.POSITION_COLOR, GL11.GL_LINES);
                array = VAOManager.vao(buffer);
            }
            buffer.uploadStream(staging, count);
            array.bind();
            // Setup on every batch also publishes the format's FFP flags through GTNHLib's registered setup hooks.
            buffer.setupState();
            array.draw();
            batches++;
            vertices += count;
        } finally {
            // A failed submission may have drawn already. end() restores state but must not submit these bytes twice.
            staging.clear();
        }
    }

    /** Flushes pending lines, then restores the caller VAO, array-buffer binding and client/FFP attribute state. */
    public void end() {
        checkActive();
        try {
            flush();
        } finally {
            try {
                GL30.glBindVertexArray(savedArray);
                GL11.glPopClientAttrib();
                GL13.glClientActiveTexture(savedClientTexture);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBuffer);
                if (angelica) AngelicaFfp.restore();
            } finally {
                active = false;
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

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Screen lines require their owner thread");
        if (closed) throw new IllegalStateException("Screen line stream is closed");
    }

    private void checkActive() {
        checkOwner();
        if (!active) throw new IllegalStateException("No admitted screen line pass");
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Screen lines require their owner thread");
        if (closed) return;
        try {
            if (active) end();
        } finally {
            try {
                if (array != null) array.delete();
                else if (buffer != null) buffer.delete();
            } finally {
                try {
                    if (staging != null) MemoryUtilities.memFree(staging);
                } finally {
                    array = null;
                    buffer = null;
                    staging = null;
                    closed = true;
                }
            }
        }
    }

    /** Optional symbols remain lazy; compatibility-profile users do not require Angelica. No backend bindings. */
    private static final class AngelicaFfp {

        static boolean supported() {
            return com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled()
                && com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.isEnabled()
                && com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.getInstance()
                    .isActive()
                && com.gtnewhorizons.angelica.glsm.GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0
                && !com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder.isActive();
        }

        static void restore() {
            // Restore the logical FFP format through its own public preparation, never bind a generated program behind
            // GLSM's cache. The physical generated program is not the application's logical CURRENT_PROGRAM zero.
            com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.getInstance()
                .preDraw(com.gtnewhorizons.angelica.glsm.GLStateManager.ctx());
        }

        static int clientTexture() {
            // CLIENT_ACTIVE_TEXTURE is legacy state and is not routed by glGetInteger in the core profile.
            return GL13.GL_TEXTURE0 + com.gtnewhorizons.angelica.glsm.GLStateManager.ctx().clientActiveTextureUnit;
        }

        static boolean textured() {
            // These are the texture targets represented by Angelica 2.2.42's FFP FragmentKey. Querying legacy
            // MAX_TEXTURE_UNITS or TEXTURE_CUBE_MAP via the core-profile driver is not a valid substitute.
            int units = com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_UNITS;
            if (units <= 0) throw new IllegalStateException("Angelica FFP texture-unit count is invalid");
            com.gtnewhorizons.angelica.glsm.states.TextureUnitArray textures = com.gtnewhorizons.angelica.glsm.GLStateManager
                .ctx().textures;
            for (int unit = 0; unit < units; unit++) if (textures.getTexture1DStates(unit)
                .isEnabled()
                || textures.getTextureUnitStates(unit)
                    .isEnabled()
                || textures.getTexture3DStates(unit)
                    .isEnabled())
                return true;
            return false;
        }
    }
}
