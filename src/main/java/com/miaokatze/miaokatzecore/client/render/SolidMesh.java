package com.miaokatze.miaokatzecore.client.render;

import net.minecraft.client.renderer.Tessellator;

/** Fixed-function solid primitives. ring/box own a Tessellator batch; cubeVertices appends to a caller batch. */
public final class SolidMesh {

    private SolidMesh() {}

    public static void squareRing(double inner, double outer, double thick, int rgb, float alpha) {
        box(-outer, -outer, -thick, -inner, outer, thick, rgb, alpha);
        box(inner, -outer, -thick, outer, outer, thick, rgb, alpha);
        box(-inner, -outer, -thick, inner, -inner, thick, rgb, alpha);
        box(-inner, inner, -thick, inner, outer, thick, rgb, alpha);
    }

    public static void ring(double inner, double outer, double front, double back, int rgb, float alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setBrightness(0xF000F0);
        color(tess, rgb, alpha);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            double b = (i + 1) * Math.PI / 24;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            quad(
                tess,
                ca * inner,
                sa * inner,
                front,
                ca * outer,
                sa * outer,
                front,
                cb * outer,
                sb * outer,
                front,
                cb * inner,
                sb * inner,
                front);
            quad(
                tess,
                cb * inner,
                sb * inner,
                back,
                cb * outer,
                sb * outer,
                back,
                ca * outer,
                sa * outer,
                back,
                ca * inner,
                sa * inner,
                back);
            quad(
                tess,
                ca * outer,
                sa * outer,
                front,
                ca * outer,
                sa * outer,
                back,
                cb * outer,
                sb * outer,
                back,
                cb * outer,
                sb * outer,
                front);
            if (inner > 0) quad(
                tess,
                ca * inner,
                sa * inner,
                back,
                ca * inner,
                sa * inner,
                front,
                cb * inner,
                sb * inner,
                front,
                cb * inner,
                sb * inner,
                back);
        }
        tess.draw();
    }

    private static void color(Tessellator tess, int rgb, float alpha) {
        tess.setColorRGBA_F(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
    }

    public static void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setBrightness(0xF000F0);
        color(tess, rgb, alpha);
        cubeVertices(tess, x0, y0, z0, x1, y1, z1);
        tess.draw();
    }

    public static void cubeVertices(Tessellator t, double a, double b, double c, double d, double e, double f) {
        quad(t, a, b, c, a, e, c, d, e, c, d, b, c);
        quad(t, d, b, f, d, e, f, a, e, f, a, b, f);
        quad(t, a, b, f, a, e, f, a, e, c, a, b, c);
        quad(t, d, b, c, d, e, c, d, e, f, d, b, f);
        quad(t, a, e, c, a, e, f, d, e, f, d, e, c);
        quad(t, a, b, f, a, b, c, d, b, c, d, b, f);
    }

    private static void quad(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, double x2,
        double y2, double z2, double x3, double y3, double z3) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x2, y2, z2);
        t.addVertex(x3, y3, z3);
    }
}
