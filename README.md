# MIAOKATZE Core

Minecraft 1.7.10 / Forge 10.13.4.1614 / Java 8 的模块化基础库。当前 API 基线为 **0.1.8**，将系列 Mod 已验证的底层实现抽出，原 Mod 继续拥有玩法、注册、配置、存档与测试维度。

## 安装与依赖

[下载普通 JAR 0.1.8](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.8/miaokatzecore-0.1.8.jar) · [SHA-256](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.8/miaokatzecore-0.1.8.jar.sha256) · [dev JAR](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.8/miaokatzecore-0.1.8-dev.jar) · [sources JAR](https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.8/miaokatzecore-0.1.8-sources.jar)

客户端和服务端安装同一版 Core。同一 JAR 包含普通 Forge Mod 和极小的 FML early loader，复用整合包 UniMixins 启动机制，不携带另一套 Mixin bootstrap。专服不选择客户端 Mixin。通用数学与所有权契约不依赖 Minecraft/OpenGL；client 包只在客户端调用。MUI2 适配面向 **GTNH 2.9.0 RC 版本**，使用相应适配功能的 Mod 负责提供该依赖。

Gradle 仓库：https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/

~~~groovy
repositories { maven { url = "https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/maven/" } }
dependencies { api("com.miaokatze.miaokatzecore:miaokatzecore:0.1.8:dev") { transitive = false } }
~~~

Forge 依赖声明：required-after:miaokatzecore@[0.1.8,)

