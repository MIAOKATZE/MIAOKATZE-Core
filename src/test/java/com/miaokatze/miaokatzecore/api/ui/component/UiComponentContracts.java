package com.miaokatze.miaokatzecore.api.ui.component;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;

/** Plain JVM contracts: no Minecraft, native library or graphical context required. */
public final class UiComponentContracts {

    private UiComponentContracts() {}

    public static void main(String[] args) {
        layouts();
        clippingAndScroll();
        pointerCapture();
        routingAndFocus();
        lifecycle();
        rendering();
        validation();
        System.out.println(
            "UiComponentContracts: PASS (layout, shared geometry, scroll, capture, focus, lifecycle, renderer)");
    }

    private static void layouts() {
        UiNode root = UiNode.panel("root")
            .layout(UiNode.Layout.ROW)
            .padding(5)
            .gap(4);
        UiNode fixed = UiNode.panel("fixed")
            .size(30, -1);
        UiNode fill = UiNode.panel("fill")
            .minSize(20, 10);
        root.add(fixed)
            .add(fill);
        try (UiScene scene = new UiScene(root)) {
            scene.layout(100, 60);
            rect(fixed.bounds(), 5, 5, 30, 50, "row fixed");
            rect(fill.bounds(), 39, 5, 56, 50, "row remaining");
            long revision = scene.layoutRevision();
            scene.layout(100, 60);
            fill.bounds(0, 0, -1, -1);
            scene.layout(100, 60);
            check(revision == scene.layoutRevision(), "unchanged layout is retained");
            fill.text("updated");
            scene.layout(100, 60);
            check(scene.layoutRevision() == revision + 1, "content invalidates once");
            fixed.visible(false);
            scene.layout(100, 60);
            rect(fill.bounds(), 5, 5, 90, 50, "hidden flow child collapses");
            fixed.visible(true)
                .enabled(false);
            scene.layout(100, 60);
            rect(fill.bounds(), 39, 5, 56, 50, "disabled child keeps slot");
        }
        UiNode grid = UiNode.panel("grid")
            .layout(UiNode.Layout.GRID)
            .gridColumns(2)
            .padding(2)
            .gap(2);
        UiNode a = UiNode.panel("a")
            .size(-1, 12),
            b = UiNode.panel("b")
                .size(-1, 20),
            c = UiNode.panel("c")
                .size(-1, 10);
        grid.add(a)
            .add(b)
            .add(c);
        try (UiScene scene = new UiScene(grid)) {
            scene.layout(100, 80);
            rect(a.bounds(), 2, 2, 47, 12, "grid first cell");
            rect(b.bounds(), 51, 2, 47, 20, "grid second cell");
            rect(c.bounds(), 2, 24, 47, 10, "grid row maximum height");
        }
    }

    private static void clippingAndScroll() {
        UiNode root = UiNode.panel("root")
            .style(UiStyle.TEXT);
        UiNode list = UiNode.panel("list")
            .bounds(10, 10, 50, 40)
            .layout(UiNode.Layout.COLUMN)
            .scrollable(true)
            .gap(2);
        UiNode first = UiNode.button("first", "A", () -> {})
            .size(-1, 30);
        UiNode second = UiNode.button("second", "B", () -> {})
            .size(-1, 30);
        root.add(
            list.add(first)
                .add(second));
        try (UiScene scene = new UiScene(root)) {
            scene.transform(new CanvasTransform(0, 0, 100, 80, 2));
            scene.layout(100, 100);
            same(first, scene.hitTest(122, 102), "transform inverse reaches first row");
            same(root, scene.hitTest(122, 182), "invisible child overflow cannot be clicked");
            check(scene.scroll(122, 102, 100), "scroll consumes displacement");
            scene.layout(100, 100);
            near(22, list.scrollY(), "scroll clamped to content extent");
            same(second, scene.hitTest(122, 122), "scrolled drawing and hit geometry agree");
            check(!scene.scroll(122, 122, 10), "edge scroll bubbles out");
            rect(second.clip(), 10, 20, 50, 30, "child clip after scroll");
            list.remove(second);
            scene.layout(100, 100);
            near(0, list.scrollY(), "content removal clamps scroll");
        }
    }

