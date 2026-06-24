# GTL 针对 AE2 的 Mixin 调研

## 调研对象

调研 jar：

```text
D:\PCL\.minecraft\versions\GregTech Leisure\mods\gtlcore-1.2.2.2-fix3.jar
```

GTL Core 的 `gtlcore.mixin.json` 中，直接针对 AE2 核心包的 mixin 包括：

```text
ae2.BasicCellInventoryMixin
ae2.CPUSelectionListMixin
ae2.CreativeCellInventoryMixin
ae2.CursedInternalSlotMixin
ae2.IOPortBlockEntityMixin
ae2.MixinCowMap
ae2.MixinGenericSlotCapacities
ae2.MixinGenericStackInv
ae2.PortableCellItemMixin
ae2.TooltipsMixin
ae2.client.BuiltInModelHooksAccessor
ae2.client.ModelBakeryMixin
ae2.crafting.CraftingCalculationMixin
ae2.crafting.CraftingCpuHelperMixin
ae2.crafting.CraftingTreeNodeMixin
ae2.crafting.CraftingTreeProcessAccessor
ae2.crafting.inv.ListCraftingInventoryMixin
ae2.gui.PatternEncodingTermMenuMixin
ae2.gui.ProcessingEncodingPanelMixin
ae2.gui.StyleManagerMixin
ae2.logic.CraftingCpuLogicNewMixin
ae2.logic.CraftingCpuLogicOldMixin
ae2.logic.ExecutingCraftingJobAccessor
ae2.logic.ExecutingCraftingJobTaskProgressAccessor
ae2.logic.PatternProviderLogicMixin
ae2.service.CraftingServiceMixin
ae2.service.StorageServiceMixin
ae2.stacks.AEItemKeyMixin
ae2.stacks.AEKey2LongMapMixin
ae2.ticking.TickHandlerMixin
```

此外，GTL Core 还包含 `extendedae.*` 和 `gtm.ae.*` mixin。这些不是直接修改 AE2 核心类，但属于 AE2 生态集成，影响高级样板供应器、ME输入/输出总线、ME输入/输出仓、保持供给总线/仓等格雷机器与 AE 网络交互的行为。

## 总体结论

GTL 对 AE2 的优化不是单点优化，而是三条线并行：

1. 自动合成链路：降低合成计算和执行阶段的重复搜索、重复抽取和 tick 调度开销。
2. 大数字和大容量：让存储元件、通用槽、IO端口、创造存储元件、显示文本和 KeyCounter 更适合超大数量。
3. 整合包体验：为样板编码终端增加倍率按钮，并修正 GTM/ExtendedAE 与 AE2 交互时的容量、缓存和配置行为。

其中对“下单合成全过程”最关键的是：

- `CraftingCalculationMixin`
- `CraftingCpuHelperMixin`
- `CraftingTreeNodeMixin`
- `ListCraftingInventoryMixin`
- `CraftingCpuLogicNewMixin`
- `CraftingCpuLogicOldMixin`
- `CraftingServiceMixin`
- `PatternProviderLogicMixin`
- `StorageServiceMixin`
- `TickHandlerMixin`

## 合成计算阶段

### CraftingCalculationMixin

GTL 覆盖了 `CraftingCalculation` 的关键调度行为：

- `run()` 直接调用 `computePlan()`，然后 `logCraftingJob()`，最后 `finish()`。
- `simulateFor(int)` 简化为：只要当前计算还没完成，就返回 `true`。
- `handlePausing()` 只检查线程中断。

语义上，这削弱了 AE2 原本“按 tick 分片推进合成模拟”的节奏，转向更直接的异步计算完成标记。

影响：

- 减少模拟调度层的额外状态检查。
- 对超大合成计划更偏向“一次计算到完成”，不在原版 tick simulation 队列里反复切片。
- 风险是单次计算对后台线程占用更集中，需要依赖线程中断和外层调度控制。

复杂度变化：

```text
原版调度层：O(分片次数 * 每片调度开销 + 实际计算开销)
GTL 调度层：O(实际计算开销 + 完成标记开销)
```

注意：这只降低调度开销，不等于合成树算法本身变成账本式算法。

### CraftingCpuHelperMixin

GTL 重写了 `CraftingCpuHelper.getValidItemTemplates(...)`：

