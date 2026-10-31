# 01 文件责任与交接范围

本记录依据 [G0 输入记录](G0-INPUT-RECORD.json) 和任务包 `START_HERE.md`、`BATCH-OVERRIDES.md`、`HANDOFF.template.md` 整理。执行基线为 `f02dbdc2f4e9891b616bfd2c48b17f28305e44f0`，工作区为 `E:/CodeGT/.worktrees/core-contracts-runtime-20261031`。用户已确认采用 GTNH 2.9.0 正式版，覆盖任务包默认 RC 要求；本批使用 Astra xhigh，不启用 Work。

## Core 内部分工

Java 包根统一为 `src/main/java/com/miaokatze/miaokatzecore/`，下表 Java 路径均相对该根。只新增本次合同 API，保留旧公开 API、数据格式和默认行为；列入责任范围不代表已改动或已验收。

| 路径或职责 | 责任方 | 边界 |
| --- | --- | --- |
| `api/animation/`，排除 `runtime/` | 01 `animation_contract` | 纯模型、严格编解码、编译及采样；相应 `AnimationContracts` 验证由同一责任方维护。 |
| `api/animation/runtime/`、`client/animation/` | 01 `runtime_contract` | 公共运行时合同和宿主显式驱动的会话；相应 `RuntimeContracts` 验证由同一责任方维护。 |
| `build.gradle.kts`、`tools/`、SDK 消费探针 | 01 主代理 | 构建及验证集成、SDK 身份与消费者接收证据；不在此阶段改发布版本或依赖。 |
| `docs/handoff-01/`、交付 SHA、恢复包、Git 操作 | 01 主代理 | 汇总实际变更与实际验证；文书代理仅整理本文件、`COMPATIBILITY.md`、`STAGE07-CASES.md`、`RECORDS-DRAFT.md`，不提交。 |
| 旧 `api/`、`client/` 与入口、proxy、注册 | 01 主代理协调原责任方 | 保留已有 MiaoVision、WorldCameras、WorkGovernor、UiScene；不顺带重构。 |
| GTUDK、Outpost、GTMB、GTIT、GTSWN、GTSR 生产代码 | 对应仓库或任务线 | 本线只读记录依赖、调用点和接入请求；不跨仓修改。 |

Common 合同不得引入 MC client、GTUDK 或 Outpost 的类型。GL、音频、camera 和真实资源接入由宿主明确持有与驱动；业务权限、伤害、经济、HATCH、各 mod 存档不进入 Core。

## 共享记录与 Wiki

| 实际位置 | 本次处理 |
| --- | --- |
| `E:/CodeGT/plan/workflow/`、`E:/CodeGT/plan/summary/` | 实际 Git 根为 `E:/CodeGT`。主代理仅新增本次独立记录，保留已有用户改动；不按目录名推定为独立仓库。 |
| Core `docs/workflow/` | 旧记录位置；未发现 Core 自有 `plan`，不创建替代路径。 |
| `C:/Users/60158/plan`、`E:/CodeGT/plan/sum` | 未发现；不伪造已读取记录。 |
| `E:/CodeGT/MIAO_Wiki_For_GTNH/` | 已有大量用户改动，由主代理保留并只新增本次独立记录。已参考 `architecture/miaokatze-gtnh290-final-baseline.md`。 |
| `E:/CodeGT/MiaoWebWiki-worktrees/core` | 当前不存在；公开 Wiki 注册表只有 `gtswn/gtit/gtsr/gtmb`。本次尚无已发布新功能说明变化，不创建 Core 分支或 worktree，不推送或发 PR。 |
| Core 测试维度指南 | 未发现 Core 专属指南；已发现的 GTMB/GTSR 指南各属对应项目，不套用为 Core 指南。 |

## 交接边界

02/03 接收同一 Core SDK 的格式、时间和生命周期合同；05/06 接收同一 SDK 的表现及计数合同，不获得业务权限。签名、能力、源码/SDK SHA256 与消费者接收回执由主代理填写最终冻结记录，当前不得凭类名或历史测试推定开放能力。

第七阶段统一负责真实实例、Tag、发布版本、clean release jar、A-Jar、push、版本 PR 与 Release，仍须满足各动作已有授权。当前保留 detached worktree，待第七阶段确认已集成且恢复材料可用后再处理清理。具体未测门见 [第七阶段用例](STAGE07-CASES.md)。
