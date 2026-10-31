# 01 共享记录草稿

以下三段仅供主代理在实际 SDK、交付 SHA 与验证结果齐备后更新并采用；尚未写入共享 plan/wiki，不代表已冻结或验收通过。

## plan/summary

01 Core 以 f02dbdc2f4e9891b616bfd2c48b17f28305e44f0 为基线，按用户确认面向 GTNH 2.9.0 正式版。范围为新增动画合同与运行时 API，保留旧 API。SDK、交付 SHA 与消费者回执待冻结；实机未测，实例及发布操作交第七阶段。

## plan/workflow

在 detached worktree 实施 api/animation、api/animation/runtime 与 client/animation，主代理汇总构建、SDK 和交接记录。当前冻结证据待补；真实 FML/GL、资源、音频、专服与性能均 NOT-RUN。本阶段不做 Tag、版本更新、clean release jar、A-Jar、push、PR 或 Release。

## localwiki

Core 本次为新增公共动画合同与运行时，目标 GTNH 2.9.0 正式版；源默认 MUI2 2.3.94 不等于已核验安装版本。SDK/能力表待冻结，真实宿主未验收。六个旧消费者依赖与调用点已只读记录。暂不发布功能说明或创建 Core Wiki 分支，待第七阶段集成。
