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
        palettes();
        transparency();
        failureRestoresClip();
        System.out.println(
            "GuidanceContracts: PASS (placement, reservations, legacy/custom palettes, alpha, clipping, failure cleanup)");
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
        Recording faded = new Recording();
        GuidanceRenderer.render(faded, anchor, viewport, "Dashboard", "Click to open", 1100, true);
        check(faded.colors.contains(0x88FFD692), "legacy final corner color retained");
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
        renderer = new Recording();
        renderer.failFill = true;
        failed = false;
        try {
            GuidanceRenderer.render(
                renderer,
                GuidanceStyle.builder()
                    .highlight(0x8068E1E8)
                    .build(),
                new UiRect(30, 12, 50, 20),
                new UiRect(0, 0, 320, 180),
                "Title",
                "Detail",
                800,
                true);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed && renderer.clips == 0, "custom palette fill failure restores outer clip");
        renderer = new Recording();
        renderer.failText = true;
        failed = false;
        try {
            GuidanceRenderer.card(
                renderer,
                GuidanceStyle.builder()
                    .background(0x80102030)
                    .build(),
                new UiRect(0, 0, 140, 40),
                "Title",
                "Detail",
                800);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed && renderer.clips == 0, "custom card text failure restores inner clip");
    }

    private static void palettes() {
        UiRect anchor = new UiRect(30, 12, 50, 20), viewport = new UiRect(0, 0, 320, 180);
        for (long elapsed : new long[] { -1, 0, 540, 800, 1100, 1440, Long.MAX_VALUE }) {
            Recording legacy = new Recording(), explicit = new Recording();
            GuidanceRenderer.render(legacy, anchor, viewport, "Title", "Detail", elapsed, true);
            GuidanceRenderer
                .render(explicit, GuidanceStyle.DEFAULT, anchor, viewport, "Title", "Detail", elapsed, true);
            check(legacy.draws.equals(explicit.draws), "legacy overload and explicit default draw identically");
            check(legacy.colors.get(0) == legacyHighlight(elapsed), "legacy highlight ARGB retained");
            check(legacy.colors.get(4) == 0xE634302A, "legacy card background retained");
            check(
                legacy.textColors.get(0) == 0xFFFFE7B4 && legacy.textColors.get(1) == 0xFFD4C9B6,
                "legacy title and detail colors retained");
            Recording oldCard = new Recording(), styledCard = new Recording();
            GuidanceRenderer.card(oldCard, new UiRect(0, 0, 140, 40), "Title", "Detail", elapsed);
            GuidanceRenderer
                .card(styledCard, GuidanceStyle.DEFAULT, new UiRect(0, 0, 140, 40), "Title", "Detail", elapsed);
            check(oldCard.draws.equals(styledCard.draws), "legacy card and explicit default draw identically");
        }
        GuidanceStyle.Builder builder = GuidanceStyle.builder()
            .background(0xA0112233)
            .titleText(0xFF223344)
            .detailText(0xFF334455)
            .highlight(0x80445566)
            .pointerFill(0xFF556677)
            .pointerOutline(0xFF667788)
            .pressedHint(0x70778899)
            .clickCorner(0xFF8899AA)
            .fadedClickCorner(0x8899AABB);
        GuidanceStyle style = builder.build();
        builder.background(0);
        check(style.background == 0xA0112233, "built palette does not retain mutable builder state");
        Recording custom = new Recording();
        GuidanceRenderer.render(custom, style, anchor, viewport, "Title", "Detail", 800, true);
        check(
            custom.colors.contains(style.background) && custom.textColors.contains(style.titleText)
                && custom.textColors.contains(style.detailText),
            "custom card roles are used");
        check(
            custom.colors.contains(style.pointerFill) && custom.colors.contains(style.pointerOutline)
                && custom.colors.contains(style.pressedHint)
                && custom.colors.contains(style.clickCorner),
            "custom pointer, press and click roles are used");
        check(
            custom.colors.get(0) == ((128 * (legacyHighlight(800) >>> 24) / 255) << 24 | 0x445566),
            "custom highlight RGB and multiplied alpha are used");
        Recording faded = new Recording();
        GuidanceRenderer.render(faded, style, anchor, viewport, "Title", "Detail", 1100, true);
        check(faded.colors.contains(style.fadedClickCorner), "custom final click phase is used");
        check(custom.clips == 0 && faded.clips == 0, "custom palette keeps clip balance");
    }

    private static void transparency() {
        for (int alpha : new int[] { 0, 1, 128, 255 }) {
            GuidanceStyle style = GuidanceStyle.builder()
                .highlight(alpha << 24 | 0x123456)
                .build();
            for (long elapsed : new long[] { Long.MIN_VALUE, -1980, 0, 660, 1980, Long.MAX_VALUE }) {
                Recording renderer = new Recording();
                GuidanceRenderer.card(renderer, style, new UiRect(0, 0, 140, 40), "Title", "Detail", elapsed);
                int actual = renderer.colors.get(1), expectedAlpha = alpha * (legacyHighlight(elapsed) >>> 24) / 255;
                check(
                    (actual >>> 24) == expectedAlpha && (actual & 0xFFFFFF) == 0x123456,
                    "highlight alpha respects palette bounds and retains RGB");
                check((actual >>> 24) <= alpha, "pulse never increases configured alpha");
            }
        }
        for (int alpha : new int[] { 0, 1, 2, 3, 4, 255 }) {
            Recording renderer = new Recording();
            GuidanceStyle style = GuidanceStyle.builder()
                .titleText(alpha << 24 | 0x123456)
                .detailText(alpha << 24 | 0x654321)
                .build();
            GuidanceRenderer.card(renderer, style, new UiRect(0, 0, 140, 40), "Title", "Detail", 0);
            check(renderer.texts.size() == (alpha < 4 ? 0 : 2), "font low alpha cannot become opaque");
            if (alpha >= 4) check(
                renderer.textColors.get(0) == style.titleText && renderer.textColors.get(1) == style.detailText,
                "supported text alpha passes through unchanged");
            check(renderer.clips == 0, "transparent text keeps card clip balance");
        }
    }

    private static int legacyHighlight(long elapsedMillis) {
        return ((int) (165 + (.5 + .5 * Math.sin(elapsedMillis / 420.0)) * 90) << 24) | 0xF2CE91;
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
        private boolean failText, failFill;
        private final List<Integer> colors = new ArrayList<>();
        private final List<Integer> textColors = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();
        private final List<String> draws = new ArrayList<>();

        @Override
        public void fill(UiRect rectangle, int argb) {
            if (failFill) throw new IllegalStateException("host failure");
            colors.add(argb);
            draws.add(
                "fill:" + rectangle.x
                    + ":"
                    + rectangle.y
                    + ":"
                    + rectangle.width
                    + ":"
                    + rectangle.height
                    + ":"
                    + argb);
        }

        @Override
        public void text(String text, double x, double y, int argb) {
            if (failText) throw new IllegalStateException("host failure");
            texts.add(text);
            textColors.add(argb);
            draws.add("text:" + text + ":" + x + ":" + y + ":" + argb);
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
