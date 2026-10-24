package com.miaokatze.miaokatzecore.api.ui.component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;

/** Retained GUI engine. Hosts forward GUI input and call blur when OS focus is lost. No client classes are loaded. */
public final class UiScene implements AutoCloseable {

    private final UiNode root;
    private CanvasTransform transform = CanvasTransform.identity();
    private double width, height, pointerX, pointerY;
    private boolean dirty = true, closed;
    private long layoutRevision;
    private UiNode hovered, focused, capture, pressed;
    private int captureButton = -1, anchor;

    public UiScene(UiNode root) {
        this.root = Objects.requireNonNull(root, "root");
        root.alive();
        if (root.parent != null || root.scene != null) throw new IllegalArgumentException("Root already attached");
        collectIds(root, new HashSet<String>());
        root.attach(this);
    }

    public UiNode root() {
        return root;
    }

    public UiNode focused() {
        return focused;
    }

    public UiNode hovered() {
        return hovered;
    }

    public UiNode captured() {
        return capture;
    }

    public boolean closed() {
        return closed;
    }

    public long layoutRevision() {
        return layoutRevision;
    }

    public CanvasTransform transform() {
        return transform;
    }

    public UiScene transform(CanvasTransform value) {
        transform = Objects.requireNonNull(value, "transform");
        return this;
    }

    public UiNode find(String id) {
        return find(root, id);
    }

    private static UiNode find(UiNode node, String id) {
        if (node.id.equals(id)) return node;
        for (UiNode child : node.children) {
            UiNode result = find(child, id);
            if (result != null) return result;
        }
        return null;
    }

    void validateNew(UiNode child) {
        Set<String> ids = new HashSet<>();
        collectIds(root, ids);
        collectIds(child, ids);
    }

    private static void collectIds(UiNode node, Set<String> ids) {
        if (!ids.add(node.id)) throw new IllegalArgumentException("Duplicate component id: " + node.id);
        for (UiNode child : node.children) collectIds(child, ids);
    }

    void markDirty() {
        dirty = true;
    }

    boolean pressed(UiNode node) {
        return pressed == node;
    }

