# GTNH 2.9.0–2.9.2 兼容交付

机制与限制见 [三版兼容边界](../sum/gtnh-290-292-compatibility.md)。本次仅静态编译与关键合同验收，不启动客户端或服务端；没有三整包、真实 Mixin 或 GPU 通过结论。

三套隔离 API 上的同一现代 Core 生产类均通过 623 处成员链接、25 个 Mixin 选择器合同。构建日志、依赖身份、最终源提交、普通/dev/sources SHA 与目标替换备份统一位于 `E:/CodeGT/.artifacts/gtnh292-compat/`。

全库 Spotless 初检发现原 HEAD 的二维码第三方源等既有格式差异；本次不引入这些无关重排，以绝对路径 `spotlessIdeHook` 将格式校验限制到本次 Java 修改。Gradle 使用 `--no-configuration-cache`，不修改既有许可脚本缓存规则。无 GL Tooltip 夹具按新上游字体重载修正后，原生分页/固定控件合同通过。

远端以核定 `origin/master` 为 PR base，不创建本地分支。没有新 Tag 授权；没有可确认的本任务现有 Release 草稿或对应最终 Tag 时只准备正文和附件，不创建 Release，不正式发布、不发布 Maven、不部署站点。