系列 Mod 的 META-INF JSON 使用 FalsePatternLib 1.12.0+ 的 modDependencies，首次缺失时请求精确版本 0.1.8。需要安装 FalsePatternLib；离线部署请预先提供 Core。该机制不覆盖已加载旧 Jar，不解决任意版本组合，也不意味着自动追踪最新版。Forge 最终校验 Core 最低版本 0.1.8，不设版本上限；其他 Mod 依赖仅要求存在，保留原有加载顺序。精确下载坐标不限制已安装 Core 的更高版本。普通 LaunchWrapper 首次补下载带 early hooks 的0.1.8可能提示退出并需要再次启动；RC 版本的 RFB 路径在 FML 扫描前下载，实际结果以启动日志为准。参见 [上游格式](https://github.com/FalsePattern/FalsePatternLib/blob/1.12.0/src/main/resources/DEPENDENCIES.md)。

## 模块

| 入口 | 职责及边界 |
| --- | --- |
| api.fx.RisingTrails | 五种确定性上升轨迹，通过 Sink 输出坐标；调用方拥有时间、随机预设、颜色与 GL 状态 |
| client.fx.RuneRenderer | 符文、环、柔光；可注入纹理，standard() 使用 Core 默认资源 |
| client.fx.VoxelGeometry | 无逐体素顶点数组分配；立即绘制与追加 Tessellator 批次分别使用对应方法 |
| client.render.SolidMesh | 实心盒、圆环、方环；box/ring 自有批次，cubeVertices 只追加顶点 |
| client.render.GlScope | 纹理单元、矩阵、属性及光照坐标保护，按 try-with-resources 单次关闭 |
| client.render.MatrixAttributeScope | 轻量矩阵与显式属性掩码保护 |
| client.itemfx.AtlasGlintRenderer | 参数化两遍物品光泽；由调用方注册，适合普通 atlas 物品，不自动覆盖其他渲染器 |
| api.itemfx.GlintStyle | 不可变颜色、缩放、滚动速度和旋转参数，数组防御复制与有限值检查 |
| client.preview.PreviewRenderScope | GL/FBO/Tessellator 预览隔离和显式第三方上下文清理；调用方控制快照、恢复时机 |
| ui.mui2.ScrollEdges | 绘制滚动内容上下渐隐与边缘；不拥有滚动、裁剪、鼠标或控件生命周期 |
| api.extension.ExtensionCatalog | 每实例命名空间注册、稳定快照、可关闭句柄、安装重入保护与注销失败重试 |
| api.camera | 不可变姿态、正交投影/反投影射线、交点、缩放与旋转缓动，不引用 Minecraft 或 GL |
| api.ui.CanvasTransform / api.camera.ViewportCoordinates | 纯 Java 8 的画布正逆映射、GUI/NDC 坐标与裁剪后的 framebuffer 整数边界 |
| client.camera.WorldCameras | 客户端镜头独占租约、优先级、世界身份及输入/GUI策略，无消费者时被动放行 |
| client.camera.WorldCameraHooks | 统一投影、同帧世界射线、渲染原点、GL/实体/鼠标恢复，消费者不复制公共 Mixin |
| client.camera.CameraSettings / CameraControls | 不可变具名策略及能力回调；准星、非模态控制条、平移旋转缩放与切层由消费者选择 |
| client.camera.CameraFrame | 成功显示的只读帧、复用坐标基、requested/displayed/committed 切层与内容 revision |
| api.camera.HeightSlice / SliceSurfaceRule | 整数上下高度区间、真实边界面及同像素渐变覆盖规则 |
| client.camera.slice.SliceBackend | RC 版本默认地形与 TESR 裁切、局部边界补面和后台上传状态；不支持活动 Iris shaderpack |
| testkit.Deadline | 可注入单调时间戳的被动预算，处理 nanoTime 回绕，不注册维度或后台任务 |

~~~java
try (GlScope scope = new GlScope()) {
    RuneRenderer.standard().unlit();
    // Draw using the chosen renderer. Only call on the client render thread.
}
~~~

ExtensionCatalog 回调同步执行于实例锁内，禁止等待另一个需要此 catalog 的线程。注销动作须精确撤销自身安装；失败后句柄保持 active，调用方可重试。不要在全局共享可变 catalog 中混合无关生命周期。

世界等距引擎的完整调用示例、所有权与坐标约定见 [相机 API](docs/world-camera.md)。Outpost 提供模式、战斗与远端策略，蓝图提供选点和编辑策略；引擎不注册两仓的按键或玩法。

`CanvasTransform(sourceX, sourceY, targetX, targetY, scale)` 将源锚点映射到目标锚点，提供 `toScreenX/Y` 和 `toLocalX/Y`；所有参数必须有限，scale 必须为正。`child.compose(parent)` 按 `parent(child(local))` 顺序组合。`ViewportCoordinates(x, y, width, height)` 使用左上原点的 GUI 坐标，要求位置、非负尺寸及右下边界有限；`contains` 左上包含、右下排除，零面积不命中。`ndcX/Y` 与 `screenX/Y` 连接 Y 向上的 NDC，允许映射视口外坐标，零尺寸轴不能归一化。`framebufferRect(guiWidth, guiHeight, displayWidth, displayHeight)` 裁剪到 GUI 边界并转换为 GL 左下原点，以 `FramebufferRect.left()/bottom()/width()/height()` 返回向外取整的像素边界，空轴保持为空；GUI 尺寸须正且有限，显示尺寸允许为零但不能为负。它们不读取 GL 状态，也不代替现有相机投影或射线 API。

## 兼容与演进

0.1.x 保持已公开签名兼容；新增能力采用新入口或重载。破坏性变更进入 0.2 并先迁移消费者。客户端保护不能随意混用 raw GL 与带缓存的状态管理器：GTIT 保留自身状态路径，Core 仅输出轨迹。第三方预览清理为显式调用，仅在类存在时执行，不创建强制依赖。

本批已接入七仓，旧公开 renderer、scope 和注册句柄由原仓保留委托。原纹理路径、维度 ID、权限、NBT、配方和业务注册仍属原仓。堆叠/枢纽组合设施、完整 UI/预览引擎、更多 TestKit runner 与报表协议属于后续批次，参见 [迁移路线](docs/migration.md)。

## 构建

使用 JDK 25 执行 Gradle，产物为 Java 8 字节码：gradlew build --no-configuration-cache。check 包含纯 JVM 画布/视口坐标、相机投影/反投影、镜头所有权、时间边界、句柄生命周期和 glint 参数契约；画布/视口契约也可单独执行 `gradlew runViewportCoordinateContracts --no-configuration-cache`。这不代表 native GUI/光效已经游戏验收。发布前必须先完成检查，提交、设置与 RELEASE_VERSION 相符的本地 Tag，确认工作树干净，再从 Tag 构建。正式 Maven 坐标不可覆盖，变更必须递增版本。

AGPL-3.0。原有实现与资源来源见 [NOTICE](NOTICE.md)。

## Platform baseline

支持 **GTNH 2.9.0 RC 版本**。公开的 0.1.x API 保持向后兼容；切片按已显示的裁切策略即时选点，边界补面与光照网格在后台更新。可选拖转阈值区分短点击与拖动，默认保留原有导航行为。可选集成保持静默隔离，持久化数据迁移仍由消费者负责。
