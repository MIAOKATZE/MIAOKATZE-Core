// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable, dimensionless drawing data. All units, labels, limits and samples belong to the caller. */
public final class TooltipChart implements TooltipBlock {

    public enum Kind {
        BARS,
        LINES,
        HEAT,
        COLUMNS
    }

    public final Kind kind;
    public final String title, xAxis, yAxis;
    public final TooltipTable table;
    private final double[][] data;
    private final double[] axis, previous;
    public final double maximum, xMax, yMax, markX, markY;
    public final List<String> legends, rowFluids, columnFluids, rowFactors, rowRates, columnFactors, columnRates;
    public final List<String> variants;
    public final String chartId;
    public final int active;
    public final boolean compact;
    public final int barColumn;
    public final List<Double> xTicks, yTicks;
    public final List<String> readouts;
    public final List<Integer> colors;
    public final int probeIndex;

    private static final class Options {

        double xMax, yMax, markX = Double.NaN, markY = Double.NaN;
        List<String> rf = Collections.emptyList(), rr = rf, cf = rf, cr = rf, variants = rf;
        String id = "";
        int active;
        boolean compact;
        int barColumn = -1, probeIndex = -1;
        List<Double> xTicks = Collections.emptyList(), yTicks = xTicks;
        List<String> readouts = Collections.emptyList();
        List<Integer> colors = Collections.emptyList();

        Options() {}

        Options(TooltipChart c) {
            xMax = c.xMax;
            yMax = c.yMax;
            markX = c.markX;
            markY = c.markY;
            rf = c.rowFactors;
            rr = c.rowRates;
            cf = c.columnFactors;
            cr = c.columnRates;
            variants = c.variants;
            id = c.chartId;
            active = c.active;
            compact = c.compact;
            barColumn = c.barColumn;
            xTicks = c.xTicks;
            yTicks = c.yTicks;
            probeIndex = c.probeIndex;
            readouts = c.readouts;
            colors = c.colors;
        }
    }

    private TooltipChart(Kind kind, String title, TooltipTable table, double[][] data, double[] axis, double[] previous,
        double maximum, String xAxis, String yAxis, List<String> legends, List<String> rowFluids,
        List<String> columnFluids, Options o) {
        this.kind = kind;
        this.title = Objects.requireNonNull(title);
        this.table = table;
        this.data = data;
        this.axis = axis;
        this.previous = previous;
        this.maximum = maximum;
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        this.legends = legends;
        this.rowFluids = rowFluids;
        this.columnFluids = columnFluids;
        xMax = o.xMax;
        yMax = o.yMax;
        markX = o.markX;
        markY = o.markY;
        rowFactors = o.rf;
        rowRates = o.rr;
        columnFactors = o.cf;
        columnRates = o.cr;
        variants = o.variants;
        active = o.active;
        chartId = o.id;
        compact = o.compact;
        barColumn = o.barColumn;
        xTicks = o.xTicks;
        yTicks = o.yTicks;
        probeIndex = o.probeIndex;
        readouts = o.readouts;
        colors = o.colors;
    }

    private TooltipChart options(Options o) {
        return new TooltipChart(
            kind,
            title,
            table,
            data,
            axis,
            previous,
            maximum,
            xAxis,
            yAxis,
            legends,
            rowFluids,
            columnFluids,
            o);
    }

    private static final List<String> NONE = Collections.emptyList();
    private static final double[] EMPTY = {};

    private static List<String> labels(List<String> labels, int n) {
        require(labels != null && labels.size() == n);
        for (String s : labels) Objects.requireNonNull(s);
        return Collections.unmodifiableList(new ArrayList<>(labels));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Tooltip chart dimensions or numeric bounds");
    }

    private static double[] numbers(double[] a, int n, boolean missing) {
        require(a != null && a.length == n && n > 0);
        double[] copy = a.clone();
        for (double d : copy) require(Double.isFinite(d) && d >= 0 || missing && Double.isNaN(d));
        return copy;
    }

    private static double[][] matrix(double[][] a, int rows, int cols, boolean missing) {
        require(a != null && a.length == rows && rows > 0);
        double[][] copy = new double[rows][];
        for (int r = 0; r < rows; r++) copy[r] = numbers(a[r], cols, missing);
        return copy;
    }

