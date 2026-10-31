# 世界相机 API（0.1.7）

## 0.2.0：GTNH 2.9.0 正式版

开发依赖对齐正式版的 GT5U 5.09.54.264、MUI2 2.3.94、GTNHLib 0.11.54、NEI 2.8.171 与 Angelica 2.2.42。公开相机、切片和渲染 API 签名保留，RC2 的 Core 0.1.28 发布物不变。

切片边界捕获跟随 Angelica 的 `drawBlock`；即时和 deferred 编码分别恢复同一任务快照作用域，完整保留非零 `vertexOffset`，两条输出路径在 `finalizeSection` 统一标记。程序删除回收改挂 `GLSMPrograms`。宽线使用正式版 vertex shader 的中心线和近裁剪插值参数计算切片坐标，不从包含线宽与像素偏移的扩张顶点反算高度。旧 geometry shader 注入不再注册。

本次验证限于构建、纯 JVM 合同及正式依赖字节码的注入锚点/局部变量核验；没有启动游戏。真实 Mixin 组合、deferred 上传、不同宽线与俯仰角的切面视觉、资源重载和 GL 状态恢复仍需客户端验收。以下 RC2 运行记录保留其原版本范围，不作为正式版实测结果。

## 0.1.22：切屏与自动暂停

失去窗口焦点时，相机租约保留，输入和拖拽暂停；回到游戏后恢复原相机。Core 保留原版 `pauseOnLostFocus` 偏好，不以保持相机为由取消单人的自动暂停。自动暂停菜单按实际对象及世界身份识别，只有该实例暂时豁免 `GuiPolicy.RELEASE`；玩家显式打开的其他界面、主动关闭、换世界和租约抢占仍遵循原生命周期。

消费者在 `GuiOpenEvent` 中处理 Esc 等业务退出时，可用 `WorldCameraHooks.isAutomaticPause()` 区分自动失焦菜单；维护模式时可用 `isAutomaticPauseScreen()` 判断当前是否仍是该自动菜单。`pauseOnLostFocus(Minecraft, Runnable)` 是 Core 注入入口使用的作用域桥，消费者无需重复包装。输入侧同时清理跨焦点的拖拽与按键状态，并保留原始光标抓取状态，避免回焦后右键或退出相机时光标恢复失效。

这些入口为增量 API，原有接口及默认方法保持兼容。GTMB 使用新增判断入口时最低要求 Core 0.1.22；GTSWN 和 Outpost 的现有 `KEEP` 调用无需改写，即可使用共享的输入恢复修复。

Core 提供可独立调用的世界等距引擎。消费者只提交锚点、角度、缩放和输入/显示策略；按键、权限、战斗、方块选点、网络和存档仍在消费者。所有相机与租约操作必须在客户端线程进行，不能从服务端类的静态字段引用客户端入口。0.1.3/0.1.4/0.1.5 的 Source、Options、View、Lease 和数学入口继续可用；新增功能通过默认方法桥接，不强迫旧调用方启用工具栏或改变准星。

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

0.1.11 新增 `WorldCameras.tryActivate(ownerId, worldToken, priority, source, onRevoked)`，供用户按键或按钮主动切换同优先级视图。它允许替换相同优先级的租约，仍拒绝抢占更高优先级镜头；旧句柄失效、渲染清理与禁止回调重入的规则不变。后台恢复和每 tick 维护继续使用 `tryAcquire`，避免多个消费者互相抢占。原 `tryAcquire` 的签名和相同优先级拒绝行为保持兼容。

`GuiPolicy` 提供 KEEP（保留）、SUSPEND（暂停画面）与 RELEASE（释放）；默认 SUSPEND。`Source.sample` 每次 `renderWorld` 采样一次，返回 null 可以只预留所有权而不应用正交投影。Options 默认全被动；隐藏手臂、关闭雾/剔除、滚轮、调试快捷键、移动抑制和游离鼠标均显式选择。`allowKey` 只控制原生 KeyBinding 状态，不替代消费者的权限或网络校验。