    private static void pointerCapture() {
        List<String> events = new ArrayList<>();
        UiNode drag = UiNode.panel("drag")
            .bounds(5, 5, 20, 20)
            .on(UiEvent.Type.DOWN, e -> {
                e.capturePointer();
                e.consume();
                events.add("down");
            })
            .on(UiEvent.Type.MOVE, e -> {
                events.add("move:" + e.localX());
                e.consume();
            })
            .on(UiEvent.Type.UP, e -> events.add("up"))
            .on(UiEvent.Type.CANCEL, e -> events.add("cancel"));
        try (UiScene scene = new UiScene(
            UiNode.panel("root")
                .add(drag))) {
            scene.layout(100, 100);
            check(scene.pointerDown(8, 8, 0), "custom pointer accepted");
            same(drag, scene.captured(), "capture owner");
            check(scene.pointerMove(150, 150), "captured movement outside viewport");
            check(events.contains("move:145.0"), "captured move keeps local coordinate");
            check(scene.pointerUp(150, 150, 1), "unmatched release swallowed");
            same(drag, scene.captured(), "unmatched release retains capture");
            scene.pointerUp(150, 150, 0);
            same(null, scene.captured(), "matching release clears capture");
            check(events.contains("up"), "matching release delivered");
            scene.pointerDown(8, 8, 0);
            check(scene.keyTyped('\0', 1, false, false), "Esc handled during capture");
            same(null, scene.captured(), "Esc clears capture");
            check(events.contains("cancel"), "Esc delivers cancel");
            scene.pointerDown(8, 8, 0);
            scene.blur();
            check(count(events, "cancel") == 2, "host blur cancels once");
        }
        int[] clicks = { 0 };
        UiNode button = UiNode.button("button", "press", () -> clicks[0]++)
            .bounds(0, 0, 20, 20);
        try (UiScene scene = new UiScene(
            UiNode.panel("root")
                .add(button))) {
            scene.layout(50, 50);
            scene.pointerDown(2, 2, 0);
            scene.pointerUp(40, 40, 0);
            check(clicks[0] == 0, "button release outside does not activate");
            scene.pointerDown(2, 2, 0);
            scene.pointerUp(2, 2, 0);
            check(clicks[0] == 1, "button click on matching release");
            scene.pointerDown(2, 2, 0);
            button.enabled(false);
            same(null, scene.captured(), "disabling capture owner cancels");
            scene.pointerUp(2, 2, 0);
            check(clicks[0] == 1, "disabled button does not click");
        }
    }

    private static void routingAndFocus() {
        List<String> route = new ArrayList<>(), edits = new ArrayList<>();
        UiNode panel = UiNode.panel("panel")
            .on(UiEvent.Type.DOWN, e -> {
                route.add(e.currentTarget.id);
                e.consume();
            });
        UiNode child = UiNode.panel("child")
            .bounds(2, 2, 10, 10)
            .on(UiEvent.Type.DOWN, e -> route.add(e.currentTarget.id));
        UiNode input = UiNode.textInput("input", "abc", edits::add)
            .bounds(20, 0, 50, 20);
        UiNode button = UiNode.button("button", "next", () -> {})
            .bounds(20, 25, 50, 20);
        UiNode hidden = UiNode.textInput("hidden", "", edits::add)
            .visible(false);
        panel.add(child)
            .add(input)
            .add(button)
            .add(hidden);
        try (UiScene scene = new UiScene(panel)) {
            scene.layout(100, 100);
            scene.pointerDown(3, 3, 1);
            check(
                route.toString()
                    .equals("[child, panel]"),
                "event bubbles target to parent");
            scene.keyTyped('\t', 15, false, false);
            same(input, scene.focused(), "Tab first focus");
            scene.keyTyped('d', 0, false, false);
            check(
                input.text()
                    .equals("abcd"),
                "printable input");
            scene.keyTyped('\0', 30, false, true);
            check(
                scene.selectedText()
                    .equals("abcd"),
                "select all");
            input.maxLength(5);
            scene.insertText("xy\nz12345");
            check(
                input.text()
                    .equals("xyz12"),
                "clipboard input filters controls and max length");
            scene.keyTyped('\0', 14, false, false);
            check(
                input.text()
                    .equals("xyz1"),
                "backspace");
            scene.keyTyped('\0', 199, false, false);
            scene.keyTyped('\0', 211, false, false);
            check(
                input.text()
                    .equals("yz1"),
                "home delete");
            scene.keyTyped('\t', 15, false, false);
            same(button, scene.focused(), "Tab next focus");
            scene.keyTyped('\t', 15, true, false);
            same(input, scene.focused(), "Shift Tab reverses");
            input.visible(false);
            same(null, scene.focused(), "hiding input clears focus");
            check(edits.size() == 4, "onChange once per actual edit");
        }
    }

