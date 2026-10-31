# 标量动画 runtime 与显式宿主

新增兼容入口 `QualityGovernor.Indexed(initialIndex, tierCount, downSeconds, upSeconds)` 供已有多档装饰策略消费。
它只持有档位与持续压力/恢复计时，档位0为最高质量；`observeDemand(wantedIndex, canRecover, seconds)` 每次最多变一档。
宿主保留过滤、负载阈值、计量与用户hard-off，不能把墙钟帧间隔称为CPU/GPU耗时。`setLocked` 清除计时并固定档位；
锁定期间 `resetTier` 只清计时，不能改当前档。所有操作在创建线程，参数有限、秒数非负；原四档API及语义不变。
Outpost五档由此同一状态机持有，保留原0.25秒降档/3秒恢复与100/70/45/25/15效果比例；截图取景token及宿主清理由消费者负责。

本轮新增生产包 `com.miaokatze.miaokatzecore.api.animation.runtime` 与
`com.miaokatze.miaokatzecore.client.animation`。它们使用唯一的 `api.animation` 文档、时间、资源 ID 和 epoch
合同；不发布 `design.*`，不复制消费者 DTO。所有可变 runtime 对象均由构造线程驱动，不注册监听、不创建线程或
executor、不启用 WorkGovernor。不可变 ticket、frame 和统计快照可交给其他线程读取；发布、资源操作和统计写入回到
原 owner 线程。

## 实际能力

|能力|本轮状态|边界|
|---|---|---|
|definition/asset/instance/context ticket|可用|精确 fence 对象身份，拒绝跨 owner 和关闭后的结果|
|ACTIVE/SUSPENDED/CLOSED|可用|纯状态；暂停允许纯缓存结果，禁止绘制|
|多维常驻预算|可用|CPU/GPU 为申请估计，不是 driver 实测；不管理全进程额度|
|owned/borrowed 资源绑定|可用|只执行 trusted host 提供的精确 owned close；borrowed 不 close|
|required/optional 能力准入|可用|拒绝缺 required；optional 缺失显式退化；不自动选择后端|
|严格统计窗口、累计损失|可用|单 metric/clock/owner；不建立跨线程采样总线|
|装饰质量迟滞|可用|只输出 tier/reason，不控制游戏、危险提示或用户许可|
|显式 AnimationSession|可用|真实 CompiledPlan → EvaluationInput → SampledFrame → trusted host 接缝|
|自动 cue/音轨/相机执行|unsupported|EventCursor 由消费者显式驱动，真实声音和相机 adapter 未实现|
|状态模拟、检查点、trail 重建|unsupported|标量任意时刻采样不能冒充有状态模拟|
|真实 GPU query、postfx、完整节点/大 mesh backend|unsupported|未添加占位实现或假 supported 声明|
|MC/FML/GL/audio 实机接入|NOT-RUN|第七阶段验收；纯 Java 结果不是像素、设备释放或专服类链接证明|

## 公共签名

以下类型位于 `api.animation.runtime`；`EpochKey` 与 `ResourceId` 来自上层唯一 `api.animation` 包。

