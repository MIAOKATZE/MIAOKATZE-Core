# 共享工作与内存准入（0.1.23 候选）

`api.work.WorkGovernor` 提供纯 Java 8 的公平 CPU 许可、按字节租约、精确键 singleflight 与过期资源回收入口。它不引用 Minecraft、GL、玩家、世界或存档，不创建线程、executor、事件监听或定时任务。消费者明确创建实例或调用 `processShared` 后才参与调度。

## 实例与进程共享

```java
WorkGovernor governor = WorkGovernor.processShared(
    1, 256L << 20, MyOwnerThreads::isCurrentOwner, myPassiveObserver);
```

首次调用配置进程共享实例；后续消费者使用相同 CPU/字节限额，得到同一个实例并附加各自的 owner guard 和观察器。不同限额会明确拒绝，不能在另一个 Mod 加载时悄悄改变既有许可。客户端和集成服务器不能各自创建一份“共享”实例。单独的 `new WorkGovernor(...)` 用于调用方明确需要隔离的工作域或测试，具有独立配额。

默认构造器的 singleflight 上限为 64；另一个构造器允许显式指定上限。Core 不读取 Mod 配置，也不以逻辑核数自动扩容。GTMB 的低负载默认值、最多两个重 CPU 许可、线程名称识别和界面状态继续由 GTMB 拥有。其他已有消费者只使用旧 API 时不需要提高最低 Core 版本。

owner guard、`Observer` 与 retired reaper 都是实例级显式登记，按对象身份去重，每类最多 16 个。登记的是短小、进程生命周期的模块回调，不得捕获玩家、世界、活动任务或存档。观察器只记录诊断，不等待、不重新进入准入、不获取游戏或 owner 锁；诊断中的运行时异常不会改变租约释放结果。owner guard 抛错会阻止本次阻塞准入。

## CPU 与内存顺序

`tryHeavyCpu(owner)` 是公平的非阻塞申请，忙时返回 `null`。`acquireHeavyCpu(owner)` 只允许 worker 使用，会中断式等待；owner guard 命中、当前线程已持 CPU 或尚未移交的内存租约时立即拒绝。worker 不得持 CPU 等 I/O、另一个 future 或需要同一许可的任务。取消在纯计算页、行或流式块边界检查；Core 不能抢占原生回调。

```java
try (WorkGovernor.Lease cpu = governor.acquireHeavyCpu("module:compress")) {
    // Pure CPU work only. Release before file I/O or owner publication.
}
```

`tryRetain(owner, bytes)` 必须在分配之前调用；不足返回 `null`，`retain` 则抛出明确的 `RejectedExecutionException`。这表示暂时无法安全分配，不能因此把合法输入标成格式错误。消费者按自己的 continuation 延后或报告匹配请求的 busy 状态。compressed bytes、展开字节、对象化页面、图片输入/输出和新旧状态共存分别计入实际同时存活量。

内存 `Lease.transfer()` 表示租约随完成消息或缓存移交，解除原 worker 的等待顺序计数，仍保留进程字节占用。最终实际持有者调用 `close()`；重复关闭无效，可由不同线程完成。CPU 租约不能移交。不能借 `transfer` 隐藏仍由同一工作阶段持有、并将阻塞申请其他资源的大对象。

## singleflight 与取消

`singleFlight(exactKey, epoch, work, boundedExecutor)` 只合并同一 epoch 下 `equals` 精确相等的不可变键。进程共享实例中的键必须包含消费者命名空间、全部纯计算依赖与语义版本，不同输出类型不能复用一个键；不能用短 hash、可变 DTO、世界或玩家作为完整内容证明。调用方提供既有有界 executor；followers 不占 CPU 等 producer。

每个 `Subscription` 有独立 future。取消一个订阅不取消其他订阅；最后一个订阅取消后，未开始的 callable 立即退休，排队取得 CPU 时每约 5ms 检查取消，运行中的纯计算通过 `currentFlightCancelled()` 检查是否还存在订阅。不同 epoch 互不复用；`cancelEpoch` 取消该次快照中的旧订阅。producer 对自己的精确键重入立即拒绝。异常移除条目并通知订阅，后续请求可重新计算。

有效参数检查完成后，`AutoCloseable Callable` 的输入所有权由 governor 接收。命中既有 producer 的未用 callable、准入/入队失败、排队全部取消和执行结束都恰好关闭一次。callable 的 `close` 必须清理大输入引用；成功输出若继续引用输入，必须先取得独立的输出所有权。相同 callable 实例重复订阅不会提前关闭已接收的那个实例。CPU 释放、callable 退休和 producer 上下文退出均先于完成消息分发。

普通 `AutoCloseable` 输出只有在无人接收时由 governor 关闭，正常交付后属于消费者。多订阅共享资源使用 `SharedOutput`：其 `close()` 只释放 producer 基准引用，各订阅必须先取得自己的引用或视图。

```java
governor.singleFlight(key, epoch, producer, boundedExecutor, (shared, failure) -> {
    if (failure != null) {
        postFailure(failure);
        return;
    }
    View owned = shared.retainView(); // synchronous: retain before queueing
    postContinuation(owned);        // the receiver closes its own view
});
```

最后一个 `onReady` 入参在订阅被 producer 完成之前安装，连 inline executor 在方法返回前完成也安全。`SharedOutput` 的 producer 引用在全部同步完成回调分发后关闭。不能先返回，再用晚注册的 `future.whenComplete` 或异步回调去借已经退休的共享引用。回调只取得视图并排队后续工作，不在回调中等待。

## 跨域退休资源

模块可以显式 `registerRetiredReaper(MyModule::reap)`。每次 `tryRetain` 先在字节配额锁外轮转执行回收，整轮候选软预算约 0.5ms，递归或同时发生的另一轮直接跳过。单个 callback 自己最多处理 256 个 reference 或约 0.5ms，不能无限排空 GC 队列。异常被隔离，后续域仍有机会回收。

这一机制不调用 GC，也不是硬实时保证；已经 GC 的另一个域对象能够在新域申请内存时释放其租约。实际对象仍有使用者时不能因为 LRU 驱逐就提前释放配额。消费者必须在取得自己 cache/owner 锁之前执行准入，回收器只处理自己退休资源，避免跨模块锁倒置。

## 核验与发布边界

`runWorkGovernorContracts` 挂入 Core `check`。纯 JVM runner 覆盖一/两许可、FIFO 等待、中断、owner guard、独立实例、进程共享、字节准入/移交、singleflight 订阅取消与 epoch、重入和异常、输入/输出所有权、提前完成竞态、观察器及有界回收入口。旧 `runCoreContracts` 保留原 API 测试。

这些组件证据不代表某个 Mod 已完成真实 client 帧、server tick、进程 CPU、GC 或原生事务验收。0.1.23 是本次候选，正式 Maven 产物、Tag 与配套消费者版本由发布流程固定，不能覆盖旧坐标。