0.1.13 修复 `KEEP` 在真实 GUI 清除 `inGameHasFocus` 后被错误暂停的问题。只要原生窗口仍激活且世界、租约有效，GUI 背景继续使用相机投影，`frame` / `projectWorld` 仍可读取显示帧。GUI 不获得世界拾取、导航或独占键盘权限；这些操作继续要求没有 GUI 且游戏有焦点。窗口失焦会撤销显示帧与捕获，恢复光标时也不会重新抓取失焦窗口；切换世界、断线及租约失效仍清理原会话。消费者应直接打开真实 GUI，无需修改 `inGameHasFocus`。原有签名、默认 `SUSPEND` 和 `RELEASE` 行为保持不变。

本批消费者优先级约定为 Outpost 普通视角20、蓝图编辑30、Outpost Support100。数字和恢复策略不是 Core 内置规则：Outpost 可在引擎空闲时恢复已请求的模式，蓝图被抢占后只接受用户重新开启。Core 不自动替任一消费者重抢镜头。

0.1.18 将相机生命周期包装移到 `updateCameraAndRender(F)V` 内的 `renderWorld(FJ)V` 调用处，覆盖原版限帧和不限帧两条分支。`renderWorld` 的方法体留在原入口，使 Curvy Pipes 1.15.9 的原生补丁仍能扫描绘制指令；原有 `try/catch/finally`、异常中止、恢复与成功帧发布保持不变。Core 不识别特定 Mod 来关闭相机功能，公开 API 也不变。

MC、Angelica 2.2.28 和已审计的系列消费者均经过该入口。未知第三方绕过 `updateCameraAndRender` 直接调用 `renderWorld` 时，不在此生命周期覆盖范围。RC2 后台隔离客户端已用玩家日志中的四个 Release 包完成原故障复现与修复对照，验证 Curvy 原生渲染和相机启用/释放；测试只适配渲染可见性的焦点读取，未授予输入权限，未覆盖硬件输入、视觉或实际管道传输验收。异常路径另由实际编译 handler 的契约回归验证。

## 坐标与拾取

`WorldCameraHooks.mouseRay(owner, world, displayX, displayY)` 使用最近一次成功显示的冻结投影，坐标是 LWJGL 窗口像素，原点在左下。`projectWorld` 在 WorldLast 使用当次冻结投影，其他阶段使用成功显示帧，返回 Y 向上的 NDC；GUI 的 Y 为 `(1 - ndcY) * scaledHeight / 2`。无有效帧、owner/world 不匹配、窗口尺寸变化、真实 GUI 或暂停时不能世界选点。调用者应拒绝该次选点，不能退回玩家眼睛射线。

射线原点在正交近裁面，方向归一化。它不是玩家交互距离，也不意味着穿过未加载区块或获得服务器编辑权限。消费者负责实体/方块查询、已加载区域、业务距离和权限。`CameraIntersections` 提供平面和 AABB 几何，返回射线距离，无命中为 NaN。

Core 在锚点驱动绘制期间临时调整 view entity 的渲染位置，在 WorldLast 前恢复真实实体插值与统一渲染原点；WorldLast 的普通渲染器继续采用原版“减真实 view entity 原点”约定。每次渲染通过 finally 恢复实体、矩阵、雾与剔除状态；消费者不应叠加另一套公共投影或自己修改玩家位置。

Core 正交世界渲染期间跳过 `renderCloudsCheck` 云绘制入口。正交投影的深度偏移可能使虚拟镜头位于云层上方，即使锚点仍在地面，云面也会挡在工厂与镜头之间；关闭雾无法消除云几何。此规则适用于所有使用 Core 相机的消费者，不修改用户云设置；普通玩家视角继续按原设置绘云，天空及其他渲染通道保持原有行为。

## 启动与版本

公共 hooks 随 Core 的 FML early loader 启动，专服返回空客户端 Mixin 列表。使用整合包已有 UniMixins，不注册 Core 自有 RFB 插件，不在 preInit 动态追加 Mixin。Angelica/Celeritas 的通用正交面/视锥门控位于 Core，业务可见性仍由消费者保留。

FalsePatternLib 精确下载0.1.7，不覆盖已安装旧版本。普通 LaunchWrapper 首次下载带 early plugin 的 JAR 可能退出并要求重启；RFB 路径需在 FML 扫描前发现下载结果。安装器退出0不能单独证明相机 hooks 已生效，应以实际启动与画面/拾取验证为准。

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

