package com.miaokatze.miaokatzecore.client.animation.render;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

import com.miaokatze.miaokatzecore.api.animation.VisualFrame;
import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;
import com.miaokatze.miaokatzecore.client.miaovision.SceneViewport;
import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;
import com.miaokatze.miaokatzecore.client.render.SolidMesh;
import com.miaokatze.miaokatzecore.client.render.internal.AngelicaFfpBridge;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Explicit owner-thread, unlit unit-box presentation to the caller's current single-color/depth target. The same entry
 * serves a screen viewport or an already-bound capture target. Owns no World, framebuffer, texture, clock or listener.
 * Uses the existing preview state boundary and ordinary intercepted GL entrypoints, never a raw-driver bypass.
 */
@SideOnly(Side.CLIENT)
public final class SolidBoxRenderer implements AutoCloseable {

    private final Thread owner = Thread.currentThread();
    private final DoubleBuffer transform = BufferUtils.createDoubleBuffer(16);
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private boolean rendering, closed;

    /** Construction allocates only small CPU buffers; the owning thread must have its GL context current at render. */
    public SolidBoxRenderer() {}

    /**
     * Reconciles the optional Angelica derived fixed-function program after a caller-owned render scope. Call on the
     * render thread with its GL context current, after restoring raw GL attributes, matrices and program. This does not
     * restore raw state and is a no-op when the supported Angelica fixed-function path is not active.
     */
    public static void restoreFixedFunctionState() {
        if (Loader.isModLoaded("angelica") && AngelicaFfpBridge.supported()) AngelicaFfpBridge.restore();
    }

