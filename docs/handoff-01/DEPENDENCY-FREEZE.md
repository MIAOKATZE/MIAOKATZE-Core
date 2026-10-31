# F-CORE-01：生产 SDK 冻结与消费边界

- owner：01；消费者：02/03/05/06；冻结日期：2026-10-31。
- 执行 base：`f02dbdc2f4e9891b616bfd2c48b17f28305e44f0`，用户确认的 GTNH 2.9.0 正式版。
- 交付提交与恢复包见 `E:/CodeGT/plan/core-contracts-runtime-20261031/HANDOFF.md`；本地 detached worktree 保留。
- SDK：`E:/CodeGT/plan/core-contracts-runtime-20261031/core-animation-sdk-local.jar`。
- SDK SHA-256：`6356ef07ecedbf86f62e5558eb25845f358d0aa065b3ae1d1f798c58166dae4d`。
- 同目录 `sdk-tested-sources.zip` 保留受测源码/fixture/runner 的精确字节，SHA-256：`060b6f535980e1242d9948dbe559fb3eefd4a5da43a92f1237236d5ea5759000`。Git checkout 可能按本机 autocrlf 转换换行，逐字节复核请用此快照；恢复完整仓库则使用 bundle。
- 受测源码聚合 SHA-256：`4f88c4ad90874e5207bd061e5ab7be7abc03c30b5d1c01cdcee4bf4a7e51e873`。算法为按路径排序的 `path + " " + fileSHA256 + "\n"` 的 UTF-8 SHA-256，具体文件见 [manifest](receipts/manifest.json)。
- 完整精确签名：[public-signatures.txt](receipts/public-signatures.txt)；不可只按类名或移动 HEAD 消费。

该 JAR 是由本次生产源码生成的纯 Java SDK，不是 `design.*` 推荐稿 SDK，也不是可放入 mods 的发布 JAR。既有发布版本号保持 0.2.0；新增能力只能按本次 artifact/hash 引用，不能推定现有 Maven 0.2.0 已包含它们。

## 门状态

|门|01 交付状态|接收条件|
|---|---|---|
|F-CORE-01a 最窄标量|PROVIDER_READY：实现、规范 fixture、签名、SDK 和两独立消费示例通过|02/03/05 确认本 SDK hash，并接入实际调用点；接收回执 PENDING|
|F-CORE-01b owner/runtime|PROVIDER_READY：下列最窄运行时合同与宿主接缝通过纯 Java 反例|接收方按 owner/线程/错误边界实现可信宿主，不得冒用未实现 backend|
|F-CORE-01c 接入就绪|PARTIAL：旧源码保留、六仓依赖/调用点已核对、同 SDK 示例通过|实际消费者接入确认、旧消费者真实依赖编译与 FML/专服/GL/音频仍未完成|

## 能力、格式与权限

|范围|实现与冻结语义|未实现/宿主边界|
|---|---|---|
|`api.animation`|不可变标量文档、严格 byte[]/InputStream codec、CompiledPlan、AnimationInstance、EvaluationInput、SampledFrame；精确文档对象身份|无完整节点/transform/材质资源 binding、模拟/检查点、脚本或 URL 执行|
|格式|`core.animation.scalar`，schema major=1/minor=0；整数 ms；有符号 long seed 不经 double；未知字段拒绝|新格式必须另行冻结，不将未来 JSON 送入 V0|
|时间/事件|120000U/s 显式换算、绝对帧号有理时间；独立有界 EventCursor 高水位、late/loop/双预算，输出观察数据|seek/reverse/export 不派一次性 cue；不执行音频/相机/伤害；宿主主动清持续效果|
|`api.animation.runtime`|RuntimeGenerations、SessionLifecycle、ResourceLedger、FeatureAdmission、MetricWindow、QualityGovernor|无 executor/自动监听/跨线程总线、实际 GPU query 或自动后端选择|
|`client.animation`|ResourceOwner 的 owned/borrowed 精确句柄；AnimationSession 显式 trusted host，准备/调用/提交/清理边界与同异常聚合|真实 GL ID/音轨/WorldCameras.Lease 由可信宿主持有和清理；JSON 不选择回调|
|预算/能力|双世代资源同时扣账；零成本 handle 仍有上限；required 缺失拒绝、optional 明确降级；MiaoVision 原 100/schema1/bounds 保持|不截断 101 bodies；无高级 geometry/postfx fallback；不改 WorkGovernor 所有权|
|计数/质量|严格 `(now-window,now]`、空值 -1 与真0区分、累计 loss/eviction/expiration；装饰档迟滞与显式锁档|host 负责近期 loss/覆盖度/clock epoch 与 usable 判定；用户 hard-off 优先；无权影响危险圈、伤害或 accepted 任务|

完整单位、ID、范围、错误 reason、迁移/拒绝表见 [标量协议](../animation-contract.md)，资源/线程与指标字典见 [运行时](../animation-runtime.md)。所有可变 owner 绑定构造线程；不可变模型可传递。世代 ticket 校验 exact fence owner，不把相同数字 epoch 当相同 owner。

## 本次实际验证

在 `E:/CodeGT/.worktrees/core-contracts-runtime-20261031` 执行：

```powershell
python tools/run-animation-contracts.py --jdk-home 'C:\Users\60158\.gradle\jdks\azul_systems__inc_-8-amd64-windows.2' --output build/animation-sdk
```

退出码 0，真实 Zulu JDK/JRE `1.8.0_482`。生产类只编译一次；合同与两个消费示例随后各自针对同一 SDK 编译运行。所有命令/退出码/日志 SHA 见 [commands.json](receipts/commands.json)，这次结果不引用历史研究 PASS。

- [AnimationContracts](receipts/AnimationContracts.txt)：13679 断言通过。
- [RuntimeContracts](receipts/RuntimeContracts.txt)：4212 断言通过，含资源 identity 复用、同异常清理、世代/owner、宿主重入和既有 MiaoVision 100/101 边界。
- [GTUDK 示例](receipts/GtudkSdkConsumer.txt)：保存/重开、精确 seed、60fps 绝对时间和定义身份通过。
- [Outpost 示例](receipts/OutpostSdkConsumer.txt)：必需能力、迟到世代、空统计与真0通过。

SDK 闭包在不含 Minecraft/GL/消费者类的 JDK8 classpath 下完成真实编译及运行，不使用 MC stub。它只证明这个纯 SDK 闭包，不替代完整 Core/FML 传递类链接。完整 Gradle 未运行；`runAnimationContracts` 和 `runAnimationRuntimeContracts` 仅已挂入常规 `check`。

## 接入请求与变更规则

02/03 的文档、时间线/预览/导出调用唯一 `api.animation`；05 的表现调用同一 SDK，不将支援权限或事务放进 Core；06 消费 `MetricWindow`/`QualityGovernor` 的单位和 usable/loss 语义，不复制 window/runtime。消费示例在 `tools/animation-consumers/`，是编译示例，不是已接入消费者生产仓的证明。

消费者接收状态均为 **PENDING**，必须回填 artifact hash、固定 consumer SHA、实际调用点和对应测试。冻结变更按“提出 delta → 旧/新签名及语义影响 → owner 确认 → 更新 artifact/hash → 受影响消费者重测”处理。第七阶段真实矩阵详见 [STAGE07-CASES.md](STAGE07-CASES.md)。