```java
RuntimeGenerations();
RuntimeGenerations.Ticket ticket(RuntimeGenerations.Purpose purpose);
boolean accepts(RuntimeGenerations.Ticket ticket);
void changedDefinition();
void reloadedAssets();
void replacedInstance();
void changedContext();
void close();
// Purpose: CPU_DECODE, GPU_UPLOAD, PRESENTATION
// Ticket: public final purpose, definitionGeneration, assetGeneration,
//         instanceGeneration, contextGeneration；构造器不公开。

SessionLifecycle(EpochKey epoch);
SessionLifecycle.Ticket ticket();
boolean canPublish(SessionLifecycle.Ticket ticket);
void changedDocument();
void resourceReload();
void suspend();
void resume();
SessionLifecycle.State state();
boolean mayRender();
void close();
// State: ACTIVE, SUSPENDED, CLOSED
// Ticket: public final epoch, resourceEpoch, documentRevision, jobGeneration。

ResourceLedger(long... limits);
ResourceLedger.Lease reserve(long... amounts); // 容量拒绝返回 null，错误输入抛异常
long used(ResourceLedger.Dimension dimension);
int activeLeases();
void close();
// Dimension 顺序：CPU_BYTES, GPU_BYTES, HANDLES, DECODE_PIXELS, VERTICES, JOBS
// limits/amounts 必须恰好六个非负 long；MAX_LEASES = 4096。
// Lease: boolean active(); void close();

FeatureAdmission.Decision FeatureAdmission.evaluate(
    Set<ResourceId> required, Set<ResourceId> optional, Set<ResourceId> supported);
// Decision: public final status, missingRequired, omittedOptional。
// Status: SUPPORTED, DEGRADED, REJECTED。

MetricWindow(long windowNanos, int capacity);
void record(long elapsedNanos, long durationNanos);
void noteUpstreamDropped(long amount);
MetricWindow.Snapshot snapshot(long elapsedNanos);
// Snapshot public final:
// long endNanos, windowNanos, coveredSpanNanos;
// long observations, capacityEvictions, timeExpirations, upstreamDropped;
// int samples; long p50, p95, p99, max;

QualityGovernor(QualityGovernor.Tier initial, long lowNanos, long highNanos,
    int minimumSamples, int downWindows, int upWindows, long cooldownNanos);
void setLocked(boolean value);
QualityGovernor.Decision observe(long windowId, long elapsedNanos, long p95Nanos,
    int samples, boolean usable);
// Tier: OFF, LOW, MEDIUM, HIGH。
// Reason: HELD, OVER_BUDGET, HEADROOM, MISSING_OR_LOSSY,
//         TOO_FEW_SAMPLES, COOLDOWN, LOCKED。
// Decision: public final tier, reason, changed。
```

所有世代在 `Long.MAX_VALUE` 拒绝继续，要求重建 owner，不环绕；普通生命周期变更不能重开已关闭对象。
CPU_DECODE 比较 definition/asset；GPU_UPLOAD 另比较 context；PRESENTATION 再比较 instance。每种 ticket 都比较
精确 fence owner。纯 CPU decoder 不得偷读 World 或携带 GL ID 来规避 context 检查。循环不增世代；显式实例替换才
调用 `replacedInstance()`。SessionLifecycle 的 ticket 是独立的较粗作业合同，AnimationSession 只复用其状态和 epoch，
对外发布围栏采用 RuntimeGenerations，不叠加两套可变作业时钟。

## 资源与预算

`ResourceLedger.reserve` 在任何分配前按六维原子检查，不够时不修改任一计数。使用 `limit - used` 比较避免 long
加法溢出。旧资源、新资源、staging、池中空闲资源仍共存时都必须计费。账本不是 GL allocator；`Lease.close()` 与
`ResourceLedger.close()` 都只归还账，不能证明 GPU 已删除。借用引擎 atlas 不拥有其 storage，不能拿 borrowed close
去归还其他 owner 的 GPU 账。

以下类型位于 `client.animation`，但本轮不依赖任何 MC 或 GL 类：

```java
ResourceOwner(RuntimeGenerations generations);
<T extends AutoCloseable> ResourceOwner.Handle<T> own(
    RuntimeGenerations.Ticket ticket, T resource, ResourceLedger.Lease accounting);
<T> ResourceOwner.Handle<T> borrow(RuntimeGenerations.Ticket ticket, T resource);
int activeHandles();
int failedReleases();
void retireInvalidated() throws Exception;
void close() throws Exception;
// MAX_HANDLES = 4096
// Handle<T>: boolean owned(); boolean valid(); T get(); void close() throws Exception;
```

ResourceOwner 仅接收当前 GPU_UPLOAD/PRESENTATION ticket，拒绝 CPU_DECODE ticket，避免借用 device handle 绕过
context 失效。纯 CPU 缓存直接使用预算和 CPU ticket，无需 device binding。登记成功才转移 ownership；拒绝登记时
原调用者仍须清理其分配和 reservation。一个 owner 内不允许重复 resource 对象或同一 reservation 双重绑定。

失效后 `Handle.get()` 立即抛异常；宿主应停止绘制或呈现自己的 placeholder。`retireInvalidated()` 只关闭失效的旧
owned 对象，不影响新 generation，也不关闭 borrowed。资源回调必须在正确的原 context 释放其设备句柄，不能把旧
GL 整数 ID 拿到新 context 删除。`contextChanged()` 要在旧 context 销毁前显式调用；如果已经丢失 context，由宿主
报告退休失败并安排其真实 context 退役协议。

