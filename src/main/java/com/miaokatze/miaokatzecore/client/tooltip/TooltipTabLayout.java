// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** The painter and pointer share the same visible window and original option indices. */
final class TooltipTabLayout {

    static final class Label {

        final int index, x, width;
        final String text;

        Label(int index, String text, int x, int width) {
            this.index = index;
            this.text = text;
            this.x = x;
            this.width = width;
        }
    }

    final List<Label> labels = new ArrayList<>();
    final boolean before, after;
    final int end;

    TooltipTabLayout(int count, int active, int available, IntFunction<String> label, String shortened,
        ToIntFunction<String> width) {
        int first = active, last = active, span = width.applyAsInt(shortened) + 8;
        boolean left = true;
        while (first > 0 || last < count - 1) {
            boolean previous = first > 0 && (left || last == count - 1);
            int index = previous ? first - 1 : last + 1;
            int cost = width.applyAsInt(label.apply(index)) + 8;
            if (span + cost + (first > 0 ? 12 : 0) + (last < count - 1 ? 12 : 0) > available) break;
            span += cost;
            if (previous) first--;
            else last++;
            left = !left;
        }
        before = first > 0;
        after = last < count - 1;
        int pen = before ? 12 : 0;
        for (int index = first; index <= last; index++) {
            String text = index == active ? shortened : label.apply(index);
            int measured = width.applyAsInt(text);
            labels.add(new Label(index, text, pen + 3, measured));
            pen += measured + 8;
        }
        end = pen;
    }
}
