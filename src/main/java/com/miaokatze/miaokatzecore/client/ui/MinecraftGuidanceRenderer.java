package com.miaokatze.miaokatzecore.client.ui;

import net.minecraft.client.Minecraft;

import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;
import com.miaokatze.miaokatzecore.api.ui.component.UiRect;
import com.miaokatze.miaokatzecore.api.ui.guidance.GuidanceRenderer;

/** Scaled-screen bridge using the existing MinecraftUiRenderer and its symmetric GlScope lifecycle. */
public final class MinecraftGuidanceRenderer {

    private MinecraftGuidanceRenderer() {}

    public static void render(Minecraft mc, UiRect anchor, int width, int height, String title, String detail,
        long elapsedMillis, boolean click, UiRect... reserved) {
        if (width <= 0 || height <= 0) return;
        MinecraftUiRenderer renderer = renderer(mc, width, height);
        renderer.begin(CanvasTransform.identity());
        try {
            GuidanceRenderer.render(
                renderer,
                anchor,
                new UiRect(0, 0, width, height),
                title,
                detail,
                elapsedMillis,
                click,
                reserved);
        } finally {
            renderer.end();
        }
    }

    public static void card(Minecraft mc, UiRect box, int width, int height, String title, String detail,
        long elapsedMillis) {
        if (width <= 0 || height <= 0) return;
        MinecraftUiRenderer renderer = renderer(mc, width, height);
        renderer.begin(CanvasTransform.identity());
        try {
            GuidanceRenderer.card(renderer, box, title, detail, elapsedMillis);
        } finally {
            renderer.end();
        }
    }

    private static MinecraftUiRenderer renderer(Minecraft mc, int width, int height) {
        return new MinecraftUiRenderer(mc.fontRenderer, width, height, mc.displayWidth, mc.displayHeight);
    }
}
