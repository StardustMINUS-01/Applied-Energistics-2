# Crafting V2 迁移设计

## 目标

当前 AE2 1.21.1 的自动合成计划器以递归树模拟为核心。它会先尝试完整订单；如果 `CRAFT_LESS` 策略下完整订单失败，就用不同数量反复重跑整棵合成树。遇到多路径合成时，还可能退化成一份一份地尝试分支。大订单下，这套算法开销很容易被放大。

Crafting V2 要把这套模型替换成参考 GTNH AE2 的账本式请求系统：

- 每个请求栈都有一份账本。
- resolver 尝试满足请求中尚未满足的部分。
- pattern task 批量创建子请求。
- 子请求解析完成后，父 task 再计算自己实际能完成多少。
- 过量请求的工作通过 partial/full refund 回退。
- 最终计划保留 task tree，后续可直接驱动 CPU 执行。

这次迁移是深度魔改：最终目标不只是更快的 planner，而是让 CPU 执行层也能消费 V2 task tree。

## 非目标

- 不直接复制 GTNH 的类名、方法名或具体实现细节。
- Crafting V2 只作为迁移项目名和阶段名保留；新增实现类和方法不使用 `V2` 后缀，而使用账本语义或职责语义命名。
- 除非没有兼容路径，否则不改公共 API 签名。
- 不重写无关的存储、网络包或 GUI 系统。
- 第一轮不激进优化复杂 crafting pattern。先保正确性，再谈性能。

## 需要保留的现有边界

迁移期间，当前外部调用形态应尽量保持不变：

- `ICraftingService.beginCraftingCalculation(...)` 仍返回 `Future<ICraftingPlan>`。
- `ICraftingPlan` 仍是菜单、CPU 选择和提交检查使用的公共计划结果。
- `CraftingService.submitJob(...)` 仍是提交调度入口。
- `CraftingPlanSummary.fromJob(...)` 应继续能处理账本式 plan。

账本式 plan 内部可以额外暴露包内或实现类专用访问器，供第二阶段的 task-tree 执行使用。

## 核心概念

### 请求账本

一个 request 表示一笔栈需求。它记录：

- 请求的 `AEKey` 和数量；
- 尚未解析的剩余数量；
- 父请求链，用于递归检查；
- 已经满足本请求一部分的 resolver 记录；
- 是否使用过缺失物品模拟；
- 字节成本和执行成本；
- 请求是否不完整。

核心不变量：

```text
请求数量 = 已解析数量 + 尚未解析数量
```

当子请求被过量创建时，refund 可以减少请求数量并回退已解析工作。

### Resolver Task

resolver task 是“满足一个请求的一种方式”。初始 resolver 类型包括：

- 从副产物库存提取。
- 从初始网络模型提取。
- 通过 crafting emitter 发射。
- 通过 pattern 合成。
- 为不完整计划模拟缺失物品。
- 如果需要，为部分/自动化模式忽略缺失物品。

resolver 有优先级。通常先提取已有物品，再尝试合成，最后才做缺失模拟。

### Crafting Context

context 是一次计算的工作状态集合，包含：

- 初始网络内容的只读或复制视图；
- 可变的工作库存模型；
- 独立的副产物库存；
- pattern 查询缓存；
- fuzzy/substitution 缓存；
- 待处理 task 队列；
- 已完成 task 列表；
- 计算时间切片和步数限制。

副产物库存必须和初始网络库存分开。这样后续 recipe 消耗副产物时，不会导致 CPU 初始化阶段错误地要求从网络里抽取这些副产物。

### Pattern Task

pattern task 表示“使用一个 pattern 满足请求”。它分两段工作：

1. 如果输入尚未请求，先计算批量合成次数，创建输入子请求，并把这些子请求交给 context。
2. 子请求解析完成后，根据子请求实际满足的输入量计算 `maxCraftable`，满足父请求，注入副产物和余数，并 refund 过量请求的子工作。

普通非复杂 pattern 应批量创建子请求。复杂 crafting pattern 第一版可以保守地按每次合成展开，以保留精确的容器物品和余数行为。

### Refund

refund 是账本模型安全工作的关键：

- `partialRefund(amount)` 回退一部分已解析工作，并把物品或 task 次数归还到 context。
- `fullRefund()` 取消一个 task 做过的所有工作，让 context 回到这个 task 未执行前的效果。
- `refundUnresolved(amount)` 只减少尚未被任何 resolver 满足的需求。

任何能满足请求的 resolver，都必须知道如何撤销自己满足的那部分。

### 账本式 Plan

`LedgerCraftingPlan` 应实现 `ICraftingPlan`，并提供当前所有公共计划字段：

