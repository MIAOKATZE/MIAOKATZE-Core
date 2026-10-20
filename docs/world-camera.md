# 世界相机 API（0.1.5）

Core 提供可独立调用的世界等距引擎。消费者只提交锚点、角度、缩放和输入/显示策略；按键、权限、战斗、方块选点、网络和存档仍在消费者。所有相机与租约操作必须在客户端线程进行，不能从服务端类的静态字段引用客户端入口。0.1.3/0.1.4 的 Source、Options、View、Lease 和数学入口继续可用；新增功能通过默认方法桥接，不强迫旧调用方启用工具栏或改变准星。

## 最小接入

```java
WorldCameras.Source camera = new WorldCameras.Source() {
    public WorldCameras.View sample(float partialTicks, int width, int height) {
        return new WorldCameras.View(
            new CameraPose(anchorX, anchorY, anchorZ, 45, 35.264389682754654),
            OrthographicProjection.scaled(width, height, 12, 256), true);
    }
    public WorldCameras.Options options() {
        return new WorldCameras.Options(true, true, true, true, false, true,
            WorldCameras.MovementPolicy.ALL, true);
    }
    public WorldCameras.GuiPolicy guiPolicy() { return WorldCameras.GuiPolicy.RELEASE; }
};
WorldCameras.Lease lease = WorldCameras.tryAcquire(
    "example:editor", mc.theWorld, 30, camera, () -> clearLocalCameraState());
// null means that the current camera has equal or higher priority.
// Explicitly exit; close is also safe after another camera has taken ownership.
if (lease != null) lease.close();
```

示例中的姿态和操作策略由消费侧替换。`scaled` 的第三个参数为每个方块占用的缩放 GUI 像素数；半视野为 `width / (2 * pixelsPerBlock)` 与 `height / (2 * pixelsPerBlock)`。角度使用度数；深度与远平面随视野计算，非法/非有限输入立即拒绝。无 GL 的 `api.camera` 可独立用于预览数学与工具。

## 所有权与生命周期

同一世界只允许一个有效租约。更高优先级撤销旧租约；相同或更低优先级返回 null，调用者应显示本地提示并等待用户操作。owner ID 不是撤销凭据，旧句柄无法撤销新的同名租约。世界通过对象身份匹配，换维度、退出或替换世界会释放。撤销回调在显式 close、被抢占及换世界时执行；回调应只清理本地状态，不能重入抢占。

`GuiPolicy` 提供 KEEP（保留）、SUSPEND（暂停画面）与 RELEASE（释放）；默认 SUSPEND。`Source.sample` 每次 `renderWorld` 采样一次，返回 null 可以只预留所有权而不应用正交投影。Options 默认全被动；隐藏手臂、关闭雾/剔除、滚轮、调试快捷键、移动抑制和游离鼠标均显式选择。`allowKey` 只控制原生 KeyBinding 状态，不替代消费者的权限或网络校验。

本批消费者优先级约定为 Outpost 普通视角20、蓝图编辑30、Outpost Support100。数字和恢复策略不是 Core 内置规则：Outpost 可在引擎空闲时恢复已请求的模式，蓝图被抢占后只接受用户重新开启。Core 不自动替任一消费者重抢镜头。

## 坐标与拾取

`WorldCameraHooks.mouseRay(owner, world, displayX, displayY)` 使用最近一次成功显示的冻结投影，坐标是 LWJGL 窗口像素，原点在左下。`projectWorld` 在 WorldLast 使用当次冻结投影，其他阶段使用成功显示帧，返回 Y 向上的 NDC；GUI 的 Y 为 `(1 - ndcY) * scaledHeight / 2`。无有效帧、owner/world 不匹配、窗口尺寸变化、真实 GUI 或暂停时不能世界选点。调用者应拒绝该次选点，不能退回玩家眼睛射线。

射线原点在正交近裁面，方向归一化。它不是玩家交互距离，也不意味着穿过未加载区块或获得服务器编辑权限。消费者负责实体/方块查询、已加载区域、业务距离和权限。`CameraIntersections` 提供平面和 AABB 几何，返回射线距离，无命中为 NaN。

Core 在锚点驱动绘制期间临时调整 view entity 的渲染位置，在 WorldLast 前恢复真实实体插值与统一渲染原点；WorldLast 的普通渲染器继续采用原版“减真实 view entity 原点”约定。每次渲染通过 finally 恢复实体、矩阵、雾与剔除状态；消费者不应叠加另一套公共投影或自己修改玩家位置。