    /** Cumulative entries on one shared scale; the highlight starts at previous[i], if supplied. */
    public static TooltipChart bars(String title, TooltipTable table, double[] totals, double[] previous,
        double maximum) {
        int n = table.rows()
            .size();
        require(Double.isFinite(maximum) && maximum > 0);
        double[][] values = { numbers(totals, n, false) };
        double[] prior = previous == null ? EMPTY : numbers(previous, n, false);
        for (int i = 0; i < n; i++) require(values[0][i] <= maximum && (prior.length == 0 || prior[i] <= values[0][i]));
        return new TooltipChart(
            Kind.BARS,
            title,
            table,
            values,
            EMPTY,
            prior,
            maximum,
            "",
            "",
            NONE,
            NONE,
            NONE,
            new Options());
    }

    public static TooltipChart lines(String title, String xAxis, String yAxis, double[] xs, List<String> legends,
        double[][] ys) {
        require(xs.length >= 2 && xs.length <= 4096 && legends.size() <= 16);
        double[] axis = numbers(xs, xs.length, false);
        for (int i = 1; i < axis.length; i++) require(axis[i] > axis[i - 1]);
        double[][] data = matrix(ys, legends.size(), xs.length, true);
        Options o = new Options();
        o.xMax = axis[axis.length - 1];
        for (double[] series : data) for (double v : series) if (!Double.isNaN(v)) o.yMax = Math.max(o.yMax, v);
        if (o.yMax == 0) o.yMax = 1;
        return new TooltipChart(
            Kind.LINES,
            title,
            null,
            data,
            axis,
            EMPTY,
            0,
            Objects.requireNonNull(xAxis),
            Objects.requireNonNull(yAxis),
            labels(legends, data.length),
            NONE,
            NONE,
            o);
    }

    public static TooltipChart heat(String title, TooltipTable table, double[][] values, double maximum,
        List<String> rowFluids, List<String> columnFluids) {
        require(Double.isFinite(maximum) && maximum > 0);
        int rows = table.rows()
            .size(),
            cols = table.headers()
                .size() - 1;
        return new TooltipChart(
            Kind.HEAT,
            title,
            table,
            matrix(values, rows, cols, false),
            EMPTY,
            EMPTY,
            maximum,
            "",
            "",
            NONE,
            labels(rowFluids, rows),
            labels(columnFluids, cols),
            new Options());
    }

    public static TooltipChart columns(String title, TooltipTable table, double[][] values, List<String> rowFluids) {
        int rows = table.rows()
            .size(),
            cols = table.headers()
                .size() - 1;
        return new TooltipChart(
            Kind.COLUMNS,
            title,
            table,
            matrix(values, rows, cols, false),
            EMPTY,
            EMPTY,
            0,
            "",
            "",
            NONE,
            labels(rowFluids, rows),
            NONE,
            new Options());
    }

    public TooltipChart media(List<String> rowFactors, List<String> rowRates, List<String> columnFactors,
        List<String> columnRates, boolean detailed) {
        require(kind == Kind.HEAT);
        Options o = new Options(this);
        o.rf = labels(rowFactors, rowFluids.size());
        o.rr = labels(rowRates, rowFluids.size());
        o.cf = labels(columnFactors, columnFluids.size());
        o.cr = labels(columnRates, columnFluids.size());
        o.compact = !detailed;
        return options(o);
    }

    public TooltipChart compact(boolean compact) {
        Options o = new Options(this);
        o.compact = compact;
        return options(o);
    }

    public TooltipChart range(double xMax, double yMax) {
        require(kind == Kind.LINES && Double.isFinite(xMax) && xMax > 0 && Double.isFinite(yMax) && yMax > 0);
        Options o = new Options(this);
        o.xMax = xMax;
        o.yMax = yMax;
        return options(o);
    }

    public TooltipChart mark(double x, double y) {
        require(kind == Kind.LINES && Double.isFinite(x) && x >= 0 && Double.isFinite(y) && y >= 0);
        Options o = new Options(this);
        o.markX = x;
        o.markY = y;
        return options(o);
    }

    /** Draw the bar beside its value, in this zero-based table column. */
    public TooltipChart barColumn(int column) {
        require(
            kind == Kind.BARS && column >= 0
                && column < table.headers()
                    .size());
        Options o = new Options(this);
        o.barColumn = column;
        return options(o);
    }

    /** Authored ticks, exact probe sample, localized readout per series and its RGB color. */
    public TooltipChart plot(double[] xTicks, double[] yTicks, double probe, List<String> readouts, int[] colors) {
        require(kind == Kind.LINES && colors.length == legends.size());
        Options o = new Options(this);
        o.xTicks = ticks(xTicks, xMax);
        o.yTicks = ticks(yTicks, yMax);
        o.probeIndex = Arrays.binarySearch(axis, probe);
        require(o.probeIndex >= 0 && probe <= xMax);
        o.readouts = labels(readouts, legends.size());
        List<Integer> copy = new ArrayList<>();
        for (int rgb : colors) copy.add(rgb | 0xff000000);
        o.colors = Collections.unmodifiableList(copy);
        return options(o);
    }