    private static void lifecycle() {
        int[] disposed = { 0 }, cancelled = { 0 };
        UiNode root = UiNode.panel("root");
        UiNode child = UiNode.panel("child")
            .size(10, 10)
            .onDispose(() -> disposed[0]++)
            .on(UiEvent.Type.DOWN, e -> e.capturePointer())
            .on(UiEvent.Type.CANCEL, e -> cancelled[0]++);
        root.add(child);
        UiScene scene = new UiScene(root);
        scene.layout(20, 20);
        scene.pointerDown(1, 1, 0);
        root.remove(child);
        check(disposed[0] == 1 && cancelled[0] == 1, "remove cancels then disposes exactly once");
        same(null, scene.captured(), "removed capture cannot receive another event");
        check(child.disposed(), "removed node cannot be reused");
        scene.close();
        scene.close();
        check(disposed[0] == 1, "scene close idempotent");
        check(scene.closed() && !scene.pointerDown(1, 1, 0), "closed scene ignores input");
        UiNode stable = UiNode.panel("canvas")
            .bounds(0, 0, 10, 10)
            .on(UiEvent.Type.DOWN, e -> e.capturePointer());
        UiNode sidebar = UiNode.panel("sidebar")
            .bounds(20, 0, 10, 10)
            .add(UiNode.label("transient", "x"));
        try (UiScene other = new UiScene(
            UiNode.panel("root")
                .add(stable)
                .add(sidebar))) {
            other.layout(40, 20);
            other.pointerDown(1, 1, 0);
            sidebar.clear();
            same(stable, other.captured(), "rebuilding unrelated branch preserves canvas capture");
        }
        int[] cleanup = { 0 };
        UiNode broken = UiNode.panel("broken")
            .onDispose(() -> { throw new IllegalStateException("dispose failure"); });
        UiNode survivor = UiNode.panel("survivor")
            .onDispose(() -> cleanup[0]++);
        UiScene failing = new UiScene(
            UiNode.panel("root")
                .add(broken)
                .add(survivor));
        expect(IllegalStateException.class, failing::close);
        check(cleanup[0] == 1 && survivor.disposed(), "one disposal failure does not retain sibling callbacks");
    }

    private static void rendering() {
        Recording renderer = new Recording();
        UiNode child = UiNode.label("label", "hello")
            .bounds(10, 10, 20, 15);
        UiNode root = UiNode.panel("root")
            .add(child);
        try (UiScene scene = new UiScene(root)) {
            scene.layout(100, 100);
            scene.render(renderer);
            check(renderer.begins == 1 && renderer.ends == 1 && renderer.depth == 0, "balanced renderer lifecycle");
            check(renderer.texts.contains("hello"), "label goes through renderer bridge");
            UiNode[] retained = root.stableChildren();
            long before = scene.layoutRevision();
            scene.render(renderer);
            check(scene.layoutRevision() == before, "render does not cause layout without dirty change");
            same(retained, root.stableChildren(), "unchanged render reuses cached children snapshot");
            child.onDraw((node, bridge) -> { throw new IllegalStateException("painter failure"); });
            expect(IllegalStateException.class, () -> scene.render(renderer));
            check(
                renderer.depth == 0 && renderer.begins == renderer.ends,
                "paint failure restores clip and host scope");
        }
        UiStyle.Paint normal = new UiStyle.Paint(1, 2, 3), disabled = new UiStyle.Paint(4, 5, 6);
        UiStyle theme = new UiStyle(normal, normal, normal, normal, disabled);
        same(disabled, theme.resolve(false, true, true, true), "disabled theme state takes precedence");
        renderMutation();
    }

