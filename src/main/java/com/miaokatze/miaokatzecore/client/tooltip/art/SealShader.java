// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.tooltip.art;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.miaokatze.miaokatzecore.client.render.internal.AngelicaFfpBridge;

/** A single clipped-by-geometry draw. Never wraps FontRenderer in our GL program (Angelica can batch and rebind it). */
final class SealShader implements IResourceManagerReloadListener {

    private static final SealShader INSTANCE = new SealShader();
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private boolean listening, failed;
    private int program, vao, proj, view, origin, rowsA, rowsB, ink, light, motif, phase;

    private SealShader() {}

    static boolean draw(ArtPlate plate, int x, int y, float time) {
        return !AngelicaFfpBridge.isSDLGPU() && GLContext.getCapabilities().OpenGL30
            && INSTANCE.paint(plate, x, y, time);
    }

    private boolean paint(ArtPlate p, int x, int y, float time) {
        if (!listening) {
            IResourceManager manager = Minecraft.getMinecraft()
                .getResourceManager();
            if (manager instanceof IReloadableResourceManager) {
                listening = true;
                ((IReloadableResourceManager) manager).registerReloadListener(this);
            }
        }
        if (failed) return false;
        try {
            if (program == 0) prepare();
        } catch (IOException | RuntimeException badProgram) {
            release();
            failed = true;
            LogManager.getLogger("MIAOKATZE-Core")
                .warn("Tooltip seal shader unavailable; drawing static seals", badProgram);
            return false;
        }
        // Ask Angelica's transformed GL API for *logical* program/VAO and GUI matrices, including FFP emulation.
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean culled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            if (culled) GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            uploadMatrix(proj, GL11.GL_PROJECTION_MATRIX);
            uploadMatrix(view, GL11.GL_MODELVIEW_MATRIX);
            GL20.glUniform2f(origin, x, y);
            GL20.glUniform4f(rowsA, p.rows[0], p.rows[1], p.rows[2], p.rows[3]);
            GL20.glUniform4f(rowsB, p.rows[4], p.rows[5], p.rows[6], p.rows[7]);
            uniformRGB(ink, p.ink);
            uniformRGB(light, p.light);
            GL20.glUniform1i(motif, p.path);
            GL20.glUniform1f(phase, time);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
            return true;
        } finally {
            GL30.glBindVertexArray(previousArray);
            GL20.glUseProgram(previous);
            if (culled) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private void uploadMatrix(int location, int selector) {
        matrix.clear();
        GL11.glGetFloat(selector, matrix);
        matrix.position(0);
        GL20.glUniformMatrix4(location, false, matrix);
    }

    private static void uniformRGB(int location, int color) {
        GL20.glUniform3f(location, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f);
    }

    private void prepare() throws IOException {
        int vs = shader(GL20.GL_VERTEX_SHADER, "seal.vert"), fs = 0;
        try {
            fs = shader(GL20.GL_FRAGMENT_SHADER, "seal.frag");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            proj = uniform("projection");
            view = uniform("modelView");
            origin = uniform("origin");
            rowsA = uniform("rowsA");
            rowsB = uniform("rowsB");
            ink = uniform("ink");
            light = uniform("lightInk");
            motif = uniform("motif");
            phase = uniform("phase");
            vao = GL30.glGenVertexArrays();
            if (vao == 0) throw new IllegalStateException("Tooltip seal VAO unavailable");
        } finally {
            GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private int uniform(String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("Missing tooltip seal uniform: " + name);
        return location;
    }

    private static int shader(int type, String name) throws IOException {
        // Executable shader source is owned by Core, never supplied by the item, the world or a server.
        String path = "/assets/miaokatzecore/shaders/tooltip/" + name;
        String source;
        try (InputStream stream = SealShader.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int len;
            while ((len = stream.read(buf)) >= 0) bytes.write(buf, 0, len);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        int id = GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(id, source);
            GL20.glCompileShader(id);
            if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetShaderInfoLog(id, 4096));
            return id;
        } catch (RuntimeException error) {
            GL20.glDeleteShader(id);
            throw error;
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        release();
        failed = false;
    }

    private void release() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        program = vao = 0;
    }
}
