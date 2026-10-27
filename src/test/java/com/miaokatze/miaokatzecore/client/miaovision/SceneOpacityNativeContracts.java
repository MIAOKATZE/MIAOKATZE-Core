package com.miaokatze.miaokatzecore.client.miaovision;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.PixelFormat;

/** Explicit opt-in real compatibility-profile GL probe; never part of headless check or a Minecraft claim. */
public final class SceneOpacityNativeContracts {

    private static int checks, modelProgram, modelArray;
    private static boolean core;

    public static void main(String[] args) throws Exception {
        Display.setDisplayMode(new DisplayMode(64, 64));
        Display.setTitle("MiaoVision isolated opacity contract");
        String mode = args.length == 0 ? "compat" : args[0];
        core = !mode.equals("compat");
        if (mode.equals("forward3"))
            Display.create(new PixelFormat(), new ContextAttribs(3, 0).withForwardCompatible(true));
        else if (mode.equals("forward31"))
            Display.create(new PixelFormat(), new ContextAttribs(3, 1).withForwardCompatible(true));
        else if (core) Display.create(new PixelFormat(), new ContextAttribs(3, 2).withProfileCore(true));
        else Display.create();
        try {
            System.out.println("GL=" + GL11.glGetString(GL11.GL_VERSION));
            System.out.println("RENDERER=" + GL11.glGetString(GL11.GL_RENDERER));
            check(SceneOpacityCompositor.supported(), "GL capability");
            check(SceneOpacityCompositor.compatibility() != core, "legacy enums match actual context");
            modelProgram = program();
            modelArray = GL30.glGenVertexArrays();
            run();
            rejectMrt();
            System.out.println("MIAOVISION_NATIVE_OPACITY=PASS checks=" + checks);
        } finally {
            if (modelArray != 0) GL30.glDeleteVertexArrays(modelArray);
            if (modelProgram != 0) GL20.glDeleteProgram(modelProgram);
            Display.destroy();
        }
    }

