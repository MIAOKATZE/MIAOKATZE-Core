# 公共运行期与单文件发布合同

本轮在 0.2.7 源码上追加实现，新 API 从 0.2.8 提供；正式制品由总协调冻结。旧公开类型和方法描述符保留，以下记录不表示已发布、已做游戏验收或完成多系统事务。

## 单文件完整字节发布

`api.io.AtomicFilePublisher.publish(Path, byte[])` 在目标现有父目录创建唯一临时文件，完整写入后 `FileChannel.force(true)`，再原子替换目标。只有 `AtomicMoveNotSupportedException` 才允许普通替换，并返回 `PublishResult.REPLACE_FALLBACK`；原子成功返回 `ATOMIC_REPLACE`。其他失败向上传播，尽力删除临时文件；清理失败附在原异常后，不隐藏首因。

`publishAtomic(Path, byte[])` 使用相同流程，禁止普通替换回退。资产操作前的 journal 应使用此入口，写入失败即停止后续操作。成功返回仅证明单文件移动完成；不保证目录元数据强制落盘、磁盘断电恢复、并发读改写合并、多个文件或世界/AE/玩家存档原子提交。父目录创建、编码、命名空间、备份、dirty/commit 判定由调用方负责。已有非普通文件或符号链接目标会拒绝；这不是对不可信并发目录攻击的完整安全机制。

GTIT 的双 stamp 键合并、GTSWN 的 owned 定义指纹和保存意图、GTMB 的付款/世界恢复日志都保留在消费者。Core 不把 BetterQuesting 的 Save 事件当成异步 Future 已完成，也不替消费者声明业务保存成功。GTIT 的 NBT 原子写与重生 checkpoint 有各自严格合同，本轮没有强行迁移这些不同设施。

已核 GTNHLib 0.11.54 没有同合同的字节发布设施；Commons 的普通 move 不提供写入 force 和同目录原子替换合同。新原语来自三仓真实需求和 GTIT 现有完整字节发布流程，没有新增 BQ 事务框架。

## 相机、FFP 与异常恢复

`AngelicaProgramBridge` 仅从客户端、Angelica 存在的安装分支访问，向原生 `GLSMHooks.PROGRAM_DELETE` 注册一次。Angelica 2.2.43 的顺序是 capture 拒绝、忽略零 ID、CompatUniform 清理、通知、backend 删除。旧 HEAD 注入会在拒绝删除时提前遗忘账本；现改用真正通知，删除前 Compat 清理抛错时也保留账本。backend 在通知后抛错仍按原协议遗忘，不能将通知说成 GPU 删除已确认。

`MixinSliceGlStateManager` 不再注册、没有注入注解；历史公开空类型保留以维护二进制兼容。独立 `GlProgram.destroyInternal → LWJGLService.glDeleteProgram` 路径继续保留钩子：实际 LWJGL2/3 实现分别调用 GL20/GL20C，不能把尚未完整核实的运行期转换链当成所有路径都会发 GLSM 事件的证明。

`ScreenLineStream`、`SolidBoxRenderer`、`PackedVertexStream` 统一调用内部 `AngelicaFfpBridge`，保留原 GLSM 缓存、FFP 激活、逻辑 program=0、Iris 排除和原生 `preDraw` 恢复合同。公开绘制接口、owner 线程、失败不重画和支持后端不变。

`SliceShaders.domain` 先完成当前 program 的 native 准备，再设置新域。准备抛错时没有可关闭的 Scope，因而必须保留原域；修复后外层 scope 可继续正常清理。已有 mesh blockAccess/FaceScope、Tessellator 捕获及 terrain/TESR 的 try/finally 已核；GTSR 自有 BUILD ThreadLocal 与 display-list 生命周期由其仓修复。

32 个历史 Core Mixin 类型逐项记录在本轮 `core-mixin-disposition.json`，包含注入点、实际候选和依据。嵌入 Celeritas 目标补了真实 2.2.43 dev JAR 的 `javap -p -c`，不是将源码缺口直接写成“必须保留”。保留结论只针对已核候选：RenderChunkEvent 在 translucent draw 后、没有 output/manager/取消或 finally，不能代替 task stamp 和真正 upload ACK；FFP 变体直接 backend 绑定，不发逻辑 PROGRAM_CHANGE。R08 跨仓 section 通知桥仍需具体维护收益和共同消费者合同，暂不扩公开框架。

## UI 与工作域

`UiNode.disposeTree` 聚合多个回调抛出的同一异常实例时跳过自抑制，继续处理余下兄弟和父节点清理，最后保留原异常。

`WorkGovernor.singleFlightInDomain(Object domain, K key, long epoch, Callable<V>, Executor, BiConsumer)` 按 domain 对象身份隔离合并；`cancelEpochInDomain(Object, long)` 只取消相同身份域的订阅。domain 应由 owner 在整个生命周期保管。旧 `singleFlight` 使用 null 域，旧 `cancelEpoch(long)` 继续全实例、跨域取消，保持原行为；共享实例使用旧取消接口时必须分配全局 epoch。新增入口没有强制现有消费者迁移，也不持有业务世界/玩家存档。

## 验证边界

本轮 JDK 8 定向编译通过；真实 Windows 原子文件替换、受锁移动失败保原文件/清临时文件、UI 同异常、工作域隔离、原生 Angelica hand/event bus、真实 GLSMPrograms 删除方法与 Slice 状态合同通过。删除方法仅替换 capture/Compat/backend 边界，不创建 GL；shader 域旧源码 red 退出 1、修后 green 退出 0。0.2.7 dev 元数据与定向生产类覆盖比较保留原 229 公开类、412 字段、978 方法；这是局部候选 ABI 证据，不是完整 runtime 制品证明。

真实 GPU 探针已交总协调，在现有客户端 render 线程创建自有两套 program，观测 uniform、重复 reload、惰性关闭和 native 删除通知。未取得运行结果前不标为游戏通过；手部事件合同也不等同真实 Iris 手部像素验收。合同与原日志位于 `E:/CodeGT/.artifacts/core-global-followup/core-focused/`。
