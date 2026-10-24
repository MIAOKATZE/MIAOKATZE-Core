package com.miaokatze.miaokatzecore.api.ui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Retained component with a stable, scene-unique id. Mutate on the host GUI thread only. */
public final class UiNode {

    public enum Kind {
        PANEL,
        LABEL,
        VALUE,
        BUTTON,
        TEXT_INPUT,
        TAB
    }

    public enum Layout {
        RECT,
        ROW,
        COLUMN,
        GRID
    }

    public final String id;
    public final Kind kind;
    private static final UiNode[] NO_CHILDREN = new UiNode[0];
    final List<UiNode> children = new ArrayList<>();
    private UiNode[] childSnapshot = NO_CHILDREN;
    final EnumMap<UiEvent.Type, Consumer<UiEvent>> handlers = new EnumMap<>(UiEvent.Type.class);
    UiNode parent;
    UiScene scene;
    Layout layout = Layout.RECT;
    double x, y, width = -1, height = -1, minWidth, minHeight, padding, gap, scrollY, contentHeight;
    int columns = 1, maxLength = 256, caret;
    boolean clipChildren, scrollable, enabled = true, visible = true, selected, pointerTransparent, disposed;
    String text = "";
    UiStyle style;
    UiRect rectangle = new UiRect(0, 0, 0, 0), clipping = rectangle;
    Runnable click, dispose;
    Consumer<String> change;
    BiConsumer<UiNode, UiRenderer> painter;

