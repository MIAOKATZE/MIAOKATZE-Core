// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;

/** One immutable table snapshot; units stay in their cells and every cell participates in layout. */
public final class TooltipTable implements TooltipBlock {

    public enum Alignment {
        LEFT,
        RIGHT
    }

    private final String title;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final List<Alignment> alignments;
    private final TooltipArt art;

    private TooltipTable(String title, List<String> headers, List<List<String>> rows, List<Alignment> alignments,
        TooltipArt art) {
        this.title = cell(title);
        if (headers == null || headers.isEmpty() || headers.size() > 8)
            throw new IllegalArgumentException("1–8 table columns required");
        this.headers = cells(headers);
        if (rows == null || rows.isEmpty() || rows.size() > 128)
            throw new IllegalArgumentException("1–128 table rows required");
        List<List<String>> copy = new ArrayList<>();
        for (List<String> row : rows) {
            if (row == null || row.size() != headers.size())
                throw new IllegalArgumentException("Every row must have one cell per column");
            copy.add(cells(row));
        }
        this.rows = Collections.unmodifiableList(copy);
        this.alignments = Collections.unmodifiableList(new ArrayList<>(alignments));
        this.art = Objects.requireNonNull(art, "art");
    }

    public static TooltipTable of(String title, List<String> headers, List<List<String>> rows) {
        List<Alignment> alignments = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) alignments.add(i == 0 ? Alignment.LEFT : Alignment.RIGHT);
        return new TooltipTable(title, headers, rows, alignments, TooltipArt.PLAIN);
    }

    public TooltipTable withArt(TooltipArt art) {
        return new TooltipTable(title, headers, rows, alignments, art);
    }

    public TooltipTable align(int column, Alignment alignment) {
        List<Alignment> next = new ArrayList<>(alignments);
        next.set(column, Objects.requireNonNull(alignment));
        return new TooltipTable(title, headers, rows, next, art);
    }

    public TooltipTable slice(int from, int to) {
        return new TooltipTable(title, headers, rows.subList(from, to), alignments, art);
    }

    public String title() {
        return title;
    }

    public List<String> headers() {
        return headers;
    }

    public List<List<String>> rows() {
        return rows;
    }

    public Alignment alignment(int column) {
        return alignments.get(column);
    }

    public TooltipArt art() {
        return art;
    }

    @Override
    public List<String> fallbackLines() {
        List<String> result = new ArrayList<>();
        if (!title.isEmpty()) result.add(title);
        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) line.append("  |  ");
                line.append(headers.get(i))
                    .append(": ")
                    .append(row.get(i));
            }
            result.add(line.toString());
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> cells(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String s : source) result.add(cell(s));
        return Collections.unmodifiableList(result);
    }

    private static String cell(String s) {
        Objects.requireNonNull(s, "table text");
        if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0 || s.length() > 2048)
            throw new IllegalArgumentException("Table cells must be single-line text up to 2048 characters");
        return s;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, headers, rows, alignments, art);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TooltipTable)) return false;
        TooltipTable t = (TooltipTable) other;
        return title.equals(t.title) && headers.equals(t.headers)
            && rows.equals(t.rows)
            && alignments.equals(t.alignments)
            && art == t.art;
    }
}
