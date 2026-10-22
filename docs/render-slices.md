# TessellatorSlice：现有 GL_QUADS 批次的 CPU 捕获与重放

`TessellatorSlice` 是 vanilla `Tessellator` 顶点数据的不可变 CPU 副本。它只向正在构建的现有批次追加顶点，不绘制、不清空或重启批次，也不持有 OpenGL 资源。源码合同见 `src/main/java/com/miaokatze/miaokatzecore/client/render/TessellatorSlice.java`；CPU 数据合同见 `TessellatorSliceContracts`。

## 捕获与 null 结果

`capture(tessellator, commands)` 要求可识别的 vanilla `Tessellator`、正在绘制的 `GL_QUADS` 批次及一致的 8-int 顶点布局和缓冲区状态。子类、其他绘制模式或无效布局可能不受支持。

`capture` 无论能否缓存，都会执行 `commands` 一次。成功时返回片段；不支持的 batch 或顶点布局返回 `null`。因此 `null` 只表示“这次没有得到可重放缓存”，不表示原绘制未执行。返回 `null` 后不能再单独执行一次 `commands`，否则同一帧会重复追加顶点。回调若抛出异常，异常直接向上传播；API 不回滚已发生的写入，也不自动重试。

回调只能向现有批次追加顶点并更新 vanilla 顶点属性。不能绘制或重启批次、修改已有顶点、读取批次计数，或执行重放时需要再次发生的副作用。若渲染扩展另存了每顶点状态，调用方必须排除这类路径；片段只保存 vanilla 的 8-int 顶点数据及属性写掩码。

## 重放与调用方回退

`replay(tessellator)` 只接受与捕获时输入属性状态相符的批次。状态包括 UV、亮度、颜色、法线、属性启用标志、颜色禁用标志、偏移、绘制模式和绘制状态。`true` 表示片段已追加到现有批次并恢复捕获结束时的属性状态；不会提交、绘制或清空该批次。

对于不支持或输入状态不匹配的批次，`replay` 返回 `false` 且不修改缓冲区、计数或属性状态。调用方随后应通过 `capture` 执行本帧原始绘制，并按返回值决定是否缓存新片段；`capture` 已执行原始绘制，即使它返回 `null` 也不可再运行一遍。

```java
if (cached != null && cached.replay(tessellator)) {
    return; // 当前批次已追加缓存顶点
}

TessellatorSlice next = TessellatorSlice.capture(tessellator, originalCommands);
if (next != null) cache.put(key, next);
// capture 已执行 originalCommands 一次；null 时也不能再重复绘制。
```

回调异常不会触发回滚或自动回退重试。若调用方捕获异常后自行重跑回调，必须先自行证明部分顶点写入及其他副作用可以安全重复。

片段只校验 Tessellator 的批次状态，不检查世界、模型、材质图集或选择条件是否改变。调用方负责缓存键、资源重载与场景变更失效，只在原始绘制输入仍相同时复用片段。

## 内存与生命周期

`retainedInts()` 返回保存的原始 int 数量。片段还保留每顶点一个属性写掩码，以及捕获前后的属性状态快照。数据是普通 CPU 数组，没有 GL 句柄、缓冲对象或其他需要释放的资源；`TessellatorSlice` 不需要 `close`。内部捕获 session 的关闭由 `capture` 的 `finally` 处理。

`TessellatorSliceContracts` 覆盖属性写入掩码、8-int 布局和状态恢复，测试不初始化 Minecraft，也不调用 GL。它验证 CPU 数据合同，不代表客户端画面或 GL 验收。