## 启动与版本

公共 hooks 随 Core 的 FML early loader 启动，专服返回空客户端 Mixin 列表。使用整合包已有 UniMixins，不注册 Core 自有 RFB 插件，不在 preInit 动态追加 Mixin。Angelica/Celeritas 的通用正交面/视锥门控位于 Core，业务可见性仍由消费者保留。

FalsePatternLib 精确下载0.1.5，不覆盖已安装旧版本。普通 LaunchWrapper 首次下载带 early plugin 的 JAR 可能退出并要求重启；RFB 路径需在 FML 扫描前发现下载结果。安装器退出0不能单独证明相机 hooks 已生效，应以实际启动与画面/拾取验证为准。

## 设置、操作与工具栏

新消费者覆盖 `Source.settings()` 和 `Source.controls()`。`CameraSettings` 的 `Render`、`Input`、`Hud`、`Navigation` 分别使用具名 builder；缓存不可变设置，仅在策略或切层请求变化时替换。`CameraSettings.legacy(options, guiPolicy)` 是旧入口的兼容桥。

`Hud.crosshair(HIDDEN)` 只取消 Forge 的 CROSSHAIRS 元素，手臂策略独立配置。`panelVisible(true)` 启用非模态工具栏，可拖动、折叠并配置位置、宽度及颜色；不会打开 GuiScreen。按钮同时要求导航策略允许和 `CameraControls.supports(...)` 返回 true。不支持俯仰的消费方可以只开放水平旋转、缩放和切层。回调只提交本地相机请求，不移动玩家、不代替网络权限检查。

旧默认仍是中键平移、Ctrl+滚轮缩放，不启用拖动旋转。Core 在 Forge 鼠标事件分派前接管已配置手势和工具栏。按下至释放的捕获绑定 owner epoch 和世界身份；GUI、失焦及抢占撤销捕获并隔离旧释放。`KEEP` 只保留画面，真实 GUI 中仍禁止世界操作。

```java
CameraSettings settings = CameraSettings.builder()
    .hud(CameraSettings.Hud.builder()
        .crosshair(CameraSettings.Hud.Crosshair.HIDDEN).panelVisible(true).build())
    .navigation(CameraSettings.Navigation.builder()
        .yaw(true).zoom(true).reset(true).sliceRange(0, 256).build())
    .slice(HeightSlice.below(80, .25))
    .build();
// Return this cached value from Source.settings(). Implement supported callbacks in Source.controls().
```

## X 光高度裁切

`HeightSlice.below(maxExclusive, feather)` 保留 Y 小于边界的方块层，`above(minInclusive, feather)` 保留边界及其上方，`between(minInclusive, maxExclusive, feather)` 保留区间。工具栏提供开关、上下方向、单高度条及窄带渐变；API 也接受双边界。高度按整数方块层提交，默认微渐变宽度为四分之一格。边缘使用有序覆盖抖动，保留原有深度与透明排序。

默认裁切地形与方块 TESR；天空、手臂、普通实体、粒子和 HUD 不参与。世界预览需显式包裹 `SliceBackend.worldPreview()`，GUI 预览不要使用该域。GPU 预览与原生边界补面配合，补面沿用 RenderBlocks 与 GT205 标准判面链，更新仅排队相关边界区段并限制每 tick 数量。活动 Iris shaderpack 会报告不支持并关闭切层；绕过标准判面的自定义 ISBRH 不能保证补面。

`CameraFrame` 是只读历史快照，不包含 World/Source，也不是输入授权。它分开记录 requested、displayed、committed 切片及内容 revision。PREVIEW、APPLYING、RESTORING 期间禁止世界选点；READY 取决于匹配网格实际上传。消费方仍需使用当前 hooks 检验所有权、焦点、捕获和就绪状态。

`rayInterval(...)` 保持原近裁面射线不变，返回可查询距离区间。查询只接受方块原生碰撞面的真实交点，并用 `SliceSurfaceRule.keeps(slice, y, normalY, displayX, displayY)` 对照同像素覆盖；不能把区间起点当成新碰撞面。区间、显示帧、真实侧面法线及像素必须来自同次查询，缓存键包含 owner epoch、变换、切片、网格 revision 与像素。

## 可选的独占输入与紧凑切层条（0.1.5）

