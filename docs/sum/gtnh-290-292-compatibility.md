# GTNH 2.9.0–2.9.2 兼容边界

Core 0.2.31 的生产编译默认使用 GTNH 2.9.2：GT5U 5.09.54.277、BQ 3.8.94、MUI2 2.3.97、GTNHLib 0.11.58、NEI 2.8.176、Angelica 2.2.46、ChromaticTooltips 1.0.38。StructureLib 仍为 1.4.47；客户端可选依赖保持 compileOnly，不打入 Core。公开 Core API、业务所有权与现有消费者的最低版本声明不因编译基线变化而统一抬升。

`dependencies.gradle` 将旧环境放在互不继承的 `gtnh290Api`、`gtnh291Api`、`gtnh292Api` 配置。版本取自 DreamAssemblerXXL 固定提交 `1a90314544e07122bdecfc48226a5091ad242750` 的三份正式 manifest；旧图不进入生产 compileClasspath。原 `coreMuiVersion/coreLibVersion/coreAngelicaVersion` 显式覆盖参数仅供定向开发，默认验收使用固定现代图。

`runGtnh290Contracts`、`runGtnh291Contracts`、`runGtnh292Contracts` 用同一份现代编译的生产类，对每套原始 dev API 检查实际调用、字段描述符与静态性，以及相应 Mixin 方法选择器。受审范围为 GT/BW、BQ、MUI2、GTNHLib、NEI、Angelica 和 ChromaticTooltips；不初始化游戏或 GL。缺少受审族的类型不会回退到现代 classpath。继承解析保留父类与接口查找。

三版均核对 623 处外部成员链接、25 个 Mixin 方法选择器。静态合同不证明注入实际应用、所有注入位置、GPU 输出或三整包运行。已有 BQ 数据库保护、Tooltip 固定与 Angelica program 删除行为继续由专属合同覆盖；StructureLib 的 1.4.47 精确版本门保持。未新增版本号驱动的业务热路径和通用版本框架。

ChromaticTooltips 1.0.38 的分页改为调用 `TooltipFontContext.pushContext(TooltipContext)`，原无 GL 夹具仅覆盖零参方法，因而触碰未启动的 Minecraft 字体环境。本次测试替身同时覆盖两个重载，保留真实 SectionComponent 分页、生产头部组装与所有顺序断言；生产 Tooltip 路径未因夹具修正而增加回退。

本次不创建、移动或推送 Tag。普通运行包须保留真实 Git 派生版本及源提交；无匹配 Tag 时不能称为 clean Tag 包。消费者使用本轮 Core 构建物做编译集成，本地文件夹具不构成 Maven 发布。
