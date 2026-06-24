# GTNH AE2 自动合成全链路调研

## 1. 调研范围

本文对比两个实现：

- GTNH AE2 参考实现：`Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2` 及其接入的 `CraftingGridCache`、`CraftingCPUCluster`、确认界面和网络包。
- 当前 AE2 1.21.1 已魔改实现：`CraftingCalculation`、`appeng.crafting.ledger`、`CraftingCpuLogic`、`ExecutingLedgerCraftingJob`、`CraftingService`、确认菜单和计划摘要。

范围不是只看下单计算，而是覆盖完整流程：

```text
玩家或机器发起请求
  -> 异步计算合成计划
  -> 确认界面展示 stored/crafting/missing
  -> 选择或自动选择 CPU
  -> 提交 job，抽取初始物品
  -> CPU tick 推送 pattern
  -> 等待 expected output / container item / emitted item
  -> 完成、取消、持久化和 UI 状态更新
```

## 2. GTNH AE2 的全链路逻辑

### 2.1 发起计算

GTNH 的 `CraftingGridCache.beginCraftingJob(...)` 直接创建 `CraftingJobV2` 并调用 `schedule()`。

关键文件：

- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/me/cache/CraftingGridCache.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingJobV2.java`

流程：

```text
beginCraftingJob(...)
  -> new CraftingJobV2(...)
  -> new CraftingContext(...)
  -> new CraftingRequest(root, PRECISE_FRESH, allowSimulation=true)
  -> context.addRequest(root)
  -> job.schedule()
  -> TickHandler.registerCraftingSimulation(...)
```

`CraftingJobV2.simulateFor(milli)` 是分片执行入口。它反复调用 `CraftingContext.doWork()`，直到任务队列完成、时间片耗尽或出错。

### 2.2 请求账本

GTNH 的 `CraftingRequest` 是每个请求节点的账本，包含：

- 请求栈 `stack`；
- `remainingToProcess`；
- `usedResolvers`；
- 父请求集合 `parentRequests`；
- 父 pattern 集合 `patternParents`，用于防递归；
- `substitutionMode`、`acceptableSubstituteFn`、`substitutionGroupSize`；
- `craftingMode`、`allowSimulation`、`wasSimulated`、`incomplete`；
- byte cost。

它提供三类回退：

- `partialRefund(context, amount)`：回退已解析工作，并让 resolver 归还库存、副产物、模拟项等。
- `refundUnresolved(amount)`：减少尚未解析的过量请求。
- `fullRefund(context)`：撤销整个请求和子树。

这比当前实现更完整，尤其在 fuzzy 替代、复杂配方、父链回退和 GUI 树序列化方面状态更多。

### 2.3 Resolver 和任务队列

GTNH 通过 `CraftingCalculations` 维护 resolver 列表：

```text
ExtractItemResolver
SimulateMissingItemResolver
EmitableItemResolver
CraftableItemResolver
IgnoreMissingItemResolver
```

每个 resolver 产出一个或多个 `CraftingTask`。task 有：

- `calculateOneStep(context)`；
- `partialRefund(context, amount)`；
- `fullRefund(context)`；
- `populatePlan(plan)`；
- `startOnCpu(context, cpuCluster, craftingInv)`；
- priority；
- `isSimulated()`。

`CraftingContext.addRequest()` 会给请求生成 resolver task，按优先级排序，然后压入 `tasksToProcess`。`CraftingContext.doWork()` 每次处理队头 task。若 task 返回新的子请求，context 会把子请求加入队列；若 task 成功，则进入 `resolvedTasks`。

这个模型的优点是 resolver 插件化强、状态可序列化、可以分片计算；代价是对象和队列管理开销较高。

### 2.4 Pattern task

GTNH 的 `CraftableItemResolver.CraftFromPatternTask` 是核心。

第一步：

```text
根据 request.remainingToProcess 和 matchingOutput 计算 toCraft
  -> 对普通 pattern 批量创建输入子请求
  -> 对复杂 crafting pattern 按 1 次 craft 展开
  -> 对输入=输出的递归 pattern 做净输入/净输出拆分
  -> 返回 StepOutput(extraInputsRequired)
