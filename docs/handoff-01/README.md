# 01 线公共动画合同交接

执行基线为 `f02dbdc2f4e9891b616bfd2c48b17f28305e44f0`，用户已明确选择 GTNH 2.9.0 正式版，覆盖任务包默认 RC 要求。`RELEASE_VERSION=0.2.0` 保持不变，不表示新增合同已经发布。

- [G0 输入记录](G0-INPUT-RECORD.json)
- [F-CORE-01 冻结、SDK 身份及实际验证](DEPENDENCY-FREEZE.md)
- [文件 owner](OWNER-MAP.md)
- [旧消费者与兼容边界](COMPATIBILITY.md)
- [第七阶段验收清单](STAGE07-CASES.md)
- [标量协议](../animation-contract.md)
- [运行时合同](../animation-runtime.md)

本线不创建分支，不启动实例，不修改其他 Mod 的源码、玩法或存档。提交保留于 detached worktree；Tag、发布版本、clean release JAR、A-Jar、push/PR 与 Release 统一留给第七阶段。

## 重现轻量验证

使用真实 JDK 8，输出到尚不存在的目录：

```powershell
python tools/run-animation-contracts.py --jdk-home 'C:\Users\60158\.gradle\jdks\azul_systems__inc_-8-amd64-windows.2' --output build/animation-sdk
```

脚本只编译实际生产动画源码及已有纯 MiaoVision 模型，并生成一份 SDK；合同测试与两个独立消费示例都以该 SDK 为唯一 Core classpath。它不下载依赖、不生成 Minecraft stub、不运行游戏。每条命令、日志 hash、源码 hash、SDK hash、全部 public class 签名和 Java 8 major 52 检查保存在输出目录。

常规项目验证另有 `runAnimationContracts`、`runAnimationRuntimeContracts`，沿用现有 JavaExec 合同方式并挂入 `check`。轻量脚本的成功不代表完整 Gradle、Forge、专服、OpenGL、音频或消费者生产仓已通过。

本次命令退出码为 0，13679 项动画断言、4212 项运行时断言和两个独立消费者示例通过。受测源码 hash 与交付前工作树一致；133 个基线 `src/main` 文件以及依赖、发布版本、settings 保持原样。实际证据见 [冻结单](DEPENDENCY-FREEZE.md)。