1. 遍历样板输入槽的 `possibleInputs`。
2. 对每个候选 key 调用 crafting inventory 的 `findFuzzyTemplates(...)`。
3. 对物品和流体分别做 `matches(...)` 过滤。
4. 最后再调用 `input.isValid(key, level)` 做合法性过滤。

作用：

- 把 fuzzy 候选收集集中到 crafting inventory 层。
- 配合 `ListCraftingInventoryMixin`，当精确 key 已有库存时直接返回 singleton，不再总是做 fuzzy 搜索。

复杂度变化：

```text
没有精确库存命中：O(possibleInputs * fuzzy候选数)
有精确库存命中：O(possibleInputs)
```

### CraftingTreeNodeMixin

GTL 在合成树节点上记录父级 `CraftingTreeProcess` 的 `patternDetails`，然后特判处理样板：

```text
如果父样板是 AEProcessingPattern：
  只使用 parentInput.getPossibleInputs()[0]
否则：
  走 CraftingCpuHelper.getValidItemTemplates(...)
```

作用：

- 处理样板不再为同一个输入槽展开大量 fuzzy/substitution 候选。
- 对格雷包常见的处理样板长链非常重要，因为处理样板通常不需要像工作台合成那样做大量替代匹配。

复杂度变化：

```text
处理样板原可能：O(输入槽数 * fuzzy候选数)
GTL 处理样板：O(输入槽数)
```

风险：

- 这相当于收窄处理样板输入替代语义。
- 如果某些处理样板确实依赖多个 possible input 或 fuzzy 替代，需要额外确认。

### ListCraftingInventoryMixin

GTL 重写 `findFuzzyTemplates(AEKey)`：

```text
如果精确 key 有库存：
  返回 singleton(key)
否则：
  KeyCounter.findFuzzy(template, FuzzyMode.IGNORE_ALL)
```

作用：

- 大量常见路径直接命中精确库存，跳过 fuzzy map 扫描。
- 和 `CraftingCpuHelperMixin`、`CraftingTreeNodeMixin` 配合，降低样板输入解析成本。

复杂度变化：

```text
精确命中：O(1)
精确未命中：O(fuzzy候选数)
```

## 合成执行阶段

### CraftingCpuLogicNewMixin / CraftingCpuLogicOldMixin

GTL 同时覆盖新旧两个 AE2 CPU 执行路径的 `executeCrafting(...)`。

普通 provider 仍然是原版语义：

```text
extractPatternInputs(...)
provider.pushPattern(...)
task -= 1
```

特供批量路径只在以下条件成立时触发：

```text
patternDetails instanceof AEProcessingPattern
provider instanceof IMEPatternPartMachine
```

触发后逻辑为：

```text
AEUtils.extractForMEPatternBuffer(pattern, inventory, taskProgress.value, expectedOutputs)
provider.pushPattern(pattern, batchInputs)
taskProgress = 0
```

这里的 `IMEPatternPartMachine` 对应 GTL/GTM 的 **ME样板总成** 类 provider。它不是 AE2 普通样板供应器。

作用：

- 对 **ME样板总成**，CPU 一次抽取剩余 N 次处理样板的全部输入。
- 一次调用 `pushPattern(...)`，由 **ME样板总成** 内部槽接住整批材料。
- 一次登记 N 次输出到 `waitingFor`。

复杂度变化：

```text
普通 provider：O(N * (抽取输入 + pushPattern + waitingFor登记 + 能量检查))
ME样板总成：O(一次批量抽取 + 一次pushPattern + 一次总量登记)
```

如果样板输入槽数为 `I`，输出槽数为 `O`：

```text
普通 provider：O(N * (I + O))
ME样板总成：O(I + O)
```

这是 GTL 在“实际下单合成过程”里最有价值的执行层优化之一。

### PatternProviderLogicMixin

GTL 对普通样板供应器的 `PatternProviderLogic` 只做了一件事：

```text
updatePatterns 后，从 patternInputs 中移除格雷集成电路
```

作用：

- 避免阻挡模式把格雷集成电路当成普通输入参与判定。
- 这不是普通样板供应器的一次发完优化。
- GTL 没有把普通 AE2 样板供应器改成批量 sendList 语义。

## 合成服务和 Tick 调度

### CraftingServiceMixin

GTL 注入 `CraftingService.onServerEndTick(...)`：