```

第二步：

```text
子请求解析回来
  -> 计算每个输入实际能支持多少 craft
  -> maxCraftable = min(所有输入可支持次数)
  -> fulfill 父请求
  -> 注入副产物和输出余数
  -> 对 over-request 子请求做 refundUnresolved / partialRefund / fullRefund
```

GTNH 的 fallback 很谨慎：`shouldAddSimulateMissingExtraCondition(...)` 会检查当前请求和父请求是否还有非模拟 resolver，只有在上层路径基本耗尽时才添加模拟缺失的 fallback，避免过早模拟 missing 阻断其他真实路径。

### 2.5 确认界面和计划展示

GTNH `ContainerCraftConfirm` 调用 `result.populatePlan(plan)`，由每个 resolved task 自己填充计划列表：

- extraction task 填 stored；
- pattern task 填 requestable/craft count；
- missing/emitter task 填对应模拟或可发射项。

如果 result 是 `CraftingJobV2`，GTNH 会额外通过 `PacketCraftingTreeData` 把整棵 crafting tree 压缩、分片发送给客户端。客户端可以反序列化 tree，用于更细的树状展示和调试。

这一点当前实现还没有完全等价：当前 `CraftingPlanSummary` 主要基于 `usedItems`、`missingItems`、`emittedItems` 和 `patternTimes` 做聚合摘要，没有把完整 task tree 发到确认界面。

### 2.6 提交 CPU

GTNH `CraftingGridCache.submitJob(...)` 会：

- 拒绝 simulation job；
- 如果用户没有指定 CPU，扫描可用 CPU；
- 可以把玩家/独立任务合并进同类型输出的 busy CPU；
- 按协处理器、存储、名字排序；
- 调用 `CraftingCPUCluster.submitJob(...)`。

`CraftingCPUCluster.submitJob(...)` 会：

```text
检查 CPU 是否空闲/容量足够/支持 job
  -> 创建 MECraftingInventory，连接 CPU inventory
  -> job.startCrafting(ci, cpu, src)
  -> ci.commit(src) 抽取初始物品
  -> 写 finalOutput、usedStorage、links
  -> waitingForMissing 并入 waitingFor
  -> 更新 CPU 和监听器
```

`CraftingJobV2.startCrafting(...)` 会遍历 `context.getResolvedTasks()`，调用每个 task 的 `startOnCpu(...)`：

- extraction task 从网络/CPU 初始化库存抽取。
- emit task 写入 `waitingForMissing`。
- pattern task 调用 `cpuCluster.addCrafting(pattern, totalCraftsDone)`。

因此 GTNH 的 V2 在计划阶段保留 task tree，但进入 CPU 执行后，主要仍落回聚合结构：

```text
Map<ICraftingPatternDetails, TaskProgress>
waitingFor
waitingForMissing
inventory
```

也就是说，GTNH V2 的 CPU 执行层不是严格按 task tree 拓扑逐 task 调度，而是把 pattern 执行次数聚合到 CPU 的旧任务表里。

### 2.7 CPU 执行、等待输出和持久化

GTNH 的 CPU 运行时维护：

- `tasks: Map<ICraftingPatternDetails, TaskProgress>`；
- `waitingFor`；
- `waitingForMissing`；
- CPU `inventory`；
- final output；
- listeners、diagnostics、elapsed time、craft count。

执行时按聚合 task 推送 pattern。pattern 推送后，预期输出进入 `waitingFor`。外部机器产物回到网络时，CPU 从 `waitingFor` 扣减；最终输出完成后结束 job。

NBT 持久化保存：

- final output；
- inventory；
- tasks；
- waitingFor；
- waitingForMissing；
- links；
- listeners；
- diagnostics；
- elapsed/remaining item counters。

GTNH 这部分非常成熟，尤其在 job merge、missing mode、通知/诊断/跟随合成方面比当前实现更丰富。

## 3. 当前 AE2 1.21.1 已魔改实现

### 3.1 发起计算

当前 `CraftingService.beginCraftingCalculation(...)` 创建 `CraftingCalculation`，交给 `CRAFTING_POOL` 异步执行。

`CraftingCalculation.run()` 当前只调用账本 planner：

```text
run()
  -> runLedgerPlan()
  -> 收集初始库存
  -> 收集 craftable pattern map
  -> 收集 emitable items
  -> new LedgerCraftingPlanner(...)
  -> planner.plan(...)