    private static void run() {
        SceneOpacityCompositor compositor = new SceneOpacityCompositor();
        check(compositor.retainedPixels() == 0, "lazy allocation");
        GL11.glViewport(0, 0, 64, 64);
        if (!core) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        quad(-1, 0, -.2f, 0, 0, 1); // Opaque left half at window depth .4.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(8, 8, 48, 48);
        if (!core) GL11.glColor4f(.2f, .3f, .4f, .6f);
        GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE);
        GL14.glBlendEquation(GL14.GL_FUNC_SUBTRACT);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        List<Number> before = state();
        check(compositor.draw(0, 0, 64, 64, .5f, () -> nativeQuad(1, 0, 0, 0)), "fade draw");
        check(before.equals(state()), "restored success state");
        pixel(16, 32, 0, 0, 255, "opaque geometry occludes faded rear model");
        pixel(48, 32, 128, 0, 0, "renderer blend/color reset cannot defeat fade");
        pixel(60, 32, 0, 0, 0, "enclosing scissor respected");
        depth(16, 32, .4f, "opaque depth retained");
        depth(48, 32, 1, "fade writes no destination depth");
        check(compositor.draw(0, 0, 64, 64, .5f, () -> nativeQuad(0, 1, 0, -.8f)), "near fade draw");
        pixel(16, 32, 0, 128, 127, "near fade tests in front of opaque geometry");
        pixel(48, 32, 64, 128, 0, "far-near compositing");
        depth(48, 32, 1, "multiple fades write no depth");
        int[] calls = { 0 };
        try {
            compositor.draw(0, 0, 64, 64, .5f, () -> {
                calls[0]++;
                throw new IllegalStateException("expected");
            });
            throw new AssertionError("callback exception was swallowed");
        } catch (IllegalStateException expected) {
            check("expected".equals(expected.getMessage()), "callback exception preserved");
        }
        check(calls[0] == 1, "callback never retried");
        check(before.equals(state()), "restored exceptional state");
        check(compositor.draw(0, 0, 4096, 2048, .5f, () -> {}), "bounded resize");
        check(compositor.retainedPixels() == 1024 * 512, "bounded RGBA plus depth storage");
        check(before.equals(state()), "resize restores caller state");
        check(compositor.draw(0, 0, 20, 30, .5f, () -> {}), "shrink");
        check(compositor.retainedPixels() == 600, "old image allocation released");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors");
        compositor.close();
        compositor.close();
        check(compositor.retainedPixels() == 0, "idempotent close releases target");
        check(before.equals(state()), "close preserves caller state");
    }

    private static void rejectMrt() {
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int framebuffer = GL30.glGenFramebuffers();
        int[] textures = { GL11.glGenTextures(), GL11.glGenTextures() };
        SceneOpacityCompositor rejected = new SceneOpacityCompositor();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            for (int index = 0; index < 2; index++) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[index]);
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    8,
                    8,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
                GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0 + index,
                    GL11.GL_TEXTURE_2D,
                    textures[index],
                    0);
            }
            IntBuffer targets = BufferUtils.createIntBuffer(2);
            targets.put(GL30.GL_COLOR_ATTACHMENT0)
                .put(GL30.GL_COLOR_ATTACHMENT1)
                .flip();
            GL20.glDrawBuffers(targets);
            check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "MRT fixture");
            GL30.glColorMaski(0, true, false, true, false);
            GL30.glColorMaski(1, false, true, false, true);
            GL30.glEnablei(GL11.GL_BLEND, 0);
            GL30.glDisablei(GL11.GL_BLEND, 1);
            if (GLContext.getCapabilities().OpenGL40) {
                GL40.glBlendFuncSeparatei(0, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                GL40.glBlendFuncSeparatei(1, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                GL40.glBlendEquationSeparatei(0, GL14.GL_FUNC_ADD, GL14.GL_FUNC_SUBTRACT);
                GL40.glBlendEquationSeparatei(1, GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_ADD);
            }
            List<Number> before = state(), indexed = indexedState();
            int[] calls = { 0 };
            check(!SceneOpacityCompositor.singleColorTarget(), "renderer preflight rejects MRT");
            check(!rejected.draw(0, 0, 8, 8, .5f, () -> calls[0]++), "compositor rejects MRT");
            check(calls[0] == 0 && rejected.retainedPixels() == 0, "MRT rejection before callback/allocation");
            check(
                before.equals(state()) && indexed.equals(indexedState()),
                "MRT rejection leaves all indexed state unchanged");
            check(rejected.failure() != null, "MRT rejection diagnostic");
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "MRT rejection has no GL errors");
        } finally {
            rejected.close();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            for (int texture : textures) GL11.glDeleteTextures(texture);
            GL30.glDeleteFramebuffers(framebuffer);
        }
    }

    private static List<Number> indexedState() {
        List<Number> result = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            ByteBuffer mask = BufferUtils.createByteBuffer(4);
            GL30.glGetBoolean(GL11.GL_COLOR_WRITEMASK, index, mask);
            for (int channel = 0; channel < 4; channel++) result.add(mask.get(channel));
            result.add(GL30.glIsEnabledi(GL11.GL_BLEND, index) ? 1 : 0);
            result.add(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index));
            if (GLContext.getCapabilities().OpenGL40)
                for (int key : new int[] { GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA,
                    GL14.GL_BLEND_DST_ALPHA, GL20.GL_BLEND_EQUATION_RGB, GL20.GL_BLEND_EQUATION_ALPHA })
                    result.add(GL30.glGetInteger(key, index));
        }
        return result;
    }

    private static void nativeQuad(float r, float g, float b, float z) {
        // Same isolation contract as production PreviewRenderScope (Angelica emulates legacy state on core).
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        FloatBuffer oldColor = BufferUtils.createFloatBuffer(16);
        if (!core) GL11.glGetFloat(GL11.GL_CURRENT_COLOR, oldColor);
        try {
            GL11.glDisable(GL11.GL_BLEND);
            if (!core) GL11.glColor4f(r, g, b, 1);
            quad(-1, 1, z, r, g, b);
        } finally {
            if (blend) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            if (!core) GL11.glColor4f(oldColor.get(0), oldColor.get(1), oldColor.get(2), oldColor.get(3));
        }
    }

    private static void quad(float left, float right, float z, float r, float g, float b) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GL20.glUseProgram(modelProgram);
            GL30.glBindVertexArray(modelArray);
            GL20.glUniform3f(GL20.glGetUniformLocation(modelProgram, "bounds"), left, right, z);
            GL20.glUniform3f(GL20.glGetUniformLocation(modelProgram, "color"), r, g, b);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousArray);
        }
    }

    private static int program() {
        int vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        int fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        int program = GL20.glCreateProgram();
        String version = GLContext.getCapabilities().OpenGL32 ? "#version 150\n" : "#version 130\n";
        GL20.glShaderSource(
            vertex,
            version + "uniform vec3 bounds;"
                + "const vec2 p[6]=vec2[6](vec2(0,0),vec2(1,0),vec2(1,1),vec2(0,0),vec2(1,1),vec2(0,1));"
                + "void main(){vec2 v=p[gl_VertexID];gl_Position=vec4(mix(bounds.x,bounds.y,v.x),v.y*2-1,bounds.z,1);}");
        GL20.glShaderSource(
            fragment,
            version + "uniform vec3 color;out vec4 resultColor;" + "void main(){resultColor=vec4(color,1);}");
        GL20.glCompileShader(vertex);
        GL20.glCompileShader(fragment);
        check(GL20.glGetShaderi(vertex, GL20.GL_COMPILE_STATUS) != 0, GL20.glGetShaderInfoLog(vertex, 4096));
        check(GL20.glGetShaderi(fragment, GL20.GL_COMPILE_STATUS) != 0, GL20.glGetShaderInfoLog(fragment, 4096));
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        check(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, GL20.glGetProgramInfoLog(program, 4096));
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static void pixel(int x, int y, int r, int g, int b, String label) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        check(
            Math.abs((pixel.get(0) & 255) - r) <= 2 && Math.abs((pixel.get(1) & 255) - g) <= 2
                && Math.abs((pixel.get(2) & 255) - b) <= 2,
            label + " actual=" + (pixel.get(0) & 255) + "," + (pixel.get(1) & 255) + "," + (pixel.get(2) & 255));
    }

    private static void depth(int x, int y, float expected, String label) {
        FloatBuffer pixel = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);
        check(Math.abs(pixel.get(0) - expected) < .0001, label);
    }

    private static List<Number> state() {
        List<Number> values = new ArrayList<>();
        for (int key : new int[] { GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL30.GL_READ_FRAMEBUFFER_BINDING,
            GL11.GL_DRAW_BUFFER, GL11.GL_READ_BUFFER, GL20.GL_CURRENT_PROGRAM, GL13.GL_ACTIVE_TEXTURE,
            GL30.GL_VERTEX_ARRAY_BINDING, GL11.GL_DEPTH_WRITEMASK, GL11.GL_DEPTH_FUNC, GL14.GL_BLEND_SRC_RGB,
            GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_EQUATION }) values.add(GL11.glGetInteger(key));
        for (int key : new int[] { GL11.GL_VIEWPORT, GL11.GL_SCISSOR_BOX, GL11.GL_COLOR_WRITEMASK }) {
            IntBuffer ints = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(key, ints);
            for (int i = 0; i < 4; i++) values.add(ints.get(i));
        }
        if (!core)
            for (int key : new int[] { GL11.GL_CURRENT_COLOR, GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX }) {
                FloatBuffer floats = BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(key, floats);
                for (int i = 0; i < (key == GL11.GL_CURRENT_COLOR ? 4 : 16); i++) values.add(floats.get(i));
            }
        for (int key : new int[] { GL11.GL_SCISSOR_TEST, GL11.GL_DEPTH_TEST, GL11.GL_BLEND })
            values.add(GL11.glIsEnabled(key) ? 1 : 0);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 2; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            values.add(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        GL13.glActiveTexture(active);
        return values;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
