package com.miaokatze.miaokatzecore.client.miaovision;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

/**
 * Lazy bounded native-model compositor. Pixel colors/depth are produced by the original renderer at full opacity;
 * its blend/color resets cannot override the later composite opacity. No Minecraft/world references or global hooks.
 * All methods require the owning GL context/thread, including close. Supports ordinary single-color GUI targets;
 * heterogeneous indexed color/blend state on inactive draw buffers is outside this contract.
 */
final class SceneOpacityCompositor implements AutoCloseable {

    static final int MAX_EDGE = 1024;
    private int framebuffer, colorTexture, depthTexture, program, vertexArray, width, height;
    private int colorUniform, depthUniform, opacityUniform;
    private String failure;
    private boolean closed;

    static boolean supported() {
        return GLContext.getCapabilities().OpenGL30 && GLContext.getCapabilities().OpenGL20;
    }

    String failure() {
        return failure;
    }

    int retainedPixels() {
        return width * height;
    }

    /** Returns false only when capability/allocation/shader setup is unavailable. Never invokes draw twice. */
    boolean draw(int viewportX, int viewportY, int viewportWidth, int viewportHeight, float opacity, Runnable draw) {
        if (closed) throw new IllegalStateException("compositor closed");
        if (draw == null || viewportWidth <= 0
            || viewportHeight <= 0
            || !Float.isFinite(opacity)
            || opacity < 0
            || opacity > 1) throw new IllegalArgumentException("compositor arguments");
        if (!supported()) {
            failure = "OpenGL 3.0 framebuffer/depth textures and GLSL 1.30 required";
            return false;
        }
        if (!singleColorTarget()) {
            failure = "MiaoVision requires one active draw buffer at output zero; MRT is unsupported";
            return false;
        }
        if (failure != null) return false;
        try (NativeScope scope = new NativeScope()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int[] size = dimensions(viewportWidth, viewportHeight);
            try {
                ensure(size[0], size[1]);
            } catch (RuntimeException failedSetup) {
                failure = failedSetup.toString();
                release();
                return false;
            }
            // The same projection/modelview is used at a bounded resolution. No GUI or body camera is recreated.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glDepthRange(0, 1);
            GL11.glClearDepth(1);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL20.glUseProgram(0);
            draw.run(); // Caller isolates arbitrary native model state with PreviewRenderScope.

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scope.drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scope.readFramebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            scope.restoreScissor();
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthRange(0, 1);
            // Far-to-near translucent bodies test against opaque room/models but never occlude each other.
            GL11.glDepthMask(false);
            if (scope.compatibility) {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_FOG);
                for (int plane = 0; plane < 6; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            }
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            if (GLContext.getCapabilities().OpenGL33) {
                GL33.glBindSampler(0, 0);
                GL33.glBindSampler(1, 0);
            }
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(program);
            GL20.glUniform1i(colorUniform, 0);
            GL20.glUniform1i(depthUniform, 1);
            GL20.glUniform1f(opacityUniform, opacity);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindVertexArray(vertexArray);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            return true;
        }
    }

    static int[] dimensions(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("viewport size");
        double scale = Math.min(1, (double) MAX_EDGE / Math.max(width, height));
        return new int[] { Math.max(1, (int) Math.round(width * scale)),
            Math.max(1, (int) Math.round(height * scale)) };
    }

