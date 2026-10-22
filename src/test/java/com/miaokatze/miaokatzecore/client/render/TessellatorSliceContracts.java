package com.miaokatze.miaokatzecore.client.render;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/** CPU contracts; never constructs a Minecraft Tessellator or invokes GL. */
public final class TessellatorSliceContracts {

    public static void main(String[] args) {
        selectiveVertexWrites();
        scalarState();
        require(TessellatorSliceData.validDeltas(32, 4, 4), "vanilla quad counts");
        require(TessellatorSliceData.validDeltas(0, 0, 0), "attribute-only callback");
        require(!TessellatorSliceData.validDeltas(48, 4, 4), "reject alternate stride");
        require(!TessellatorSliceData.validDeltas(32, 4, 3), "reject converted vertex counts");
        require(!TessellatorSliceData.validDeltas(-8, -1, -1), "reject reset/draw counts");
        System.out.println("Tessellator slice CPU contracts passed (no Minecraft initialization or GL).");
    }

    private static void selectiveVertexWrites() {
        int[] source = new int[40];
        for (int i = 0; i < source.length; i++) source[i] = i + 1;
        int[] target = new int[56];
        Arrays.fill(target, 999);
        TessellatorSliceData.copy(source, new byte[] { 0, 1, 6, 15, 15 }, target, 8);
        require(target[0] == 999 && target[55] == 999, "preserve surrounding batch data");
        require(target[8] == 1 && target[10] == 3 && target[11] == 999, "position-only vertex");
        require(target[14] == 999, "later unknown renderer enabling normals sees original destination slot");
        require(target[19] == 12 && target[20] == 13 && target[21] == 999, "texture writes two slots only");
        require(target[29] == 22 && target[30] == 999 && target[31] == 24, "color and brightness preserve normal");
        for (int i = 24; i < 40; i++) require(target[8 + i] == source[i], "full-mask bulk run");
        TessellatorSliceData.copy(new int[0], new byte[0], null, 0);
    }

    private static void scalarState() {
        Map<String, Object> values = new HashMap<>();
        TessellatorAccessor tess = (TessellatorAccessor) Proxy.newProxyInstance(
            TessellatorAccessor.class.getClassLoader(),
            new Class<?>[] { TessellatorAccessor.class },
            (proxy, method, arguments) -> {
                String name = method.getName();
                if (name.startsWith("miaokatze$set")) {
                    values.put(name.substring(13), arguments[0]);
                    return null;
                }
                Object value = values.get(name.substring(13));
                if (value != null) return value;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == double.class) return 0D;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        tess.miaokatze$setIsDrawing(true);
        tess.miaokatze$setDrawMode(7);
        TessellatorSlice.State input = new TessellatorSlice.State(tess);
        require(input.matches(tess), "same input state");
        tess.miaokatze$setVertexCount(12);
        tess.miaokatze$setRawBufferIndex(96);
        tess.miaokatze$setAddedVertices(12);
        require(input.matches(tess), "input state independent of existing batch length");
        String[] scalars = { "TextureU", "TextureV", "Brightness", "Color", "Normal", "HasTexture", "HasColor",
            "HasBrightness", "HasNormals", "IsColorDisabled", "XOffset", "YOffset", "ZOffset", "DrawMode",
            "IsDrawing" };
        for (String scalar : scalars) {
            Object before = values.get(scalar);
            Object changed;
            if (scalar.equals("IsDrawing")) changed = false;
            else if (scalar.startsWith("Has") || scalar.equals("IsColorDisabled")) changed = true;
            else if (scalar.endsWith("Offset") || scalar.startsWith("Texture")) changed = 1D;
            else changed = 1;
            values.put(scalar, changed);
            require(!input.matches(tess), "reject input mismatch: " + scalar);
            if (before == null) values.remove(scalar);
            else values.put(scalar, before);
        }
        tess.miaokatze$setTextureU(-0D);
        require(!input.matches(tess), "compare double raw bits");
        tess.miaokatze$setHasNormals(true);
        tess.miaokatze$setNormal(123);
        tess.miaokatze$setHasBrightness(true);
        tess.miaokatze$setBrightness(456);
        TessellatorSlice.State output = new TessellatorSlice.State(tess);
        input.apply(tess);
        require(input.matches(tess), "restore input flags and scalars");
        output.apply(tess);
        require(
            output.matches(tess) && tess.miaokatze$getHasNormals() && tess.miaokatze$getNormal() == 123,
            "replay output preserves batch flags and current attributes");
        require(tess.miaokatze$getVertexCount() == 12, "scalar apply preserves batch counters");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
