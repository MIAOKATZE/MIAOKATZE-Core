# 物品展示顺序（0.1.10）

`api.items.ItemOrderCatalog` 只提供共享展示能力。业务领域分组、物品注册、MTE ID 和存档兼容仍由各 Mod 自己维护。

```java
ItemOrderCatalog.register("example", ExampleCreativeTab::getRawItems);
// 创意目录已完整登记之后，提供按领域分组、稳定序号排列的全部物品。
// 原始 supplier 不得回调 orderedItems/sort，以免递归。
List<ItemStack> creativeItems = ItemOrderCatalog.orderedItems("example");
```

`register(owner, Supplier<List<ItemStack>>)` 懒读取目录；同 owner 再注册会替换 supplier 并清除缓存。登记列表增长后调用 `invalidate(owner)`（或无参 `invalidate()`）。配置重载也会自动失效。初始化顺序建议是静态登记 supplier，每次添加条目后失效，supplier 从 Mod 自己的原始登记表取快照。Core 不认识业务分组，也不查其他 Mod 的物品表。

`orderedItems(owner)` 返回去重后的独立 stack 副本；不会添加未登记项。`sort(owner, list)` 只重排传入条目，已知项在前且按目录顺序，未知项稳定留在后面，不增删条目、不改 stack。

归属键是 Item 对象身份加精确 damage。GT 的共享 `ItemMachines` 中不同 MTE 元数据可以归属于不同 Mod；未声明的元数据仍属原生 GT。NBT 和数量不参与归属，NBT、注册名、ID、序列化数据不会被修改。一个变体同时被不同 owner 声明会在构造快照时明确抛出异常；同 owner 内的重复项保留首条。

Core 在客户端存在 `NotEnoughItems` 时自动安装适配器。消费 Mod 无需注册 NEI 事件监听器或复制比较器。NEI 仅为 compileOnly，公共目录 API 和通用 Mod 入口可在无 NEI 的专服加载。

适配器保留 NEI 的选项名称及排序优先级，只替换 minecraft、mod、id 三层。minecraft/mod 使用精确变体归属，避免 GT MTE 被分散在 gregtech 组。id 使用统一元组 `(首项实时物品ID, 目录项优先, owner, 目录rank)`。固定首项的 ID 每次直接从注册表读取，切换存档或服务器重映射不缓存旧数字 ID；其余目录归属和 rank 使用不可变快照，每次比较为 O(1)。default、damage、name 保留上游行为；用户将 name 提到前面仍按名称优先。

`tools/tests/ItemOrderingRegression.java` 是独立的有界回归入口。使用带 Minecraft/FML、GT5U 5.09.54.205、NEI 2.8.145 官方 dev jar 的 GTNH RC2 开发类路径，编译生产目录 API/适配器及测试，执行 `ItemOrderingRegression` 和 `ItemOrderingRegression --without-nei`。后者在隔离 LaunchClassLoader 中确实移除所有 NotEnoughItems jar。测试覆盖多 Mod、实际 GT ItemMachines 的元数据归属、随机 ID 重映射、比较器传递性、配置重载、名称优先、重复登记、缓存失效、外部创意项和无 NEI 类加载。