`retireInvalidated()` 与最终 `close()` 均按登记逆序（LIFO）处理其快照：先登记依赖 A，再登记使用 A 的 B，释放顺序
为 B、A。退休只处理已失效 binding，跳过仍有效的新 generation；borrowed 只摘下 binding，不调用资源 close。
最终关闭先 fence owner，每个句柄先 detach 再执行 owned close；回调重入和重复 close 不重复释放。

关闭先摘下 Java binding，再调用精确 owned close，成功后才归还 reservation。单项抛异常仍尝试其余句柄；相同
Exception 对象不 self-suppress，原失败不会被这个操作覆盖。失败的 close 不会自动重试，reservation 保持 active，
`failedReleases()` 累计失败数；`activeHandles()==0` 不能解释为设备全部释放。调用者要保留必要的原 resource/lease
以处理明确失败，不能无条件 `ledger.close()` 后宣称设备零泄漏。清理回调期间禁止登记新资源，防止旧 close 误释放
重入新绑定的 reservation。

CORE-03-01 修复将既有 FIFO 观察顺序改为上述 LIFO，公开签名和其他 ownership/计费语义不变。若多项抛不同异常，
逆序中首先遇到的失败为主异常，其余为 suppressed。02/03/05/06 及实际宿主须使用修复后的源码/SDK hash 重新确认，
尤其是依赖释放顺序的 adapter；旧 SDK 验收不能证明这一新顺序，01c 不因此自动关闭。

## 最窄显式宿主

```java
AnimationSession(EpochKey epoch, AnimationV1.Document document,
    AnimationSession.TrustedHost host);
SessionLifecycle.State state();
RuntimeGenerations.Ticket ticket(RuntimeGenerations.Purpose purpose);
boolean accepts(RuntimeGenerations.Ticket ticket);
ResourceOwner resources();
SampledFrame presentedFrame();
boolean present(long localTimeMs) throws Exception;
boolean seek(long localTimeMs) throws Exception;
void suspend() throws Exception;
void resume();
void replaceDocument(AnimationV1.Document replacement) throws Exception;
void resourceReload() throws Exception;
void contextChanged() throws Exception;
void close() throws Exception;

interface AnimationSession.TrustedHost extends AutoCloseable {
    void present(SampledFrame frame) throws Exception;
    void stopTransientEffects() throws Exception;
    void close() throws Exception;
}
```

构造与替换都先完成 `CompiledPlan.compile(document)`，包括规范编码字节预算和 content hash。准备失败保留原 plan
和 presented frame；准备成功后才提交新 plan、失效旧 ticket，再清旧资源。清理失败会关闭 session 并报告异常，不能
继续使用半退休的 mapping。`present` 通过编译 plan 纯采样，宿主只调用一次，回调成功且 session 仍有效才记录 presented
frame。宿主必须自行在 native callback 前后检查 viewport、exact world 和 camera lease。

宿主回调内可以 `close()`：立即关闭围栏，实际资源清理延后至回调返回，旧帧不能提交。其他嵌套 render、替换、reload、
seek 等 mutation 会明确拒绝。最终 close 会主动停 transient effects、关闭本 session 资源，再关闭 trusted host；一项
失败不跳过其余项。宿主自己的 close 也应先清保存字段再关闭精确 token。

`present(timeMs)` 可按任意时刻采样但不派发 cue；`seek` 额外主动停 transient handles。消费者若持有 EventCursor，仍须
一起 `seekSilently`，然后再恢复正向事件时钟；Session 不创建第二事件游标。声音引擎未接入，`stopTransientEffects`
的真实句柄回收必须在第七阶段验证。用户关闭震屏、闪光、世界镜头等硬设置由 trusted host 在提交效果前判断。

现有 `WorldCameras.tryAcquire/tryActivate` 的优先级和 exact lease 不变。宿主可保存真实返回的 `Lease`，在自己的
`close` 中仅关闭这个对象；没有跨 mod 自动 camera 栈、自动恢复被抢占镜头或全局 clear。MiaoVision 局部 GUI camera
无须世界镜头许可。UiScene 的 CANCEL/input capture、实际 MUI viewport 生命周期由消费者转发；本模块不注册新的入口。
`TrustedHost` 是受审应用代码的接缝，不是 Java/native 回调沙箱，也不能让 JSON 任意选择类或执行脚本。