```

当前实现已经移除旧树 planner 入口和源码本体。

### 3.2 当前请求账本

当前 `CraftingRequest` 更轻：

- `AEKey what`；
- 初始 `amount`；
- `remainingAmount`；
- LIFO `contributions`；
- `fulfill(amount, contribution)`；
- `refund(amount)`；
- `fullRefund()`。

`CraftingPlanningContext` 分成：

- `availableItems`：初始网络库存；
- `workingItems`：副产物/余数/容器物品；
- `usedItems`：提交时需要从网络抽取；
- `emittedItems`：需要 emitter 供应。

这已经具备账本核心，但比 GTNH 少：

- substitution mode；
- acceptable substitute predicate；
- substitution group size；
- live request/resolver queue；
- task-level serialization；
- request parent set；
- pattern parent deny-list；
- task 级 `isSimulated()` 和 fallback 控制。

### 3.3 当前 planner

当前 `LedgerCraftingPlanner` 是递归实现，不是 GTNH 那种显式 task queue。流程：

```text
resolve(request)
  -> 先从 workingItems / availableItems 提取
  -> 再尝试 emitter
  -> 防 AEKey 递归
  -> 遍历可用 pattern
  -> 对普通 pattern 批量 planPatternAttempt
  -> 根据 childRequests 计算 maxTimes
  -> refund 过量子请求
  -> 注入 remaining output / container output
  -> 记录 PatternCraftingTask
```

对于当前已经测试过的普通大订单，复杂度主要由依赖图规模决定，不再随订单数量线性增长。

对于有 remaining input 的 pattern，当前仍保守地逐次处理：

```text
for i in requestedTimes
```

这对应文档中“复杂/容器相关行为先保正确性”的策略。

### 3.4 确认界面和计划展示

当前 `CraftConfirmMenu` 等待 `Future<ICraftingPlan>` 完成，然后调用：

```text
CraftingPlanSummary.fromJob(grid, actionSource, plan)
```

`CraftingPlanSummary` 聚合：

- `usedItems`；
- `missingItems`；
- `emittedItems`；
- `patternTimes`。

因此当前 UI 展示仍是聚合视图，不是 GTNH 那种完整 crafting tree 视图。

### 3.5 提交 CPU

当前 `CraftingService.submitJob(...)` 会：

- 拒绝 simulation plan；
- 如果没有指定 CPU，调用 `findSuitableCraftingCPU(...)`；
- 当前只选择 idle CPU，不合并进 busy CPU；
- 返回详细 `ICraftingSubmitResult`，例如 no CPU、CPU busy、CPU too small、missing ingredient。

`CraftingCpuLogic.trySubmitJob(...)` 做实际提交：

```text
检查 CPU 有无 job / 是否在线 / bytes 是否足够
  -> CraftingCpuHelper.tryExtractInitialItems(...)
  -> 创建 CraftingLink
  -> 如果 plan 是 LedgerCraftingPlan
       new ExecutingLedgerCraftingJob(...)
     否则
       new ExecutingCraftingJob(...)
  -> 更新 CPU final output
  -> 通知 job owner STARTED
```

这和 GTNH 最大的结构差异是：当前 ledger plan 不是进入旧 CPU 聚合 `Map<pattern,count>`，而是进入专门的 `ExecutingLedgerCraftingJob`。

### 3.6 当前 ledger CPU 执行

`ExecutingLedgerCraftingJob` 保存：

- final output；
- remaining amount；
- waitingFor；
- task list；
- next task index；
- suspended；
- link；
- elapsed time tracker；
- execution profiling counters。

构造时它会：

- 把 `emittedItems` 放入 `waitingFor`；
- 把 `PatternCraftingTask` 转为 `TaskProgress`；
- 合并相邻重复 pattern task；
- 预估 expected outputs。

`CraftingCpuLogic.executeLedgerCrafting(...)` 每 tick：

```text
如果整轮都 input-blocked 且 CPU inventory serial 未变化，直接短路
  -> 从 nextTaskIndex 开始轮转扫描 task
  -> 跳过已完成 task
  -> 跳过 input-blocked task
  -> 查询 provider
  -> 跳过 busy provider
  -> 从 CPU inventory 抽 pattern 输入
  -> 检查能量
  -> provider.pushPattern(...)
  -> expected output/container item 进入 waitingFor
  -> task.remainingTimes--
  -> 记录 profiling counters
