# SOLID_BOX 可视合同候选

本次新增 `core.animation.visual` schema **1.0**，实现无外部资源方盒的可保存定义和纯绑定求值。所有公共类型位于 `com.miaokatze.miaokatzecore.api.animation`。这是可供消费者接入的新合同候选，不能单独证明 F03 可见作品或 F-CORE-01c 已闭环。

旧 `core.animation.scalar` V0 的公开签名、字段白名单、时间单位、随机算法、错误和规范编码保持原有行为。新格式不交给旧 codec 猜测，也不向 V0 添加 target、material、transform 或 keyId 字段。

## 支持范围

|能力|本次合同|
|---|---|
|图元|仅 `SOLID_BOX`，本地中心为原点、坐标 `[-0.5,0.5]` 的单位盒|
|静态姿态|三轴位置、单位四元数、三轴正缩放，矩阵为 `T × R × S`|
|材质|仅 `SOLID_COLOR_UNLIT`：24 位 RGB 与直通 alpha；没有纹理、光照、HDR 或材质脚本|
|动态通道|X/Y/Z、统一 SCALE、本地 Z 轴 ROTATION_DEGREES、OPACITY、VISIBILITY|
|身份|target/material 使用持久局部 ID；frame 验证确切完整 VisualClip 对象|
|状态/时间|调用现有 Sampler；seek/reverse/export 只求当前值，无状态推进或 cue 通道|
|外部资源、cue、BRIGHTNESS、FOV、节点层级、anchor、模型、粒子、脚本|明确 UNSUPPORTED|

首版整体 profile 是必需能力，没有可选图元降级或 `requiredFeatures/optionalFeatures` 字段。可信 host 必须确实支持上述图元、材质与姿态；不满足时拒绝新候选并保留旧计划，不自动改成别的物体。

## 格式与不可变模型

根字段完整白名单为 `format/schemaVersion/scalar/targets/materials`。`scalar` 是完整原样的 V0 对象，唯一持有文档 id、durationMs、seed 和 tracks。本 profile 要求其 `resources/cues` 均为空。

```text
format: "core.animation.visual"
schemaVersion: { major: 1, minor: 0 }
scalar: 原有 core.animation.scalar V0 文档
targets[]: {
  id, primitive: "SOLID_BOX", materialId,
  transform: { position: [x,y,z], rotationQuaternion: [qx,qy,qz,qw], scale: [sx,sy,sz] }
}
materials[]: { id, kind: "SOLID_COLOR_UNLIT", rgb, opacity }
```

所有字段必需。target/material ID 沿用 `[a-z0-9][a-z0-9_.-]{0,63}`；两表各自 ID 唯一，target 的 materialId 必须存在，每条 scalar 轨道的 targetId 必须存在。目标移动、改姿态或改材质不改变 ID。没有轨道的目标按静态姿态绘制；允许零目标的明确空白编辑文档，不将它称为已生成可见作品。

target、material 均最多 100。位置为有限 block 值，范围 ±1000000，空间为作品本地空间，无 World 绑定。四元数必须有限、范数平方与 1 的差不超过 0.00001；存储保留作者给出的合格分量，求值时归一化，避免连续编解码不断改写内容 hash。三轴基础 scale 各在 `(0,32]`，单位盒乘缩放后即实际三轴尺寸。rgb 为整数 `0..16777215`，直接对应 normalized unlit RGB 分量；opacity 为有限 `0..1`。

构造器本身完成计数、有限数、身份、引用与通道校验，并防御复制集合。V0 三种插值都不超出端点极值，因此构造阶段检查所有 SCALE key，保证完整曲线乘基础三轴 scale 后各轴不超过 32。不会在运行时截断作者的尺寸。

## 唯一绑定语义

|通道|无轨道时|有轨道时|
|---|---|---|
|X/Y/Z|基础位置的对应分量|采样值替换该分量，非累加偏移|
|SCALE|统一倍率 1|基础三轴 scale 乘采样值|
|ROTATION_DEGREES|角度 0|`qFinal = qBase × qLocalZ(angle)`，右手本地 Z 轴旋转|
|OPACITY|倍率 1|材质 opacity 乘采样值|
|VISIBILITY|可见|仅 1 可见，沿用 V0 STEP/0/1 约束|

最终任一缩放轴为 0、最终 opacity 为 0 或 VISIBILITY 为 0 时，`TargetPose.visible=false`。其中包括合法极小值乘法下溢至 0；不创建无效几何或回退尺寸。完整静态 TRS 不代表已有任意旋转通道、四元数 key 插值或父子动画。

`VisualPlan` 预编译 target/material/track 关系，使用 `Sampler` 提供的确切 `SampledFrame` 求姿态。它不复制另一套曲线采样器。运算使用 strictfp 和 StrictMath；输出目标按稳定 targetId 排序，具体 renderer 的透明排序在相同相机输入下执行。

## 新增 API

