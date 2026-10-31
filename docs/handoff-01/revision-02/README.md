# CORE-03-01：LIFO 修复候选

基于冻结提交 `522b63e9b2571d8e9164f9ba2fc473427b036800`。本次修复 `ResourceOwner.retireInvalidated()` 与 `close()` 的释放顺序：从登记顺序改为逆登记顺序。公开签名不变，旧 scalar V0、单位、计费、借用资源、owner/世代与失败清理语义保持；多项不同异常时，主异常随逆序确定，不承诺原 FIFO 主异常身份。

旧提交、旧 SDK 和恢复 bundle 均保留。本次 SDK 是新候选，不能覆盖旧 artifact/hash 或把旧消费者签收沿用到新 SDK。新完整 Core dev/发布产物本轮不构建。

## 实际验证

- 真正 JDK8 新反例在修复前失败：先登记依赖A、再登记使用A的B，错误的A先释放可被观察；原失败及修复后输出见 receipts。
- 修复后 RuntimeContracts **4236** 项、AnimationContracts **13679** 项及两个独立SDK消费示例通过。使用已有 `tools/run-animation-contracts.py`，本轮输出目录 `build/animation-sdk-r2-lifo`，没有覆盖旧 `build/animation-sdk`。
- 覆盖最终关闭及失效退休LIFO、旧/新generation共存、borrowed不close、回调可见的detach/fence、失败继续、同异常保真、失败reservation保留和重复关闭。
- 新旧 SDK `javap -public` 全量输出逐字一致。只证明纯SDK接口和本次行为，不能代替消费者实际接入确认、完整Mod链接或实机。

新 SDK SHA256：`2ca1f8c22111c5e84177a925ef70f759dac462e075cdd3518cf585bf890ea91e`。
受测源码聚合 SHA256：`6c631b0d8aac0ea6bc5184ced77e1054cf2bebfcd8948ee7364075c372cc42c2`。
命令/源码hash/日志见 [receipts/manifest.json](receipts/manifest.json) 与 [commands.json](receipts/commands.json)。完整构建、GL/实例及发布仍未运行。

## 消费者影响与重签

|消费者|当前观察|所需确认|
|---|---|---|
|02|原同步viewport不登记新ResourceOwner资源|确认新SHA/hash及原文档保存接口不受影响|
|03|ScalarPreview透传ResourceOwner；现有ScalarAuthoringContracts仍断言FIFO并明确LIFO阻断|由03更新旧FIFO断言/独立资源限制/runner冻结锁，验证B→A及其真实宿主，再确认新artifact；不能绕过锁后宣称旧回执有效|
|05|SupportDecoration只持标量样本、不登记设备资源|确认新SHA/hash和原表现接缝；不因此代关04 Aux门|
|06|MetricWindow接缝，无新增设备资源|确认新SHA/hash；QualityGovernor/录制宿主等独立缺口仍保留|

状态为 **修复提供方已验证、消费者复验待确认**。不单方面关闭CORE-03-01消费门或整体01c；F03作品格式、02/03挂载/编辑合同与06宿主不是本补丁已实现范围。
