package com.miaokatze.miaokatzecore.api.ui.guidance;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.miaokatzecore.api.ui.component.UiRect;
import com.miaokatze.miaokatzecore.api.ui.component.UiRenderer;

/** Plain JVM contracts; rendering decisions require no Minecraft or OpenGL context. */
public final class GuidanceContracts {

    private GuidanceContracts() {}

    public static void main(String[] args) {
        placement();
        rendering();
        failureRestoresClip();
        System.out
            .println("GuidanceContracts: PASS (placement, reservations, click phases, clipping, failure cleanup)");
    }

    private static void placement() {
        UiRect viewport = new UiRect(0, 0, 320, 180);
        UiRect anchor = new UiRect(260, 12, 48, 20);
        UiRect box = GuidanceLayout.place(anchor, viewport, 160, 34);
        check(box != null && inside(box, viewport), "edge caption remains in viewport");
        check(
            box.intersect(anchor)
                .empty(),
            "caption does not obscure target");
        UiRect reserved = box;
        UiRect moved = GuidanceLayout.place(anchor, viewport, 160, 34, reserved);
        check(
            moved != null && moved.intersect(reserved)
                .empty(),
            "reserved HUD is avoided");
        check(GuidanceLayout.place(viewport, viewport, 160, 34) == null, "full screen focus omits obstructing caption");
        check(
            GuidanceLayout.place(anchor, new UiRect(0, 0, 18, 18), 160, 34) == null,
            "tiny screen omits unreadable caption");
        UiRect offsetViewport = new UiRect(80, 90, 320, 180);
        UiRect offset = GuidanceLayout.place(new UiRect(80, 90, 40, 20), offsetViewport, 160, 34);
        check(offset != null && inside(offset, offsetViewport), "local offset viewport is honored");
    }

    private static void rendering() {
        UiRect anchor = new UiRect(30, 12, 50, 20), viewport = new UiRect(0, 0, 320, 180);
        Recording idle = new Recording();
        GuidanceRenderer.render(idle, anchor, viewport, "Dashboard", "Click to open", 0, true);
        check(idle.clips == 0 && idle.maxClips == 2, "nested caption clip balances");
        check(idle.colors.contains(0xFFFFE7B4) && idle.colors.contains(0xFF30231A), "GTMB pointer palette retained");
        check(!idle.colors.contains(0x70EAB85F), "idle phase is not pressed");
        Recording pressed = new Recording();
        GuidanceRenderer.render(pressed, anchor, viewport, "Dashboard", "Click to open", 800, true);
        check(pressed.colors.contains(0x70EAB85F), "press phase is visible");
        check(pressed.colors.contains(0xFFF2CE91), "expanding corners are visible");
        Recording noClick = new Recording();
        GuidanceRenderer.render(noClick, anchor, viewport, "Hint", "Details", 800, false);
        check(!noClick.colors.contains(0xFF30231A), "non-click cue omits fake pointer");
        Recording hidden = new Recording();
        GuidanceRenderer.render(hidden, new UiRect(400, 0, 20, 20), viewport, "", "", 0, true);
        check(hidden.colors.isEmpty() && hidden.clips == 0, "offscreen target is not drawn");
        Recording narrow = new Recording();
        GuidanceRenderer.card(narrow, new UiRect(0, 0, 27, 34), "A\uD83D\uDE00B", "Long detail", 0);
        check(narrow.clips == 0, "narrow card clip balances");
        for (String text : narrow.texts) check(narrow.textWidth(text) <= 13, "text fits narrow card");
    }

    private static void failureRestoresClip() {
        Recording renderer = new Recording();
        renderer.failText = true;
        boolean failed = false;
        try {
            GuidanceRenderer
                .render(renderer, new UiRect(30, 12, 50, 20), new UiRect(0, 0, 320, 180), "Title", "Detail", 0, true);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed && renderer.clips == 0, "render failure restores both clips");
    }

    private static boolean inside(UiRect box, UiRect viewport) {
        return box.x >= viewport.x && box.y >= viewport.y
            && box.right() <= viewport.right()
            && box.bottom() <= viewport.bottom();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Recording implements UiRenderer {

        private int clips, maxClips;
        private boolean failText;
        private final List<Integer> colors = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();

        @Override
        public void fill(UiRect rectangle, int argb) {
            colors.add(argb);
        }

        @Override
        public void text(String text, double x, double y, int argb) {
            if (failText) throw new IllegalStateException("host failure");
            texts.add(text);
        }

        @Override
        public void pushClip(UiRect rectangle) {
            maxClips = Math.max(maxClips, ++clips);
        }

        @Override
        public void popClip() {
            check(clips > 0, "clip underflow");
            clips--;
        }
    }
}
