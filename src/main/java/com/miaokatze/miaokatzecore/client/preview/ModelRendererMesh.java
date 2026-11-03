package com.miaokatze.miaokatzecore.client.preview;

import java.lang.reflect.Field;
import java.util.Objects;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Reads vanilla model geometry without rendering, compiling display lists or changing a Tessellator batch. */
@SideOnly(Side.CLIENT)
public final class ModelRendererMesh {

    private static final Field QUADS = ReflectionHelper.findField(ModelBox.class, "quadList", "field_78254_i");
    private static final Field INVERT_NORMAL = ReflectionHelper
        .findField(TexturedQuad.class, "invertNormal", "field_78238_c");

    private ModelRendererMesh() {}

    /** Consecutive groups of four vertices form a quad; UVs retain the native model's normalized coordinates. */
    public interface VertexSink {

        void vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz);
    }

    /**
     * Applies vanilla render(scale)'s offsets, pivots and Z/Y/X rotations, including child models and mirrored boxes.
     * The caller owns textures, coloring, outer pose and any submission batch. The model is only read.
     *
     * @since 0.2.5
     */
    public static void emit(ModelRenderer root, float scale, VertexSink sink) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(sink, "sink");
        if (!Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("positive finite model scale required");
        emit(root, scale, new double[] { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0 }, sink);
    }

    private static void emit(ModelRenderer part, float scale, double[] parent, VertexSink sink) {
        if (part.isHidden || !part.showModel) return;
        double[] pose = parent.clone();
        translate(pose, part.offsetX, part.offsetY, part.offsetZ);
        translate(pose, part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale);
        rotate(pose, 0, 1, part.rotateAngleZ);
        rotate(pose, 2, 0, part.rotateAngleY);
        rotate(pose, 1, 2, part.rotateAngleX);
        try {
            for (Object raw : part.cubeList) {
                ModelBox box = (ModelBox) raw;
                for (TexturedQuad quad : (TexturedQuad[]) QUADS.get(box)) emit(quad, scale, pose, sink);
            }
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Cannot read native model geometry", inaccessible);
        }
        if (part.childModels != null)
            for (Object child : part.childModels) emit((ModelRenderer) child, scale, pose, sink);
    }

    private static void translate(double[] pose, double x, double y, double z) {
        for (int row = 0; row < 12; row += 4) pose[row + 3] += pose[row] * x + pose[row + 1] * y + pose[row + 2] * z;
    }

    private static void rotate(double[] pose, int a, int b, float angle) {
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        for (int row = 0; row < 12; row += 4) {
            double x = pose[row + a], y = pose[row + b];
            pose[row + a] = x * cosine + y * sine;
            pose[row + b] = y * cosine - x * sine;
        }
    }

    private static void emit(TexturedQuad quad, float scale, double[] pose, VertexSink sink)
        throws IllegalAccessException {
        double[][] points = new double[4][3];
        for (int i = 0; i < points.length; i++) {
            PositionTextureVertex vertex = quad.vertexPositions[i];
            float x = (float) vertex.vector3D.xCoord * scale;
            float y = (float) vertex.vector3D.yCoord * scale;
            float z = (float) vertex.vector3D.zCoord * scale;
            for (int axis = 0; axis < 3; axis++) {
                int row = axis * 4;
                points[i][axis] = pose[row] * x + pose[row + 1] * y + pose[row + 2] * z + pose[row + 3];
            }
        }
        double ax = points[1][0] - points[0][0], ay = points[1][1] - points[0][1], az = points[1][2] - points[0][2];
        double bx = points[2][0] - points[0][0], by = points[2][1] - points[0][1], bz = points[2][2] - points[0][2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double normalScale = length == 0 ? 0 : (INVERT_NORMAL.getBoolean(quad) ? -1 : 1) / length;
        for (int i = 0; i < points.length; i++) {
            PositionTextureVertex vertex = quad.vertexPositions[i];
            sink.vertex(
                points[i][0],
                points[i][1],
                points[i][2],
                vertex.texturePositionX,
                vertex.texturePositionY,
                (float) (nx * normalScale),
                (float) (ny * normalScale),
                (float) (nz * normalScale));
        }
    }
}