    /**
     * Layout runs only after a structural, geometry, content or viewport change. -1 child size fills available space.
     */
    public void layout(double width, double height) {
        open();
        UiNode.positive(width);
        UiNode.positive(height);
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            dirty = true;
        }
        ensureLayout();
    }

    private void ensureLayout() {
        if (!dirty) return;
        arrange(root, new UiRect(0, 0, width, height), new UiRect(0, 0, width, height));
        dirty = false;
        layoutRevision++;
    }

    private void arrange(UiNode node, UiRect bounds, UiRect inheritedClip) {
        node.rectangle = bounds;
        node.clipping = inheritedClip.intersect(bounds);
        double iw = Math.max(0, bounds.width - node.padding * 2), ih = Math.max(0, bounds.height - node.padding * 2);
        UiRect inner = new UiRect(bounds.x + node.padding, bounds.y + node.padding, iw, ih);
        UiRect childClip = node.clipChildren ? inheritedClip.intersect(inner) : inheritedClip;
        List<UiNode> visible = new ArrayList<>();
        for (UiNode child : node.children) if (child.visible) visible.add(child);
        int count = visible.size(), automatic = 0;
        double fixed = Math.max(0, count - 1) * node.gap;
        boolean row = node.layout == UiNode.Layout.ROW;
        if (row || node.layout == UiNode.Layout.COLUMN) {
            for (UiNode child : visible) {
                double size = row ? child.width : child.height;
                if (size < 0) automatic++;
                else fixed += Math.max(size, row ? child.minWidth : child.minHeight);
            }
        }
        double share = automatic == 0 ? 0 : Math.max(0, ((row ? iw : ih) - fixed) / automatic);
        double cursor = 0, rowTop = 0, rowHeight = 0, extent = 0;
        double cellWidth = Math.max(0, (iw - (node.columns - 1) * node.gap) / node.columns);
        for (int i = 0; i < count; i++) {
            UiNode child = visible.get(i);
            double cx = child.x, cy = child.y;
            double cw = child.width < 0 ? Math.max(0, iw - cx) : child.width;
            double ch = child.height < 0 ? Math.max(0, ih - cy) : child.height;
            if (node.layout == UiNode.Layout.ROW) {
                cx = cursor;
                cw = child.width < 0 ? share : child.width;
                cursor += Math.max(cw, child.minWidth) + node.gap;
            } else if (node.layout == UiNode.Layout.COLUMN) {
                cy = cursor;
                ch = child.height < 0 ? share : child.height;
                cursor += Math.max(ch, child.minHeight) + node.gap;
            } else if (node.layout == UiNode.Layout.GRID) {
                if (i > 0 && i % node.columns == 0) {
                    rowTop += rowHeight + node.gap;
                    rowHeight = 0;
                }
                cx = (i % node.columns) * (cellWidth + node.gap);
                cy = rowTop;
                cw = child.width < 0 ? cellWidth : child.width;
                ch = child.height < 0 ? Math.max(24, child.minHeight) : child.height;
                rowHeight = Math.max(rowHeight, Math.max(ch, child.minHeight));
            }
            cw = Math.max(cw, child.minWidth);
            ch = Math.max(ch, child.minHeight);
            extent = Math.max(extent, cy + ch);
            arrange(child, new UiRect(inner.x + cx, inner.y + cy - node.scrollY, cw, ch), childClip);
        }
        node.contentHeight = extent;
        double clamped = node.scrollable ? Math.min(node.scrollY, node.maxScrollY()) : 0;
        if (clamped != node.scrollY) {
            node.scrollY = clamped;
            arrange(node, bounds, inheritedClip);
        }
    }

    public void render(UiRenderer renderer) {
        open();
        ensureLayout();
        Objects.requireNonNull(renderer, "renderer");
        renderer.begin(transform);
        try {
            paint(root, renderer, true);
        } finally {
            renderer.end();
        }
    }

    private void paint(UiNode node, UiRenderer renderer, boolean ancestorsEnabled) {
        if (!node.visible || node.disposed) return;
        boolean enabled = ancestorsEnabled && node.enabled;
        if (!node.clipping.empty()) {
            renderer.pushClip(node.clipping);
            try {
                UiStyle.Paint paint = node.style.resolve(enabled, hovered == node, pressed == node, node.selected);
                UiRect rect = node.rectangle;
                if ((paint.background >>> 24) != 0) renderer.fill(rect, paint.background);
                if ((paint.border >>> 24) != 0 && rect.width > 0 && rect.height > 0) {
                    double edge = Math.min(1, Math.min(rect.width, rect.height));
                    renderer.fill(new UiRect(rect.x, rect.y, rect.width, edge), paint.border);
                    renderer.fill(new UiRect(rect.x, rect.bottom() - edge, rect.width, edge), paint.border);
                    renderer.fill(new UiRect(rect.x, rect.y, edge, rect.height), paint.border);
                    renderer.fill(new UiRect(rect.right() - edge, rect.y, edge, rect.height), paint.border);
                }
                paintText(node, renderer, paint, rect);
                if (node.painter != null) node.painter.accept(node, renderer);
            } finally {
                renderer.popClip();
            }
        }
        for (UiNode child : node.stableChildren()) paint(child, renderer, enabled);
    }

    private void paintText(UiNode node, UiRenderer renderer, UiStyle.Paint paint, UiRect rect) {
        if (node.text.isEmpty() && node.kind != UiNode.Kind.TEXT_INPUT) return;
        boolean center = node.kind == UiNode.Kind.BUTTON || node.kind == UiNode.Kind.TAB;
        double tx = center ? rect.x + Math.max(3, (rect.width - renderer.textWidth(node.text)) / 2) : rect.x + 4;
        double ty = rect.y + Math.max(2, (rect.height - renderer.textHeight()) / 2);
        if (node.kind == UiNode.Kind.TEXT_INPUT && focused == node) {
            clampSelection(node);
            double caretWidth = renderer.textWidth(node.text.substring(0, node.caret));
            tx -= Math.max(0, caretWidth - Math.max(0, rect.width - 10));
            if (anchor != node.caret) {
                double left = renderer.textWidth(node.text.substring(0, Math.min(anchor, node.caret)));
                double right = renderer.textWidth(node.text.substring(0, Math.max(anchor, node.caret)));
                renderer.fill(new UiRect(tx + left, ty, right - left, renderer.textHeight()), 0x805A9BAF);
            }
            renderer.fill(new UiRect(tx + caretWidth, ty, 1, renderer.textHeight()), paint.text);
        }
        renderer.text(node.text, tx, ty, paint.text);
    }

    public UiNode hitTest(double screenX, double screenY) {
        if (closed) return null;
        ensureLayout();
        return hit(root, transform.toLocalX(screenX), transform.toLocalY(screenY));
    }

    private UiNode hit(UiNode node, double x, double y) {
        if (!node.interactive()) return null;
        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode child = hit(node.children.get(i), x, y);
            if (child != null) return child;
        }
        return !node.pointerTransparent && node.clipping.contains(x, y) ? node : null;
    }

    public boolean pointerDown(double screenX, double screenY, int button) {
        if (closed) return false;
        updatePointer(screenX, screenY);
        if (capture != null) return true;
        UiNode target = hovered;
        focus(target != null && target.focusable() ? target : null);
        if (target == null) return false;
        UiEvent event = dispatch(UiEvent.Type.DOWN, target, button, 0);
        if (!event.consumed() && target.interactive() && button == 0 && target.focusable()) {
            if (target.kind != UiNode.Kind.TEXT_INPUT) pressed = target;
            capture(target, button);
            event.consume();
        }
        return event.consumed() || capture != null;
    }

    public boolean pointerMove(double screenX, double screenY) {
        if (closed) return false;
        updatePointer(screenX, screenY);
        UiNode target = capture != null ? capture : hovered;
        return target != null && (dispatch(UiEvent.Type.MOVE, target, captureButton, 0).consumed() || capture != null);
    }

    public boolean pointerUp(double screenX, double screenY, int button) {
        if (closed) return false;
        updatePointer(screenX, screenY);
        if (capture != null && captureButton != button) return true;
        UiNode target = capture != null ? capture : hovered;
        boolean hadCapture = capture != null;
        UiNode clicked = pressed;
        pressed = null;
        try {
            boolean consumed = target != null && dispatch(UiEvent.Type.UP, target, button, 0).consumed();
            if (clicked != null && clicked.interactive() && hovered == clicked && button == 0) {
                if (clicked.click != null) clicked.click.run();
                consumed = true;
            }
            return consumed || hadCapture;
        } finally {
            capture = null;
            captureButton = -1;
        }
    }

    /** Positive delta scrolls down in GUI pixels. At an edge, the next enclosing scroll container may handle it. */
    public boolean scroll(double screenX, double screenY, double delta) {
        if (closed) return false;
        if (!Double.isFinite(delta)) throw new IllegalArgumentException("Invalid scroll delta");
        updatePointer(screenX, screenY);
        if (hovered == null) return false;
        UiEvent event = dispatch(UiEvent.Type.SCROLL, hovered, -1, delta);
        if (event.consumed()) return true;
        for (UiNode node = hovered; node != null; node = node.parent) {
            if (node.scrollable) {
                double old = node.scrollY;
                node.scrollTo(old + delta);
                if (old != node.scrollY) return true;
            }
        }
        return false;
    }

    private void updatePointer(double screenX, double screenY) {
        ensureLayout();
        pointerX = transform.toLocalX(screenX);
        pointerY = transform.toLocalY(screenY);
        hovered = hit(root, pointerX, pointerY);
    }

    private UiEvent dispatch(UiEvent.Type type, UiNode target, int button, double wheel) {
        UiEvent event = new UiEvent(this, type, target, pointerX, pointerY, button, wheel);
        List<UiNode> path = new ArrayList<>();
        for (UiNode node = target; node != null; node = node.parent) path.add(node);
        for (UiNode node : path) {
            if (node.disposed) continue;
            event.currentTarget = node;
            Consumer<UiEvent> handler = node.handlers.get(type);
            if (handler != null) handler.accept(event);
            if (event.consumed()) break;
        }
        return event;
    }

    void capture(UiNode node, int button) {
        if (node.scene == this && node.interactive() && (capture == null || capture == node)) {
            capture = node;
            captureButton = button;
        }
    }

    void release(UiNode node) {
        if (capture == node) {
            capture = null;
            captureButton = -1;
            pressed = null;
        }
    }

    public void focus(UiNode node) {
        if (closed) return;
        if (node != null && (node.scene != this || !node.interactive() || !node.focusable())) return;
        focused = node;
        if (node != null) {
            node.caret = node.text.length();
            anchor = node.caret;
            ensureLayout();
            for (UiNode parent = node.parent; parent != null; parent = parent.parent) {
                if (!parent.scrollable) continue;
                double top = parent.rectangle.y + parent.padding;
                double bottom = parent.rectangle.bottom() - parent.padding;
                double delta = node.rectangle.y < top ? node.rectangle.y - top
                    : node.rectangle.bottom() > bottom ? node.rectangle.bottom() - bottom : 0;
                if (delta != 0) {
                    parent.scrollTo(parent.scrollY + delta);
                    ensureLayout();
                }
            }
        }
    }

    public void blur() {
        try {
            cancelCapture();
        } finally {
            focused = null;
            hovered = null;
        }
    }

    /**
     * Cancel when the host changes page, switches mode or loses input ownership. Dispatches CANCEL before detaching.
     */
    public void cancelCapture() {
        UiNode old = capture;
        int oldButton = captureButton;
        capture = null;
        captureButton = -1;
        pressed = null;
        if (old != null) dispatch(UiEvent.Type.CANCEL, old, oldButton, 0);
    }

    void invalidateInteraction(UiNode subtree) {
        try {
            if (inside(capture, subtree)) cancelCapture();
        } finally {
            if (inside(focused, subtree)) focused = null;
            if (inside(hovered, subtree)) hovered = null;
        }
    }

    private static boolean inside(UiNode node, UiNode subtree) {
        for (; node != null; node = node.parent) if (node == subtree) return true;
        return false;
    }

    /** Uses LWJGL 2 key codes. IME and clipboard hosts can supply plain text through insertText. */
    public boolean keyTyped(char character, int key, boolean shift, boolean control) {
        if (closed) return false;
        if (key == 1) {
            boolean handled = capture != null || focused != null;
            blur();
            return handled;
        }
        if (key == 15) {
            traverseFocus(shift);
            return true;
        }
        UiNode node = focused;
        if (node == null || !node.interactive()) return false;
        if (node.kind != UiNode.Kind.TEXT_INPUT) {
            if (key == 28 || key == 57 || key == 156) {
                if (node.click != null) node.click.run();
                return true;
            }
            return false;
        }
        clampSelection(node);
        if (control && key == 30) {
            anchor = 0;
            node.caret = node.text.length();
            return true;
        }
        if (key == 203) node.caret = Math.max(0, node.caret - 1);
        else if (key == 205) node.caret = Math.min(node.text.length(), node.caret + 1);
        else if (key == 199) node.caret = 0;
        else if (key == 207) node.caret = node.text.length();
        else if (key == 14 || key == 211) {
            if (anchor == node.caret) {
                if (key == 14) anchor = Math.max(0, node.caret - 1);
                else anchor = Math.min(node.text.length(), node.caret + 1);
            }
            insertText("");
            return true;
        } else if (key == 28 || key == 156) {
            focused = null;
            return true;
        } else if (character >= 32 && character != 127 && !control) {
            insertText(String.valueOf(character));
            return true;
        } else return control || key == 200 || key == 208;
        if (!shift) anchor = node.caret;
        return true;
    }

    public String selectedText() {
        UiNode node = focused;
        if (node == null || node.kind != UiNode.Kind.TEXT_INPUT) return "";
        clampSelection(node);
        return node.text.substring(Math.min(anchor, node.caret), Math.max(anchor, node.caret));
    }

    /** Edits the current selection and calls onChange only when the text actually changes. */
    public boolean insertText(String value) {
        Objects.requireNonNull(value, "text");
        UiNode node = focused;
        if (closed || node == null || node.kind != UiNode.Kind.TEXT_INPUT || !node.interactive()) return false;
        clampSelection(node);
        int start = Math.min(anchor, node.caret), end = Math.max(anchor, node.caret);
        StringBuilder clean = new StringBuilder();
        int room = Math.max(0, node.maxLength - (node.text.length() - (end - start)));
        for (int i = 0; i < value.length() && clean.length() < room; i++) {
            char c = value.charAt(i);
            if (c >= 32 && c != 127) clean.append(c);
        }
        String next = node.text.substring(0, start) + clean + node.text.substring(end);
        node.caret = start + clean.length();
        anchor = node.caret;
        if (!next.equals(node.text)) {
            node.text = next;
            markDirty();
            if (node.change != null) node.change.accept(next);
        }
        return true;
    }

    private void clampSelection(UiNode node) {
        node.caret = Math.min(node.caret, node.text.length());
        anchor = Math.min(anchor, node.text.length());
    }

    private void traverseFocus(boolean reverse) {
        List<UiNode> order = new ArrayList<>();
        focusOrder(root, order);
        if (order.isEmpty()) {
            focused = null;
            return;
        }
        int index = order.indexOf(focused);
        if (index < 0) focus(order.get(reverse ? order.size() - 1 : 0));
        else focus(order.get((index + (reverse ? order.size() - 1 : 1)) % order.size()));
    }

    private void focusOrder(UiNode node, List<UiNode> result) {
        if (!node.interactive()) return;
        if (node.focusable()) result.add(node);
        for (UiNode child : node.children) focusOrder(child, result);
    }

    private void open() {
        if (closed) throw new IllegalStateException("Scene is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            blur();
        } finally {
            root.disposeTree();
        }
    }
}
