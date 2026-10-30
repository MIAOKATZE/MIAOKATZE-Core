package com.miaokatze.miaokatzecore.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

/** CPU packing for the fixed POSITION_COLOR stream. No Minecraft initialization or GL. */
final class ScreenLineData {

    private ScreenLineData() {}

    static boolean idle(boolean drawing, int vertices, int rawIndex, int added) {
        return !drawing && vertices == 0 && rawIndex == 0 && added == 0;
    }

    static void offsets(TessellatorAccessor input, double[] target) {
        target[0] = input.miaokatze$getXOffset();
        target[1] = input.miaokatze$getYOffset();
        target[2] = input.miaokatze$getZOffset();
    }

    static int color(float red, float green, float blue, float alpha, ByteOrder order) {
        int r = channel(red), g = channel(green), b = channel(blue), a = channel(alpha);
        return order == ByteOrder.LITTLE_ENDIAN ? a << 24 | b << 16 | g << 8 | r : r << 24 | g << 16 | b << 8 | a;
    }

    private static int channel(float value) {
        int result = (int) (value * 255F);
        return result < 0 ? 0 : result > 255 ? 255 : result;
    }

    static void vertex(ByteBuffer target, double x, double y, double z, double offsetX, double offsetY, double offsetZ,
        int color) {
        target.putFloat((float) (x + offsetX));
        target.putFloat((float) (y + offsetY));
        target.putFloat((float) (z + offsetZ));
        target.putInt(color);
    }
}