## 统计与装饰质量

MetricWindow 保留 `(now-window, now]`，容量 1..65536；过期删除与容量驱逐分别计数。时间是宿主单调 origin 后的
非负 elapsed，不能直接假定 raw `System.nanoTime()` 非负。duration、loss 非负；时钟不允许回退。percentile 使用
nearest-rank；空窗 -1，测得 0 仍为 0。observations/evictions/expirations/dropped 为 owner 创建以来的饱和累计计数，
不是当前回看窗 loss。coveredSpan 只表示保留样本首尾跨度，不能证明完整采样覆盖。snapshot 排序在低频展示/汇总处调用。

每个窗口只放一种真实指标和时钟域。CPU_SUBMIT 纳秒不代表 GPU_PASS；GPU unsupported/pending 不能填零。宿主证明
coverage、原 measurement frame、epoch、loss 区间等后才令 `QualityGovernor.observe(..., usable=true)`。

QualityGovernor 只在连续有效窗口严格高于 high 或低于 low 后变一档，冷却、缺样、有损、样本不足和锁档均清 streak。
windowId 严格递增。OFF 是自适应最低档，可因 headroom 升 LOW，不能替代用户永久关闭许可；离线 capture 可锁档，用户
硬禁用仍由 renderer 前的许可门执行。可信危险提示/accepted 工作/半径/费用/服务器权限没有进入此 API，不受装饰降档。

## 保持的生产边界与验证

未修改现有 MiaoVision、WorldCameras、WorkGovernor、UiScene 的签名、容量或默认行为。测试直接实例化生产
SceneSnapshot/SceneBounds：100 个合法 body 接受、101 个拒绝，schema 仍为 1，跨度 >256 和越界 body 仍拒绝。
未添加 BackendChoice 或新路由器，也没有用截前 100 个假装兼容大场景。

`src/test/java/com/miaokatze/miaokatzecore/api/animation/RuntimeContracts.java` 测试真实生产类：精确 owner/世代、
暂停/关闭、Long.MAX_VALUE 预算、六维原子拒绝、old+new 重叠计费、4096 lease 上限、borrowed 失效不删除、旧 handle
不释放新 handle、失败保账和继续清理、同异常保真、重入拒绝/延迟关闭、编译预算拒绝保留旧 plan、窗口边界/0/缺失/loss、
quality 迟滞与锁档、100/101 原生产边界。

2026-10-31 首次冻结的定向开发证据保留在 `build/runtime-evidence/`：`commands.txt`、`jdk.txt`、`compile.txt`、`run.txt`、`sources.txt` 和
`sources-sha256.json`（4209 assertions 的较早版本）。最终版随后补了 device binding 拒绝 CPU ticket 和资源退休回调
重入 close 三项断言，由总集成 runner 编译/运行为 `RuntimeContracts PASS (4212 assertions)`；该次冻结证据以
`build/animation-sdk/RuntimeContracts.txt`、`commands.json`、`manifest.json` 为准，九个 runtime/host/测试文件 hash
已逐一核对与 manifest 一致。实际使用本机 JDK 8 `javac 1.8.0_482`，不使用 MC stub、不启动实例、不跑 Gradle。
独立 SDK/消费者编译也由该总集成 runner 出具；本页不把推荐稿历史 PASS 或文本 import 扫描当成本轮真实类链接/GL 证据。

CORE-03-01 LIFO 修复另存 `build/lifo-repair/before/` 与 `after/`，均记录实际命令、JDK 版本、源码 hash 和退出码。
新增依赖测试在旧 FIFO 实现上失败；修复后的 JDK 8 定向编译及 `RuntimeContracts` 为 **4236 assertions PASS**，
覆盖两种逆序清理、borrowed、跨 owner/重复登记拒绝、新 generation 保留、失败继续与保账、重入和重复 close。
旧 `build/animation-sdk` 未覆盖，其 hash 不再代表这次修复；新的 SDK 综合验证及消费者按新 hash 复验另行登记。

第七阶段仍需真实 FML client/dedicated-server 类链接、受审 adapter 白名单、world/camera 抢占回调、GL/context/atlas
reload 与异常资源计数、音频 seek/close 主动回收、输入 capture 注销，以及等工作量性能观测。此处没有性能改善承诺。