`CameraFrame` 是只读历史快照，不包含 World/Source，也不是输入授权。它分开记录 requested、displayed、committed 切片及内容 revision。切片策略显示后即可选点，APPLYING/RESTORING 描述后台边界网格状态；READY 与 committed 仍取决于匹配网格实际上传。消费方仍需使用当前 hooks 检验所有权、焦点、捕获及 requested/displayed revision，旧帧不能用于新切片。

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

`HeightSlice.withFeatheredCaps(true)` 显式启用整层透明覆盖：按实际表面所属方块层的中心计算渐变，而不再将切面强制不透明。1格渐变使边缘一层覆盖率0.5、下一层完全不透明；多格宽度按smoothstep逐层变化。使用有序覆盖透明（discard），而非源alpha混合，以保留RC2地形原有深度写入和半透明方块排序；因此近看有4×4像素覆盖纹理。CPU拾取与GLSL采用相同的所属层、覆盖函数及像素门槛。GPU重建预览可能暂缺补面；此时按已显示的裁切策略即时拾取，READY仍须等待匹配网格实际上传。

新模式补出羽化带内被邻层遮挡的原生上下表面，并将被移除或渐变邻层从局部AO遮挡查询中排除；不修改世界、碰撞或光照传播。原生天空/方块光仍可能因真实屋顶和洞穴而偏暗，自定义ISBRH自行查询世界/绕过标准面链时仍不能保证补面或AO一致性。旧 `HeightSlice` 工厂默认不启用此模式，保留实体边界盖面、原逐片元渐变与四分之一格工具栏默认；消费方需要1格默认时显式传入1。旧模式只有边界高度影响网格，新模式的 `ceil(featherWidth)` 也参与网格依赖，带宽改变所需面数时局部重建。

## 可选的短点击与拖转（0.1.7）

`Navigation.Builder.rotationClickThreshold(4)` 配合 `rotateDrag(0, 0)`，在移动超过4个逻辑GUI像素后开始左拖旋转；越过阈值后即使回到原位置，也不会再产生短点击。小幅抖动或未移动的释放只调用 `WorldCameras.Source.rotationClick(int button, int displayX, int displayY)`，坐标为左下原点的framebuffer像素。回调发生在Core结束捕获之后，可通过当前 `pixelRayInterval` 执行业务选点；取消、焦点丢失、GUI打开或owner/world失效不触发点击。`rotateExcludesControl(true)` 继续把Ctrl手势交给Forge业务监听器。默认阈值0保留原来的立即拖转，新回调默认空实现，既有消费者不受影响。

## 滚轮单位、业务修饰键与切面光照（0.1.6）

`Navigation.wheelDeltaUnit` 的默认值 `LEGACY_120` 保留旧入口行为。`NOTCH` 直接接受1为一格滚轮，`AUTO` 通过实际运行时的 `org.lwjglx.input.Mouse` 桥选择：RC2 lwjgl3ify 3.0.37 使用1为一格，经典LWJGL使用120为一格。`wheelSensitivity` 默认1；`wheelZoomLimit` 默认不额外限幅。新调用方可配置：

```java
CameraSettings.Navigation.builder()
    .pan(true).yaw(true).zoom(true)
    .panButton(1).rotateDrag(0, Keyboard.KEY_R)
    .panExcludesControl(true).rotateExcludesControl(true)
    .wheelZoom(CameraSettings.Navigation.WheelZoom.DIRECT_EXCEPT_ALT)
    .wheelDeltaUnit(CameraSettings.Navigation.WheelDeltaUnit.AUTO)
    .wheelSensitivity(50).wheelZoomLimit(1.5)
    .build();
HeightSlice slice = HeightSlice.below(80, 1)
    .withFeatheredCaps(true).withLightCompensation(true);
```

