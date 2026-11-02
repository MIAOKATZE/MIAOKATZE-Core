# Core 分发与验证

本页区分源码版本、Maven 制品和 GitHub Release。以下历史状态截至 2026-11-02；本轮源码目标为 **0.2.4 候选**，当前推荐的已分发版本为 **0.2.3**。

## 当前状态

| 对象 | 可核查状态 |
| --- | --- |
| 普通 Maven `0.2.0` | 在 Maven 固定提交 `7230dfe839af62e683aa9a8fde40cb31b13b36a5` 中没有该版本目录 |
| `v0.2.0-git.8+099f028d1a` | 独立的历史精确版本，不能改名或复制为普通 `0.2.0` 补发 |
| Maven `0.2.3` | 已分发；源提交 `66d72c0b5c3f043d31dbd446308aa2e66f2039ba`，Maven 提交 `7230dfe839af62e683aa9a8fde40cb31b13b36a5` |
| 源码 Tag `v0.2.3` | 正式 Tag 已存在，指向上述源提交 |
| GitHub Release `430739227` | `tag_name=v0.2.3`，仍为草稿：`draft=true`、`published_at=null` |
| `0.2.4` | 本轮本地候选与源码版本 PR；Maven、远端正式 Tag、GitHub Release 均未发布 |

FML 最低版本是 API 兼容范围，不能证明相同数字的 Maven 坐标存在。不能将历史特殊版本改名、重新打包或覆盖既有坐标来补齐普通 `0.2.0`。发现依赖缺失时，应由对应消费仓审查并选择实际已分发、满足其需求的精确版本。

## 已分发 0.2.3 的证据

既有交付回执位于相邻 GTSR 仓库：

- `GT-Steam-Reborn/plan/implementation/cloud-review-v214/evidence/delivery/maven-remote-verification.json`：固定 Maven 提交下 normal、dev、sources、POM、module 五项远端下载核验，历史结果为 `PASS`。
- `GT-Steam-Reborn/plan/implementation/release-drafts-v214/remote-refs.json`：正式 Tag `v0.2.3` 的解引用源提交。
- `GT-Steam-Reborn/plan/implementation/release-drafts-v214/core-draft-final.json`：Release `430739227` 的草稿快照。

| 0.2.3 制品 | SHA-256 |
| --- | --- |
| normal JAR | `fb6ab8282e5c694866a2d6c269cab1dd50cb0af6bc4858a5825419d2867766b9` |
| dev JAR | `6ac361ee67baedc3cf2f0ab57ab6118ec125d16bd670588d8d69f64faffe56b3` |
| sources JAR | `ba8ec76988e4306d271806ea9147f8c094b78f612816e39c1066a9bc0b7a97ac` |

正式 Maven 坐标不可覆盖。后续制品发生变化时递增版本，并记录最终源 SHA、固定 Maven 提交、制品 SHA-256 和验证回执。固定地址和 SHA-256 可以确认取得哪些字节；源版本一致及校验报告可以核查声明的一致性，**不能单独证明这些二进制确由该源提交构建**。完整构建溯源仍需对应构建过程与独立证明。

## 本轮候选的交付边界

0.2.4 只包含版本及文档修正、分发验证工具和独立解析探针；不修改 Java API 源码。本地 Tag 下的 clean 构建用于候选审查。源码版本 PR 合并后，必须以最终合并 SHA 重新构建并核验；获得发布授权后才发布新的 Maven 坐标、远端正式 Tag 和 GitHub Release。候选回执、历史 `PASS` 及 PR 创建均不等于正式发布完成。

本轮新增工具入口见 [工作记录](workflow/v0.2.4_20261102.md)。提交前本页与工作记录保留当时的待验证状态；后续最终结果归档于 `GT-Miao-Blueprint/plan/core-v024-distribution-20261102/`，不通过改写已打 Tag 的源仓文书补填。尚未执行或未取得回执的项目均保持待验证；不将单项坐标解析解释为完整 GTNH 冷构建、游戏启动或客户端渲染验收。

## 消费仓保持各自边界

本轮不统一升级五个消费 Mod，不修改其 API、FML minimum 或下载声明。当前只读检索结果如下：

| 消费仓 | 默认编译 Core | runtime manifest Core | FML minimum |
| --- | --- | --- | --- |
| GT-Miao-Blueprint | 0.2.1 | 0.2.1 | 0.2.0 |
| GT-Simple-Wireless-Network | 0.2.0 | 0.2.0 | 0.2.0 |
| GT-Steam-Reborn | 0.2.3 | 0.2.3 | 0.2.3 |
| GT-Universal-Development-Kit | 0.2.2 | 0.2.2 | 0.2.2 |
| GT-Outpost | 0.2.2 | 0.2.2 | 0.2.2 |

GTSWN 的普通 `0.2.0` 精确坐标缺失由其独立任务处理，不在本轮 Core 改动中修复。GTMB 的最低兼容版本与默认下载版本分别保留。新的集成可使用 README 中已分发的 `0.2.3` 示例，但不得据此替换其他消费仓已审查的版本边界。

## 公开 Wiki 核对

只读核对 MiaoWebWiki 的 `AGENTS.md`、`docs/mod-sync.md` 与 `tools/mod_sync_sources.json`：四个持久 Mod 绑定中没有 Core 专属同步绑定。GTMB 专属工作树中的 v0.3.5 文案记录其当时的源码、Core 0.2.1 默认依赖与 0.2.0 minimum，属于其独立历史验收边界，不能因本轮 Core 候选而改写。

本轮没有玩法或界面变化，不新增图片，不操作消费仓 Wiki 分支。公共 Wiki 的独立发布状态与旧截图版本不随本次 Core 分发文档修正自动推进。
