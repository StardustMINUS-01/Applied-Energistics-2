# GTL ME样板总成批量派发调研

## 结论

GTL 的“一次发完”优化没有应用到 AE2 普通样板供应器。

在 `gtlcore-1.2.2.2-fix3.jar` 中，`CraftingCpuLogicNewMixin` 的批量分支只在以下条件同时成立时触发：

```text
patternDetails instanceof AEProcessingPattern
provider instanceof IMEPatternPartMachine
```

`IMEPatternPartMachine` 的实际代表是 GTL/格雷科技现代版的 **ME样板总成**，例如：

```text
block.gtceu.me_mini_pattern_buffer    -> 小型ME样板总成
block.gtceu.me_extend_pattern_buffer  -> 扩展ME样板总成
block.gtceu.me_final_pattern_buffer   -> 终极ME样板总成
```

因此，GTL 的优化是格雷特供 provider 优化，不是普通 AE2 样板供应器优化。

## GTL 的实际执行逻辑

GTL 在 CPU 执行阶段遍历合成 task。对普通 provider，它仍走原版逐份抽取和逐份 `pushPattern`：

```text
extractPatternInputs(...)
provider.pushPattern(...)
taskProgress -= 1
```

只有当 provider 是 `IMEPatternPartMachine` 时，才走批量抽取：

```text
AEUtils.extractForMEPatternBuffer(pattern, inventory, taskProgress.value, expectedOutputs)
provider.pushPattern(pattern, batchInputs)
taskProgress = 0
```

`AEUtils.extractForMEPatternBuffer` 对每个输入槽按 `input.getMultiplier() * taskTimes` 抽取材料，并按 `output.amount() * taskTimes` 一次登记预期输出。

## ME样板总成为什么能这么做

`MEPatternBufferPartMachine` 本身实现 `ICraftingProvider` 和 `IMEPatternPartMachine`。它的 `pushPattern` 不走普通样板供应器的外部库存派发语义，而是：

1. 确认 ME 网络在线。
2. 确认当前 provider 有该样板槽。
3. 确认输入 key 只包含物品或流体。
4. 找到样板对应的内部槽。
5. 把整批输入写入该内部槽的物品/流体 map。

对应的 `InternalSlot.pushPattern` 调用 `AEUtils.pushInputsToMEPatternBufferInventory`，最终只是把输入累加到内部库存。后续由格雷机器和 ME样板总成自己的 recipe handler 慢慢消费这些内部材料。

这和 AE2 普通样板供应器完全不同。普通样板供应器的语义是把输入推给相邻外部库存或专用合成机器，并用 `sendList` 保存暂时没推出去的材料；`sendList` 非空时 provider 对 CPU 表示 busy。

## PatternProviderLogicMixin 的作用

GTL 的 `PatternProviderLogicMixin` 没有实现一次发完。

它只在普通样板供应器更新样板输入集合之后，把格雷集成电路从阻挡模式检查集合中移除。作用是避免阻挡模式把集成电路当作普通输入，导致格雷样板供应器误判目标库存已包含样板输入。

这和批量派发无关。

## 对当前 AE2 修改的含义

当前项目已经新增了 `IBulkCraftingProvider`，并让 CPU 在 provider 实现该接口且样板支持 `supportsPushInputsToExternalInventory()` 时尝试批量派发。

其中普通 `PatternProviderLogic` 当前也实现了 `IBulkCraftingProvider`，但为了通过现有 GameTest，只在以下条件下接受批量：

```text
非阻挡模式
只有一个外部目标
```

这个实现与 GTL 的真实优化路线并不完全一致。GTL 的做法更接近：

```text
普通 AE2 样板供应器：保持逐份派发
格雷/ME样板总成类 provider：自己实现专用批量承接能力
```

## 后续可能回退的内容

如果后续决定严格贴近 GTL 的语义，建议回退普通 AE2 样板供应器上的批量实现：

