// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Chart coordinates are GUI pixels. Glyphs, axes, tabs and data are measured in the same font context. */
public final class NativeChartComponent implements ITooltipComponent {

    private static final int[] COLORS = { 0xff87ccce, 0xffe1ad69, 0xffbc94d3, 0xff9cbd76, 0xff8c9bd6 };
    private final String item;
    private final TooltipChart chart;
    private final int from, to, limit;
    private FontRenderer measuredFont;
    private boolean measuredUnicode;
    private int[] widths;
    private final java.util.Map<String, TooltipInput> marked = new java.util.HashMap<>();
    private NativeTooltipMouse.Drawing drawing;

    public NativeChartComponent(String item, TooltipChart chart) {
        this(
            item,
            chart,
            0,
            chart.table == null ? 0
                : chart.table.rows()
                    .size(),
            Integer.MAX_VALUE);
    }

    private NativeChartComponent(String item, TooltipChart chart, int from, int to, int limit) {
        this.item = item;
        this.chart = chart;
        this.from = from;
        this.to = to;
        this.limit = limit;
    }

    public TooltipChart chart() {
        return chart;
    }

    private static int font() {
        return TooltipFontContext.getFontHeight();
    }

    private int width(String text) {
        return TooltipInput.hasMaterial(text) ? marked.computeIfAbsent(text, TooltipInput::materials)
            .width(TooltipFontContext::getStringWidth) : TooltipFontContext.getStringWidth(text);
    }

    private void text(String s, int x, int y) {
        if (TooltipInput.hasMaterial(s)) NativeInputComponent.drawRuns(
            item,
            marked.computeIfAbsent(s, TooltipInput::materials)
                .runs(),
            x,
            y,
            0xffffffff,
            drawing);
        else TooltipFontContext.drawString(s, x, y);
    }