```text
if ((currentTick & CRAFT_MASK) != 0) cancel
```

`CRAFT_MASK` 来自配置 `ae2CraftingServiceUpdateInterval`，并取最近的 2 的幂后减一。

作用：

- 让 AE2 合成服务不是每 tick 都更新，而是按配置间隔更新。
- 对大型网络能降低合成服务常驻 tick 开销。

复杂度变化：

```text
原版：O(T * 每tick合成服务开销)
GTL：O((T / interval) * 每次合成服务开销)
```

代价：

- 合成状态刷新和任务推进可能变得更粗粒度。

### StorageServiceMixin

GTL 覆盖 `StorageService.onServerEndTick()`：

```text
如果 interestManager 为空：
  只标记 cachedStacksNeedUpdate = true
否则：
  按 ae2StorageServiceUpdateInterval 降频调用 updateCachedStacks()
```

作用：

- 降低存储缓存更新频率。
- 当没有 watcher 时不急着重算 cached stacks。
- 对大网络、大库存、高频变化很重要。

复杂度变化：

```text
原版：O(T * 缓存更新检查/刷新)
GTL：O((T / interval) * 缓存刷新 + T * 轻量标记)
```

### TickHandlerMixin

GTL 覆盖 server level end tick 的 grid tick 逻辑：

```text
readyBlockEntities(level)
for each grid:
  grid.onLevelEndTick(level)
```

并保留 crash report 包装。

同时 `registerCraftingSimulation(...)` 和 `simulateCraftingJobs(...)` 在反汇编中表现为覆盖/访问点，和 `CraftingCalculationMixin` 的简化模拟节奏配套。

作用：

- 简化 AE2 tick handler 的 server level end tick 流程。
- 合成模拟队列不再按原版方式承担主要推进职责。

## 存储容量、大数字和 IO

### BasicCellInventoryMixin

GTL 在基础存储元件构造后改写 `maxItemTypes`：

```text
maxItemTypes = cellType.getTotalTypes(stack) * ConfigHolder.cellType
```

并让 `getBytesPerType()` 返回 `0`。

作用：

- 单个存储元件可容纳的类型数按配置放大。
- 每类型字节成本归零，弱化 AE2 原本“类型占用字节”的限制。

### PortableCellItemMixin

GTL 让便携存储元件 `getBytes(stack)` 直接返回 tier bytes。

作用：

- 修正或放宽便携元件字节容量显示/计算。
- 与其大容量存储改动保持一致。

### CreativeCellInventoryMixin

GTL 让创造存储元件 `getAvailableStacks(long)` 返回：

```text
Long.MAX_VALUE
```

作用：

- 创造存储元件在可用数量查询上变成真正的近似无限。

### MixinGenericSlotCapacities

GTL 修改 AE2 通用槽容量表：

```text
items  -> Integer.MAX_VALUE
fluids -> Integer.MAX_VALUE
```

作用：

- 外部库存、配置槽或通用槽容量不再被小默认值限制。

### MixinGenericStackInv

GTL 修改 `GenericStackInv.getMaxAmount(AEKey)`：

```text
如果是 AEItemKey：
  返回 Integer.MAX_VALUE
否则：
  返回该 key type 的容量
```

作用：

- 对物品 key 直接放大最大数量。
- 配合格雷大并行和大库存交互，避免小上限造成反复拆分。

### AEKey2LongMapMixin

GTL 重写 `AEKey2LongMap.addTo(AEKey, long)`，加入 long 溢出保护：

```text
正向溢出 -> Long.MAX_VALUE
负向溢出 -> Long.MIN_VALUE
```

作用：

- 大数量加法变成饱和加法。
- 避免超大订单、创造元件或极大缓存导致 long 回绕。

### AEItemKeyMixin

GTL 缓存 `AEItemKey.getFuzzySearchMaxValue()` 的结果：

```text
第一次读取 item.getMaxDamage()
之后返回缓存值
```

作用：

- 降低 fuzzy 搜索中重复读取物品最大损伤值的开销。

### IOPortBlockEntityMixin

GTL 覆盖 IO端口的 `transferContents(...)`：

- 每次最多移动 8192 个 entry。
- EMPTY 模式下从元件向网络插入。
- FILL 模式下从网络向元件插入。
- 移动数量受 `AEKey.getAmountPerOperation()` 和目标可插入量约束。
- 使用 `StorageHelper.poweredInsert(...)` 处理能量消耗。