    private static List<Double> ticks(double[] ticks, double max) {
        require(ticks.length >= 2 && ticks[0] == 0 && ticks[ticks.length - 1] == max);
        List<Double> copy = new ArrayList<>();
        double previous = -1;
        for (double tick : ticks) {
            require(Double.isFinite(tick) && tick > previous);
            copy.add(tick);
            previous = tick;
        }
        return Collections.unmodifiableList(copy);
    }

    TooltipChart variants(String id, List<String> labels, int selected) {
        require(!id.isEmpty() && !labels.isEmpty() && selected >= 0 && selected < labels.size());
        Options o = new Options(this);
        o.id = id;
        o.variants = labels(labels, labels.size());
        o.active = selected;
        return options(o);
    }

    public double value(int row, int column) {
        return data[row][column];
    }

    public double x(int point) {
        return axis[point];
    }

    public int points() {
        return axis.length;
    }

    public double previous(int row) {
        return previous.length == 0 ? value(0, row) : previous[row];
    }

    public static String number(double n) {
        return java.math.BigDecimal.valueOf(n)
            .stripTrailingZeros()
            .toPlainString();
    }

    @Override
    public List<String> fallbackLines() {
        List<String> out = new ArrayList<>();
        if (!title.isEmpty()) out.add(title);
        if (!variants.isEmpty())
            out.add("§b" + variants.get(active) + " §7(" + (active + 1) + "/" + variants.size() + ")");
        if (table != null) {
            if (!columnRates.isEmpty()) for (int c = 0; c < columnRates.size(); c++) out.add(
                table.headers()
                    .get(c + 1) + ": "
                    + columnFactors.get(c)
                    + ", "
                    + columnRates.get(c));
            if (!rowRates.isEmpty()) for (int r = 0; r < rowRates.size(); r++) out.add(
                table.rows()
                    .get(r)
                    .get(0) + ": "
                    + rowFactors.get(r)
                    + ", "
                    + rowRates.get(r));
            List<String> rows = table.fallbackLines();
            out.addAll(
                rows.subList(
                    table.title()
                        .equals(title) && !title.isEmpty() ? 1 : 0,
                    rows.size()));
        } else {
            out.add(xAxis + " / " + yAxis);
            for (int r = 0; r < data.length; r++) {
                StringBuilder s = new StringBuilder(legends.get(r)).append(": ");
                int n = Math.min(6, points());
                for (int k = 0; k < n; k++) {
                    int i = k * (points() - 1) / (n - 1);
                    if (k > 0) s.append("; ");
                    s.append(number(x(i)))
                        .append(" → ")
                        .append(Double.isNaN(value(r, i)) ? "—" : number(value(r, i)));
                }
                out.add(s.toString());
            }
            if (probeIndex >= 0) {
                for (int r = 0; r < data.length; r++)
                    out.add(xAxis + " " + number(x(probeIndex)) + ": " + legends.get(r) + " " + readouts.get(r));
            }
            if (Double.isFinite(markX)) out.add("(" + number(markX) + ", " + number(markY) + ")");
        }
        out.replaceAll(TooltipInput::plainMaterials);
        return Collections.unmodifiableList(out);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TooltipChart)) return false;
        TooltipChart c = (TooltipChart) other;
        return kind == c.kind && title.equals(c.title)
            && Objects.equals(table, c.table)
            && Arrays.deepEquals(data, c.data)
            && Arrays.equals(axis, c.axis)
            && Arrays.equals(previous, c.previous)
            && maximum == c.maximum
            && xMax == c.xMax
            && yMax == c.yMax
            && Double.compare(markX, c.markX) == 0
            && Double.compare(markY, c.markY) == 0
            && xAxis.equals(c.xAxis)
            && yAxis.equals(c.yAxis)
            && legends.equals(c.legends)
            && rowFluids.equals(c.rowFluids)
            && columnFluids.equals(c.columnFluids)
            && rowFactors.equals(c.rowFactors)
            && rowRates.equals(c.rowRates)
            && columnFactors.equals(c.columnFactors)
            && columnRates.equals(c.columnRates)
            && variants.equals(c.variants)
            && active == c.active
            && chartId.equals(c.chartId)
            && compact == c.compact
            && barColumn == c.barColumn
            && probeIndex == c.probeIndex
            && xTicks.equals(c.xTicks)
            && yTicks.equals(c.yTicks)
            && readouts.equals(c.readouts)
            && colors.equals(c.colors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, title, table, chartId, active, Arrays.deepHashCode(data));
    }
}
