# 标量动画公共合同

本批新增包为 `com.miaokatze.miaokatzecore.api.animation`。执行基线 `f02dbdc2f4e9891b616bfd2c48b17f28305e44f0` 没有独立动画 API；此次保留现有 MiaoVision、WorldCameras、WorkGovernor、UiScene 签名与默认行为。原始设计稿 `design.core.animation.v1` 未成为生产包或依赖。本实现逐类采用已审标量规则，新增流读取边界和实际可用的计划/实例路径。

## 可用范围与数据流

`AnimationV1.Document → CompiledPlan → AnimationInstance → EvaluationInput → SampledFrame` 已实现。Document 构造时检查全部标量语义并复制集合；CompiledPlan 验证规范编码的可移植大小并缓存内容 hash；Instance 持有当前计划和播放身份；EvaluationInput 只提供绝对本地毫秒；SampledFrame 保存只读轨道值和确切定义来源。

标量轨道已经在 Document 中完成顺序与范围验证，所以本次编译不伪造通用节点编译器或另建相同轨道结构。资源闭包、可信绑定及实际 HostHandle 由宿主层负责，详见运行时交接。纯求值不加载资源、不访问 World/Entity/GL、不执行音频、相机或游戏回调。

|能力|本批状态|
|---|---|
|scalar V0 严格 JSON、不可变模型、规范 hash|已实现|
|STEP / LINEAR / SMOOTHSTEP 标量点采样|已实现，整数 ms，含终帧|
|计划可移植预算、实例原子替换、失败保留旧计划|已实现|
|独立有界观察 cue、高水位、late / loop / 双预算|已实现；只返回数据|
|120000 units/s 显式时间桥|已实现；不改变 V0 字段单位|
|typed legacy fade → BRIGHTNESS|已实现；仅调用方提供的标量序列|
|新完整节点格式、拓扑、transform、材质/资源绑定、参数/anchor 输入|UNSUPPORTED|
|Hermite、四元数/look-at、粒子/碰撞/尾迹模拟、checkpoint 重建|UNSUPPORTED|
|持续音轨、GL 后端、真实相机/音频执行、导出编码器|不由此纯合同提供|
|任意脚本/URL/class/shader 执行、玩法/伤害/费用/权限行为|不接纳|

## 唯一格式与单位

只接受 `format="core.animation.scalar"`、`schemaVersion={"major":1,"minor":0}`。根字段完整白名单是 `format/schemaVersion/id/durationMs/seed/resources/tracks/cues`；空数组也是必需字段。Track 为 `id/targetId/property/keys`；Key 为 `timeMs/value/interpolation`；Cue 为 `id/timeMs/kind/latePolicy/validForMs/resource`。未知字段、版本、枚举及其它研究格式明确拒绝，不截断或删除字段假迁移。

`durationMs/key.timeMs/cue.timeMs/validForMs` 均为整数毫秒。时间及 seed 不接受指数或小数 token，seed 直接解析完整有符号 long，支持 `Long.MIN_VALUE..Long.MAX_VALUE`。Document ID 和资源 ID 使用 `[a-z0-9_]+:[a-z0-9_./-]+`，最长 192，拒绝绝对路径、空段、`.`、`..`；这只证明逻辑语法，不证明存在、许可或访问权限。Track/target/cue ID 为 `[a-z0-9][a-z0-9_.-]{0,63}`。

|Property|范围|单位|
|---|---|---|
|X / Y / Z|-1000000..1000000|block|
|SCALE|0..1024|ratio|
|OPACITY|0..1|ratio|
|BRIGHTNESS|0..16|linear|
|ROTATION_DEGREES|-360000..360000|degree|
|VISIBILITY|仅 0 或 1，且仅 STEP|boolean|
|FOV_DEGREES|10..150|degree|

同一 target/property 只能被一条轨道声明。Key 严格递增且不超过文档时长；左 Key 决定下一段插值；首 Key 前和尾 Key 后保持端点值。采样将输入 clamp 到 `[0,durationMs]`，负输入取首姿态，终帧不做循环取模。SampledFrame 每次分配不可变快照，未声称零分配或真实帧率。

`Sampler.randomUnit(seed,stableId,ordinal)` 保留推荐标量算法及模 2^64 混合，结果 `[0,1)`，ordinal 非负；没有全局 RNG。它不是外部 `HASH64_V1`，不能将相同 seed 宣称为跨算法相同画面。采样来源通过 `sampledFrom(document)` 的对象身份验证；同 ID 或同内容 hash 不能冒充同一对象。

