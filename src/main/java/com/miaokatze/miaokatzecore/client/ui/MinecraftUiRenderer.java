package com.miaokatze.miaokatzecore.client.ui;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.camera.ViewportCoordinates;
import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;
import com.miaokatze.miaokatzecore.api.ui.component.UiRect;
import com.miaokatze.miaokatzecore.api.ui.component.UiRenderer;
import com.miaokatze.miaokatzecore.client.render.GlScope;

/** Native HUD/GuiScreen drawing bridge. Construct and use only on the Minecraft render thread. */
public final class MinecraftUiRenderer implements UiRenderer {

    private final FontRenderer font;
    private final double guiWidth, guiHeight;
    private final int displayWidth, displayHeight;
    private final Deque<UiRect> clips = new ArrayDeque<>();
    private GlScope scope;
    private CanvasTransform transform;
    private int[] originalClip;

    public MinecraftUiRenderer(FontRenderer font, double guiWidth, double guiHeight, int displayWidth,
        int displayHeight) {
        this.font = Objects.requireNonNull(font, "font");
        if (!Double.isFinite(guiWidth) || !Double.isFinite(guiHeight)
            || guiWidth <= 0
            || guiHeight <= 0
            || displayWidth < 0
            || displayHeight < 0) throw new IllegalArgumentException("Invalid render viewport");
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
    }

    @Override
    public void begin(CanvasTransform transform) {
        if (scope != null) throw new IllegalStateException("Renderer is already active");
        this.transform = Objects.requireNonNull(transform, "transform");
        scope = new GlScope();
        try {
            if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
                IntBuffer box = BufferUtils.createIntBuffer(16);
                GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
                originalClip = new int[] { box.get(0), box.get(1), box.get(2), box.get(3) };
            } else originalClip = null;
            GL11.glTranslated(transform.toScreenX(0), transform.toScreenY(0), 0);
            GL11.glScaled(transform.scale(), transform.scale(), 1);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } catch (RuntimeException e) {
            end();
            throw e;
        }
    }

    @Override
    public void end() {
        clips.clear();
        originalClip = null;
        if (scope != null) {
            GlScope closing = scope;
            scope = null;
            closing.close();
        }
    }

    @Override
    public void fill(UiRect rect, int argb) {
        active();
        if ((argb >>> 24) == 0 || rect.empty()) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(rect.x, rect.bottom());
        GL11.glVertex2d(rect.right(), rect.bottom());
        GL11.glVertex2d(rect.right(), rect.y);
        GL11.glVertex2d(rect.x, rect.y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public void text(String text, double x, double y, int argb) {
        active();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, (int) Math.round(x), (int) Math.round(y), argb);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public double textWidth(String text) {
        return font.getStringWidth(text);
    }

    @Override
    public double textHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void pushClip(UiRect rectangle) {
        active();
        clips.push(
            clips.isEmpty() ? rectangle
                : clips.peek()
                    .intersect(rectangle));
        applyClip();
    }

    @Override
    public void popClip() {
        active();
        if (clips.isEmpty()) throw new IllegalStateException("Clip stack underflow");
        clips.pop();
        applyClip();
    }

    private void applyClip() {
        if (clips.isEmpty()) {
            if (originalClip == null) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            else {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(originalClip[0], originalClip[1], originalClip[2], originalClip[3]);
            }
            return;
        }
        UiRect clip = clips.peek();
        ViewportCoordinates.FramebufferRect pixels = new ViewportCoordinates(
            transform.toScreenX(clip.x),
            transform.toScreenY(clip.y),
            clip.width * transform.scale(),
            clip.height * transform.scale()).framebufferRect(guiWidth, guiHeight, displayWidth, displayHeight);
        int left = pixels.left(), bottom = pixels.bottom(), right = left + pixels.width(),
            top = bottom + pixels.height();
        if (originalClip != null) {
            left = Math.max(left, originalClip[0]);
            bottom = Math.max(bottom, originalClip[1]);
            right = Math.min(right, originalClip[0] + originalClip[2]);
            top = Math.min(top, originalClip[1] + originalClip[3]);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }

    private void active() {
        if (scope == null) throw new IllegalStateException("Renderer is not active");
    }
}
