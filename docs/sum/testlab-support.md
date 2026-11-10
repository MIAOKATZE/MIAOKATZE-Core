# Testlab 支持与消费合同

Core 公共 SDK 基线为 `0.2.9` / `v0.2.9` / `134521a2b79bc39136f3d7b6e1877a162830d84e`。本页另记录其后实际运行发现的 CodeSource 兼容修正；后续候选源码和 JAR 身份须按对应回执核对，不能与原 Tag 制品混用。

## 普通包与完整测试替换包

正式包保留 `com.miaokatze.miaokatzecore.api.testlab` 的七个公共类型和薄反射 bootstrap；实际 Driver、命令和环境处理器只位于 `src/testlab/java`。生产使用的 `testkit.Deadline` 继续保留。

| 用途 | classifier |
| --- | --- |
| 正式编译 / 普通实例 | `dev` / 无 classifier |
| 需要测试实现的开发编译 / 测试实例 | `testlab-dev` / `testlab` |

Maven 坐标为 `com.miaokatze.miaokatzecore:miaokatzecore:0.2.9`。完整测试包具有相同 modid，必须替换普通 Core，不能并装；各消费 Mod 的完整测试变体同理。此轮已确认 MavenLocal 发布，不据此推定公开远端仓或 Release 已更新。

`testlabJar` 复用普通 Jar 的完整 CopySpec，包含 Tags、Mixin、refmap 与资源，再加入 testlab 输出。RFG 原生规则配置 `reobfTestlabJar`，并继承普通 reobf 的额外 SRG。`assemble` 生成完整测试运行变体；`check` 包含 testlab 编译和 `runTestlabContracts`。两种变体的 330 个对应生产条目已逐字节核对一致，manifest 的测试变体标记单独保留。

## 适配器与生命周期

`LabModule` 提供 `id()`、`enabled()`、`cases()`、`execute(ICommandSender,String[])`、`status()`、`ownsWorld(World)`。兼容重载 `execute(ICommandSender,String[],String requestId)` 默认委托两参数入口。`snapshot(): Map<String,Object>` 默认只有 `legacyStatus`；模块应覆写权威 runId、state、outcome、reportPath、reportWritten、cleanupPending、Actor 和 transport 等事实，未知项保持未知。

`LabCase(id,title,lane,Set<String> tags,defaultSuite,requiresRealActor)` 不可变，tags 防御复制，各 getter 与字段同名。lane/tags 不自动赋予执行能力，默认 suite 由模块决定。

使用 `LabRegistry.register(module)` 在 init/preInit 注册已启用的轻量适配器；适配器自身不保存 world/player。未启用模块不加入注册表，不同适配器重复 ID 明确拒绝。Core serverStarting 仅在有注册模块时反射发现 Driver；普通包缺 Driver 时公共 bootstrap 略过，明确开启测试的消费模块可自行拒绝缺测试变体的组合。serverStopped 清事件及计数，保留适配器供集成服重开。业务状态、世界所有权和恢复由各模块负责，ownsWorld 必须判断确切所属 World，不能只比维度号。

## OP2 命令与机读回执

```text
/miaotest <module> list|status|run|stop|reset|report [args] [--request UUID]
```

统一根命令要求 OP2，支持控制台。list/status 由 Driver 读取目录与快照；run/stop/reset/report 的动作转小写，后续参数保留并交模块适配，Core 不重写旧业务清场和恢复语义。末尾 request 选项被剥离后以三参数 execute 传递，缺省生成 UUID。

正常处理后的消息为 `[MIAO-TESTLAB] ` 加 JSON，含 `schema=1`、requestId、module、action、accepted、snapshot、events、eventStats、artifact；list 增 cases，处理失败可附 error。accepted=true 仅表明读取或适配器分派正常返回，不等于任务完成、断言 PASS 或原报告落盘。权限拒绝、非法 UUID 属命令错误，不保证同格式回执。

Driver 用 `AtomicFilePublisher.publishAtomic` 尝试写实例工作目录 `logs/miao-testlab/<requestId>.json`，回执含 receiptPath。同 UUID 后续请求替换文件，因此它是最新请求快照，不是完整日志。文件写失败通过消息中的 receiptError 明示。业务报告仍保留，报告和清理状态以模块权威快照及原证据为准。

