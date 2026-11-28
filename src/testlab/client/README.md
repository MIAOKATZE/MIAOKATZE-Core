# Core Tooltip 原生客户端探针

1. `audit/CoreTooltipInteractionFixture.java` 是显式启用的 2.9.2 客户端 fixture，不属于 Gradle main/testlab Java 源集，不进入普通或完整 testlab JAR。
2. 复用登记的 GTSR 原生客户端宿主与共享 `tools/testlab/host.py`。plan 需 `scriptOnly=core-tooltip-interaction-v1`、明确 expectedArtifacts SHA-256，宿主只创建本轮拥有的生存世界；先 catalog/doctor，使用 bundle 配对 Core 普通/testlab 来源。
3. 原生 `Mui2ClientQa` 已有同名 fixture 钩子（client/server tick、render、cancel、complete）。编译时沿用 `GT-Steam-Reborn/tools/testlab/client_qa/compile.py`，把其 Java 源枚举中的旧同名 fixture 换成这里的绝对路径；保持原编译器 `__file__` 与其其他源不变。不要同时编入两个同名类，不改 GTSR 业务源码或旧 fixture。
4. 编译前保存原生 helper 缓存和同名 class，结束逐字节恢复；原生编译收据的 `sources` 必须记录本文件的实际 SHA。runner 默认归档宿主目录源码，归档时需以实际参与编译的本文件替换旧副本并核对 hash，不能保留错误的取证源码。
5. 探针借用进程内 lwjgl3ify 队列快照注入事件，所有快照在 finally 恢复；不使用操作系统键鼠。普通 Shift 点击走真实 GuiScreen 处理和库存包，并在集成服务端核实转移。其他检查调用真实输入总线、实际绘制命中、原生链接确认与 NEI 入口。
6. 原包/配置/选项/偏好、键位、指针快照、库存与 helper 按原生宿主协议恢复。失败保留恢复材料，不把未完成的恢复或某阶段取消记为 PASS。此次结果与最小复现范围见 `docs/workflow/tooltip-design_20261128.md`。
