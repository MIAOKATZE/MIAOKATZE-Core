# MIAOKATZE Core

为 MIAOKATZE 系列模组提供共享渲染、相机、界面与任务管理能力的基础库。

A shared library for rendering, cameras, UI and task management across MIAOKATZE mods.

## 版本与依赖 / Versions & Requirements

| Core | GTNH | Minecraft / Forge | Java | 下载 / Download |
| --- | --- | --- | --- | --- |
| 0.2.6 (draft) | 2.9.0 RC-2 | 1.7.10 / 10.13.4.1614 | 8+ | Release draft (unpublished) |
| 0.2.5 | 2.9.0 RC-2 | 1.7.10 / 10.13.4.1614 | 8+ | [测试分发 JAR](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.2.5/miaokatzecore-0.2.5.jar) |
| 0.2.4 | 2.9.0 | 1.7.10 / 10.13.4.1614 | 8+ | [Release](https://github.com/MIAOKATZE/MIAOKATZE-Core/releases/tag/v0.2.4) |
| 0.1.28 | 2.9.0 RC-2 | 1.7.10 / 10.13.4.1614 | 8+ | [JAR](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.28/miaokatzecore-0.1.28.jar) |

客户端和服务端安装同一版 Core，具体最低版本以所用模组的依赖要求为准。

Install the same Core version on client and server. Follow each mod's minimum-version requirement.

## 开发依赖 / Development dependency

```groovy
repositories { maven { url = "https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/" } }
dependencies { api("com.miaokatze.miaokatzecore:miaokatzecore:0.2.6:dev") { transitive = false } }
```

## 模块

| 入口 | 职责及边界 |
| --- | --- |
| api.fx.RisingTrails | 五种确定性上升轨迹，通过 Sink 输出坐标；调用方拥有时间、随机预设、颜色与 GL 状态 |
| client.fx.RuneRenderer | 符文、环、柔光；可注入纹理，standard() 使用 Core 默认资源 |
| client.fx.VoxelGeometry | 无逐体素顶点数组分配；立即绘制与追加 Tessellator 批次分别使用对应方法 |
| client.render.SolidMesh | 实心盒、圆环、方环；box/ring 自有批次，cubeVertices 只追加顶点 |
| client.render.PackedVertexStream | 有界复用 Angelica packed 提交；固定 quad/strip 布局，不支持的上下文由调用方完整回退 |
| client.render.GlScope | 纹理单元、矩阵、属性及光照坐标保护，按 try-with-resources 单次关闭 |
| client.render.MatrixAttributeScope | 轻量矩阵与显式属性掩码保护 |
| client.itemfx.AtlasGlintRenderer | 参数化两遍物品光泽；由调用方注册，适合普通 atlas 物品，不自动覆盖其他渲染器 |
| api.itemfx.GlintStyle | 不可变颜色、缩放、滚动速度和旋转参数，数组防御复制与有限值检查 |
| client.preview.PreviewRenderScope | GL/FBO/Tessellator 预览隔离和显式第三方上下文清理；调用方控制快照、恢复时机 |
| client.preview.ModelRendererMesh | 纯 CPU 提取原生 ModelRenderer 的四边形、UV、法线与子模型姿态；不调用 GL、编译模型或开关绘制批次 |
| api.items.NativeItemIds | 按 GTNH EndlessIDs 原生格式读写数值物品 ID，重映射时同步或清除 idExt；不查询注册表或解释物品资产 |
| ui.mui2.ScrollEdges | 绘制滚动内容上下渐隐与边缘；不拥有滚动、裁剪、鼠标或控件生命周期 |
| api.extension.ExtensionCatalog | 每实例命名空间注册、稳定快照、可关闭句柄、安装重入保护与注销失败重试 |
| api.camera | 不可变姿态、正交投影/反投影射线、交点、缩放与旋转缓动，不引用 Minecraft 或 GL |
| api.ui.CanvasTransform / api.camera.ViewportCoordinates | 纯 Java 8 的画布正逆映射、GUI/NDC 坐标与裁剪后的 framebuffer 整数边界 |
| client.camera.WorldCameras | 客户端镜头独占租约、优先级、世界身份及输入/GUI策略，无消费者时被动放行 |
| client.camera.WorldCameraHooks | 统一投影、同帧世界射线、渲染原点、GL/实体/鼠标恢复，消费者不复制公共 Mixin |
| client.camera.CameraSettings / CameraControls | 不可变具名策略及能力回调；准星、非模态控制条、平移旋转缩放与切层由消费者选择 |
| client.camera.CameraFrame | 成功显示的只读帧、复用坐标基、requested/displayed/committed 切层与内容 revision |
| api.camera.HeightSlice / SliceSurfaceRule | 整数上下高度区间、真实边界面及同像素渐变覆盖规则 |
| client.camera.slice.SliceBackend | 正式版默认地形与 TESR 裁切、局部边界补面和后台上传状态；SDL GPU/Vulkan 保留正常渲染但不支持高度切片；不支持活动 Iris shaderpack |
| testkit.Deadline | 可注入单调时间戳的被动预算，处理 nanoTime 回绕，不注册维度或后台任务 |
| api.work.WorkGovernor | 显式共享 CPU/字节准入、资源租约、精确键 singleflight、owner guard 与被动观察器；无游戏依赖、自动线程或监听 |

~~~java
try (GlScope scope = new GlScope()) {
    RuneRenderer.standard().unlit();
    // Draw using the chosen renderer. Only call on the client render thread.
}
~~~

## API 文档 / API Documentation

- [动画协议 / Animation](docs/animation-contract.md) · [运行时 / Runtime](docs/animation-runtime.md) · [绘制 / Rendering](docs/animation-visual-renderer.md)
- [相机 / Camera](docs/world-camera.md) · [任务准入 / Work governor](docs/work-governor.md)
- [最近访问列表 / Bounded MRU](docs/bounded-mru.md) · [屏幕线条 / Screen lines](docs/screen-lines.md)
- [Packed 顶点流 / Packed vertex stream](docs/packed-vertex-stream.md)

## 构建 / Build

使用 JDK 25 执行 `gradlew build --no-configuration-cache`，产物为 Java 8 字节码。

Build with JDK 25 using `gradlew build --no-configuration-cache`. Output targets Java 8.

## License

[AGPL-3.0](LICENSE) · [NOTICE](NOTICE.md)