## 有界事件与工件身份

`LabEvents.publish(module,run,task,phase,check,result,cleanup)` 仅接收已注册且仍启用的模块。每模块保留最近 256 项，每个文本字段最多 1024 字符，不持有 World、Player、sender 或业务对象；snapshot 返回不可变列表。

`stats(module)` 返回 published、retained、dropped；累计 300 项的示例为 300/256/44。clear(module)/clearAll() 同步清事件和计数，sequence 是全局序号，不证明某次 run 的完整性。回执 eventStats 明示截断，原业务报告负责完整 trace。

`LabResult` 为 PASS、FAIL、BLOCKED、SKIP、UNSUPPORTED、CANCELLED、TIMEOUT、RECOVERY_PENDING；进行中允许 result=null。建议 phase 使用 run.started、task.started、check、task.finished、cleanup.finished、run.finished、run.cancelled、report.written、report.failed，API 不强制校验名称。准备 READY、受理、断言、报告、清理和恢复分别记录。

`ArtifactIdentity.capture(anchorClass).asMap()` 保留实际类的原始 CodeSource，捕获本地 JAR SHA-256、manifest 中的 Miaokatze-Source-Commit、Miaokatze-Source-Dirty 与 Implementation-Version。回执分别记录 Core 和模块适配器工件。缺值为 unknown；开发类目录为 classes-directory，不以工作区 HEAD 或猜测文件名代替实际 JAR。

实际 RFB/LaunchWrapper 链路会给出 `jar:file:/.../X.jar!/Class.class`，初版只接受 file 根 URL，导致身份 unknown、host 拒绝候选。后续修正用 JarURLConnection 取得本地 file 容器，再直接读其字节及 manifest；保留 raw CodeSource，不接受 HTTP 容器，也不猜 mods 目录。真实自定义 URLClassLoader 的 ProtectionDomain 合同验证了中文、空格、百分号、感叹号路径，以及远端容器不连接。

## 测试世界与真实生存

环境处理器只在完整 Core 测试变体存在，每次事件重查模块 enabled 和精确 ownsWorld，客户端和其他世界不命中。

- LOWEST PotentialSpawns/CheckSpawn 拒绝自然生成；EntityJoinWorld 默认拒绝测试世界 IMob。
- `LabWorldPolicy.withFixtureSpawns(Runnable)` 允许显式 fixture 怪物，写 owner、dimension ID、provider 类型标记；嵌套及异常退出均恢复 scope，标记可随实体 NBT 重载。
- END tick 仅清理该世界已加载、未具匹配标记的 IMob，保留显式 fixture、Player、Item 和 foreign/disabled 世界。明确战斗用例通过 scope 创建怪物。
- 天气仅缓存反射清 World 实例四个 rain/thunder strength 字段；不调 1.7.10 client-only setter，不写可能共享的 WorldInfo。provider 继续负责自然天气和雷击规则。

不改 difficulty、gamerules、玩家 health/capabilities，不伪造资源扣除或权限。各模块继续持有真实 Actor、资产、保存、未决付款与恢复。Core 不启动客户端或操作当前桌面；GUI/GL 需要真实客户端及独立后台桌面，固定实例、互斥和候选替换由 host runner 管理。

## 证据范围

证据根为 `E:/CodeGT/.artifacts/testlab-system-optimization/core/`。final-targeted-contracts.log 覆盖离线事件、精确世界、ambient join/旧残留、fixture scope、HARD、OP2/UUID/回执与真实临时 JAR 身份；World/Entity 为惰性对象，不启动 Minecraft。build-clean-publish.utf8.log 和 delivery.json 记录原 v0.2.9 clean build/check、本地发布和条目边界。

后续身份修正通过 jar-entry-identity-contracts.log，候选构建及本地发布见 jar-entry-identity-publish.log，候选路径及 SHA 见 jar-entry-identity-candidate.json。候选与原 Tag 制品分开识别。真实实例曾暴露身份兼容问题；本页没有真实机器、GUI/GL、网络、桌面隔离或完整生存玩法通过结论。过程见[独立 workflow](../workflow/testlab-support_20261110.md)。