以下设置不会改变旧消费者的默认操作；`pan`、`yaw`、`pitch`、`zoom` 仍分别要求 `CameraControls.supports` 和相应回调。鼠标编号遵循 LWJGL：左键0、右键1、中键2。`rotateDrag(button, requiredKey)` 的按键为 LWJGL 码，0表示无修饰键；-1鼠标编号关闭手势。`rotationDegreesPerPixel` 使用缩放GUI像素，默认0.35度；俯仰遵循 `pitchRange`。旋转按下时检查修饰键，完整捕获持续到鼠标释放。滚轮策略有 `CTRL`（旧默认）、`DIRECT`、`DIRECT_EXCEPT_ALT`。

```java
CameraSettings settings = CameraSettings.builder()
    .input(CameraSettings.Input.builder()
        .freeCursor(true).consumeHotbarScroll(true).blockDebugReloads(true)
        .movement(WorldCameras.MovementPolicy.ALL).exclusiveKeyboard(true).build())
    .hud(CameraSettings.Hud.builder()
        .crosshair(CameraSettings.Hud.Crosshair.HIDDEN)
        .panelVisible(false).sliceBarVisible(true)
        .sliceDirectionVisible(true).sliceLabelVisible(true)
        .panelPosition(12, 12).build())
    .navigation(CameraSettings.Navigation.builder()
        .pan(true).yaw(true).pitch(true).zoom(true)
        .panButton(1).rotateDrag(0, Keyboard.KEY_R)
        .wheelZoom(CameraSettings.Navigation.WheelZoom.DIRECT_EXCEPT_ALT)
        .sliceRange(0, 256).build())
    .slice(HeightSlice.below(80, 1).withFeatheredCaps(true))
    .build();
```

`exclusiveKeyboard(true)` 仅在当前会话可交互、没有真实GUI时过滤Minecraft的原始键盘事件队列，早于硬编码原版快捷键、KeyBinding和FML KeyInputEvent。`Source.allowRawKey(int)` 决定哪些事件可进入原版/FML，默认桥接到旧 `allowKey(int)`。进入该模式时清除之前残留的KeyBinding按下状态。消费者可以将 `allowRawKey` 和 `allowKey` 都返回false，再在客户端tick使用raw按键边沿和按住轮询处理自己的白名单（含退出键），从而避免其他Mod在相同按键上收到事件。其他Mod直接调用 `Keyboard.isKeyDown` 的轮询无法由此隔离；放行某个原始键也会允许其他订阅者看到它。恢复GUI或退出相机即恢复原生事件；消费者仍需处理自身切换/退出按键的边沿与失焦复位。

`sliceBarVisible(true)` 在大工具栏关闭且请求切层启用时显示72×220逻辑GUI像素的竖高度条；通过顶部拖动整体位置，拖动竖条选择整数Y，顶部对应最大高度。`sliceDirectionVisible` 控制“隐藏上方/隐藏下方”切换按钮，`sliceLabelVisible` 控制Y值，均默认true。大面板默认保持旧外观；两者同时开启时显示大面板。切层开关（例如X）仍由消费方拥有，Core不注册按键。紧凑条不会引入MUI2依赖。

`HeightSlice.withFeatheredCaps(true)` 显式启用整层透明覆盖：按实际表面所属方块层的中心计算渐变，而不再将切面强制不透明。1格渐变使边缘一层覆盖率0.5、下一层完全不透明；多格宽度按smoothstep逐层变化。使用有序覆盖透明（discard），而非源alpha混合，以保留RC2地形原有深度写入和半透明方块排序；因此近看有4×4像素覆盖纹理。CPU拾取与GLSL采用相同的所属层、覆盖函数及像素门槛。GPU重建预览可能暂缺补面，READY仍须等待匹配网格实际上传。

新模式补出羽化带内被邻层遮挡的原生上下表面，并将被移除或渐变邻层从局部AO遮挡查询中排除；不修改世界、碰撞或光照传播。原生天空/方块光仍可能因真实屋顶和洞穴而偏暗，自定义ISBRH自行查询世界/绕过标准面链时仍不能保证补面或AO一致性。旧 `HeightSlice` 工厂默认不启用此模式，保留实体边界盖面、原逐片元渐变与四分之一格工具栏默认；消费方需要1格默认时显式传入1。旧模式只有边界高度影响网格，新模式的 `ceil(featherWidth)` 也参与网格依赖，带宽改变所需面数时局部重建。
