# 01 兼容边界与消费者清单

执行基线为 `f02dbdc2f4e9891b616bfd2c48b17f28305e44f0`。本文件是兼容要求与只读依赖记录，不是编译、二进制链接或实例验收通过声明。本次新增 `api/animation`；在该基线执行 `git ls-tree -r --name-only <base> -- src/main/java/com/miaokatze/miaokatzecore/api/animation` 无输出，不能把任务材料中的 scalar V0 推荐稿视作基线已有生产 codec。

## 需要保留的合同

| 对象 | 兼容要求 | 当前证据范围 |
| --- | --- | --- |
| 原公开 API 与旧消费者 | 不删除或改写既有签名、默认行为和数据约定。 | 本次 133 个既有 src/main 文件无修改，依赖/版本/settings 不变；见 receipts/final-static-check.json。未另跑旧类 ABI 比对或完整消费者构建。 |
| MiaoVision | 保留原有 100 bodies、schema 1 和 bounds；不截断数据冒充兼容。 | RuntimeContracts 对原生产类的 100/101、schema1、bounds 断言通过；真实渲染仍 NOT-RUN。 |
| WorldCameras、WorkGovernor、UiScene | 保留旧接口职责；不把 WorkGovernor 改为 executor owner，不引入跨 mod 全局相机控制栈。 | 本文仅记录约束，未执行旧消费者运行验证。 |
| 材料 scalar V0 格式 | `core.animation.scalar`、schema `1.0`、整数毫秒及未知字段严格拒绝；120000 时间单位只允许显式桥接，不能重解释旧 1000ms。精确 long seed/计数不得经 double。 | 本次生产实现与冻结签名以主代理最终产物为准；此行是格式兼容要求。 |
| 新格式、transform、资源 binding | 未冻结的新格式不得交给旧 codec；未实现能力标明 unsupported。 | 能力表与字段/单位/拒绝规则见 DEPENDENCY-FREEZE.md 及 animation-contract.md；完整节点/transform/backend 为 UNSUPPORTED。 |
| Common/client 与保存边界 | Common 不引入客户端类及消费者业务类型；保持旧注册、NBT、存档与通信约定。 | 文本 import 检查不能替代真实传递类链接；本次未运行世界或写入存档。 |

## 六仓只读核验

2026-10-31 对以下固定提交执行只读核验；读取时各仓 HEAD 与列出的 SHA 一致。六仓 `dependencies.gradle` 均有 `miaokatzeCoreVersion` 属性覆盖入口，默认值为 `0.2.0`，声明坐标为 `com.miaokatze.miaokatzecore:miaokatzecore:${coreVersion}:dev`，`transitive = false`。这说明源配置默认值，不代表实例已经安装该版本。

引用文件数按 `git grep -l 'com.miaokatze.miaokatzecore' <SHA> -- src/main/java` 统计，含全限定名引用；它不是 API 数或编译覆盖率。下表路径相对 `E:/CodeGT/`。

| 消费者 / 仓库目录 | 固定提交 SHA | 引用文件数 | 代表调用文件（相对仓库） |
| --- | --- | ---: | --- |
| GTUDK / `GT-Universal-Development-Kit` | `1c96df781b4c8ed69f6a6358cc2f914f35ebc37b` | 2 | `src/main/java/com/miaokatze/gtudk/ponder/render/SceneRenderer.java`；`src/main/java/com/miaokatze/gtudk/register/CreativeTabManager.java` |
| Outpost / `GT-Outpost` | `00990221094670ed0e25026af9d420594aeda9a6` | 13 | `src/main/java/com/miaokatze/gto/client/support/SupportWorldCamera.java`；`src/main/java/com/miaokatze/gtoos/client/isometric/CombatModeBridge.java` |
| GTMB / `GT-Miao-Blueprint` | `edc8f17d39b10423b52cbecfa4dd5e9f55a328e0` | 23 | `src/main/java/com/miaokatze/gtmb/api/GTMBExtensions.java`；`src/main/java/com/miaokatze/gtmb/client/hologram/HologramClient.java` |
| GTIT / `GT-Interesting-Thing` | `9db08f1abf36521adabdd8abb7500ca1c963a0cb` | 12 | `src/main/java/com/miaokatze/gtit/client/gui/NekoMeTransferParticleWidget.java`；`src/main/java/com/miaokatze/gtit/client/vending/scene/VendingCatChoreography.java` |
| GTSWN / `GT-Simple-Wireless-Network` | `9def0f901d13bd2b9b8a690c28471702094eb442` | 28 | `src/main/java/com/miaokatze/gtswn/client/DeviceTerminalAnchorRenderer.java`；`src/main/java/com/miaokatze/gtswn/client/QuantumVoxelParticleHandler.java` |
| GTSR / `GT-Steam-Reborn` | `f126e9040f2a817b272c1bfa940edb453f447a97` | 12 | `src/main/java/com/miaokatze/gtsr/client/critical/IrradiatedDustRenderer.java`；`src/main/java/com/miaokatze/gtsr/client/encounter/FineRuneRenderer.java` |

可复查命令为 `git show <SHA>:dependencies.gradle` 与上述 `git grep`。本次文书核验未改这些消费者，未构建六仓，也未用 MC stub 代替真实依赖。SDK 示例编译只能证明该示例对指定 SDK 的调用关系，不能替代上述旧消费者整体编译或 FML 实机验收。

## 尚待接收的证据

| 项目 | 状态 / 后续责任方 |
| --- | --- |
| 最终 Core 源码及 API 差异、SDK SHA256、生产定向验证 | PROVIDER_READY，见 DEPENDENCY-FREEZE.md；真实 JDK/JRE8、13679+4212 断言及同 SDK 两消费示例通过。 |
| 02/03/05/06 接收同一冻结 artifact 与签名 | PENDING，对应消费者责任方确认 hash 和实际调用点。 |
| 六仓旧消费者真实依赖编译与传递类链接 | NOT-RUN，01 协调对应仓库责任方及第七阶段。 |
| 客户端、专服、多人、真实 GL/资源/音频与性能 | NOT-RUN，第七阶段独立验收，详见 [用例表](STAGE07-CASES.md)。 |

冻结后若签名或语义变化，应附影响文件与新 artifact hash，由受影响消费者确认并重测对应 case；不得只更新文档后沿用旧收据。回退以确认过的交付 SHA、恢复包和旧 artifact 身份为准，具体路径及校验由主代理补充；不覆盖用户原工作区或世界原件。
