# ScreenLineStream（0.1.28）

固定 `POSITION_COLOR`（3 个 native-order float 与 RGBA 四字节）、`GL_LINES` 的客户端有界提交能力。只接收调用方已经投影好的屏幕坐标；不投影、排序、去重，不保留蓝图、模型、材质或场景引用。CPU staging 固定为 16368 顶点、261888 字节；此数字不是包括驱动和 GPU 在内的总内存上限。

## 调用与回退

在渲染线程创建实例，并在开始原线 pass **之前**调用 `begin(tessellator)`。返回 false 时没有提交任何新顶点，调用方完整执行原来的 Tessellator 路径。`lastRejection()` 给出拒绝类别；拒绝不算作成功 fast pass。

```java
if (!stream.begin(tessellator)) {
    originalLinePass();
} else {
    try {
        stream.color(red, green, blue, alpha);
        stream.line(x1, y1, biasedZ1, x2, y2, biasedZ2);
        // 可在原 box/group 边界调用 flush；单条线绝不分裂。
    } finally {
        stream.end();
    }
}
```

接受后的异常传播，不能捕获异常后再执行原 pass；已有提交无法回滚。`end()` 恢复状态，失败的 upload/draw 不会重试其 staging 内容。

资格限定为 idle、无待处理顶点的 exact vanilla Tessellator，Core 的字段桥接存在，GTNHLib 渲染线程，且无 Core/GTNHLib/Direct/DisplayList 捕获。要求 OpenGL 3.0 与可用 VAO。foreign/Direct、pending batch、捕获、未覆盖 GL/外部 shader、开启纹理或 lighting 的状态回退。不能把 GUI 当前 program 非零一概视作外部 shader：Angelica 2.2.28 的生成 FFP program 是物理绑定，其逻辑请求仍为零。当前实现支持启用缓存的 Angelica FFP、无 Iris shader pack；其实际初始化/链接失败不吞为兼容回退。没有 Angelica 时，只接受兼容上下文的无 shader 固定管线。

Angelica 资格读其公开缓存的纹理单元、1D/2D/3D enables 与 client-active unit；不在 core-profile driver 上查询已移除的 `MAX_TEXTURE_UNITS`、`CLIENT_ACTIVE_TEXTURE` 或 `TEXTURE_CUBE_MAP`。兼容上下文按其合法的固定管线查询检查纹理。实际资格覆盖与像素等价需要目标客户端验证，不能以 accepted pass 单独代替 submitted vertex 证据。

## 几何与 Tessellator 状态

内部只读原 Tessellator 的三个 double offsets。每坐标严格先计算 `input + offset`，再转换成 float。调用方先计算原来的 Z 缩放与偏置，例如 `pointZ * depthScale + .15`，然后传入；stream 不重排该算式。颜色沿用 vanilla `setColorRGBA_F` 的 float 乘 255、截断与 `[0,255]` clamp，RGBA 字节顺序不随平台改变。

stream 不 start/draw/reset/discard 输入 Tessellator，不修改其计数、缓冲区、translation、颜色或其他属性 flags。调用方后续传统 pass 仍须照常 `startDrawing` 并设置自己的颜色，不能依赖 stream 留下某个 Tessellator 颜色。

## GL 与资源

调用方继续拥有矩阵、深度、blend、alpha、line width 等绘制政策。上传使用现成 GTNHLib `VertexBuffer.uploadStream` 与 VAO API，不调用 Angelica `drawDirect`，不直接绑定其生成 program 或绕开 GLSM 缓存。每个 batch 经 GTNHLib format setup 公布 FFP 属性；结束时恢复调用者 VAO、ARRAY_BUFFER、client-active texture、client/FFP flags，并经 Angelica 自身公开 FFP preparation 更新生成 shader 状态。仅 `unbind(0)` 或 `GL_ALL_ATTRIB_BITS` 不是这里的恢复合同。

构造函数不创建 GL 对象；CPU/GPU 资源在第一次接受后惰性分配。`close()` 必须在实例 owner 渲染线程、context 销毁前执行，可重复关闭，关闭后不得 reopen。GUI close、resource reload、world teardown 由调用方显式关闭，不能依赖 GC 释放 GPU 对象。

`acceptedPasses()`、`submittedBatches()`、`submittedVertices()` 是实例内轻量计数，无日志或 I/O。单元合同验证独立字节 oracle、translation 运算顺序、只读输入、拒绝及 owner/关闭生命周期，不创建 Minecraft 或 GL。真实接纳、FFP/client 属性恢复、后续 sentinel 绘制、capture 回退以及像素等价必须由 Native GL 用例验证。