## 输入与错误边界

`StrictJsonCodec.decode(InputStream)` 最多读取 **2097152 + 1** 字节：最后一个只用于超限探测。超限在 DTO 构造前拒绝；未关闭调用方流，I/O 错误原样传播，宿主负责来源超时。`decode(byte[])` 仍独立检查 2 MiB。UTF-8 使用 REPORT；拒绝 BOM、非法编码、孤立 surrogate、控制字符、解转义后的重复 key、全角 Unicode hex 和 trailing data。

|限制|上限|
|---|---|
|文档时长|1..3600000 ms|
|tracks / 每轨 keys / 总 keys|512 / 4096 / 65536|
|cues / resources|128 / 256|
|JSON 字节 / 嵌套深度|2097152 / 32（根深度为 0）|
|字符串 UTF-16 单元 / 数字 token 字符|512 / 32|
|generic array / 所有 value|65536 / 262144|
|事件 age / cue TTL|315360000000 ms / 10000 ms|

这些是硬保护上限，不能当作流畅播放容量。合法 Java Document 也可能超过规范 JSON 2 MiB；`encode` 和 `CompiledPlan.compile` 均拒绝这种计划，避免“模型合法即可交付”的假成功。

`CodecException extends IllegalArgumentException`，公开 `path/code`，fail-fast；path 最长 515、说明最长 259 字符。可见 code 包括 `BYTE_LIMIT/UTF8/DEPTH/VALUES/ARRAY_LIMIT/STRING_LIMIT/NUMBER_LIMIT/DUPLICATE_KEY/UNKNOWN_FIELD/MISSING_FIELD/FORMAT/MAJOR/MINOR/INTEGER/INTEGER_RANGE/FINITE/RESOURCE_ID/COUNT/TOTAL_KEYS/KEY/TRACK/CUE/DOCUMENT` 等。直接 Java 构造的非法参数抛 `IllegalArgumentException`，owner/closed/重入失败抛 `IllegalStateException`。

规范编码固定字段次序，resource/track/cue 按 ID 排序，Key 保持已验证的时间顺序，double 用 `Double.toString`。hash 为规范 UTF-8 字节的 SHA-256，不是来源签名或代数归一化。黄金 fixture 位于 `src/test/resources/animation/decorative-ring.v0.json`，SHA-256 为 `cb2546d02326dff010359f8bd142e270a9b56e505f00a8f8d34136bdd3ffd810`。

## 新增公开签名

以下均位于同一生产包；完整字段和嵌套枚举可见源码及冻结产物的 `javap` 清单。

```java
AnimationV1.Key(long timeMs, double value, Interpolation interpolation)
AnimationV1.Track(String id, String targetId, Property property, List<Key> keys)
AnimationV1.Cue(String id, long timeMs, CueKind kind, LatePolicy latePolicy,
    long validForMs, ResourceId resource)
AnimationV1.Document(String id, int schemaMinor, long durationMs, long seed,
    List<Track> tracks, List<Cue> cues, Set<ResourceId> resources)
Property.supports(Interpolation interpolation) // boolean; public final min/max/unit

ResourceId.parse(String text) // static ResourceId; value equals/hashCode/toString
EpochKey(long connectionEpoch, long worldEpoch, long sessionEpoch)
// EpochKey has the three public final long fields and value equals/hashCode.

StrictJsonCodec.decode(byte[] bytes) // static Document
StrictJsonCodec.decode(InputStream input) throws IOException // static Document
StrictJsonCodec.encode(AnimationV1.Document document) // static byte[]
StrictJsonCodec.contentHash(AnimationV1.Document document) // static String

Sampler.sample(AnimationV1.Document document, long localTimeMs) // static SampledFrame
Sampler.randomUnit(long seed, String stableId, long ordinal) // static double
SampledFrame.sampledFrom(AnimationV1.Document document) // boolean
SampledFrame.value(String trackId) // double, unknown ID throws
// SampledFrame public final: documentId, localTimeMs, Map<String,Double> valuesByTrack.

CompiledPlan.compile(AnimationV1.Document document) // static CompiledPlan
CompiledPlan.sample(EvaluationInput input) // SampledFrame
// CompiledPlan public final: Document document; String contentHash.
EvaluationInput(long localTimeMs) // public final localTimeMs
AnimationInstance(String instanceId, long playbackGeneration, CompiledPlan initialPlan)
AnimationInstance.plan() // CompiledPlan
AnimationInstance.evaluate(EvaluationInput input) // SampledFrame
AnimationInstance.replace(CompiledPlan plan) // void
AnimationInstance.replaceFrom(InputStream input) throws IOException // void
AnimationInstance.close() // void
// AnimationInstance public final: instanceId, playbackGeneration.
```

