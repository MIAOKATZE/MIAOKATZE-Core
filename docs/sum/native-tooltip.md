# 原生 Tooltip 组件（GTNH 2.9.0）

0.2.16 开发源码。旧的 `TooltipPages` 和 `MachineTooltipPages` 仍是物品偏好与说明页的入口。普通键负责收纳，修饰组合切换该物品动画，实际绑定以控制设置为准；偏好属于客户端物品身份，不写物品 NBT。原生表不占用左 Shift，结构页由 GT 负责。

消费端先照常输出完整的可读字符串，再用 `NativeTooltips.replace(stack, lines, expected, snapshot)` 登记其自己拥有的一段。`TooltipTable` 保存同一时点的标题、表头、行和对齐方式。Core 在 Chromatic Tooltips 1.0.36-GTNH 的 `TextLinesConverterEvent` 里按目标物品身份与整段原文匹配，成功才把该组转换为组件。原 `ItemTooltipEvent` 列表不放组件令牌，NEI 搜索、纯文本 UI、未加载可选绘制层、译文已改到不能匹配时都保有原文字。不会为了替换失败而删除或改写其他 Mod 行。

表格用当前 Tooltip 字体测量单元格，再按列绘制；名称左对齐，数值和单位在同一格中靠右。GUI 缩放作用于已计算的 GUI 像素；宽度不足时转紧凑纵向布局。长表参与 Chromatic 的分页。标题效果和正文分开，动画开关不改变列宽、原文和玩法。GTSR 首个消费者展示维度辐照耦合锅炉两组额定材料；不读取背包物品以外的世界状态。

扩展点分开：公共 `TooltipBlock` 提供完整的纯文本回退；客户端 `NativeTooltipClient.registerRenderer` 为新的快照类型提供 Chromatic 组件。后续精灵图可用固定尺寸按客户端时钟选帧，图表可消费业务侧已同步的不可变样本，本版没有新增网络同步或精灵图／图表控件。

[标题动效](../tooltip-art.md) 说明通用、GTSWN、GTSR、GTIT、GTMB 的视觉语言、静态回退和绑定方式。首个消费端仅启用蒸汽与维度撕裂两种。
