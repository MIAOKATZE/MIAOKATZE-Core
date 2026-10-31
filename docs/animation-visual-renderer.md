# SOLID_BOX 原生渲染接缝

`com.miaokatze.miaokatzecore.client.animation.render.SolidBoxRenderer` 消费生产 `VisualFrame`，用现有
`SolidMesh.box(-.5, -.5, -.5, .5, .5, .5, rgb, opacity)` 绘制单位盒。它不解释 scalar 轨道，不复制 visual DTO，
不持有 World/Entity/TileEntity，不推进时间，不播放声音，也不注册监听、executor 或世界相机。该类有 `@SideOnly(CLIENT)`，
需要真实 Minecraft/LWJGL 类，不属于纯动画 SDK；不得从 common 初始化路径加载。

## 精确接口与宿主调用

```java
public final class SolidBoxRenderer implements AutoCloseable {
    public SolidBoxRenderer();
    public void render(VisualFrame frame, SceneViewport viewport,
        CameraPose camera, OrthographicProjection projection);
    public void close();
}
```

`VisualFrame` 来自 `api.animation`。`SceneViewport` 复用 `client.miaovision` 的实际 framebuffer 像素矩形，原点在左下；
`CameraPose` 与 `OrthographicProjection` 复用既有 `api.camera` 值类型。没有新的相机 DTO、auto-fit 或 SceneBounds
房间限制。视图变换与既有 `CameraBasis` 一致：

```text
projection = ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far)
view       = Tz(-depthOffset) * Rx(pitch) * Ry(yaw) * T(-anchor)
model      = VisualFrame.TargetPose.matrix()  // 已求值的列主序 T * R * S
```

构造只分配小型 CPU buffer，不创建 GL 对象。构造线程必须也是显式调用 render/close 的线程，render 时它必须持有当前
GL context。一次 render 绘制完整 frame，不支持嵌套 render；close 幂等，但绘制期间关闭会明确拒绝。

宿主先验证 frame 的完整 VisualClip 身份与 viewport generation，再按以下次序调用：

1. 在 owner 线程绑定已知的单色输出与 depth target，确认 viewport 位于该 framebuffer 内。
2. 屏幕 UI 提供实际 widget 对应像素矩形；capture 提供目标图像的像素矩形。两者传同一 VisualFrame、camera 和 projection
   时使用同一绘制入口，分辨率和 aspect 政策由宿主显式保持。
3. 宿主初始化背景，capture 若需透明背景应清为透明黑；renderer 不清 color、不分配 FBO，也不 readback。
4. 调用一次 `render(...)`。它只在 viewport 与既有 scissor 的交集内清 depth 并绘制；空交集不清、不绘制。
5. capture 宿主在成功返回后 readback/导出；失败的 capture 必须丢弃。宿主最终在 owner 线程调用 close。

**depth 区域必须由本次 visual pass 独占。** 不要把本入口当共享场景 depth 的 overlay，也不要逐 target 重复调用而不断
清掉先前深度。需要叠加到已有 3D 画面时先使用消费者自有离屏 target，再由消费者合成；本类未增加通用 compositor。
已有 UI 的输入、MUI viewport、capture 生命周期仍由消费者管理，Core 不新增 UI 控件或跨仓自动接入。

## 当前绘制语义

仅处理 VisualPlan 已验证的 SOLID_BOX。无材质文件、纹理、Item/TESR adapter 或外部模型加载。使用求值后的 rgb、opacity
及完整 TRS；不再应用额外缩放、材质 opacity、camera cue 或变换轨道。`visible=false` 不提交；VisualPlan 负责零尺寸和零
透明度不可见的规则。最多 100 个 target 的格式界限来自 visual 合同，没有修改 MiaoVision 的 100-body/schema1 合同。

不透明盒先绘制、写 depth；透明盒按 `CameraBasis.project(...).ndcZ()` 从远到近稳定排序、测试 depth 但不写 depth。
相同深度保留输入的 target ID 稳定顺序。单位盒采用 CCW 正面和背面剔除，保持实体外表面语义；本格式不允许反射负缩放。
这不是逐片元或逐面 order-independent transparency；相交/互穿透明盒的正确合成不在此实现保证内。

关闭纹理、光照、雾、alpha test、用户 clip planes、logic op、polygon offset/stipple 和 alpha-to-coverage，使用实心填充。
每面是相同固色 unlit，不增加灯光、阴影或轮廓线。RGB byte 除以 255 后直接提交，framebuffer 格式、sRGB 与 MSAA 政策由
宿主保持一致；本入口不执行颜色空间转换。