AnimationInstance 绑定构造线程；准备期间允许读取旧计划和纯求值，拒绝同实例 `replace/replaceFrom` 重入；`close` 可在输入回调内发生，且阻止最终发布。解析/编译失败保持原计划；正常关闭幂等且不保留计划引用。显式重播需由宿主创建新播放 generation；替换计划本身不重播 cue。

```java
EventBudget(int maxPerFrame, int maxPerSecond) // 1..128 and 1..4096
EventCursor(EpochKey epoch, String instanceId, long generation, Document document,
    boolean looping, EventBudget instanceBudget, EventBudget sharedOwnerBudget)
EventCursor.start(long ageMs, boolean fresh, long frameId, long monotonicMs) // Dispatch
EventCursor.advance(long ageMs, long frameId, long monotonicMs) // Dispatch
EventCursor.seekSilently(long ageMs) // void
EventCursor.close() // void
// Dispatch public final List<Occurrence> events;
// int scanned/expired/policyDropped/budgetDropped.
// Occurrence public final epoch/instanceId/cueId/playbackGeneration/
// loopOccurrence/absoluteTimeMs/cue.

TimelineTimebase.fromMilliseconds(long milliseconds) // static long
TimelineTimebase.fromLegacyTicks20(long ticks) // static long
TimelineTimebase.toMillisecondsFloor(long units) // static long
TimelineTimebase.toMillisecondsExact(long units) // static long
TimelineTimebase.frameTimeFloor(long frameIndex, long fpsNumerator, long fpsDenominator)
TimelineTimebase.frameCountCeiling(long durationUnits, long fpsNumerator, long fpsDenominator)
// Both frame methods are static long. Constants: UNITS_PER_SECOND=120000,
// UNITS_PER_MILLISECOND=120, UNITS_PER_LEGACY_TICK_20HZ=6000.

LegacyScalarMigrator.TickKey(long tick, double value)
LegacyScalarMigrator.migrateBrightness(String id, long durationTicks, long seed,
    List<TickKey> fadeKeys) // static Result: public final document/warnings
```

EventCursor/EventBudget 绑定构造线程；两个 gate 必须不同对象；多个 cursor 共用同一 owner gate。非法 fresh start 或第二 gate 时钟拒绝不改变第一 gate。`fresh=true` 仅 age=0；晚到先计算实际 age/TTL，DROP 不补发；每个 cue 只考察最新循环 occurrence，最多扫描 128，候选按绝对时间/ID 稳定排序。预算不足立即记数丢弃并推进高水位，无待补队列。滚动窗口为 `(now−1000,now]`；frame/wall 不得回退。高水位只升不降，seek/reverse/export 通过纯采样或 silent seek 均零 cue；循环不改变 session/generation。循环 cue 必须 `<duration`，非循环可在终点。

宿主仍需限制活动 cursor 总数、音轨/镜头句柄，并在关闭、暂停、seek 时主动回收或重建持续状态。当前没有公平调度器；观察 cue 丢弃绝不能用于丢服务器权威状态或模拟内部事件。

时间桥仅接收非负时间/帧号、正 P/Q，超 long 或不精确转换明确拒绝。绝对帧时间为 `floor(n×120000×Q/P)`；帧数为 `ceil(D×P/(120000×Q))`，导出区间 `[0,D)`。60fps 首帧步长 2000U 转 V0 时为 16ms；这是真实量化，不宣称亚毫秒 V0 采样。

## 验证与剩余门

本批真实 JDK 8 `javac/java` 定向执行 `AnimationContracts`，**13679 项显式断言通过**，覆盖独立线性扫描插值 oracle、固定随机向量、golden bytes/hash、UTF-8/重复字段/版本/深度/计数/流硬限额、long seed 边界、编译预算、失败保留/重入/关闭、seek/reverse、late/loop/百万循环扫描、共享滚动预算和有理时间溢出。执行脚本、命令、实际源码 hash、编译器版本和退出码保存在 worktree 的 `build/animation-evidence/`；整套 SDK 及消费者编译结果由集成交接另记。

这里只证明当前生产纯 Java 合同。完整 Gradle/FML/专服传递类链接、旧消费者真实依赖编译、MC/GL/音频/相机、资源 reload 和真实性能仍须后续门验收；未执行的检查不标 PASS。完整节点格式仍为 UNSUPPORTED，不能由这些测试推定已完成。