    /**
     * Clears depth inside the intersection of viewport and enclosing scissor, never color. The caller owns target size,
     * background, MSAA, readback and alpha conversion. Input must be between Tessellator batches with logical FFP
     * program
     * zero. One failed draw aborts the pass and is never retried. Intersecting translucent boxes are not
     * order-independent.
     */
    public void render(VisualFrame frame, SceneViewport viewport, CameraPose camera,
        OrthographicProjection projection) {
        checkOwner();
        if (closed) throw new IllegalStateException("renderer closed");
        if (rendering) throw new IllegalStateException("reentrant solid-box render");
        if (frame == null || viewport == null || camera == null || projection == null)
            throw new IllegalArgumentException("render arguments");
        List<Draw> ordered = prepare(frame, camera, projection);
        boolean angelica = requireTarget();
        PreviewRenderScope.ClientTessState tess;
        try {
            tess = new PreviewRenderScope.ClientTessState(Tessellator.instance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot isolate tessellator", failure);
        }
        PreviewRenderScope.RenderState state = new PreviewRenderScope.RenderState();
        rendering = true;
        Throwable failure = null;
        try {
            state.push();
            if (!clip(viewport)) return;
            state.useProgram(0);
            state.cleanTextureUnits();
            GL11.glViewport(viewport.x, viewport.y, viewport.width, viewport.height);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthRange(0, 1);
            GL11.glClearDepth(1);
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
            GL11.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
            for (int plane = 0; plane < 6; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(
                -projection.halfWidth(),
                projection.halfWidth(),
                -projection.halfHeight(),
                projection.halfHeight(),
                projection.near(),
                projection.far());
            Tessellator.instance.setTranslation(0, 0, 0);
            for (Draw draw : ordered) {
                VisualFrame.TargetPose pose = draw.pose;
                boolean opaque = pose.opacity == 1;
                if (opaque) GL11.glDisable(GL11.GL_BLEND);
                else GL11.glEnable(GL11.GL_BLEND);
                GL11.glDepthMask(opaque);
                // Reload the view instead of borrowing another matrix-stack level for every body.
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                GL11.glTranslated(0, 0, -projection.depthOffset());
                GL11.glRotated(camera.pitchDegrees() % 360, 1, 0, 0);
                GL11.glRotated(camera.yawDegrees() % 360, 0, 1, 0);
                GL11.glTranslated(-camera.anchorX(), -camera.anchorY(), -camera.anchorZ());
                transform.clear();
                transform.put(pose.matrix());
                transform.flip();
                GL11.glMultMatrix(transform);
                SolidMesh.box(-.5, -.5, -.5, .5, .5, .5, pose.rgb, (float) pose.opacity);
            }
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            try {
                restore(tess, state, angelica, failure);
            } finally {
                rendering = false;
            }
        }
    }

    private static List<Draw> prepare(VisualFrame frame, CameraPose camera, OrthographicProjection projection) {
        CameraBasis basis = new CameraBasis(camera);
        List<Draw> opaque = new ArrayList<>(), translucent = new ArrayList<>();
        for (VisualFrame.TargetPose pose : frame.targets) {
            if (!pose.visible) continue;
            double depth = basis.project(projection, pose.x, pose.y, pose.z)
                .ndcZ();
            Draw draw = new Draw(pose, depth);
            (pose.opacity == 1 ? opaque : translucent).add(draw);
        }
        // Greater projected Z is farther. Stable sorting retains target-ID order at equal depth.
        translucent.sort(
            Comparator.comparingDouble((Draw draw) -> draw.depth)
                .reversed());
        opaque.addAll(translucent);
        return opaque;
    }

    private static boolean requireTarget() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL14) throw new IllegalStateException("OpenGL 1.4 blend support required");
        boolean angelica = Loader.isModLoaded("angelica");
        if (angelica) {
            if (!AngelicaFfpBridge.supported())
                throw new IllegalStateException("supported Angelica fixed-function state required");
        } else {
            boolean compatibility = caps.OpenGL32
                ? (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0
                : caps.OpenGL31 ? caps.GL_ARB_compatibility
                    : !caps.OpenGL30
                        || (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT)
                            == 0;
            if (!compatibility)
                throw new IllegalStateException("native core-profile fixed-function rendering unsupported");
            if (caps.OpenGL20 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0)
                throw new IllegalStateException("solid-box renderer requires logical fixed-function program zero");
        }
        if (GL11.glGetInteger(GL11.GL_DEPTH_BITS) == 0)
            throw new IllegalStateException("solid-box target requires a depth buffer");
        if (GL11.glIsEnabled(GL11.GL_STENCIL_TEST)) throw new IllegalStateException(
            "solid-box viewport supports scissor clipping, not active stencil clipping");
        if (caps.OpenGL20 || caps.GL_ARB_draw_buffers) {
            if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0) == GL11.GL_NONE)
                throw new IllegalStateException("solid-box output zero is disabled");
            int buffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
            for (int i = 1; i < buffers; i++) if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i) != GL11.GL_NONE)
                throw new IllegalStateException("solid-box renderer does not support MRT");
        } else if (GL11.glGetInteger(GL11.GL_DRAW_BUFFER) == GL11.GL_NONE) {
            throw new IllegalStateException("solid-box color output is disabled");
        }
        return angelica;
    }

    private boolean clip(SceneViewport viewport) {
        int x = viewport.x, y = viewport.y;
        long right = (long) x + viewport.width, top = (long) y + viewport.height;
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            scissor.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
            x = Math.max(x, scissor.get(0));
            y = Math.max(y, scissor.get(1));
            right = Math.min(right, (long) scissor.get(0) + scissor.get(2));
            top = Math.min(top, (long) scissor.get(1) + scissor.get(3));
        }
        if (right <= x || top <= y) return false;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, (int) (right - x), (int) (top - y));
        return true;
    }

    private static void restore(PreviewRenderScope.ClientTessState tess, PreviewRenderScope.RenderState state,
        boolean angelica, Throwable primary) {
        Throwable failure = primary;
        try {
            tess.restore();
        } catch (IllegalAccessException thrown) {
            IllegalStateException wrapped = new IllegalStateException("cannot restore tessellator", thrown);
            if (failure == null) failure = wrapped;
            else failure.addSuppressed(wrapped);
        } catch (RuntimeException | Error thrown) {
            if (failure == null) failure = thrown;
            else if (failure != thrown) failure.addSuppressed(thrown);
        }
        try {
            state.restore();
        } catch (RuntimeException | Error thrown) {
            if (failure == null) failure = thrown;
            else if (failure != thrown) failure.addSuppressed(thrown);
        }
        if (angelica) {
            try {
                AngelicaFfpBridge.restore();
            } catch (RuntimeException | Error thrown) {
                if (failure == null) failure = thrown;
                else if (failure != thrown) failure.addSuppressed(thrown);
            }
        }
        if (primary == null && failure != null) {
            if (failure instanceof Error) throw (Error) failure;
            throw (RuntimeException) failure;
        }
    }

    private static final class Draw {

        final VisualFrame.TargetPose pose;
        final double depth;

        Draw(VisualFrame.TargetPose pose, double depth) {
            this.pose = pose;
            this.depth = depth;
        }
    }

    /** No device handles to delete. The host closes on the owner thread after a render has returned. */
    @Override
    public void close() {
        checkOwner();
        if (rendering) throw new IllegalStateException("cannot close during render");
        closed = true;
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }
}
