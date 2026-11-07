# PackedVertexStream 使用契约

本文描述 Core 的有界打包顶点流，依据 PackedVertexStream.java、PackedVertexData.java 与 ScreenLineStream.java。

## 固定布局

调用方通过 Layout 枚举选择格式，不传原始 OpenGL mode 或 flags：

| Layout | 拓扑 | 每顶点数据 | 字节数 |
|---|---|---|---:|
| TEXTURED_QUADS | GL_QUADS | position XYZ、UV、RGBA color | 24 |
| COLOR_QUADS | GL_QUADS | position XYZ、RGBA color | 16 |
| COLOR_TRIANGLE_STRIP | GL_TRIANGLE_STRIP | position XYZ、RGBA color | 16 |

每流最多 16,368 个顶点。Direct staging buffer 首次成功 begin 时惰性分配，最多保留 392,832 B，并供后续 pass 复用。构造 stream 不创建 GL 资源。

## 准入与坐标

begin(tessellator, layout) 会先检查调用条件，再分配 staging、改客户端状态或提交顶点。外部 shader、扩展顶点格式、捕获状态、foreign Tessellator、pending batch、非 render thread、不支持的后端及纹理/lighting 状态不匹配都会安静返回 false；lastRejection 可用于诊断。false 时调用方必须执行原有完整 pass，API 不会自动 fallback。

true 表示该 pass 已由 stream 接管。此后异常向外传播，不能再用原 Tessellator 重试同一 pass。

输入世界坐标以 double 保留到偏移相加后，再转换为 float；RGBA 使用与 vanilla setColorRGBA_F 相同的乘法、截断和通道夹取规则。PackedVertexData 是仅供包内使用的 CPU 编码器。

## Flush 与生命周期

stream 不自动 flush 或截断数据。调用方应按完整 primitive/source 边界使用 remainingVertices 判断容量，并自行 flush：

- 两种 QUADS 布局的 flush 顶点数须为 4 的倍数。
- TRIANGLE_STRIP flush 至少 3 个顶点；每个 strip 必须单独 flush，不能自动拼接或拆段。
- 空 flush 是 no-op。

vertex、布局或内部 flush 失败会将当前 pass 标为 failed。之后 end 只恢复状态，不会重复提交该批字节。调用方自己的业务异常不会自动撤销已暂存数据；若 pass 仍健康且缓冲内容符合布局完整性，finally 中的 end 会尝试提交该数据。因此这不是事务式 staging rollback。

在创建 stream 的 owner thread 上完成 begin、写入、flush、end 与 close；begin 还必须通过 TessellatorManager 的 render-thread 检查。每次 begin 成功后都要在 finally 调用 end。关闭会释放 native staging，且须在 GL context 销毁前进行；关闭后的实例不能重开。

在首次 render 时创建并长期持有实例，避免每帧重新分配 native buffer。每次绘制的最小调用顺序如下（辅助发射函数由调用方提供）：

    // stream 已在当前 render thread 惰性创建，并跨帧复用。
    if (!stream.begin(tessellator, PackedVertexStream.Layout.TEXTURED_QUADS)) {
        drawOriginalCompletePass();
        return;
    }
    try {
        emitCompleteSources(stream);
        stream.flush();
    } finally {
        stream.end();
    }

世界或渲染器卸载时，在同一 owner thread 调用 stream.close()；不要把每个 pass 的 end 与实例的最终 close 混为一体。

调用方负责矩阵、主纹理、depth、blend、alpha 等绘制状态。stream 复用 Angelica 公共 TessellatorStreamingDrawer.drawPacked 路径和其缓冲区，不自建 VBO；end 恢复 VAO、array-buffer、client texture、client-attribute 与 Angelica 逻辑 FFP 状态。ScreenLineStream 的嵌套 AngelicaFfp helper 为包内可见，以供新流复用。
