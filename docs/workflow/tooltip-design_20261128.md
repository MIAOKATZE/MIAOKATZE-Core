# Tooltip 职责重构核验（2026-11-28）

1. 源码基线：Core `master`，`ea7721a57a1a5f3904acd8f93faafc2050ad8133`，原版本 v0.2.31。本次是独立源码重构，不更改发行版本，不创建 Tag/Release，不发布 Maven，不替换 A-Jar/玩家实例。
2. 机制与实际方法树见 [原生 Tooltip 摘要](../sum/native-tooltip.md)。保留消费者 API、纯文字与 GT 结构页、键盘阅读、父子固定、材料与网址、主题动效；普通内容鼠标交互仅由主动固定会话启用。旧路径是实际删除，非并行新路径遮盖。
3. 2.9.2 定向组合检查：`runNativePageContracts runTooltipPinContracts assemble` 通过；修改范围 `spotlessJavaCheck checkstyleMain` 通过。覆盖文档隔离与过期档位、真实 public section 及 revision、输入配对/删除路径依赖边界、160 组列分配等价、11 种印记、纯文字与 GT finisher、偏好延迟落盘和固定会话状态。
4. 与已发行普通 0.2.31 JAR 比较了 `api/tooltip` 下 31 个类的 public/protected 字段和方法签名，未缺失既有符号；新包不包含 TierHeaderSlot、TooltipContextAccessor、SealShader、Tooltip shader 或独立客户端探针。
5. 原生客户端：复用登记 GTSR 宿主（2.9.2、Java 26.0.2.1、4 GiB、私有桌面），单次 `core-tooltip-interaction-v1`，runId `382b68f5-0d87-4a3c-b1b7-06ac11272223`。GTSR 2.1.88 仅作宿主，未改其源码。Core 独立探针位于 `src/testlab/client`，沿用原生编译/运行器，编译时显式替换过期 fixture 源；归档源码与 helper 编译收据逐一核对，复用缓存恢复。
6. 客户端实际通过：普通 V 阅读；普通 Shift 左击与 release 未取消，真实服务端库存完成热栏到主背包转移；C 主动固定；点击真实图表标签并在布局失效后配对松开；固定键重绑鼠标按钮；原生链接确认及 Esc 返回；固定材料查询打开实际 NEI 配方界面；GUI scale 变化重建；资源重载清除固定并恢复控制行。四个实绘点 GL error 为 0，截图审阅了固定图表和缩放。
7. 恢复结果：宿主报告 PASS；原 JAR、配置、options 与 Tooltip 偏好恢复，helper 移除；探针库存恢复，专用临时世界删除、集成服务器停止，宿主进程退出且私有桌面句柄关闭。没有操作系统键鼠或焦点切换。
8. 本次客户端实际加载的 Core testlab SHA-256：`96e6574b9ddb1044955a4c057b0a1748d0775a5f1fc0a215be3e8efde905b5ed`；配对普通候选为 `a729fa8f429948cb1f2897b79dc0763dadcba5edda091b5963f9ccd78634da92`。随后仅更新状态提示译文、测试源码格式和文档，生产 Java 编译保持 UP-TO-DATE；最终候选另存真实 hash，不将 dirty 候选称作发行 clean 包。
9. 本地证据入口：`E:/CodeGT/.artifacts/core-tooltip-design-20261128/` 下的 API/打包收据、最终候选清单、组合构建日志和 `testlab/<runId>/report.json`、`client/core-tooltip-interaction.json`。客户端功能断言与宿主恢复结论分别保留；不据此声称所有外部 GUI 或机器业务均已实测。
10. 公开 Wiki 四 Mod 绑定未包含 Core Tooltip 专题，且本次没有新发行来源，不推进其版本/来源指针。内部知识库 Core 专题同步当前源码；不合并或部署站点。
