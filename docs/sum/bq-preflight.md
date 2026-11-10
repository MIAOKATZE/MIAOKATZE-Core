# BQ 注入前校验与相机生命周期

以下入口从 Core 0.2.7 起提供；最低依赖只随实际采用新入口的消费者提高。当前面向 GTNH 2.9.0 的 BetterQuesting 3.8.92-GTNH 与 Angelica 2.2.43，旧公开 API 和旧档格式保留。

## BQ 可选入口

`api.bq.QuestDefinitionPreflight.validateJsonIds(JsonElement)` 在格式化 JSON 转 NBT 前递归校验 `questIDHigh/Low`、`questLineIDHigh/Low`：仅接受 `:4` long 类型标记及精确 long 数值，拒绝小数、溢出、字符串和可能覆盖同名 UUID 半段的其他类型标记。

`QuestDefinitionPreflight.validate(NBTTagCompound)` 验证 quest UUID、原生 prerequisite 结构，并将副本交给新的、未登记到全局数据库的 `QuestInstance.readFromNBT`，使当前已注册的 BQ task/reward 解析器在第一次业务写入前运行。保留原生旧整数数组 prerequisite；UUID list 中缺失或错误类型的半段会拒绝。输入定义保持原样；第三方解析器自己的副作用没有回滚保证。

`api.bq.QuestPackPreflight.prepareResources(JsonArray, Function<String, NBTTagCompound>)` 接收消费者 manifest 的 `questLines` 数组。它一次解析每个资源路径，校验 line UUID、int orderIndex、重复 line、quest/entry UUID 对应关系、同一 line 内重复 quest 与同 UUID 的不同定义文件，之后对全部 quest 作 detached native 校验。明确空 entries 与空 pack 有效；缺资源或错误定义抛异常，不作 live 数据库写入。返回的 map 与 NBT 由消费者持有。

调用方仍负责可选 BQ 存在性判断、读取资源、JSON 转换前调用 `validateJsonIds`、运行期业务目标重写、ownership、stamp、pruning、定义/进度写入、保存和同步。这里没有把自有任务、材料、维度或网络策略放入 Core，也不保证 live 阶段事务回滚。

## 客户端相机

相机事件安装仅在客户端发生；Angelica 存在时内部 `AngelicaHandBridge` 向原生 `RenderHandEvent.BUS` 注册取消谓词，直接读取既有 `WorldCameraHooks.hideHand()`。它覆盖 Iris 独立手部 pass，保持原版 `renderHand` hook 与所有消费者公开调用不变。无相机或解除 hideHand 后事件继续放行。没有新增消费侧 API、Iris Mixin 或按旧 Angelica 版本反射分派。

资源重载不等于销毁 Angelica 的 FFP 缓存程序。`SliceShaders.resourceReloaded()` 结束当前 frame 后只移除已经关闭的 uniform 账本，保留其他程序待关闭的条目与计数。旧程序下一次真实绑定时沿既有 upload 路径关闭 slice uniform；实际程序删除仍由 `forgetProgram` 精确移除。Core 不为清账本主动绑定或删除消费者程序。

## 验证与边界

`runQuestPreflightContracts` 使用真实 MC NBT/BQ 类，在隔离 JVM 中只初始化注册表与 FML 侧别，覆盖精确 UUID、错误 prerequisite、native 空定义解析、资源缺失/冲突、空 pack 与 orderIndex 溢出。`runAngelicaHandContracts` 使用真实 Angelica event bus，覆盖放行、取消和恢复。

`runSliceReloadContracts` 使用真实 Angelica `RenderBackend` 抽象类的记录子类，隔离 backend manager 与 Iris 状态，执行真实 Core 状态转移。旧源码在 A/B 两程序与重复 reload 场景丢失待关闭 A，故障合同退出 1；修后覆盖 A 重用关闭、精确删除与清账本退出 0。这里记录驱动调用，不创建真实 GL context。

这些合同不启动游戏、不创建 GL context、不安装完整 BQ 插件，不代替真实 task/reward 集合、存档、多人网络或 Iris 像素验收。构建与发布身份由对应 workflow 记录，不从源码版本推定已分发。