该示例在RC2每格实际缩放约1.5倍，反向为2/3，随后仍受 `scaleRange` 约束；同样的经典120单位事件结果一致。旧版本在RC2将raw 1再次除120，步进约1.000794倍。修正单位并设置增益/限幅后，相对于该实际旧响应超过50倍；这不表示相对于规范旧120单位整格响应仍乘50。限幅防止直接 `pow(1.1, 50)` 一格跳到缩放端点。旧调用仍保留原单位、增益和无额外事件限幅。

`panExcludesControl(true)` 和 `rotateExcludesControl(true)` 在按住任一Ctrl时不开始相应导航捕获，让该鼠标事件进入消费方Forge处理（例如Ctrl+左/右设置固定两个角点）。默认均false；已捕获的拖动仍持续到原按钮释放。业务操作优先级由消费方在鼠标监听器中决定，Core不拥有选角规则。

`HeightSlice.withLightCompensation(true)` 是可选的局部渲染补偿，默认false。切面及羽化带邻域在普通RenderBlocks光照读取和RC2 Celeritas独立LightDataCache中使用完整环境光通道，并保留真实方块光；被裁掉或渐变邻层不再贡献遮挡/AO。环境光仍通过当前维度和昼夜的lightmap呈现，不修改World、区块存储、真实光照传播或碰撞。补偿标志及其带宽参与网格依赖，更改后必须等待对应顶点网格上传。自定义ISBRH自行读取世界或硬编码颜色/亮度时仍不保证补偿覆盖；远离切面邻域的真实暗部保留。

0.1.5仅替换RenderBlocks的邻层访问；RC2会在随后 `copyRawBuffer` 中使用原WorldSlice重新计算平滑光照，覆盖先前AO结果。0.1.6将同一冻结切片作用域同时应用于该独立光缓存，避免这一覆盖路径。

## 即时选点、后台网格与诊断（0.1.7）

Core继续区分请求、显示预览和实际网格上传。切片显示后，拾取使用该帧的世界射线和 displayed 高度区间，无需等待整个视野的边界补面重建。`pickReady()` 可以在 APPLYING 或 RESTORING 期间为true；`SliceState.READY` 与 committed revision仍只在匹配切片和最新native提交帧真实上传后更新，未完成的补面或光照不能冒充已上传。GUI、焦点、owner/world、捕获、requested/displayed revision及renderer generation仍会拒绝过期或越权点击。网格上传本身不改变射线与裁切区间，因此不会单独阻断选点。

排队前已开始的当前切片任务不会再被Core自身取消。没有活任务token的dirty区段可以重试；RC2已经标为pending但丢失队列入口时会重新进入原生interim队列，仍保持每预算最多4个区段。不会使用超时强制READY。生产实际使用的 `SliceMeshProgress` 纯JVM回归覆盖提交/确认、取消重试、隐藏转可见、旧回执和恢复转换；它不能替代真实渲染上传验证。

需要有限的本机排查时，客户端线程可显式调用 `SliceMeshes.diagnostics()` 获取只读状态、dirty/visiblePending/queued/building/installed/token/update/orphan计数、revision和区段样例。该入口不自动打印、不操作队列；正常游戏无诊断日志开销。RC2队列桥只在编译期引用fastutil 8.5.18（与Angelica RC2相同），不会把该库打进Core产物。

## 本地玩家本体（0.1.14，可选）

`CameraSettings.Render.builder().renderPlayerBody(true)` 在 Core 正交世界画面中显示本地玩家本体，默认 `false`，旧版 `Options` 桥接也保持关闭。Core 在实体阶段仅延后本地玩家，等 WorldLast 前恢复真实玩家插值坐标和渲染原点后统一绘制；第一人称和进入前已用 F5 切换的第三人称均只绘制一次，不会把本体画在自由镜头的虚拟锚点。普通视角与其他实体的原版通道不变。

调用方无需复制实体、修改 `renderViewEntity`、F5 选项或玩家坐标。Core 复用原生 `RenderManager` 和已存在的完整渲染状态 scope；矩阵栈、纹理/光照贴图缓存、GL 属性与程序/缓冲绑定在 `finally` 恢复。该策略不改变玩家移动、碰撞、服务端位置或游戏权限。`WorldCameraHooks.beforeWorldLast(Minecraft)` 保留兼容入口，内部 Mixin 使用带 `partialTicks` 的重载以正确插值本体动画。