作用：

- 更直接地控制 IO端口批量搬运。
- 对大容量元件和大库存网络，减少单次过小移动导致的 tick 拉长。

### TooltipsMixin / CPUSelectionListMixin

GTL 将字节数、CPU 存储量等显示改成 `NumberUtils.numberText(long)`。

作用：

- 让大数字更可读。
- 不影响核心性能，但对超大容量包很必要。

## 样板编码和客户端模型

### PatternEncodingTermMenuMixin

GTL 给样板编码终端注册客户端动作：

```text
modifyPatter(Integer)
```

服务端收到后，同时修改输入和输出配置库存里的 GenericStack 数量。

支持倍率：

```text
x2, x3, x5, ÷2, ÷3, ÷5
```

除法会要求所有非空槽位数量都能整除，否则不修改。

乘法会检查结果不能超过 `Integer.MAX_VALUE`，否则不修改。

作用：

- 快速放大或缩小处理样板数量。
- 对格雷包中大量“同一配方按并行倍数编码”的体验提升很明显。

### ProcessingEncodingPanelMixin

GTL 在处理样板编码面板增加 6 个按钮：

```text
样板配方 x 2
样板配方 x 3
样板配方 x 5
样板配方 ÷ 2
样板配方 ÷ 3
样板配方 ÷ 5
```

按钮调用 `PatternEncodingTermMenuMixin` 的 `modifyPatter` 动作。

### StyleManagerMixin

GTL 替换样板编码终端相关样式文件：

```text
wireless_pattern_encoding_terminal.json
  -> /screens/wtlib/modify_wireless_pattern_encoding_terminal.json

pattern_encoding_terminal.json
  -> /screens/terminals/modify_pattern_encoding_terminal.json
```

作用：

- 给新增倍率按钮留 UI 布局。

### ModelBakeryMixin / BuiltInModelHooksAccessor

GTL 让命名空间为 `gtlcore` 的模型请求优先查 AE2 的 built-in model map。

作用：

- 支持复用 AE2 内建模型 hook。
- 属于客户端渲染兼容，不影响合成性能。

## 其他行为修正

### MixinCowMap

GTL 在 `CowMap.putIfAbsent` 前加同步检查：

```text
如果 map 已包含 key，则 cancel
```

作用：

- 避免并发下重复 putIfAbsent。
- 与通用槽容量注册等静态注册逻辑有关。

### CursedInternalSlotMixin

GTL 修改菜单点击内部 slot 时对无限存储元件的处理：

- 如果物品有 `diskuuid`，读取对应无限存储数据。
- 创建新 UUID 的物品栈。
- 把旧数据映射到新 UUID。
- 将新物品放到玩家光标/点击结果中。

作用：

- 避免无限存储元件复制、移动或菜单操作时共享错误 UUID。
- 属于 GTL 自己无限存储系统的安全修正。

## ExtendedAE 相关 Mixin

这些 mixin 不是直接修改 AE2 核心，但属于 AE2 扩展生态：

```text
extendedae.ContainerPatternModifierMixin
extendedae.InfinityCellMixin
extendedae.PartExPatternProviderMixin
extendedae.TileExIOPortMixin
extendedae.TileExPatternProviderMixin
```

从方法名和字节码看，主要方向是：

- 放大 ExtendedAE 样板供应器或样板修改器中的容器物品处理数量。
- 让 ExtendedAE 无限存储元件使用更大的数量上限。
- 调整 ExtendedAE IO端口单次移动数量。
- 调整扩展样板供应器对容器物品的处理。

这说明 GTL 不只处理 AE2 原版方块，也处理了高级样板供应器/扩展 IO 类方块在大数量环境下的上限问题。

## GTM AE 集成相关 Mixin

这些 mixin 修改的是格雷科技现代版的 AE 集成机器：

