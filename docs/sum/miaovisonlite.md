# MiaoVisonLite 客户端结构网格

Core 的 `client.miaovisonlite` 提供独立的结构网格、区域选择、可取消的后台拓扑和离屏目标。首个消费者为 GTMB，消费方提供坐标、材质、图集 UV、颜色、相机和绘制次序。源数据采集、蓝图编辑与施工属于消费 Mod。

`MiaoVisonLiteMesh` 在具备相应 GL、Angelica 和 GTNHLib VAO 后端的客户端，将有序顶点直接写入网格缓冲。世界模式可使用模型坐标，活动相机矩阵由调用方设置。网格可以复用，创建、绘制、释放均由持有该 GL 上下文的线程执行。原生 Forge 和方块实体场景仍可由调用方在原绘制顺序中提供。

`MiaoVisonLiteSession` 按视口持有区域结果、后台任务、有序材质提交、宽线和离屏目标。消费方在资源重载、换场景和关闭时释放会话，并在 GL 上下文变化时退休自己持有的原生模型。使用后台拓扑时只传入只读区域与材质判定，按原区域顺序取回普通单元及可见面，注册表、原生模型和 GL 调用仍由渲染端执行。

`MiaoVisonLiteSubmission` 维持消费方的面和材质次序，在满足复用条件时选择常驻 VAO、兼容显示包或 CPU 顶点切片。进入常驻路径后释放同一批次的 CPU 顶点副本，图集依赖变化时重新提交原始顶点。试验计数通过 `-Dmiaokatze.miaovisonlite.diagnostics=true` 启用。

常驻宽线在标准布局下直接生成 Angelica 对应的顶点内容，非标准布局继续调用原有打包路径。会话复用暂存缓冲，保留线宽、颜色、顶点顺序和深度规则。`runMiaoVisonLiteWideLineContracts` 核对两种标准格式及回退格式的逐字节等价。
