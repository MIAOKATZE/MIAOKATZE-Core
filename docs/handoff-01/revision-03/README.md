# 缺口修复 R3：可视合同与质量接缝

用户已明确授权01协调必要的02/03/06消费者修改。主工作区、原冻结提交与bundle保持；本轮仅在独立detached工作树实施，发布/实例仍在第七阶段。

## Core变更

- 继承R2的LIFO修复。
- 独立 `core.animation.visual` 1.0：固色无光照方盒、稳定target/material、完整静态TRS、受限旧标量轨道绑定；复用同一严格词法解析与scalar V0，不复制codec，不放宽旧JSON。
- `VisualClip / VisualClipCodec / VisualPlan / VisualFrame` 为纯Java；文档见 [可视协议](../../animation-visual.md)。
- `SolidBoxRenderer` 提供显式screen/capture绘制入口，复用SolidMesh和既有相机/preview scope；只编译验证，真实GL待07，见 [原生接缝](../../animation-visual-renderer.md)。
- `QualityGovernor.Indexed` 为已有五档消费者提供按索引迟滞状态机；旧四档API保持。原滤波/阈值/负载由宿主负责，只有一份档位/计时状态，锁期间reset不改变当前档。

持久key编辑身份、工程snapshot/history/save、GUI、capture与用户模式归消费者；Core不持World、业务权限、HATCH/NBT或服务器事务。完整节点、外部纹理/模型、任意脚本、GPU query、状态模拟没有借本补丁开放。

## 验证与工件

真实Zulu JDK/JRE8，`tools/run-animation-contracts.py` 输出 `build/animation-sdk-gap-close-r3`：13679动画、4236运行时、140可视、42质量索引断言及两个独立消费者示例通过。新SDK SHA256：`b0c353a8c892e23a7ce7684b4128515b488186c8b429cd8fcad2592b3f46479f`。69个旧SDK类的public声明逐字一致（固定旧JAR类顺序、统一换行）；初次检查仅因outer/inner排序不同失败，原输出保留，未为比较改源码。

原生renderer在真实patchedMC/Forge/LWJGL/Angelica依赖上JDK8编译通过；证据 `build/visual-render-evidence/r3`。没有GL context、FML或像素验收。

为消费者定向编译提供单一**link-only候选**：`build/core-link-candidate-r3/miaokatzecore-gap-close-link-only.jar`，SHA256 `3ccd2ac167dc607a6e56af259f9cd2ac29cf8b39295138cdb1f566d1e4e4e634`。由固定旧完整dev（SHA256 `16f5b4d2adbdf3ed5416a6bef714210f6e5bbd84814eeeddac93c864f989b693`）保留未变class/resources，替换为已实编的新animation与native renderer class；逐项provenance见同目录。组装工具 `tools/assemble-core-link-candidate.py` 校验所有输入hash、major52与未变项，不是完整Gradle/clean release build；禁止安装、发布或进入A-Jar，不得与SDK叠放。

源码、SDK、link-only候选、恢复包及最终consumerSHA由统一接入交接单锁定；旧回执不自动继承。此Core提交仅提供能力，01c关闭仍需本轮授权消费者接线、定向证据、精确SHA/hash与共同收口记录。