    private static void renderMutation() {
        int[] paints = { 0, 0 }, disposed = { 0 };
        UiNode root = UiNode.panel("root");
        UiNode victim = UiNode.panel("victim")
            .bounds(0, 20, 20, 20)
            .onDraw((node, bridge) -> paints[0]++)
            .onDispose(() -> disposed[0]++);
        UiNode late = UiNode.panel("late")
            .bounds(0, 40, 20, 20)
            .onDraw((node, bridge) -> paints[1]++);
        UiNode mutator = UiNode.panel("mutator")
            .bounds(0, 0, 20, 20)
            .onDraw((node, bridge) -> {
                node.onDraw(null);
                root.remove(victim)
                    .add(late);
            });
        root.add(mutator)
            .add(victim);
        try (UiScene scene = new UiScene(root)) {
            scene.layout(100, 100);
            UiNode[] initial = root.stableChildren();
            scene.render(new Recording());
            check(disposed[0] == 1 && paints[0] == 0, "removed sibling is disposed and skipped in active render");
            check(paints[1] == 0, "new sibling waits for next layout and traversal");
            UiNode[] changed = root.stableChildren();
            check(
                initial != changed && changed.length == 2 && changed[1] == late,
                "structure changes replace snapshot");
            scene.render(new Recording());
            check(paints[1] == 1, "new sibling paints on next render");
            same(changed, root.stableChildren(), "next render retains replacement snapshot");
            late.on(UiEvent.Type.DOWN, e -> {
                e.capturePointer();
                root.remove(late);
                e.consume();
            });
            check(scene.pointerDown(1, 41, 0), "input handler may remove its own target");
            check(late.disposed() && scene.captured() == null, "self removal cleans capture during input callback");
        }
    }

    private static void validation() {
        expect(
            IllegalArgumentException.class,
            () -> new UiScene(
                UiNode.panel("r")
                    .add(UiNode.panel("x"))
                    .add(UiNode.panel("x"))));
        UiNode root = UiNode.panel("r");
        try (UiScene scene = new UiScene(root)) {
            expect(IllegalArgumentException.class, () -> root.add(UiNode.panel("r")));
            expect(IllegalArgumentException.class, () -> root.add(root));
            expect(IllegalArgumentException.class, () -> root.bounds(0, 0, Double.NaN, 1));
            expect(IllegalArgumentException.class, () -> root.size(-2, 1));
            expect(IllegalArgumentException.class, () -> scene.layout(-1, 1));
            expect(IllegalArgumentException.class, () -> new UiRect(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
        }
    }

    private static int count(List<String> values, String value) {
        int count = 0;
        for (String candidate : values) if (candidate.equals(value)) count++;
        return count;
    }

    private static final class Recording implements UiRenderer {

        int begins, ends, depth;
        final List<String> texts = new ArrayList<>();

        @Override
        public void begin(CanvasTransform transform) {
            begins++;
        }

        @Override
        public void end() {
            ends++;
        }

        @Override
        public void fill(UiRect rectangle, int argb) {
            check(depth > 0, "fill always clipped");
        }

        @Override
        public void text(String text, double x, double y, int argb) {
            texts.add(text);
        }

        @Override
        public void pushClip(UiRect rectangle) {
            depth++;
        }

        @Override
        public void popClip() {
            depth--;
        }
    }

    private static void rect(UiRect actual, double x, double y, double width, double height, String message) {
        near(x, actual.x, message + " x");
        near(y, actual.y, message + " y");
        near(width, actual.width, message + " width");
        near(height, actual.height, message + " height");
    }

    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1e-8, message + ": " + expected + " != " + actual);
    }

    private static void same(Object expected, Object actual, String message) {
        check(expected == actual, message);
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected exception", failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
