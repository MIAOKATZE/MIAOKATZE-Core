package com.miaokatze.miaokatzecore.client.preview;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

import cpw.mods.fml.relauncher.ReflectionHelper;

/** Native model vertices without a graphics context, display-list compilation or a Tessellator batch. */
public final class ModelRendererMeshContracts {

    private ModelRendererMeshContracts() {}

    public static void main(String[] args) throws Exception {
        ModelRenderer box = new ModelRenderer(new ModelBase() {}, 0, 0);
        box.addBox(0, 0, 0, 2, 3, 4);
        List<double[]> vertices = read(box, .5f);
        require(vertices.size() == 24, "six native quads");
        TexturedQuad[] quads = (TexturedQuad[]) ReflectionHelper.findField(ModelBox.class, "quadList", "field_78254_i")
            .get(box.cubeList.get(0));
        int index = 0;
        for (TexturedQuad quad : quads) for (PositionTextureVertex v : quad.vertexPositions) {
            double[] actual = vertices.get(index++);
            near(v.vector3D.xCoord * .5, actual[0], "native x");
            near(v.vector3D.yCoord * .5, actual[1], "native y");
            near(v.vector3D.zCoord * .5, actual[2], "native z");
            near(v.texturePositionX, actual[3], "native u");
            near(v.texturePositionY, actual[4], "native v");
            near(1, actual[5] * actual[5] + actual[6] * actual[6] + actual[7] * actual[7], "unit face normal");
        }
        require(
            !ReflectionHelper.findField(ModelRenderer.class, "compiled", "field_78812_q")
                .getBoolean(box),
            "model was not compiled or rendered");

        ModelRenderer parent = new ModelRenderer(new ModelBase() {});
        parent.setRotationPoint(2, 0, 0);
        parent.offsetX = .25f;
        parent.rotateAngleZ = (float) (Math.PI / 2);
        ModelRenderer child = new ModelRenderer(new ModelBase() {});
        child.setRotationPoint(0, 2, 0);
        child.addBox(0, 0, 0, 2, 2, 2);
        parent.addChild(child);
        vertices = read(parent, .5f);
        require(vertices.size() == 24, "child model included");
        bounds(vertices, 0, -.75, .25);
        bounds(vertices, 1, 0, 1);
        bounds(vertices, 2, 0, 1);
        near(0, vertices.get(0)[5], "rotated positive x normal x");
        near(1, vertices.get(0)[6], "rotated positive x normal y");
        parent.isHidden = true;
        require(read(parent, .5f).isEmpty(), "hidden parent also hides children");
        parent.isHidden = false;
        child.showModel = false;
        require(read(parent, .5f).isEmpty(), "disabled child omitted");

        ModelRenderer mirrored = new ModelRenderer(new ModelBase() {});
        mirrored.mirror = true;
        mirrored.addBox(0, 0, 0, 2, 2, 2);
        vertices = read(mirrored, .5f);
        for (double[] v : vertices)
            require((v[0] - .5) * v[5] + (v[1] - .5) * v[6] + (v[2] - .5) * v[7] > 0, "mirrored outward normal");
        try {
            read(box, Float.NaN);
            throw new AssertionError("invalid scale accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("ModelRendererMeshContracts PASS: native UV/geometry, child transforms, normals and no GL");
    }

    private static List<double[]> read(ModelRenderer model, float scale) {
        List<double[]> result = new ArrayList<>();
        ModelRendererMesh
            .emit(model, scale, (x, y, z, u, v, nx, ny, nz) -> result.add(new double[] { x, y, z, u, v, nx, ny, nz }));
        return result;
    }

    private static void bounds(List<double[]> vertices, int axis, double min, double max) {
        near(
            min,
            vertices.stream()
                .mapToDouble(v -> v[axis])
                .min()
                .getAsDouble(),
            "transformed lower bound");
        near(
            max,
            vertices.stream()
                .mapToDouble(v -> v[axis])
                .max()
                .getAsDouble(),
            "transformed upper bound");
    }

    private static void near(double expected, double actual, String label) {
        require(Math.abs(expected - actual) < .000001, label + ": " + expected + " != " + actual);
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