透明 source 使用直通颜色和 straight alpha，blend 为 RGB `SRC_ALPHA / ONE_MINUS_SRC_ALPHA`、A
`ONE / ONE_MINUS_SRC_ALPHA`。透明黑背景上的累积 RGB 因而是 premultiplied；若 PNG 管线要求 straight RGBA，capture
宿主须在 readback 后明确转换，不能直接把这一缓冲内容标成 straight alpha。屏幕和 capture 不另走两个渲染算法。

## GL 状态与失败边界

状态隔离复用现有 `PreviewRenderScope.RenderState` 和 `ClientTessState`，不另造 GL 保存器。必须在 Tessellator batch
之间进入；已有活动 batch 会在任何 draw 前拒绝。Tessellator translation 在本 pass 设为零，结束时恢复原字段/缓冲游标。
GL scope 恢复矩阵、属性、viewport/scissor、纹理单元、lightmap cache、program、FBO/VAO/buffer 等其既有覆盖状态。

实际准入条件：

- GL 1.4 blend 能力，当前 target 有 depth buffer，输出零启用且没有其他活动 draw buffer；MRT 拒绝。
- 支持普通单色 target 的统一 indexed color/blend 状态；异构 indexed 状态不在此 scope 合同内。
- 只支持矩形 scissor clipping；已有 stencil test 开启时明确拒绝，不静默破坏非矩形 UI 裁剪。
- 无 Angelica 时，需要原生 compatibility fixed-function context 和 program 0；原生 core profile 不支持。
- Angelica 2.2.42 路径要求 caching 与 FFP shader manager 启用且 active，逻辑 program 0，Iris shader provider 未激活。
  不把 Angelica 实际绑定的生成 shader 当应用 program。其他未审核后端/版本、shader pack 未承诺可用。

FFP 资格采用现有 `ScreenLineStream` 的相同公开 Angelica 接口。该类的 gate 是 private，不能直接复用，因此此处仅保留
一个私有嵌套资格检查和 FFP reconciliation，没有复制其 renderer/runtime 或新增路由器。正常 GL11/GL14/GL20 入口与
既有 PreviewRenderScope 经过相同的生产 GL 重定向；没有反射调用 raw backend、禁用缓存或私自恢复 driver program。
在 GL scope 恢复后，经 Angelica 自己的 `ShaderManager.preDraw(GLStateManager.ctx())` 同步派生 FFP 状态。

异常后仍分别尝试 Tessellator 和 GL 恢复，再进行可用的 FFP reconciliation；原 draw 异常保留，其他清理失败作为
suppressed，相同 Throwable 不 self-suppress。没有失败 fallback 再画一次的路径。状态恢复不能撤销已写入的像素；宿主
不能把异常帧提交为成功结果。GL scope 和可选 redirection 的真实运行效果仍需目标客户端验证。

## 当前证据与未测门

定向工具为 `build/visual-render-evidence/compile_renderer.py`，每次输出新目录。它只调用实际 JDK 8 javac/javap：
当前 `api.animation` 生产源码和本 renderer 同次编译，既有辅助类链接完整旧 Coredev
`0.2.0-lane01-522b63e9`。Minecraft 类来自本 worktree 已有真实 patchedMc 输出和 Forge 1614 binaries；其余直接依赖为
LWJGL 2.9.4、Angelica 2.2.42 dev 与正式 Java-8 API 依赖。所有文件/目录 digest、编译命令、退出码和生成 class-major
记录在输出 manifest。未用 MC/GL stub，旧 Coredev 不充当新 Visual 类型替身。

最终定向编译证据以 `build/visual-render-evidence/r3/` 为准；r1/r2 是保留的中间编译收据。此证据只说明真实依赖上的
Java 8 接口编译，**没有启动 Minecraft/FML、创建 GL context、执行 draw/readback，也没有完整 Gradle 构建**。

实际屏幕/capture 像素、非均匀 TRS、相机变换、透明排序、alpha 导出、scissor/target 边缘、异常后 GL/FFP 状态、
重载/换 context、close/reopen 和正式版 Angelica 用户路径仍待消费者及第七阶段验收。不能据编译通过关闭 F03 的可见
作品验证或 01c；消费者必须使用包含新原生类的完整 Core 候选产物，纯 SDK 单独不足以运行此 renderer。
