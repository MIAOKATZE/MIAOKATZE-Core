# 保留式组件 UI

`api.ui.component` 是无 Minecraft / OpenGL 依赖的小型通用组件树；`client.ui.MinecraftUiRenderer` 是原生 HUD / GuiScreen 绘制桥。它不解析 HTML、CSS 或 JavaScript。布局与输入参考 MUI2 2.3.92 的 Flow、TextField 和视口机制，但组件核心可以在 JVM 中独立执行；现有 MUI2 和相机 API 不变。

```java
UiNode root = UiNode.panel("root").layout(UiNode.Layout.COLUMN).padding(6).gap(4);
UiNode value = new UiNode("status", UiNode.Kind.VALUE).text("Ready").size(-1, 18);
root.add(UiNode.button("action", "Run", action).size(-1, 20));
root.add(value);
UiScene scene = new UiScene(root);
scene.layout(guiWidth, guiHeight);
scene.render(new MinecraftUiRenderer(fontRenderer, guiWidth, guiHeight, displayWidth, displayHeight));
```

宿主持有并复用 scene 和 node。数据变化调用 `text` / `enabled` / `selected`；结构变化才 add/remove。`layout` 同尺寸且未 dirty 时直接返回。`bounds` 同值不触发布局，文本发生变化才 dirty；渲染不重建组件树。

- `RECT`：子项 bounds 相对于父内区；`ROW` / `COLUMN`：主轴依序布局，固定尺寸和 gap 扣除后的剩余空间均分给主轴 `-1` 子项；交叉轴 `-1` 填满父内区。minSize 是下限，空间不足允许溢出，配合 overflowClip 或 scrollable。
- `GRID`：固定列数、均分单元宽度，每行高为该行最高项；未给高度的项为至少 24。visible(false) 折叠占位，disabled 保留占位。
- `bounds()` 是布局后的 scene-local 绝对矩形；`clip()` 是同坐标系的可见交集。绘制、命中共用它们；右边与下边不包含。scrollable 自动裁剪父内区、位移 children，并在内容减少时钳制位置；滚轮正 delta 向下，以 GUI 像素为单位。滚动边缘不消费无位移的默认事件。
- 主题 `UiStyle` 为不可变的 normal / hover / pressed / selected / disabled 调色板；disabled 优先级最高。LABEL/VALUE 默认只画文字、指针透明；其他默认实体背景。TAB 使用按钮的行为与 selected 状态，具体页面显隐由宿主控制。

所有 pointer / hitTest 入参是屏幕 GUI 坐标，scene.transform(CanvasTransform) 同时控制绘制变换和命中逆变换。`UiRenderer` 的绘制和裁剪入参保持 scene-local；begin(transform) 由绘制桥应用变换。Minecraft 桥复用 GlScope 和 ViewportCoordinates，以原始外层 scissor 的交集裁剪并在 end 恢复 GL 状态。自绘回调中自行修改 GL 的宿主也需成对恢复自己的状态。

事件 `node.on(Type, handler)` 从最深命中项向父级冒泡，consume 停止传播。事件 x/y 为 scene-local 绝对坐标，localX/localY 相对于当前冒泡节点。custom canvas 用 onDraw 绘制，用 DOWN 内 capturePointer 获取捕获；MOVE 与匹配 UP 即使离开视口也送到捕获项。其他按钮释放不会结束捕获。默认按钮主键按下时捕获，匹配释放且仍命中原按钮时调用 onClick。

宿主须转发 pointerDown / pointerMove / pointerUp / scroll；失去系统焦点或输入租约调用 blur。Esc、blur、取消捕获、禁用/隐藏/移除节点以及 close 都发送 CANCEL 并清理捕获。业务拖动在 CANCEL 回滚自己的预览。`captured()!=null`、`focused()!=null` 可用于宿主的输入租约；改变页面时可以显式 cancelCapture。

keyTyped 使用 LWJGL 2 key code，支持 Tab / Shift-Tab、按钮 Enter/Space、文本光标/Home/End、Shift 选择、Ctrl-A、Backspace/Delete。文本为单行、实时 onChange；Enter 结束焦点，Esc 清焦点但保留已编辑内容。复制/剪切/粘贴或输入法由宿主通过 selectedText / insertText 接入；Core 无操作系统剪贴板依赖。onChange 不因程序性 text 更新而触发。maxLength 限制用户插入；程序性 text 可用于显示权威数据。

id 在一个 scene 内唯一，重复会拒绝；child 只属于一个 parent。remove/clear 永久销毁对应子树，先取消其捕获/焦点，再执行 onDispose；不影响其他分支的捕获。close 幂等、销毁所有节点并解除回调。node/scene 均只允许宿主 GUI 线程访问。

`runUiComponentContracts` 覆盖布局、dirty、共享命中/变换/clip、滚动、捕获按钮匹配、Esc/失焦/移除清理、冒泡、焦点/文本、销毁以及绘制异常收尾。无头契约不证明字体、IME、GPU 裁剪或游戏内视觉；这些需要客户端验收。