1. 从 `PatternProviderLogic` 移除 `IBulkCraftingProvider` 实现。
2. 删除或禁用 `PatternProviderLogic.pushPatternBatchToExternalInventory(...)`。
3. 保留 `IBulkCraftingProvider` 接口和 CPU 侧能力判断，让第三方或未来的格雷样板供应器、ME样板总成类 provider 自行实现。
4. 调整测试：普通样板供应器不再断言批量成功，只断言会安全回退到逐份派发；批量成功测试改用测试专用 provider 或未来格雷 provider stub。

这样可以避免把普通样板供应器的 `sendList`、阻挡模式、多面轮询语义强行合并进一次发完优化里。

## 当前建议

短期内，普通样板供应器的批量实现应视作实验性优化。它已经被限制到较窄场景，但不是 GTL 已验证的路径。

更稳的方向是保留 CPU 侧能力接口，把批量派发定义为“provider 自己能安全承接整批输入”的能力；普通 AE2 样板供应器默认逐份派发，格雷类 provider 再实现整批内部账本语义。

## GTM 1.21.1 源码确认与当前落地

用户提供的 GTM 1.21.1 源码目录为：

```text
E:\MC stuff\GregTech-Modern-1.21
```

已确认当前 GTM 1.21.1 和前面反编译的 GTL 包存在一个关键接口差异：

```text
GTL: provider instanceof org.gtlcore.gtlcore.api.machine.trait.IMEPatternPartMachine
GTM 1.21.1: com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine 直接实现 ICraftingProvider
```

当前落地目标已经进一步收窄：这不是 GTL 兼容层，而是给新整合包使用的 GTM 1.21.1 **ME样板总成** 特判。因此，运行时识别不再匹配 GTL 的 `IMEPatternPartMachine` 接口，也不匹配 GTLCore 的 `MEPatternBufferPartMachine` 类名。

GTM 1.21.1 的 `MEPatternBufferPartMachine` 逻辑如下：

1. 方块中文名为 **ME样板总成**，镜像为 **ME样板总成镜像**。
2. `MEPatternBufferPartMachine` 实现 `ICraftingProvider` 和 `PatternContainer`。
3. `MEPatternBufferProxyPartMachine` 不实现 `ICraftingProvider`；它只是连接/共享原始 **ME样板总成**。
4. `pushPattern(IPatternDetails, KeyCounter[])` 会检查结构是否成型、ME 节点是否活跃、当前总成是否包含该样板，以及输入是否只包含物品/流体。
5. 通过检查后，`pushPattern` 找到样板对应的内部槽，并调用 `InternalSlot.pushPattern(...)`。
6. `InternalSlot.pushPattern(...)` 调用 `patternDetails.pushInputsToExternalInventory(inputHolder, this::add)`，把整批输入累加进内部物品/流体库存。
7. `isBusy()` 恒为 `false`，因此 **ME样板总成** 能由自身内部库存/配方处理器承接连续大批量输入。

因此，当前 AE2 侧实现采用无硬依赖识别：

```text
如果 patternDetails 是 AEProcessingPattern
并且 provider 的类或父类匹配 GTM 1.21.1 的 ME样板总成类型
则 CPU 一次抽取 task 剩余 N 次输入
然后直接调用 provider.pushPattern(patternDetails, batchInputs)
成功后一次登记 N 次预期输出，并把该 task 清零
```

已识别的类型名只包括：

```text
com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine
```

这样 AE2 不需要在编译期依赖 GTM，但运行时能识别新整合包里的 GTM **ME样板总成**。只实现 GTL `IMEPatternPartMachine` 的 provider 会回到逐份派发，除非它另外显式实现 `IBulkCraftingProvider`。

同时，普通 AE2 `PatternProviderLogic` 已回退为不实现批量能力接口。普通样板供应器继续走逐份 `extractPatternInputs(...)`、`pushPattern(...)` 和 task 递减 1；批量派发只留给明确能承接整批输入的专用 provider，或者未来自行实现 `IBulkCraftingProvider` 的第三方 provider。