```

这部分当前实现比 GTNH 更接近“task list 直接驱动执行”。GTNH 的 V2 计划树进入 CPU 后会聚合到 pattern map；当前实现保留 task list、cursor 和 input-blocked 状态。

### 3.7 当前等待输出、完成、取消和 NBT

外部产物回到 CPU 时，`insertIntoLedgerJob(...)`：

- 先从 `waitingFor` 模拟/实际扣减；
- 如果是 final output，则插入 requester link，减少 `remainingAmount`；
- 如果不是 final output，则进入 CPU inventory，并增加 `cpuInventoryChangeSerial`；
- final output 全部交付后 `finishLedgerJob(true)`。

NBT 持久化保存：

- link；
- final output；
- remaining amount；
- waitingFor；
- elapsed time；
- task list；
- next task index；
- suspended；
- profiling stats。

取消时，当前实现清理 waitingFor、通知 owner、清空 job，然后 `storeItems()` 把 CPU inventory 尽量回存网络。

## 4. 关键逻辑差距

### 4.1 当前已经追平或超过 GTNH 的部分

1. `CRAFT_LESS` 不再二分重算。

GTNH 和当前账本模型都能在一次请求账本里自然得到部分可满足数量。当前测试显示，5000 万请求、材料只够 2500 万时：

```text
账本算法：1 次 planner run，5 次 pattern attempt
旧二分外壳：26 次 planner run，130 次 pattern attempt
```

2. 普通非复杂 pattern 大订单按图规模批量规划。

当前 5000 万订单测试中，最终只有 6 次 pattern attempt 和 6 个 planned task，说明没有按数量展开。

3. CPU 执行层当前比 GTNH 更 task-list 化。

GTNH 计划阶段有 task tree，但 CPU 执行阶段主要聚合成 `Map<pattern, count>`。当前 `ExecutingLedgerCraftingJob` 保留 task list、轮转 cursor、input-blocked cache、相邻 task grouping 和 profiling counters。

4. 当前执行热路径有 provider busy 预筛和 input-blocked 短路。

GTNH CPU 聚合执行也能跑得很稳，但当前实现已经加入了一些面向大订单执行吞吐的局部优化。

### 4.2 当前仍落后于 GTNH 的部分

1. Resolver 插件体系不足。

GTNH 的 resolver 是独立接口，可扩展 `CraftingRequestResolver`。当前 `LedgerCraftingPlanner` 把 extraction、emitter、pattern、missing 逻辑写在一个类里。短期效率高，长期扩展性弱。

2. 替代输入语义没有 GTNH 完整。

GTNH 有：

- `PRECISE_FRESH`；
- `PRECISE`；
- `ACCEPT_FUZZY`；
- `acceptableSubstituteFn`；
- `substitutionGroupSize`；
- pattern slot 合法性检查；
- fuzzy pattern cache。

当前实现有 `input.isValid(key, level)` 和可 craftable 输入选择，但缺少完整的 substitution mode 和 group-size 语义。

3. 父请求/父 pattern 防递归不如 GTNH 精细。

GTNH 用 `parentRequests` 和 `patternParents`，既能避免无限递归，也能允许输入=输出 pattern 做净输入/净输出拆分。当前主要按 `AEKey` 防递归，遇到某些“输入输出同类但净产出为正”的 pattern 会更保守。

4. 复杂 crafting pattern 处理不如 GTNH 完整。

GTNH 会用 fake crafting table 模拟复杂 pattern 的剩余格子、容器物品、可复用 substitute input，并把复杂 pattern 按单次 craft 展开。当前实现主要通过 `patternHasRemainingInputs` 保守逐次处理 remaining input，但没有 GTNH 那套完整的 complex detector 和 workbench simulation。

5. Missing/Ignore Missing 模式不如 GTNH 细。

GTNH 有 `SimulateMissingItemResolver` 和 `IgnoreMissingItemResolver`，并且 `CraftingMode.IGNORE_MISSING` 会一路传入 job/CPU 的 missing mode。当前有 `REPORT_MISSING_ITEMS` 和 `CRAFT_LESS`，但没有完整复刻 GTNH 的 ignore-missing 执行语义。

6. 确认界面缺少完整 crafting tree。

GTNH 会把 `CraftingJobV2` tree 压缩分片发给客户端。当前只发聚合后的 `CraftingPlanSummary`。这对调试长单、多路径、missing 来源分析会弱一些。

7. CPU job merge 不如 GTNH。

GTNH 可以把同输出的 standalone/player job 合并到 busy CPU，只要容量足够。当前 `findSuitableCraftingCPU(...)` 排除 busy CPU，提交逻辑也要求 CPU 没有 job。因此高并发同类订单吞吐可能不如 GTNH。

8. 诊断、通知、跟随合成和 pattern 优化集成不如 GTNH。

GTNH CPU 有 diagnostic sessions、craft notification、follow craft、pattern optimization matrix 等附加能力。当前 ledger executor 有 profiling counters，但生态级诊断和 UI 工具还少。

## 5. 时间复杂度分析

### 5.1 变量约定

```text
Q = 根请求数量，例如 50,000,000
K = 不同请求 key 数量
P = 某个 key 的候选 pattern 数
P_total = 网络中 pattern 总数
E = 实际访问到的 pattern 输入边数量
F = fuzzy 候选数量
R = resolver/task 数量
T = 计划出的 pattern task 数量
C = CPU 中某个 pattern 的 provider 数量
M = pattern 实际执行次数总和
B = refund 涉及的贡献记录数
```

### 5.2 GTNH planning

普通非复杂 pattern：

```text
O(K + R log R + E + F + B)
```

解释：

- 每个 request 会生成 resolver 列表并按 priority 排序。
- precise pattern 通过 cache 查找。
- fuzzy pattern 首次需要构建 fuzzy cache，代价接近 `O(P_total * outputsPerPattern)`，之后查询接近候选集大小。
- pattern task 批量创建子请求，不随 `Q` 展开。
- refund 代价取决于 used resolver entries 和 child request 数。

复杂 crafting pattern：

```text
O(Q * complexRecipeCost + Q * inputResolutionCost)
```

GTNH 对复杂 pattern 会按请求数量创建 task，保正确性优先。

### 5.3 当前 planning

普通非 remaining-input pattern：

```text
O(P_total_scan + K + E + P + B)
```

解释：

- `CraftingCalculation.runLedgerPlan()` 当前每次计算会扫描 craftables 和 pattern 输入来构建 `patterns` 和 `emitableItems`。
- `LedgerCraftingPlanner` 对普通 pattern 批量尝试，按依赖图形状增长。
- 5000 万订单测试证明 `Q` 不进入普通 pattern 的主复杂度。

带 remaining input / 容器相关 pattern：

```text
O(Q * E_remaining)
```

当前这类 pattern 仍逐次处理，这是正确性优先的保守路径。

fuzzy/替代输入：

```text
O(A * S)
```

其中 `A` 是当前 working/available inventory 中被扫描的 key 数，`S` 是 slot validity 检查成本。GTNH 有 fuzzy cache，当前这块还有优化空间。

### 5.4 GTNH CPU execution

GTNH 执行时把任务聚合为 `Map<pattern, count>`。

每 tick 粗略复杂度：

```text
O(T_map_scan + C + inputExtractionCost)
```

完整 job：

```text
O(M * (providerSelection + inputExtraction + pushPattern + waitingForUpdate))
```

因为 CPU 层是按 pattern 次数实际推送，最终执行仍必须随 `M` 增长；任何实现都不能绕过真实机器执行次数。

GTNH 的优势在于成熟的 CPU 状态、missing mode、merge job、诊断和 NBT；劣势是 task tree 在 CPU 阶段被聚合，丢失了部分拓扑调度信息。

### 5.5 当前 CPU execution

当前 ledger executor 每 tick：

```text
O(scannedTasks + providerLookups + inputExtraction + pushPattern)
```

其中：

- cursor 避免每 tick 从 task 0 开始；
- input-blocked serial 避免 CPU inventory 未变化时重复抽输入；
- provider busy 预筛减少无效 input extraction；
- 相邻重复 task grouping 降低 task 数；
- all-input-blocked short circuit 避免整轮重复扫描。

完整 job 仍是：

```text
O(M * realPatternPushCost + 调度扫描开销)
```

当前优化目标是把“调度扫描开销”压低到接近实际可推进的 pattern 数，而不是消除 `M`。

## 6. 推荐后续方向

### 6.1 短期优先级最高

1. 为当前 `LedgerCraftingPlanner` 加 resolver 抽象层。

目标不是照搬 GTNH 类名，而是把 extraction、emitter、pattern、missing/ignore-missing 从单个大类里拆出来。这样后续实现更完整的 fuzzy、missing mode 和 custom resolver 会更安全。

2. 补完整 substitution mode。

至少引入：

- 精确但允许库存提取；
- 根请求 fresh 模式；
- fuzzy/slot-valid 模式；
- group-size，避免一个 pattern input group 混用多个 fuzzy 候选。

3. 实现 pattern parent deny-list。

当前按 `AEKey` 防递归偏粗。建议改成 request parent + pattern parent 双轨，支持输入=输出但净产出为正的 pattern。

### 6.2 中期

1. 复杂 crafting pattern detector。

参考 GTNH：

- 试算一次 crafting grid；
- 检查剩余格子；
- 检查容器物品；
- 检查可复用 substitute input。

复杂 pattern 保留逐次执行；非复杂 pattern 保持批量。

2. 计划树/任务树 UI。

当前确认界面只有聚合摘要。建议增加可选调试包或开发者界面，展示：

- request tree；
- resolver 来源；
- missing 来源；
- refund 后实际保留量；
- pattern attempts。

3. job merge。

参考 GTNH，把同输出、standalone、容量足够的任务合并进 busy CPU。需要先确认当前 `ExecutingLedgerCraftingJob` 合并 task list、remainingAmount、waitingFor、link 语义。

### 6.3 长期

1. 完整 missing/ignore-missing 执行语义。

GTNH 的 `waitingForMissing` 和 `isMissingMode` 对自动化很有价值。当前可以逐步引入，但需要小心避免凭空物品进入普通路径。

2. 诊断和 profiling UI。

当前已有 planner/executor counters，但还没有类似 GTNH diagnostic sessions 的用户可见链路。建议把 counters 暴露给 debug command 或 crafting status UI。

3. 独立 JMH/benchmark。

JUnit 的毫秒数只能做烟测。建议为以下场景建立基准：

- 5000 万材料充足；
- 5000 万材料不足 `CRAFT_LESS`；
- 多路径且第一路径失败；
- fuzzy 替代候选很多；
- provider 大量 busy；
- CPU inventory 长期 input-blocked。

## 7. 总结

GTNH AE2 的强项是完整、成熟、可扩展的 V2 请求账本体系，以及和旧 CPU 生态深度集成的生产级功能：tree 序列化、missing mode、job merge、诊断、通知和复杂配方处理。

当前 AE2 1.21.1 已魔改实现的强项是已经把核心 planning 切到轻量账本模型，并且 CPU 执行层比 GTNH 更进一步：`LedgerCraftingPlan` 进入专门的 `ExecutingLedgerCraftingJob`，不是只聚合成 `patternTimes` 或 `Map<pattern,count>`。因此在普通大订单、材料不足 `CRAFT_LESS` 和 provider 调度热路径上，当前结构已经有很好的理论上限。

后续最值得补的不是再改一次大框架，而是把 GTNH 那些成熟边角补齐：resolver 抽象、substitution/fuzzy group、pattern parent deny-list、复杂配方 detector、job merge 和可视化诊断。
