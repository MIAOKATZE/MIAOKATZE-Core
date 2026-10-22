package com.miaokatze.miaokatzecore.client.render;

/** Vanilla 8-int vertex layout operations, independent of Minecraft initialization or GL. */
final class TessellatorSliceData {

    private TessellatorSliceData() {}

    static boolean validDeltas(int ints, int vertices, int added) {
        return vertices >= 0 && ints == vertices * 8L && added == vertices;
    }

    static void copy(int[] source, byte[] masks, int[] target, int offset) {
        for (int vertex = 0; vertex < masks.length;) {
            int mask = masks[vertex];
            int start = vertex;
            if (mask == 15) {
                while (vertex < masks.length && masks[vertex] == 15) vertex++;
                System.arraycopy(source, start * 8, target, offset + start * 8, (vertex - start) * 8);
                continue;
            }
            int src = vertex * 8;
            int dst = offset + src;
            System.arraycopy(source, src, target, dst, 3);
            if ((mask & 1) != 0) System.arraycopy(source, src + 3, target, dst + 3, 2);
            if ((mask & 2) != 0) target[dst + 5] = source[src + 5];
            if ((mask & 8) != 0) target[dst + 6] = source[src + 6];
            if ((mask & 4) != 0) target[dst + 7] = source[src + 7];
            vertex++;
        }
    }
}
