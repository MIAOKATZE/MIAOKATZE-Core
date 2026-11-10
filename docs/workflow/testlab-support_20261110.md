# 2026-11-10 Core Testlab 支持实施记录

公共 SDK 从 0.2.8/1f9da904 实施，冻结为 0.2.9/134521a2b79bc39136f3d7b6e1877a162830d84e、Tag v0.2.9。原有 97 项工作区状态由总协调核对、隔离并恢复。本次文档维护不改其内容，也不自行提交或移动 Tag。

## 实施与必要修正

新增被动公共 API、薄反射 bootstrap、src/testlab 独立 Driver、OP2 命令、UUID/JSON 原子回执及精确测试世界策略；模块保留目录、业务执行、真实资源权限、原报告和恢复。生产 Deadline 保留在普通包。

完整测试替换包复用普通 Jar CopySpec，保留 Tags、Mixin 和 refmap。对比发现正常 reobf 的额外 Mixin SRG 未自动进入自定义任务，补继承后 330 个对应生产条目字节一致。测试变体与普通包同 modid，不可并装；Maven classifier 为 testlab-dev/testlab，版本坐标固定 0.2.9。

纯 JVM 没有 Forge launch transformer；合同核对真实 @Cancelable 注解，仅补它在游戏中生成的 isCancelable 方法，继续执行真实事件处理。WorldServerMulti 可能共享 WorldInfo，故只清局部 strength；setter 又是 client-only，改用缓存字段反射。合同确认 foreign 世界及 HARD 保留。

后补 eventStats 三计数，明确 256 项 ring 的截断；补 owned/启用世界 hostile join 拒绝和旧 IMob 清理，显式 fixture scope 写标记且异常释放。首轮完整构建的功能合同已过，但通配符导入及 processResources 配置缓存检查失败；分别修正后，总协调 clean build/check 和 publishToMavenLocal 通过。

## 实际运行暴露的身份兼容问题

后续真实 RFB/LaunchWrapper 服务器链路返回 jar:file:/.../X.jar!/Class.class 形状的 CodeSource；原 capture 只接受 file 根，身份 unknown，host 正确拒绝。授权后只改 ArtifactIdentity 与对应合同：JarURLConnection 解析本地 file 容器，保留原 URL，不读 HTTP、不猜文件名。自定义 URLClassLoader 真实定义类的 ProtectionDomain 使用该 jar entry URL，验证中文、空格、百分号、感叹号路径的实际 SHA 和 manifest；远端容器直接保留 unknown，不连接。

定向 runTestlabContracts/checkstyleMain/checkstyleTest 通过，随后增量 reobfTestlabJar/publishToMavenLocal 成功。330 个生产条目仍与完整变体一致。该候选是原 Tag 之后的局部修正，不将原 clean 制品身份沿用到新候选；提交、后续正式身份与真实实例复测由总协调管理。

## 证据与范围

证据根：E:/CodeGT/.artifacts/testlab-system-optimization/core/。原阶段为 final-targeted-contracts.log、checkstyle-fix.log、build-clean-publish.utf8.log、delivery.json；身份修正为 jar-entry-identity-contracts.log、jar-entry-identity-publish.log、jar-entry-identity-candidate.json。

本次文档写入曾因 PowerShell 向 Python 管道使用非 UTF-8 编码而将新增中文替成问号，已改为 .NET UTF-8 直接写入并复核中文字符及相对链接。原有文档内容、README 与原 97 项不借此清理。

离线事件使用惰性 World/Entity；实际实例发现了身份缺口，不表示修正已获真实机器、GUI/GL、网络、桌面隔离或完整生存验收。维护合同见[摘要](../sum/testlab-support.md)。