    private static void box(int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) Gui.drawRect(x, y, x + w, y + h, color);
    }

    private static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(hi, n));
    }

    private static int scale(double v, double max, int pixels) {
        return max <= 0 ? 0 : (int) Math.round(Math.min(1, v / max) * pixels);
    }

    private static Fluid fluid(String id) {
        return id.isEmpty() ? null : FluidRegistry.getFluid(id);
    }

    private static boolean hasIcon(String id) {
        Fluid f = fluid(id);
        return f != null && f.getStillIcon() != null
            && !"missingno".equals(
                f.getStillIcon()
                    .getIconName());
    }

    private void icon(String id, int x, int y) {
        Fluid f = fluid(id);
        if (f == null) return;
        IIcon sprite = f.getStillIcon();
        if (sprite == null) return;
        int c = f.getColor();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
            Minecraft.getMinecraft().currentScreen.drawTexturedModelRectFromIcon(x, y, sprite, 10, 10);
        } finally {
            GL11.glPopAttrib();
        }
        drawing.material(MaterialSprite.fluid(id), x, y, 10, 10);
    }

    private void fluidName(String id, String name, int x, int y) {
        text(name, x, y);
        if (!TooltipInput.hasMaterial(name)) drawing.material(MaterialSprite.fluid(id), x, y, width(name), font());
    }

    private int tabsHeight() {
        return chart.variants.isEmpty() ? 0 : font() + 5;
    }

    private int titleHeight() {
        return chart.title.isEmpty() ? 0 : font() + 2;
    }

    private boolean medium() {
        return !chart.rowRates.isEmpty();
    }

    private int headerHeight() {
        return medium() && !chart.compact ? 3 * font() + 5 : font() + 4;
    }

    private int rowHeight() {
        return font() + (chart.kind == TooltipChart.Kind.BARS && chart.barColumn < 0 ? 9 : 4);
    }

    private int overhead() {
        return tabsHeight() + titleHeight() + headerHeight();
    }

    private String rowLabel(int r) {
        return chart.table.rows()
            .get(r)
            .get(0);
    }

    private int colWidth(int col) {
        // Chromatic creates a component per tooltip context. A font may still change across measurement passes.
        FontRenderer font = TooltipFontContext.getFontRenderer();
        if (widths == null || font != measuredFont || font.getUnicodeFlag() != measuredUnicode) {
            measuredFont = font;
            measuredUnicode = font.getUnicodeFlag();
            widths = new int[chart.table.headers()
                .size()];
            for (int i = 0; i < widths.length; i++) widths[i] = measureColumn(i);
        }
        return widths[col];
    }

    private int measureColumn(int col) {
        TooltipTable t = chart.table;
        int w = width(
            t.headers()
                .get(col));
        if (chart.kind == TooltipChart.Kind.BARS) {
            for (List<String> row : t.measurementRows()) w = Math.max(w, width(row.get(col)));
            if (chart.barColumn == col) w += 32;
        } else if (col == 0) {
            for (int r = 0; r < t.rows()
                .size(); r++) {
                boolean icon = hasIcon(chart.rowFluids.get(r)) && !TooltipInput.hasMaterial(rowLabel(r));
                int name = (!chart.compact || !icon) ? width(rowLabel(r)) + 3 : 0;
                int badge = medium() ? width(chart.rowFactors.get(r)) + 4 : 0;
                int rate = (medium() && !chart.compact) ? width(chart.rowRates.get(r)) + 4 : 0;
                w = Math.max(w, (icon ? 13 : 0) + badge + name + rate);
            }
        } else {
            if (chart.kind == TooltipChart.Kind.HEAT && medium()) {
                boolean icon = hasIcon(chart.columnFluids.get(col - 1)) && !TooltipInput.hasMaterial(
                    t.headers()
                        .get(col));
                if (chart.compact && icon) w = 0;
                w = Math.max(w, (icon ? 13 : 0) + width(chart.columnFactors.get(col - 1)));
                if (!chart.compact) w = Math.max(w, width(chart.columnRates.get(col - 1)));
                if (chart.compact && !icon) w = width(
                    t.headers()
                        .get(col))
                    + width(chart.columnFactors.get(col - 1))
                    + 4;
            }
            for (List<String> row : t.measurementRows())
                w = Math.max(w, width(row.get(col)) + (chart.kind == TooltipChart.Kind.COLUMNS ? 30 : 0));
        }
        return w + 7;
    }

    private int naturalWidth() {
        int w;
        if (chart.kind == TooltipChart.Kind.LINES) {
            w = Math.max(chart.probeIndex >= 0 ? 280 : 215, Math.max(width(chart.xAxis), width(chart.yAxis)) + 36);
            for (String l : chart.legends) w = Math.max(w, width(l) + 18);
        } else {
            w = 4;
            for (int c = 0; c < chart.table.headers()
                .size(); c++) w += colWidth(c);
            w = Math.max(w, chart.kind == TooltipChart.Kind.BARS ? 270 : 154);
        }
        return Math.max(w, width(chart.title) + 6);
    }

    @Override
    public int getWidth() {
        return Math.min(limit, naturalWidth());
    }

    private float fit() {
        return chart.table == null ? 1 : Math.min(1, limit / (float) naturalWidth());
    }

    private int lineHeight() {
        if (chart.probeIndex >= 0) return 70 + 2 * font() + readouts(0, 0, getWidth(), false);

        return (chart.legends.size() + 2) * font() + 73;
    }

    @Override
    public int getHeight() {
        return chart.kind == TooltipChart.Kind.LINES ? tabsHeight() + titleHeight() + lineHeight()
            : (int) Math.ceil(fit() * (overhead() + (to - from) * rowHeight()));
    }

    @Override
    public int getSpacing() {
        return 3;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int maxWidth, int maxHeight) {
        int min = chart.kind == TooltipChart.Kind.LINES ? Math.max(145, width(chart.xAxis) + 18)
            : (int) Math.ceil(.72 * naturalWidth());
        if (maxWidth < min) {
            // Long localized tables can be taller than a whole screen. Give Chromatic individual rows to paginate.
            List<String> wrapped = new java.util.ArrayList<>();
            for (String line : chart.fallbackLines())
                wrapped.addAll(TooltipFontContext.listFormattedStringToWidth(line, Math.max(1, maxWidth)));
            int n = Math.max(1, maxHeight / font());
            if (n < wrapped.size()) return new ITooltipComponent[] { new TextComponent(wrapped.subList(0, n)),
                new ChartTextPage(wrapped.subList(n, wrapped.size())) };
            return new ITooltipComponent[] { new TextComponent(wrapped) };
        }
        float scale = chart.table == null ? 1 : Math.min(1, maxWidth / (float) naturalWidth());
        int rows = ((int) (maxHeight / scale) - overhead()) / rowHeight();
        if (chart.table != null && rows > 0 && from + rows < to)
            return new ITooltipComponent[] { new NativeChartComponent(item, chart, from, from + rows, maxWidth),
                new NativeChartComponent(item, chart, from + rows, to, maxWidth) };
        return new ITooltipComponent[] {
            limit == maxWidth ? this : new NativeChartComponent(item, chart, from, to, maxWidth) };
    }

    private static final class ChartTextPage extends TextComponent {

        ChartTextPage(List<String> lines) {
            super(lines);
        }

        @Override
        public ITooltipComponent[] paginate(TooltipContext context, int maxWidth, int maxHeight) {
            int n = Math.max(1, maxHeight / font());
            return n >= textLines.size() ? new ITooltipComponent[] { this }
                : new ITooltipComponent[] { new TextComponent(textLines.subList(0, n)),
                    new ChartTextPage(textLines.subList(n, textLines.size())) };
        }
    }

    private String tab(int index) {
        String label = chart.variants.get(index);
        return TooltipInput.hasMaterial(label) ? label : "[" + label + "]";
    }

    private void tabs(int x, int y, int w, TooltipContext context, int originX, int originY, float scale) {
        int n = chart.variants.size(), a = chart.active;
        String active = tab(a);
        int max = w - 32;
        if (width(active) > max) {
            String name = TooltipInput.materials(chart.variants.get(a))
                .plain()
                .trim();
            if (name.startsWith("[") && name.endsWith("]")) name = name.substring(1, name.length() - 1);
            while (name.length() > 1 && width("[" + name + "…]") > max) name = name.substring(0, name.length() - 1);
            active = "[" + name + "…]";
        }
        TooltipTabLayout layout = new TooltipTabLayout(n, a, w, this::tab, active, this::width);
        if (layout.before) text("§8…", x, y + 1);
        for (TooltipTabLayout.Label label : layout.labels) {
            NativeInputComponent
                .text(item, label.text, x + label.x, y + 1, label.index == a ? 0xffffe093 : 0xffa9a1b1, drawing);
            if (n > 1) NativeTooltipMouse.chartLabel(
                context,
                item,
                chart.chartId,
                label.index,
                label.text,
                originX + (x + label.x) * scale,
                originY + (y + 1) * scale,
                scale);
        }
        if (layout.after) text("§8…", x + layout.end, y + 1);
    }

    @Override
    public void draw(int x, int y, int available, TooltipContext context) {
        if (!chart.variants.isEmpty()) ChartPointer.anchor(context, item, chart.chartId, y);
        float scale = fit();
        drawing = scale < 1 ? new NativeTooltipMouse.Drawing(context, x, y, scale)
            : new NativeTooltipMouse.Drawing(context);
        try {
            if (scale < 1) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(x, y, 0);
                    GL11.glScalef(scale, scale, 1);
                    drawContent(0, 0, naturalWidth(), context, x, y, scale);
                } finally {
                    GL11.glPopMatrix();
                }
            } else drawContent(x, y, Math.max(getWidth(), Math.min(available, limit)), context, 0, 0, 1);
        } finally {
            drawing = null;
        }
    }

    private void drawContent(int x, int y, int w, TooltipContext context, int originX, int originY, float scale) {
        if (!chart.variants.isEmpty()) {
            tabs(x + 2, y, w - 4, context, originX, originY, scale);
            y += tabsHeight();
        }
        if (!chart.title.isEmpty()) {
            text(chart.title, x + 3, y);
            y += titleHeight();
        }
        if (chart.kind == TooltipChart.Kind.LINES) {
            drawLines(x, y, w);
            return;
        }
        TooltipTable t = chart.table;
        int count = t.headers()
            .size();
        int[] edges = new int[count + 1];
        edges[0] = x + 2;
        int width = 4;
        for (int i = 0; i < count; i++) width += colWidth(i);
        int extra = Math.max(0, w - width);
        for (int i = 0; i < count; i++)
            edges[i + 1] = edges[i] + colWidth(i) + extra * (i + 1) / count - extra * i / count;
        for (int c = 0; c < count; c++) {
            String label = t.headers()
                .get(c);
            int xx = edges[c] + 2;
            if (c > 0 && medium()) {
                String id = chart.columnFluids.get(c - 1);
                boolean has = hasIcon(id) && !TooltipInput.hasMaterial(label);
                if (has) icon(id, xx, y);
                String badge = chart.columnFactors.get(c - 1);
                if (!has && chart.compact) {
                    fluidName(id, label, xx, y + 1);
                    text(badge, xx + width(label + " "), y + 1);
                } else text(badge, xx + (has ? 13 : 0), y + 1);
                if (!chart.compact) {
                    fluidName(id, "§b" + label, xx, y + font() + 1);
                    text("§7" + chart.columnRates.get(c - 1), xx, y + 2 * font() + 1);
                }
            } else text("§7" + label, xx, y + 1);
        }
        y += headerHeight();
        box(x + 2, y - 2, w - 4, 1, 0xff513950);
        for (int r = from; r < to; r++) {
            for (int c = 0; c < count; c++) {
                int xx = edges[c] + 2, cw = edges[c + 1] - edges[c] - 4;
                String s = t.rows()
                    .get(r)
                    .get(c);
                if (c == 0 && !chart.rowFluids.isEmpty()) {
                    String id = chart.rowFluids.get(r);
                    boolean has = hasIcon(id) && !TooltipInput.hasMaterial(s);
                    if (has) {
                        icon(id, xx, y);
                        xx += 13;
                    }
                    if (medium()) {
                        String factor = chart.rowFactors.get(r);
                        text(factor, xx, y + 1);
                        xx += width(factor) + 4;
                    }
                    if (!chart.compact || !has) {
                        fluidName(id, s, xx, y + 1);
                        xx += width(s) + 4;
                    }
                    if (medium() && !chart.compact) text("§7" + chart.rowRates.get(r), xx, y + 1);
                } else if (c == chart.barColumn && chart.kind == TooltipChart.Kind.BARS) {
                    int bw = 28, bx = edges[c + 1] - bw - 3;
                    text(s, xx, y + 1);
                    box(bx, y + 3, bw, 4, 0xff33283c);
                    box(bx, y + 3, scale(chart.value(0, r), chart.maximum, bw), 4, 0xff62b8ab);
                } else if (c > 0 && chart.kind == TooltipChart.Kind.HEAT) {
                    double f = Math.min(1, chart.value(r, c - 1) / chart.maximum);
                    int color = (255 << 24) | ((36 + (int) (149 * f)) << 16)
                        | ((38 + (int) (178 * f)) << 8)
                        | (55 + (int) (131 * f));
                    if (!medium()) color = 0xff000000 | ((15 + (int) Math.round(38 * f)) << 16)
                        | ((24 + (int) Math.round(80 * f)) << 8)
                        | (34 + (int) Math.round(77 * f));
                    box(edges[c], y, cw + 3, rowHeight() - 1, color);
                    NativeInputComponent
                        .text(item, s, xx + 1, y + 1, medium() && f > 0.47 ? 0xff11131a : 0xfff0f5ef, drawing);
                } else if (c > 0 && chart.kind == TooltipChart.Kind.COLUMNS) {
                    double max = 0;
                    int figures = 0;
                    for (int j = 0; j < t.rows()
                        .size(); j++) {
                        max = Math.max(max, chart.value(j, c - 1));
                        figures = Math.max(
                            figures,
                            width(
                                t.rows()
                                    .get(j)
                                    .get(c)));
                    }
                    text(s, xx + figures - width(s), y + 1);
                    int bx = xx + figures + 3, bw = cw - figures - 3;
                    box(bx, y + 3, bw, 4, 0xff352e3c);
                    box(bx, y + 3, scale(chart.value(r, c - 1), max, bw), 4, COLORS[(c - 1) % COLORS.length]);
                } else {
                    if (t.alignment(c) == TooltipTable.Alignment.RIGHT) xx += cw - width(s);
                    text(s, xx, y + 1);
                }
            }
            if (chart.kind == TooltipChart.Kind.BARS && chart.barColumn < 0) {
                int px = x + 3, py = y + font() + 2, bw = w - 6, n = scale(chart.value(0, r), chart.maximum, bw),
                    prior = scale(chart.previous(r), chart.maximum, bw);
                box(px, py, bw, 3, 0xff302a38);
                box(px, py, prior, 3, 0xff68989c);
                box(px + prior, py, n - prior, 3, 0xffe4bf73);
            }
            y += rowHeight();
        }
    }

    private static void segment(Tessellator mesh, int x, int y, int x1, int y1) {
        int dx = Math.abs(x1 - x), sx = x < x1 ? 1 : -1, dy = -Math.abs(y1 - y), sy = y < y1 ? 1 : -1, e = dx + dy;
        while (true) {
            mesh.addVertex(x, y, 0);
            mesh.addVertex(x, y + 1, 0);
            mesh.addVertex(x + 1, y + 1, 0);
            mesh.addVertex(x + 1, y, 0);
            if (x == x1 && y == y1) break;
            int e2 = 2 * e;
            if (e2 >= dy) {
                e += dy;
                x += sx;
            }
            if (e2 <= dx) {
                e += dx;
                y += sy;
            }
        }
    }

    private int seriesColor(int series) {
        return chart.colors.isEmpty() ? COLORS[series % COLORS.length] : chart.colors.get(series);
    }

    private void drawSeries(int left, int right, int top, int bottom) {
        // Batch the complete rasterized plot; individual pixels must not become individual GL draws.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            Tessellator mesh = Tessellator.instance;
            mesh.startDrawingQuads();
            for (int s = 0; s < chart.legends.size(); s++) {
                int px = 0, py = 0;
                boolean valid = false;
                mesh.setColorOpaque_I(seriesColor(s));
                for (int i = 0; i < chart.points(); i++) {
                    double val = chart.value(s, i);
                    if (Double.isNaN(val) || chart.x(i) > chart.xMax || val > chart.yMax) {
                        valid = false;
                        continue;
                    }
                    int xx = left + scale(chart.x(i), chart.xMax, right - left),
                        yy = bottom - scale(val, chart.yMax, bottom - top);
                    segment(mesh, valid ? px : xx, valid ? py : yy, xx, yy);
                    px = xx;
                    py = yy;
                    valid = true;
                }
            }
            mesh.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static String tick(double value) {
        return value >= 1e6 ? TooltipChart.number(value / 1e6) + "M"
            : value >= 1e3 ? TooltipChart.number(value / 1e3) + "k" : TooltipChart.number(value);
    }

    /** Same flow for measurement and paint; the colored legend is the numerical probe readout. */
    private int readouts(int x, int y, int w, boolean draw) {
        String prefix = net.minecraft.util.StatCollector
            .translateToLocalFormatted("miaokatzecore.page.probe", tick(chart.x(chart.probeIndex)));
        int pen = width(prefix) + 2, off = 0;
        if (draw) text(prefix, x, y);
        for (int i = 0; i < chart.legends.size(); i++) {
            String readout = chart.legends.get(i) + " " + chart.readouts.get(i);
            int ww = width(readout) + 10;
            if (pen > 0 && pen + ww > w) {
                off += font();
                pen = 0;
            }
            if (draw) {
                box(x + pen, y + off + 3, 5, 2, seriesColor(i));
                NativeInputComponent.text(item, readout, x + pen + 7, y + off, seriesColor(i), drawing);
            }
            pen += ww;
        }
        return off + font();
    }

    private void drawProbePlot(int x, int y, int w) {
        text("§7" + chart.yAxis, x, y);
        int top = y + font() + 4, bottom = top + 50, left = x, right = x + w - 4;
        for (double v : chart.yTicks) left = Math.max(left, x + width(tick(v)) + 5);
        for (double v : chart.yTicks) {
            int yy = bottom - scale(v, chart.yMax, bottom - top);
            box(left, yy, right - left, 1, 0xff49394c);
            text("§7" + tick(v), x, yy - 3);
        }
        box(left, top, 1, bottom - top + 1, 0xff988a9d);
        int last = x - 3;
        for (double v : chart.xTicks) {
            int xx = left + scale(v, chart.xMax, right - left);
            String label = tick(v);
            int at = clamp(xx - width(label) / 2, x, x + w - width(label));
            box(xx, bottom, 1, 3, 0xff988a9d);
            // Reserve the end label in advance on a narrow viewport.
            int end = x + w - width(tick(chart.xMax));
            if (at < last + 2 || (v != chart.xMax && at + width(label) + 2 > end)) continue;
            text("§7" + label, at, bottom + 5);
            last = at + width(label);
        }
        int index = chart.probeIndex;
        int xx = left + scale(chart.x(index), chart.xMax, right - left);
        for (int yy = top; yy < bottom; yy += 4) box(xx, yy, 1, 2, 0xff877481);
        drawSeries(left, right, top, bottom);
        for (int i = 0; i < chart.legends.size(); i++) {
            double value = chart.value(i, index);
            if (Double.isFinite(value) && value <= chart.yMax)
                box(xx - 1, bottom - scale(value, chart.yMax, bottom - top) - 1, 3, 3, seriesColor(i));
        }
        int axis = y + font() + 66;
        text("§7" + chart.xAxis, x, axis);
        readouts(x, axis + font(), w, true);
    }

    private void drawLines(int x, int y, int w) {
        if (chart.probeIndex >= 0) {
            drawProbePlot(x, y, w);
            return;
        }
        for (int s = 0; s < chart.legends.size(); s++) {
            box(x + 2, y + 4, 9, 2, COLORS[s % COLORS.length]);
            text(chart.legends.get(s), x + 14, y);
            y += font();
        }
        text("§7" + chart.yAxis, x + 2, y);
        y += font() + 2;
        int left = x + Math.max(18, width(TooltipChart.number(chart.yMax)) + 4), right = x + w - 6, bottom = y + 52;
        for (int i = 0; i <= 2; i++) {
            int yy = bottom - i * 26;
            box(left, yy, right - left, 1, 0xff393342);
            String n = TooltipChart.number(chart.yMax * i / 2.0);
            text("§7" + n, left - 2 - width(n), yy - 3);
        }
        box(left, y, 1, 53, 0xff9a919d);
        box(left, bottom, right - left + 1, 1, 0xff9a919d);
        drawSeries(left, right, y, bottom);
        if (Double.isFinite(chart.markX) && chart.markX <= chart.xMax && chart.markY <= chart.yMax) {
            int xx = left + scale(chart.markX, chart.xMax, right - left),
                yy = bottom - scale(chart.markY, chart.yMax, 52);
            box(xx - 1, yy - 1, 3, 3, 0xffffd281);
            String mark = "(" + TooltipChart.number(chart.markX) + ", " + TooltipChart.number(chart.markY) + ")";
            text("§e" + mark, clamp(xx + 3, left, right - width(mark)), clamp(yy - font(), y, bottom - font()));
        }
        text("§70", left, bottom + 2);
        String last = TooltipChart.number(chart.xMax);
        text("§7" + last, right - width(last), bottom + 2);
        text("§7" + chart.xAxis, left + (right - left - width(chart.xAxis)) / 2, bottom + 2 + font());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NativeChartComponent && ((NativeChartComponent) o).item.equals(item)
            && chart.equals(((NativeChartComponent) o).chart)
            && from == ((NativeChartComponent) o).from
            && to == ((NativeChartComponent) o).to
            && limit == ((NativeChartComponent) o).limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, chart, from, to, limit);
    }
}