    public UiNode(String id, Kind kind) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        pointerTransparent = kind == Kind.LABEL || kind == Kind.VALUE;
        style = pointerTransparent ? UiStyle.TEXT : UiStyle.DEFAULT;
    }

    public static UiNode panel(String id) {
        return new UiNode(id, Kind.PANEL);
    }

    public static UiNode button(String id, String text, Runnable click) {
        return new UiNode(id, Kind.BUTTON).text(text)
            .onClick(click);
    }

    public static UiNode label(String id, String text) {
        return new UiNode(id, Kind.LABEL).text(text);
    }

    public static UiNode textInput(String id, String text, Consumer<String> change) {
        return new UiNode(id, Kind.TEXT_INPUT).text(text)
            .onChange(change);
    }

    public UiNode parent() {
        return parent;
    }

    public List<UiNode> children() {
        return Collections.unmodifiableList(children);
    }

    public UiRect bounds() {
        return rectangle;
    }

    public UiRect clip() {
        return clipping;
    }

    public String text() {
        return text;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean visible() {
        return visible;
    }

    public boolean selected() {
        return selected;
    }

    public boolean disposed() {
        return disposed;
    }

    public double scrollY() {
        return scrollY;
    }

    public double maxScrollY() {
        return Math.max(0, contentHeight - Math.max(0, rectangle.height - padding * 2));
    }

    /** Child ownership is exclusive. Removing a child disposes its subtree. */
    public UiNode add(UiNode child) {
        alive();
        Objects.requireNonNull(child, "child")
            .alive();
        if (child.parent != null || child.scene != null) throw new IllegalArgumentException("Child already attached");
        for (UiNode ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) throw new IllegalArgumentException("Tree cycle");
        }
        if (scene != null) scene.validateNew(child);
        child.parent = this;
        children.add(child);
        childSnapshot = null;
        child.attach(scene);
        dirty();
        return this;
    }

    public UiNode remove(UiNode child) {
        alive();
        if (children.remove(child)) {
            childSnapshot = null;
            dirty();
            try {
                if (scene != null) scene.invalidateInteraction(child);
            } finally {
                child.parent = null;
                child.disposeTree();
            }
        }
        return this;
    }

    public UiNode clear() {
        for (UiNode child : new ArrayList<>(children)) remove(child);
        return this;
    }

    public UiNode bounds(double x, double y, double width, double height) {
        finite(x);
        finite(y);
        dimension(width);
        dimension(height);
        if (this.x != x || this.y != y || this.width != width || this.height != height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            dirty();
        }
        return this;
    }

    public UiNode size(double width, double height) {
        return bounds(x, y, width, height);
    }

    public UiNode layout(Layout value) {
        layout = Objects.requireNonNull(value, "layout");
        dirty();
        return this;
    }

    public UiNode padding(double value) {
        padding = positive(value);
        dirty();
        return this;
    }

    public UiNode gap(double value) {
        gap = positive(value);
        dirty();
        return this;
    }

    public UiNode minSize(double width, double height) {
        minWidth = positive(width);
        minHeight = positive(height);
        dirty();
        return this;
    }

    public UiNode gridColumns(int count) {
        if (count < 1) throw new IllegalArgumentException("columns must be positive");
        columns = count;
        dirty();
        return this;
    }

    public UiNode overflowClip(boolean value) {
        clipChildren = value;
        dirty();
        return this;
    }

    public UiNode scrollable(boolean value) {
        scrollable = value;
        clipChildren |= value;
        dirty();
        return this;
    }

    public UiNode scrollTo(double value) {
        finite(value);
        double next = Math.max(0, Math.min(maxScrollY(), value));
        if (scrollY != next) {
            scrollY = next;
            dirty();
        }
        return this;
    }

    public UiNode text(String value) {
        Objects.requireNonNull(value, "text");
        if (!text.equals(value)) {
            text = value;
            caret = Math.min(caret, text.length());
            dirty();
        }
        return this;
    }

    public UiNode maxLength(int value) {
        if (value < 0) throw new IllegalArgumentException("maxLength must be nonnegative");
        maxLength = value;
        return this;
    }

    public UiNode style(UiStyle value) {
        style = Objects.requireNonNull(value, "style");
        return this;
    }

    public UiNode enabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            if (!value && scene != null) scene.invalidateInteraction(this);
        }
        return this;
    }

    /** Invisible nodes collapse their flow slot and cannot receive input. */
    public UiNode visible(boolean value) {
        if (visible != value) {
            visible = value;
            if (!value && scene != null) scene.invalidateInteraction(this);
            dirty();
        }
        return this;
    }

    public UiNode selected(boolean value) {
        selected = value;
        return this;
    }

    public UiNode pointerTransparent(boolean value) {
        pointerTransparent = value;
        return this;
    }

    public UiNode onClick(Runnable action) {
        click = action;
        return this;
    }

    public UiNode onChange(Consumer<String> action) {
        change = action;
        return this;
    }

    public UiNode onDraw(BiConsumer<UiNode, UiRenderer> action) {
        painter = action;
        return this;
    }

    public UiNode onDispose(Runnable action) {
        dispose = action;
        return this;
    }

    public UiNode on(UiEvent.Type type, Consumer<UiEvent> action) {
        handlers.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(action, "action"));
        return this;
    }

    public boolean focused() {
        return scene != null && scene.focused() == this;
    }

    public boolean hovered() {
        return scene != null && scene.hovered() == this;
    }

    public boolean pressed() {
        return scene != null && scene.pressed(this);
    }

    boolean focusable() {
        return kind == Kind.BUTTON || kind == Kind.TAB || kind == Kind.TEXT_INPUT;
    }

    boolean interactive() {
        for (UiNode n = this; n != null; n = n.parent) if (!n.visible || !n.enabled || n.disposed) return false;
        return true;
    }

    void attach(UiScene owner) {
        scene = owner;
        for (UiNode child : children) child.attach(owner);
    }

    /** Rebuilt only after structural changes; an active traversal stays safe when callbacks remove siblings. */
    UiNode[] stableChildren() {
        if (childSnapshot == null) childSnapshot = children.toArray(NO_CHILDREN);
        return childSnapshot;
    }

    void dirty() {
        alive();
        if (scene != null) scene.markDirty();
    }

    void alive() {
        if (disposed) throw new IllegalStateException("Disposed component: " + id);
    }

    void disposeTree() {
        if (disposed) return;
        disposed = true;
        scene = null;
        RuntimeException failure = null;
        for (UiNode child : new ArrayList<>(children)) {
            try {
                child.disposeTree();
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        children.clear();
        childSnapshot = NO_CHILDREN;
        handlers.clear();
        try {
            if (dispose != null) dispose.run();
        } catch (RuntimeException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        painter = null;
        click = null;
        change = null;
        dispose = null;
        if (failure != null) throw failure;
    }

    static double positive(double value) {
        finite(value);
        if (value < 0) throw new IllegalArgumentException("Negative size");
        return value;
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite coordinate");
    }

    private static void dimension(double value) {
        finite(value);
        if (value < 0 && value != -1) throw new IllegalArgumentException("Use -1 for automatic size");
    }
}