- 最终输出；
- 字节数；
- 是否为模拟计划；
- 是否有多路径；
- 初始需要从网络抽取的物品；
- emitter 需要发射的物品；
- 缺失物品；
- pattern 执行次数。

同时，`LedgerCraftingPlan` 内部保存已解析的 task tree，或一个拓扑排序后的 task 列表，供第二阶段 CPU 执行使用。

## Planner 数据流

```text
beginCraftingCalculation
  -> 创建 CraftingPlanningContext
  -> 创建根 CraftingRequest
  -> 为根请求入队 resolver
  -> 在时间切片内处理 task 队列
  -> 构建 LedgerCraftingPlan
  -> 暴露兼容 ICraftingPlan 的字段
```

planner 不应再对 `CRAFT_LESS` 做二分重算。部分成功应由请求账本自然产生：请求能解析多少算多少，多拿的部分 refund，最后报告实际解析出的数量。

## CPU 执行数据流

当前生产路径已经由账本式 planner 和账本式 executor 接管。`LedgerCraftingPlan` 仍填充 `patternTimes`、`usedItems` 和 `emittedItems` 等公共字段，用于菜单、摘要和兼容检查；CPU 提交层在看到 `LedgerCraftingPlan` 时直接创建 `ExecutingLedgerCraftingJob`，按计划中的 task 列表执行。

```text
submitJob(LedgerCraftingPlan)
  -> 抽取初始 usedItems
  -> 根据 task tree 创建 ExecutingLedgerCraftingJob
  -> 每 tick 选择 ready executable task
  -> 向 provider 推送 pattern 输入
  -> 等待预期输出和容器物品
  -> 解锁依赖它的后续 task
  -> 最终输出交付后完成 job
```

账本式 executor 保留 CPU 操作吞吐限制，并在此基础上加入了 provider busy 预筛、cursor 轮转、同 provider 重复 push、输入阻塞短路和相邻重复 pattern task 合并。`patternTimes` 不再驱动账本式执行，只作为公共 API 与 UI 兼容数据保留。

## 兼容策略

### 公共 API 兼容

外部 API 仍以 `ICraftingPlan` 为边界。账本式 plan 填充聚合字段，让现有菜单、摘要和提交检查继续工作：

- `CraftingCpuHelper.tryExtractInitialItems` 使用 `usedItems`。
- `CraftingPlanSummary` 使用同一组公共字段。
- `patternTimes` 继续暴露给需要聚合视图的消费方，但账本式执行不依赖它。

### 执行兼容

`CraftingCpuLogic.trySubmitJob` 检测 `LedgerCraftingPlan` 并创建 `ExecutingLedgerCraftingJob`。非账本式 `ICraftingPlan` 仍可走旧 `ExecutingCraftingJob` 兼容路径，但当前 `CraftingCalculation` 生产入口只产出账本式计划。

### 旧路径清理

旧树 planner 的运行入口和源码本体已经移除；历史系统属性 `appeng.crafting.legacyPlanner` 不再影响生产 planner 选择，只在测试中保留为“被忽略”的兼容断言。后续清理重点应放在更细的 profiling、压力基准和真实存档长单验证上。

## 错误处理

- planner 异常应干净地结束计算；能生成不完整模拟计划时，尽量返回不完整模拟计划。
- 步数限制应阻止恶意或失控的大计算。
- 递归 pattern 应通过父 pattern 追踪拒绝，或转换成净输入/净输出形式。
- 提交时真实抽取失败必须返回 missing ingredient 结果，不能丢物品。
- 账本式执行状态必须持续保持可持久化，跨保存/读取后不能丢失 task、等待输出、进度和 profiling 统计。

## 测试要求

planner 测试应覆盖：

- 大型单路径非复杂订单；
- 大型多路径非复杂订单；
- 不再通过二分重算实现的 `CRAFT_LESS`；
- 副产物复用；
- 容器物品复用；
- 输入和输出递归的 pattern；
- fuzzy 替代输入；
- 缺失物品模拟；
- resolver 失败后的路径回退；
- 字节成本和 `patternTimes` 兼容性。

execution 测试应覆盖：

- 账本式 plan 提交；
- 初始物品抽取；
- pattern task 执行；
- 预期输出等待；
- 最终输出交付；
- 取消和 refund 行为；
- NBT 保存/读取恢复。

## 性能预期

对于普通非复杂 pattern，planning 应主要随依赖图规模、resolver 数量和 fuzzy 候选数量增长，而不是直接随请求数量增长。

目标复杂度形态：

```text
O(请求节点数 + pattern 边数 + resolver 候选数 + fuzzy 候选数 + refund 成本)
```

复杂 crafting pattern 第一版可以仍按请求数量展开：

```text
O(请求数量 * 复杂 recipe 成本)
```

这是第一轮迁移可以接受的保守选择，因为它优先保留精确行为。
