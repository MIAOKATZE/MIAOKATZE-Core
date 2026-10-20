# 世界相机 API（0.1.3）

Core 提供可独立调用的世界等距引擎。消费者只提交锚点、角度、缩放和输入/显示策略；按键、权限、战斗、方块选点、网络和存档仍在消费者。所有相机与租约操作必须在客户端线程进行，不能从服务端类的静态字段引用客户端入口。

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

`WorldCameraHooks.mouseRay(owner, world, displayX, displayY)` 使用刚应用的同帧投影，坐标是 LWJGL 窗口像素，原点在左下。`projectWorld` 返回 Y 向上的 NDC；GUI 的 Y 为 `(1 - ndcY) * scaledHeight / 2`。无有效帧、owner/world 不匹配、窗口尺寸变化或暂停时返回 null。调用者应拒绝该次选点，不能退回玩家眼睛射线。

射线原点在正交近裁面，方向归一化。它不是玩家交互距离，也不意味着穿过未加载区块或获得服务器编辑权限。消费者负责实体/方块查询、已加载区域、业务距离和权限。`CameraIntersections` 提供平面和 AABB 几何，返回射线距离，无命中为 NaN。

Core 在锚点驱动绘制期间临时调整 view entity 的渲染位置，在 WorldLast 前恢复真实实体插值与统一渲染原点；WorldLast 的普通渲染器继续采用原版“减真实 view entity 原点”约定。每次渲染通过 finally 恢复实体、矩阵、雾与剔除状态；消费者不应叠加另一套公共投影或自己修改玩家位置。

## 启动与版本

公共 hooks 随 Core 的 FML early loader 启动，专服返回空客户端 Mixin 列表。使用整合包已有 UniMixins，不注册 Core 自有 RFB 插件，不在 preInit 动态追加 Mixin。Angelica/Celeritas 的通用正交面/视锥门控位于 Core，业务可见性仍由消费者保留。

FalsePatternLib 精确下载0.1.3，不覆盖已安装旧版本。普通 LaunchWrapper 首次下载带 early plugin 的 JAR 可能退出并要求重启；RFB 路径需在 FML 扫描前发现下载结果。安装器退出0不能单独证明相机 hooks 已生效，应以实际启动与画面/拾取验证为准。
