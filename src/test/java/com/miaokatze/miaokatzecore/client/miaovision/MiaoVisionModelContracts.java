package com.miaokatze.miaokatzecore.client.miaovision;

/** Production transform policy only: no Minecraft classes, GL context, or claims of native model pixel verification. */
public final class MiaoVisionModelContracts {

    private MiaoVisionModelContracts() {}

    public static int run() {
        int checks = 0;
        equal(SceneSpriteTransform.planar(0), -.5, "sprite left/bottom");
        equal(SceneSpriteTransform.planar(1), .5, "sprite right/top");
        equal(SceneSpriteTransform.depth(-SceneSpriteTransform.NATIVE_DEPTH), -.5, "sprite back face");
        equal(SceneSpriteTransform.depth(0), .5, "sprite front face");
        equal(SceneSpriteTransform.depth(-SceneSpriteTransform.NATIVE_DEPTH / 2), 0, "sprite depth centered");
        equal(SceneSpriteTransform.depth(0) * .027 * 2, .027, "thin sprite exact world half-depth");
        checks += 6;
        InventoryModelTransform block = InventoryModelTransform.forHelper(true);
        InventoryModelTransform pixels = InventoryModelTransform.forHelper(false);
        for (int corner = 0; corner < 8; corner++) {
            double x = corner & 1, y = (corner >> 1) & 1, z = (corner >> 2) & 1;
            equal(block.x(x), x - .5, "helper block X");
            equal(block.y(y), y - .5, "helper block Y");
            equal(block.z(z), z - .5, "helper block Z");
            checks += 3;
        }
        equal(block.x(.5), 0, "helper model center X");
        equal(block.y(.5), 0, "helper model center Y");
        equal(block.z(.5), 0, "helper model center Z");
        equal(block.x(1) - block.x(0), 1, "helper is not reduced to 1/16 scale");
        checks += 4;
        for (int value : new int[] { 0, 8, 16 }) {
            equal(pixels.x(value), value / 16.0 - .5, "GUI-pixel X");
            equal(pixels.y(value), .5 - value / 16.0, "GUI-pixel Y flips to scene up");
            equal(pixels.z(0), 0, "GUI plane remains centered");
            checks += 3;
        }
        // Real ArchitectureCraft 1.12.18 declares helper=true and inventoryTrans=Trans3.blockCenter:
        // its centered mesh is translated by +.5 before Core receives it. Keep unit size and recenter it.
        for (double meshPoint : new double[] { -.5, 0, .5 }) {
            equal(block.x(meshPoint + .5), meshPoint, "ArchitectureCraft blockCenter X");
            equal(block.y(meshPoint + .5), meshPoint, "ArchitectureCraft blockCenter Y");
            equal(block.z(meshPoint + .5), meshPoint, "ArchitectureCraft blockCenter Z");
            checks += 3;
        }
        if (!InventoryModelTransform.blockAtlas(0) || InventoryModelTransform.blockAtlas(1)
            || InventoryModelTransform.blockAtlas(-1)) throw new AssertionError("Forge atlas selection");
        return checks + 3 + ordering();
    }

    private static int ordering() {
        com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot scene = new com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot(
            "sort",
            0,
            0,
            java.util.Arrays.asList(
                body("a-near", 0, 0, 3),
                body("b-opaque", 0, 0, 0),
                body("c-far", 0, 0, -3),
                body("d-tie", 0, 0, -3),
                body("e-hidden", 0, 0, -5),
                body("f-zero", 0, 0, -4)));
        com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation presentation = new com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation() {

            @Override
            public boolean visible(String id) {
                return !id.equals("e-hidden");
            }

            @Override
            public float opacity(String id) {
                return id.equals("b-opaque") ? 2 : id.equals("f-zero") ? -1 : .5f;
            }
        };
        java.util.List<SceneRenderOrder.Entry> ordered = SceneRenderOrder
            .prepare(scene, presentation, new SceneCamera(0, 0, 1));
        String[] expected = { "b-opaque", "c-far", "d-tie", "a-near" };
        if (ordered.size() != expected.length) throw new AssertionError("invisible/zero models must be skipped");
        for (int i = 0; i < expected.length; i++) if (!expected[i].equals(ordered.get(i).body.id))
            throw new AssertionError("opaque first/far-near/stable ties");
        equal(ordered.get(0).opacity, 1, "opacity clamps high");
        ordered = SceneRenderOrder.prepare(scene, presentation, new SceneCamera(180, 0, 1));
        if (!ordered.get(1).body.id.equals("a-near")) throw new AssertionError("camera reversal changes depth order");
        equal(
            Math.rint(SceneRenderOrder.eyeZ(body("x", 3, 0, 0), new SceneCamera(90, 0, 1))),
            -3,
            "yaw uses actual modelview sign");
        equal(
            Math.rint(SceneRenderOrder.eyeZ(body("y", 0, 3, 0), new SceneCamera(0, 90, 1))),
            3,
            "pitch uses actual modelview sign");
        try {
            SceneRenderOrder.prepare(scene, new com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation() {

                @Override
                public float opacity(String id) {
                    return Float.NaN;
                }
            }, new SceneCamera(0, 0, 1));
            throw new AssertionError("non-finite opacity accepted");
        } catch (IllegalArgumentException expectedFailure) {
            // Validation is pure and precedes any GL/Tessellator state change.
        }
        return 10;
    }

    private static com.miaokatze.miaokatzecore.api.miaovision.SceneBody body(String id, double x, double y, double z) {
        return new com.miaokatze.miaokatzecore.api.miaovision.SceneBody(id, .5, .5, .5, x, y, z);
    }

    private static void equal(double actual, double expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
