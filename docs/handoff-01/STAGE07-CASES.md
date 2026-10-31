# 01 第七阶段未测门

本表摘录任务包既定验收要求，限定 Core 相关接入；不是新增实现范围，也不是已执行记录。全部实例用例当前为 **NOT-RUN**，执行责任方为第七阶段独立验收 owner，修复回到对应文件责任方。任务包默认 RC 已由用户明确选择的 GTNH 2.9.0 正式版覆盖。未启动客户端、专服或测试维度。

## 入场材料

在执行前锁定 Core/GTUDK/Outpost 及参与旧消费者的精确提交组合、Core jar/SDK SHA256、配置和资源 hash。01 SDK/签名/纯测试见 DEPENDENCY-FREEZE.md，交付 SHA 与恢复包见 `E:/CodeGT/plan/core-contracts-runtime-20261031/HANDOFF.md`；消费者冻结回执及整体集成组合仍为 PENDING，不以移动 HEAD 代替。

实际环境 manifest 必须记录正式版具体构建、MUI2、JVM、FML/Mixin、Angelica/lwjgl3ify/LWJGL。G0 的源默认 MUI2 `2.3.94-1.7.10` 不代表已核验安装版本；JDK source/target 8 或 major 52 不代表真实 JRE8 运行通过。无法满足目标工具链时记录原因和实际矩阵，不静默替代。

Core 未发现专属 `plan/测试维度指南.md`，不能借用 GTMB/GTSR 的指南作为 Core 规则。第七阶段结合实际宿主确认可复现入口；尚未实现或尚无宿主的能力标 unsupported/PENDING，并由接收方判断是否阻断所需功能，不能记 PASS。

## 最小实例矩阵

| 编号 | 场景 / 固定输入 | 观察与放行条件 | 协作 owner | 状态 |
| --- | --- | --- | --- | --- |
| C07-01 | Core+GTUDK；Core+Outpost 不装 GTSR，再装固定 GTSR；GTMB/GTIT/GTSWN/GTSR 旧消费者；分别验证 client/dedicated/多人。 | 使用真实依赖；无缺类、旧公开 API 失配或 client 类泄入 common。Outpost 装不装 GTSR 不改变既定 10 活动白名单。记录各组合 jar hash 与加载日志。 | 01、02、04 及旧消费者 owner | NOT-RUN |
| C07-02 | 使用冻结 SDK 的合法标量文档及已有非法输入 fixture；验证真实宿主拒绝加载后的画面/状态。 | malformed UTF8、解转义重复 key、超字节/深度/计数、未知字段和非法数值均按冻结 reason 拒绝；精确 long seed 保持；拒绝后保留上一合法 plan。纯 Java 结果与宿主接入结果分别记录。 | 01、02/03 | NOT-RUN |
| C07-03 | 同定义/time/seed 的预览与离线导出；seek/reverse/截图；端点、循环、late cue 与长 delta。 | 采样结果按冻结语义一致；seek/reverse/导出不触发一次性 cue 或玩法事件；正放高水位不重复、扫描及派发有界。仅测实际已开放能力。 | 01、02/03、05 | NOT-RUN |
| C07-04 | A 准备→reload B→close→A 迟到；不同 owner 使用同数字 epoch；world/连接/context 变更。 | 过时 ticket 不发布也不释放新资源；定义、asset、instance、context 身份与 exact owner 校验有效；旧相机 lease 不恢复新 owner；记录资源计数。 | 01、02/03、05 | NOT-RUN |
| C07-05 | 真实 GL 栈下 reload 失败、atlas/context 销毁、反复 resize/close、同一 Exception 对象同时来自调用和 cleanup。 | borrowed 资源不 delete；旧句柄不继续使用；单项清理失败仍清其余 owner；不发生 self-suppression 掩盖原错、不重复调用 renderer 重试；无 GL 状态泄漏。 | 01 与实际渲染宿主 | NOT-RUN |
| C07-06 | 实际声音、camera 宿主 seek/close/接管，资源准备中取消和宿主退出。 | 声音及相机归属独立可核查，关闭回到资源基线；禁震屏/闪光/镜头偏好优先；不存在由视觉 JSON 引发的伤害、命令、费用或退款。 | 01、02/03、05 | NOT-RUN |
| C07-07 | MiaoVision 100/101 body；required 能力缺失与 optional 降级；实际 query/target/后处理缺失。 | 原边界保持且不截断；缺必需能力明确拒绝；unsupported/pending/真 0 分开；装饰降级不改变权威危险边界或 accepted 任务。未实现高级后端不冒充已有 fallback。 | 01、05/06 与渲染宿主 | NOT-RUN |
| C07-08 | 新世界与只读旧档的副本；旧消费者注册、NBT 与存档恢复。 | 原世界 hash 不变；对比副本加载与原行为，不隐式迁移、清零或更换旧身份。保存与业务变更仍由对应 mod 负责。 | 第七阶段、各存档 owner | NOT-RUN |
| C07-09 | 固定世界副本、配置/JAR/资源、输入脚本、相机、逻辑 tick、初始任务和质量；baseline/candidate ABBA 或 BAAB，多 run，probe off/on。 | 先验证等工作量、终态及状态 hash/画面容差，再记录 CPU/GPU、排队、loss、资源峰值和回收；CPU_SUBMIT 与 GPU_PASS 分列；没有基线不报告提升百分比，不自动上传报告。 | 第七阶段、01、06 | NOT-RUN |

GTUDK 的完整创作/保存闭环、导出编码器帧数，Outpost 的支援权限、调度和崩溃恢复由对应线提供完整用例；Core 的模型或 SDK 探针通过不能代签这些产品验收。

## 执行记录与收尾

每个执行结果记录 case、候选提交/artifact hash、实际命令及 cwd、工具链、fixture hash、退出码、原始日志和观察值；只使用 PASS/FAIL/BLOCKED/NOT-RUN。历史 V3 runner 或历史 API 比对不算本次验证。修复改变候选 SHA 后更新 manifest，并只复验受影响门。

重 Gradle 与实例必须由第七阶段协调者确认同机共享锁并串行执行；`Local\MIAOKATZE-HeavyBuild` 是建议互斥体名，不是其他任务已接入的证明。本轮没有重动作，未持有该锁。运行前核算客户端/专服/Gradle/OS/GPU 预算；客户端 Java heap 不超过 5G，另测总 RSS。启动后按任务要求置于后台，保留失败证据，不为 clean 清理用户工作区。

Tag、发布版本、clean release jar、A-Jar 更新、push、版本 PR、Release 均未在本阶段执行，由第七阶段在对应授权内统一处理；Release 仍需单独确认。A-Jar 替换前记录旧 artifact 与回退来源。第七阶段确认交付 SHA 已安全集成、恢复材料已核验后，才处理 detached worktree；不得提前删除唯一恢复来源。