```text
gtm.ae.machine.MEInputBusPartMachineMixin
gtm.ae.machine.MEInputHatchPartMachineMixin
gtm.ae.machine.MEOutputBusPartMachineMixin
gtm.ae.machine.MEOutputHatchPartMachineMixin
gtm.ae.machine.MEStockingBusPartMachineMixin
gtm.ae.machine.MEStockingHatchPartMachineMixin
gtm.ae.slot.ExportOnlyAEItemListMixin
gtm.ae.slot.ExportOnlyAEFluidListMixin
gtm.ae.slot.ExportOnlyAEStockingItemListMixin
gtm.ae.slot.ExportOnlyAEStockingFluidListMixin
gtm.ae.slot.ExportOnlyAEItemSlotMixin
gtm.ae.slot.ExportOnlyAEStockingItemSlotMixin
gtm.ae.slot.ExportOnlyAEStockingFluidSlotMixin
```

主要方向：

- ME输入总线/ME输入仓：自动 IO 注入。
- ME输出总线/ME输出仓：实现 GTL 的 `IMEOutputPart`，增加自动输出、配置器、返回存储等行为。
- 保持供给总线/保持供给仓：增加 auto pull、配置读取、加载后刷新列表。
- ExportOnlyAE 物品/流体列表：记录 changed 状态，暴露 ME item/fluid list，用于 recipe handler 消耗或展示。
- Configurable slot：增加配置变更回调。

这些优化更偏“格雷机器如何从 AE 网络取/还物品和流体”，不直接改 AE2 的合成 CPU。但它们会影响合成全过程里的后半段：材料进入格雷机器、产物回流、ME 总线/仓刷新和保持供给行为。

## 对当前 AE2 魔改的借鉴价值

已落地背景：

1. **ME样板总成批量派发** 已经按 GTM 1.21.1 `MEPatternBufferPartMachine` 落地；普通样板供应器也已回到逐份派发语义。后续调研不再把它列为待借鉴重点，只作为 GTL 已验证路线的背景依据。

后续更值得评估：

1. **处理样板候选收窄**：对处理样板避免 fuzzy 候选爆炸，尤其适合格雷包大批处理样板。
2. **精确库存优先的 fuzzy 查找**：精确命中时 O(1)，能降低大订单 planner 的候选展开成本。
3. **StorageService / CraftingService 降频**：对大网络常驻 tick 开销帮助明显，但会改变响应粒度，需要配置化。
4. **AEKey2LongMap 饱和加法**：对超大订单和创造/无限存储很有价值，可避免 long 溢出。

中等优先级：

1. **IO端口批量搬运**：适合大容量元件环境，但和当前合成重构关系较弱。
2. **通用槽容量放大**：适合整合包大数字环境，但需要确认是否会破坏原版兼容预期。
3. **样板编码倍率按钮**：强体验优化，不是性能优化。

低优先级或仅作参考：

1. 客户端模型 hook。
2. 样式文件替换。
3. GTL 无限存储 UUID 修正。
4. GTM AE 总线/仓 mixin，除非当前新整合包也要同步魔改 GTM 的 AE 集成机器。

## 当前项目需要避免的误判

GTL 没有证明“普通 AE2 样板供应器可以安全一次发完整批输入”。GTL 的一次发完依赖的是 **ME样板总成** 自己有整批内部库存语义，并且 `isBusy()` 恒为 false。

因此当前项目的方向应继续保持：

```text
普通 AE2 样板供应器：逐份派发
GTM 1.21.1 ME样板总成：允许 CPU 批量抽取并一次 pushPattern
未来自研格雷 provider：通过显式能力或明确类语义承接批量输入
```

## 本次使用的验证命令

```powershell
& 'C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2\bin\jar.exe' tf 'D:\PCL\.minecraft\versions\GregTech Leisure\mods\gtlcore-1.2.2.2-fix3.jar' | Select-String -Pattern '^org/gtlcore/gtlcore/mixin/ae2/.+\.class$'
```

结果：列出 31 个 AE2 核心相关 mixin class。

```powershell
& 'C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2\bin\javap.exe' -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicNewMixin
```

结果：确认普通 provider 逐份执行，只有 `AEProcessingPattern && provider instanceof IMEPatternPartMachine` 时批量抽取剩余 task 次数。

```powershell
& 'C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2\bin\javap.exe' -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.service.StorageServiceMixin
```

结果：确认存储缓存更新按 `ae2StorageServiceUpdateInterval` 降频。

```powershell
& 'C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2\bin\javap.exe' -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.gui.ProcessingEncodingPanelMixin
```

结果：确认样板编码终端新增 x2/x3/x5/÷2/÷3/÷5 六个倍率按钮。
