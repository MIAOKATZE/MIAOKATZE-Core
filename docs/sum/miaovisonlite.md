# MiaoVisonLite 客户端结构网格

Core 的 `client.miaovisonlite` 提供独立的结构网格、区域选择、可取消的后台拓扑和离屏目标。首个消费者为 GTMB，消费方提供坐标、材质、图集 UV、颜色、相机和绘制次序。源数据采集、蓝图编辑与施工属于消费 Mod。

`MiaoVisonLiteMesh` 在具备相应 GL、Angelica 和 GTNHLib VAO 后端的客户端，将有序顶点直接写入网格缓冲。世界模式可使用模型坐标，活动相机矩阵由调用方设置。网格可以复用，创建、绘制、释放均由持有该 GL 上下文的线程执行。原生 Forge 和方块实体场景仍可由调用方在原绘制顺序中提供。

后台拓扑接收只读区域与材质判定，产出顺序稳定的普通单元及可见面。消费方负责资源重载、换场景和关闭时终止旧任务与释放 GPU 资源。试验计数通过 `-Dmiaokatze.miaovisonlite.diagnostics=true` 启用。