```java
VisualClip.Transform(double x, double y, double z,
    double qx, double qy, double qz, double qw,
    double scaleX, double scaleY, double scaleZ)
VisualClip.Target(String id, String materialId, VisualClip.Transform transform)
VisualClip.Material(String id, int rgb, double opacity)
VisualClip(AnimationV1.Document scalar, List<VisualClip.Target> targets,
    List<VisualClip.Material> materials)
VisualClip.supports(AnimationV1.Property property) // static boolean

VisualClipCodec.decode(byte[] bytes) // static VisualClip
VisualClipCodec.decode(InputStream input) throws IOException // static VisualClip
VisualClipCodec.encode(VisualClip clip) // static byte[]
VisualClipCodec.contentHash(VisualClip clip) // static String

VisualPlan.compile(VisualClip clip) // static VisualPlan
VisualPlan.evaluate(SampledFrame frame) // VisualFrame
// public final: VisualClip clip; String contentHash

VisualFrame.sampledFrom(VisualClip clip) // boolean, exact complete definition identity
// public final: String documentId; long localTimeMs; List<TargetPose> targets
VisualFrame.TargetPose.matrix() // double[16], fresh copy, column-major T*R*S
// TargetPose public final:
// String targetId, materialId;
// double x,y,z,qx,qy,qz,qw,scaleX,scaleY,scaleZ;
// int rgb; double opacity; boolean visible;
```

调用示例：先 `VisualPlan.compile(clip)`；在任意绝对毫秒调用 `Sampler.sample(clip.scalar, time)`；将此帧交 `plan.evaluate(frame)`。scalar 帧属于另一份对象时，即使 ID/hash 相同也拒绝。VisualFrame 的完整来源还包括 target、transform 和材质；材质修改后产生的 clip 不能被旧 frame 的 `sampledFrom` 接受。

一个合法 scalar frame 可显式绑定到共享该 scalar 的另一份完整 clip，但结果来源是新 clip，完整内容 hash 也覆盖新材质。宿主的 revision、异步 receipt 和 presentation generation 必须覆盖整个可视定义，不能只比较 scalar 引用。

## Codec 复用与失败

`StrictJsonCodec` 仅提取包私有的有界读取、parse、已解析 V0 校验和已有字段/数值助手；未新增公共解析树或格式路由。新 codec 直接消费同一个严格解析树里的原始数字 token，嵌套 long seed 不经过 double 或 Gson。外层与内层共用一次整体 depth/value 限额。V0 的语义错误重定位到 `$.scalar...`；词法错误本来就保留完整嵌套路径。

所有新输入仍限制为 2 MiB、depth 32、字符串 512 UTF-16 单元、数字 token 32、generic array 65536、总 values 262144。流入口最多读 `2097152+1` 字节，最后一个仅检测超限；不关闭 caller 的流，IOException 原样传播。UTF-8、解转义重复 key、非有限值、未知字段、版本、primitive/material kind、错误向量长度严格拒绝。

失败沿用 `StrictJsonCodec.CodecException` 及有界 `path/code`。新语义 code 包括 `UNSUPPORTED/RANGE/MATERIAL/TARGET/TRANSFORM/CLIP`；声明关系失败的 CLIP 信息说明重复身份、悬空引用或尺寸边界。无隐式图元、材质、资源或版本 fallback。

规范编码保留嵌套 scalar 的既定字节规则，target/material 按 ID 排序，固定外层字段顺序。`VisualPlan.compile` 验证整个规范编码不超过 2 MiB并缓存 SHA-256。定义对象合法但整体不可移植时编译失败；编译和求值没有可变当前状态，不会污染先前计划。

黄金文件 `src/test/resources/animation/solid-box.visual-v1.json` 的 SHA-256 为 `35618adb48a83ec2b5f4cff33389a35f81bd3c68694bf5bb5d93e1d20d54734b`。旧黄金 scalar 文件保持不变。

## Owner 与接线边界

Core 负责以上唯一公共类型、严格编解码和绑定数学。实际 client renderer 消费 TargetPose/matrix，复用 SolidMesh 的单位盒绘制；不自行重解释轨道。纯 Java SDK 不导入 MC/FML/GL，原生绘制入口使用真实依赖单列编译，不能以 stub 替代。

02/03 仍负责 UI、目标创建/改名、工程封套、history/save、资源宿主生命周期，以及同一帧/相机输入下的预览和导出接线。Key 的持久身份仍归 editor sidecar：不添加到 Core Key，不用时间、数组下标或对象引用冒充持久 keyId；移动、撤销、重开时须随作品快照原子保存。外部结构目标和权威混合 history 没有因本格式自动开放。

旧 bare scalar 文档继续按旧格式工作；变为可见作品需显式提供 target、transform 和材质绑定，不能靠猜测 targetId 自动补方盒。未来 texture/model、节点或 cue 能力必须另行版本化，不能向本严格 profile 静默增字段。

## 当前验证

真实 Zulu JDK 8 1.8.0_482 定向编译和执行：**VisualContracts 140 项、旧 AnimationContracts 13679 项均 PASS**。检查涵盖独立 TRS 组合 oracle、矩阵隔离、完整来源身份、材质/target 引用、尺寸/数量/有限数、规范字节/hash、nested long/duplicate/路径、未知能力、流硬限额与 I/O 失败。

证据在独立 `build/visual-contract-evidence/`，含脚本、真实命令/cwd、JDK 版本、源码与 fixture hash、编译日志及退出码；初版 139 项记录单独保留。后续 SDK、消费者和真实 client 编译由集成结果单独冻结。

本组证据未运行 GL、实例、MUI、图片/视频像素对比或跨仓保存闭环。Core 公共部分通过不能直接关闭 F03；需要 02/03 接入确认和相应实际验收。
