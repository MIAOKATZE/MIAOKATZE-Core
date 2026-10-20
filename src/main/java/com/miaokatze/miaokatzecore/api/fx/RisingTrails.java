package com.miaokatze.miaokatzecore.api.fx;

/** Deterministic rising trajectories. The caller owns color, batching, GL state and entry lifetimes. */
public final class RisingTrails {

    private RisingTrails() {}

    @FunctionalInterface
    public interface Sink {

        void particle(int index, float x, float y, float progress, float alpha);
    }

    public static void emit(int preset, float x, float y, float progress, float alpha, Sink sink) {
        switch (preset) {
            case 0:
                renderStraightRise(sink, x, y, progress, alpha);
                break;
            case 1:
                renderZigzagRise(sink, x, y, progress, alpha);
                break;
            case 2:
                renderScatterRise(sink, x, y, progress, alpha);
                break;
            case 3:
                renderVShapeRise(sink, x, y, progress, alpha);
                break;
            case 4:
                renderFanRise(sink, x, y, progress, alpha);
                break;
            default:
                throw new IllegalArgumentException("preset");
        }
    }

    private static void renderStraightRise(Sink sink, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 2;
        // v1.6.28: 2 粒子 X 偏移（-3, 3）
        float[] xOffsets = { -3f, 3f };
        // v1.6.29: 粒子起点 Y 错落——第二个粒子提前 2px 上移，避免所有粒子起点完全重合
        float[] initialYOffsets = { 0f, -2f };
        for (int p = 0; p < particleCount; p++) {
            // v1.6.28: 上升幅度减半 25→12.5
            float yOffset = -progress * 12.5f + initialYOffsets[p];
            // 轻微摆动（1 圈，幅度 2 像素）
            float xOffset = xOffsets[p]
                + (float) Math.sin(progress * 2f * (float) Math.PI + p * (float) (Math.PI / 2)) * 2f;

            float px = centerX + xOffset;
            float py = centerY + yOffset;

            sink.particle(p, px, py, progress, alpha);
        }
    }

    /**
     * 预设1：之字形上升（3 粒子）
     * <p>
     * 3 个粒子初始 X 偏移不同（-5, 0, 5），向上运动同时左右摆动。
     * 每个粒子相位不同，形成交错摆动效果。给人"蜿蜒传输"的感觉。
     * v1.6.28: 上升幅度减半 20→10。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private static void renderZigzagRise(Sink sink, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 3;
        // v1.6.29: 粒子起点 Y 错落——阶梯式提前上移（0, -2, -4）
        float[] initialYOffsets = { 0f, -2f, -4f };
        for (int p = 0; p < particleCount; p++) {
            // 初始 X 偏移（-5, 0, 5）
            float initialXOffset = (p - 1) * 5f;
            // 相位不同（0, π/2, π）
            float phase = p * (float) (Math.PI / 2);
            // v1.6.28: 向上运动幅度减半 20→10
            float yOffset = -progress * 10f + initialYOffsets[p];
            // 左右摆动（2 圈，幅度 6 像素）
            float xOffset = (float) Math.sin(progress * 4f * (float) Math.PI + phase) * 6f;

            float px = centerX + initialXOffset + xOffset;
            float py = centerY + yOffset;

            sink.particle(p, px, py, progress, alpha);
        }
    }

    /**
     * 预设2：散开上升（4 粒子）
     * <p>
     * 4 个粒子从物品底部向四周散开同时上升，呈花朵绽放效果。
     * 上升幅度 9px（参考原 parabolic 的 18 减半）。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private static void renderScatterRise(Sink sink, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 4;
        // v1.6.29: 粒子起点 Y 错落——阶梯式提前上移（0, -1.5, -3, -4.5）
        float[] initialYOffsets = { 0f, -1.5f, -3f, -4.5f };
        for (int p = 0; p < particleCount; p++) {
            // 初始角度均匀分布（0°, 90°, 180°, 270°）
            float initialAngle = p * (float) (Math.PI / 2);
            // v1.6.28: 水平散开（0→8 像素）
            float scatterRadius = progress * 8f;
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f + initialYOffsets[p];

            float px = centerX + (float) Math.cos(initialAngle) * scatterRadius;
            float py = centerY + (float) Math.sin(initialAngle) * scatterRadius * 0.5f + yOffset;

            sink.particle(p, px, py, progress, alpha);
        }
    }

    /**
     * 预设3：V形上升（5 粒子）
     * <p>
     * 5 个粒子初始位置水平分布，向中心汇聚后上升，形成 V 形轨迹。
     * 上升幅度 9px。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private static void renderVShapeRise(Sink sink, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 5;
        // v1.6.29: 粒子起点 Y 错落——中间粒子起点最低，两侧递减（-1, 0, -2, 0, -1）
        // 中间粒子提前上移 2px，两侧粒子起点稍低，形成 V 形错落起点
        float[] initialYOffsets = { -1f, 0f, -2f, 0f, -1f };
        for (int p = 0; p < particleCount; p++) {
            // 初始 X 位置均匀分布（-8, -4, 0, 4, 8）
            float initialX = (p - 2) * 4f;
            // v1.6.28: V 形效果——向中心汇聚（progress=1 时全部到中心）
            float xOffset = initialX * (1.0f - progress);
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f + initialYOffsets[p];

            float px = centerX + xOffset;
            float py = centerY + yOffset;

            sink.particle(p, px, py, progress, alpha);
        }
    }

    /**
     * 预设4：扇形上升（6 粒子）
     * <p>
     * 6 个粒子沿扇形方向（-60° 到 +60°）向外扩散同时上升，呈扇形喷洒效果。
     * 上升幅度 9px。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private static void renderFanRise(Sink sink, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 6;
        // 扇形张角 120°，6 个粒子均匀分布
        float fanSpread = (float) (Math.PI / 3); // 60° 半张角
        // v1.6.29: 粒子起点 Y 错落——阶梯式提前上移（0, -1, -2, -3, -4, -5）
        float[] initialYOffsets = { 0f, -1f, -2f, -3f, -4f, -5f };
        for (int p = 0; p < particleCount; p++) {
            // 角度从 -60° 到 +60° 均匀分布
            float angle = -fanSpread + (float) p / (particleCount - 1) * 2f * fanSpread;
            // v1.6.28: 沿角度方向向外移动（0→10 像素）
            float distance = progress * 10f;
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f + initialYOffsets[p];

            float px = centerX + (float) Math.cos(angle) * distance;
            float py = centerY + (float) Math.sin(angle) * distance * 0.5f + yOffset;

            sink.particle(p, px, py, progress, alpha);
        }
    }

}