    private void ensure(int width, int height) {
        if (program == 0) {
            String version = GLContext.getCapabilities().OpenGL32 ? "#version 150\n" : "#version 130\n";
            int vertex = shader(
                GL20.GL_VERTEX_SHADER,
                version + "out vec2 uv;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);"
                    + "uv=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
            int fragment = 0;
            try {
                fragment = shader(
                    GL20.GL_FRAGMENT_SHADER,
                    version + "uniform sampler2D colorImage;uniform sampler2D depthImage;uniform float opacity;"
                        + "in vec2 uv;out vec4 resultColor;"
                        + "void main(){vec4 c=texture(colorImage,uv);float d=texture(depthImage,uv).r;"
                        + "if(d>=1.0||c.a<=0.0)discard;gl_FragDepth=d;resultColor=c*opacity;}");
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(
                    "MiaoVision composite link: " + GL20.glGetProgramInfoLog(program, 4096));
                colorUniform = GL20.glGetUniformLocation(program, "colorImage");
                depthUniform = GL20.glGetUniformLocation(program, "depthImage");
                opacityUniform = GL20.glGetUniformLocation(program, "opacity");
            } finally {
                GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
            }
        }
        if (vertexArray == 0) vertexArray = GL30.glGenVertexArrays();
        if (framebuffer != 0 && this.width == width && this.height == height) return;
        releaseImages();
        framebuffer = GL30.glGenFramebuffers();
        colorTexture = texture(width, height, false);
        depthTexture = texture(width, height, true);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTexture,
            0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("MiaoVision composite framebuffer incomplete");
        this.width = width;
        this.height = height;
    }

    private static int texture(int width, int height, boolean depth) {
        int texture = GL11.glGenTextures();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812f /* CLAMP_TO_EDGE */);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812f);
            if (depth) GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 0x884c /* TEXTURE_COMPARE_MODE */, GL11.GL_NONE);
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                depth ? 0x81a6 /* DEPTH_COMPONENT24 */ : GL11.GL_RGBA8,
                width,
                height,
                0,
                depth ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA,
                depth ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
            return texture;
        } catch (RuntimeException failure) {
            GL11.glDeleteTextures(texture);
            throw failure;
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(
                "MiaoVision composite shader: " + GL20.glGetShaderInfoLog(shader, 4096));
            return shader;
        } catch (RuntimeException failure) {
            GL20.glDeleteShader(shader);
            throw failure;
        }
    }

    private void releaseImages() {
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (colorTexture != 0) GL11.glDeleteTextures(colorTexture);
        if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
        framebuffer = colorTexture = depthTexture = width = height = 0;
    }

    private void release() {
        releaseImages();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        vertexArray = 0;
    }

    @Override
    public void close() {
        if (!closed) {
            release();
            closed = true;
        }
    }

    /** Single-color Minecraft GUI target only. Called before changing any GL/Tessellator state. */
    static boolean singleColorTarget() {
        if (!GLContext.getCapabilities().OpenGL20) return true;
        if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0) == GL11.GL_NONE) return false;
        int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        for (int index = 1; index < count; index++)
            if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index) != GL11.GL_NONE) return false;
        return true;
    }

    static boolean compatibility() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.OpenGL32)
            return (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
        if (caps.OpenGL31) return caps.GL_ARB_compatibility;
        return (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT) == 0;
    }

    /** Only state changed by the compositor. Native model code is separately guarded by PreviewRenderScope. */
    private static final class NativeScope implements AutoCloseable {

        final boolean compatibility = compatibility();
        final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] viewport = integers(GL11.GL_VIEWPORT, 4), scissorBox = integers(GL11.GL_SCISSOR_BOX, 4);
        final int[] colorMask = integers(GL11.GL_COLOR_WRITEMASK, 4), polygonMode = integers(GL11.GL_POLYGON_MODE, 2);
        final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final float[] clearColor = floats(GL11.GL_COLOR_CLEAR_VALUE, 4);
        final double[] depthRange = doubles(GL11.GL_DEPTH_RANGE, 2);
        final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
            dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
            equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int[] texture = new int[2], sampler = new int[2];
        final int[] enables = compatibility
            ? new int[] { GL11.GL_SCISSOR_TEST, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_BLEND,
                GL11.GL_COLOR_LOGIC_OP, GL11.GL_POLYGON_OFFSET_FILL, GL11.GL_ALPHA_TEST, GL11.GL_LIGHTING, GL11.GL_FOG,
                GL11.GL_CLIP_PLANE0, GL11.GL_CLIP_PLANE1, GL11.GL_CLIP_PLANE2, GL11.GL_CLIP_PLANE3, GL11.GL_CLIP_PLANE4,
                GL11.GL_CLIP_PLANE5 }
            : new int[] { GL11.GL_SCISSOR_TEST, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_BLEND,
                GL11.GL_COLOR_LOGIC_OP, GL11.GL_POLYGON_OFFSET_FILL };
        final boolean[] enabled = new boolean[enables.length];

        NativeScope() {
            for (int i = 0; i < enables.length; i++) enabled[i] = GL11.glIsEnabled(enables[i]);
            for (int i = 0; i < 2; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                texture[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                if (GLContext.getCapabilities().OpenGL33) sampler[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            GL13.glActiveTexture(activeTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }

        void restoreScissor() {
            enable(GL11.GL_SCISSOR_TEST, enabled[0]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }

        @Override
        public void close() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            // Read/draw buffer selections belong to each FBO; never collapse a caller's MRT draw-buffer list.
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            for (int i = 0; i < 2; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture[i]);
                if (GLContext.getCapabilities().OpenGL33) GL33.glBindSampler(i, sampler[i]);
            }
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            GL11.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthRange(depthRange[0], depthRange[1]);
            GL11.glClearDepth(clearDepth);
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            if (compatibility) {
                GL11.glPolygonMode(GL11.GL_FRONT, polygonMode[0]);
                GL11.glPolygonMode(GL11.GL_BACK, polygonMode[1]);
            } else GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode[0]);
            for (int i = 0; i < enables.length; i++) enable(enables[i], enabled[i]);
        }

        private static void enable(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability);
            else GL11.glDisable(capability);
        }

        private static int[] integers(int name, int count) {
            java.nio.IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(name, buffer);
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = buffer.get(i);
            return result;
        }

        private static double[] doubles(int name, int count) {
            java.nio.DoubleBuffer buffer = org.lwjgl.BufferUtils.createDoubleBuffer(16);
            GL11.glGetDouble(name, buffer);
            double[] result = new double[count];
            for (int i = 0; i < count; i++) result[i] = buffer.get(i);
            return result;
        }

        private static float[] floats(int name, int count) {
            java.nio.FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(name, buffer);
            float[] result = new float[count];
            for (int i = 0; i < count; i++) result[i] = buffer.get(i);
            return result;
        }
    }
}
