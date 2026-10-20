package com.miaokatze.miaokatzecore.client.fx;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/** Allocation-free cube geometry. Callers own GL state, batches and effect budgets. */
public final class VoxelGeometry {

    private static final int[] IMMEDIATE = { 0, 4, 5, 1, 2, 3, 7, 6, 0, 1, 3, 2, 4, 6, 7, 5, 0, 2, 6, 4, 1, 5, 7, 3 };
    private static final int[][] TESSELLATED = { { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 }, { 2, 3, 7, 6 },
        { 0, 2, 6, 4 }, { 1, 5, 7, 3 } };

    private VoxelGeometry() {}

    public static void box(double x, double y, double z, double size) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int corner : IMMEDIATE) {
            GL11.glVertex3d(
                x + ((corner & 1) == 0 ? -size : size),
                y + ((corner & 4) == 0 ? -size : size),
                z + ((corner & 2) == 0 ? -size : size));
        }
        GL11.glEnd();
    }

    public static void tessellatedCube(Tessellator tess, double x, double y, double z, double radius, float red,
        float green, float blue, float alpha, float[] faceShade) {
        if (faceShade == null || faceShade.length != 6) throw new IllegalArgumentException("Six face shades required");
        for (int side = 0; side < TESSELLATED.length; side++) {
            float shade = faceShade[side];
            tess.setColorRGBA_F(red * shade, green * shade, blue * shade, alpha);
            for (int corner : TESSELLATED[side]) {
                tess.addVertex(
                    x + ((corner & 1) == 0 ? -radius : radius),
                    y + ((corner & 4) == 0 ? -radius : radius),
                    z + ((corner & 2) == 0 ? -radius : radius));
            }
        }
    }
}
