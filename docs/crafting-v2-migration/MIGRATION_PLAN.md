# Crafting V2 迁移计划

## 基本规则

- 每完成一个实质步骤，都更新 `docs/crafting-v2-migration/STATE.md`。
- 上下文压缩后继续工作时，先读取 `STATE.md`、`DESIGN.md` 和本计划。
- 每个阶段都先补或更新测试，再改实现。
- 在账本式执行层准备好之前，保持 `ICraftingPlan` 兼容。
- 优先小步推进，避免一次性替换过多系统。
- 在账本式路径具备等价测试覆盖前，不移除旧 planner 或旧 executor；当前旧树 planner 已在覆盖到位后退役并删除。
- Crafting V2 只作为迁移项目名和阶段名保留；新增实现类和方法不使用 `V2` 后缀。

## Phase 0: 基线和测试支架

目标：在修改实现前，建立可衡量、可回归的行为基线。

任务：

1. 运行现有 crafting simulation 测试。
2. 添加面向大订单行为和账本式语义的 planner 测试。
3. 添加便于比较 plan 的断言工具，覆盖：
   - used items；
   - emitted items；
   - missing items；
   - pattern counts；
   - final output amount；
   - 在确定性场景下的 byte cost。
4. 至少添加一个回归测试，展示当前多路径大订单的弱点。

验证：

- `./gradlew test --tests appeng.crafting.simulation.CraftingSimulationTest`
- 创建后运行额外的账本式 planner 专项测试。

退出标准：

- 现有测试通过。
- 新测试要么通过，要么只在明确针对尚未实现的账本式行为时先保持失败/禁用。

## Phase 1: 带 ICraftingPlan 兼容层的账本式 Planner

目标：引入账本式 planner，同时保持当前 CPU 执行层不变。

新增或修改组件：

- `appeng.crafting.ledger.CraftingRequest`
- `appeng.crafting.ledger.CraftingPlanningContext`
- `appeng.crafting.ledger.CraftingTask`
- `appeng.crafting.ledger.CraftingResolver`
- `appeng.crafting.ledger.LedgerCraftingPlan`
- extraction、emitter、pattern crafting、missing simulation 等 resolver 实现。

任务：

1. 实现请求账本状态：
   - 请求的 key/amount；
   - 剩余数量；
   - 已使用 resolver 记录；
   - 父链和 pattern 递归保护；
   - byte accounting。
2. 实现可变 planning inventory：
   - 初始网络模型；
   - 副产物模型；
   - extraction 和 fuzzy lookup helper。
3. 实现 resolver 注册和优先级排序。
4. 实现 extraction resolver。
5. 实现 emitter resolver。
6. 实现 missing simulation resolver。
7. 实现非复杂 pattern resolver：
   - 批量创建子请求；
   - 计算实际可合成次数；
   - 注入副产物/余数；
   - refund 过量请求的子项。
8. 实现保守的 complex pattern 支持。
9. 构建 `LedgerCraftingPlan` 兼容字段：
   - `usedItems`；
   - `emittedItems`；
   - `missingItems`；
   - `patternTimes`；
   - `bytes`；
   - `multiplePaths`。
10. 在 `CraftingService` 中加入 feature toggle 或本地开关，用于调用账本式 planner。

验证：

- 现有 crafting simulation 测试在账本式路径下通过。
- 新增账本式 planner 测试通过。
- 大型非复杂多路径订单不会再按请求数量线性尝试分支。

退出标准：

- `CraftingService.beginCraftingCalculation` 可以返回 `LedgerCraftingPlan`。
- 当前 `CraftingCpuLogic` 可以通过兼容字段提交并执行账本式 plan。
- 本阶段不改 CPU 执行内部逻辑，除非需要最小类型检查。

## Phase 2: 账本式 CPU 执行

目标：CPU 直接执行 task tree，不再只依赖聚合后的 `patternTimes`。

新增或修改组件：

- `appeng.crafting.execution.ExecutingLedgerCraftingJob`
- executable task record
- 账本式 job 持久化格式
- `CraftingCpuLogic` 中的 `LedgerCraftingPlan` 集成路径

任务：

1. 定义 executable task 表示：
   - task id；
   - pattern details；
   - 剩余执行次数；
   - 预期输出；
   - 预期容器物品；
   - 依赖状态。
2. 把 `LedgerCraftingPlan` task tree 转换为 executable tasks。
3. 从 `CraftingCpuLogic.trySubmitJob` 提交账本式 job。
4. 保留当前初始抽取安全检查。
5. 用当前 CPU 操作限制实现拓扑顺序执行循环。
6. 保持 `waitingFor` 行为与 storage insertion 兼容。
7. 通过现有 link/requester 行为交付最终输出。
8. 添加账本式 NBT write/read。
9. 添加取消行为和物品 dump 行为。

验证：

- 旧 plan 的 CPU 执行行为仍可工作。
- 简单和多步账本式 plan 可以成功执行。
- 账本式 job 能保存/读取后继续。
- 取消不会复制物品，也不会比现有 AE2 语义更容易丢物品。

退出标准：

- `LedgerCraftingPlan` 执行不再依赖 `patternTimes`，但仍可为了 UI 兼容暴露它。
- `CraftingCpuLogic.trySubmitJob` 对 `LedgerCraftingPlan` 创建 `ExecutingLedgerCraftingJob`，覆盖 submit、tick、insert、NBT、取消 dump 和执行统计。
- 非账本式 `ICraftingPlan` 的旧 executor 兼容路径仅用于外部/历史计划对象；当前 `CraftingCalculation` 生产入口只返回账本式计划。

## Phase 3: 调度和性能优化

目标：利用 task tree 提升执行吞吐，减少 provider 阻塞。

任务：

1. 跳过 busy provider，避免阻塞无关 ready task。
2. 用 cursor 轮转避免每次从 task 0 开始扫描。
3. 在 provider 能安全接收重复 pattern push 的情况下，同一执行调用内连续推送同一 task。
4. 记录 provider lookup、busy skip、input unavailable、input blocked、pattern push 等 profiling counter。
5. 将已知 input-blocked 判断前置到 provider lookup 之前，避免 CPU inventory 未变化时重复检查。
6. 当整轮剩余 task 都等待输入时，在同一 CPU inventory serial 下直接短路。
7. 合并相邻重复 pattern task，降低 provider lookup，同时保留非相邻 task 的顺序语义。

验证：

- 单元测试覆盖 provider busy、重复 push、input blocked 短路和相邻重复 task grouping。
- 全量测试和 game-test smoke。
- 可选：真实存档长单 profiling 前后对比。
- 容器物品/副产物行为无回归。

退出标准：

- 大订单 planning 和 execution 开销下降。
- provider 阻塞不会卡住独立 ready task。
- 常见热路径有可观察统计，用于后续压测继续定位。

## Phase 4: 清理和旧路径退役

目标：账本式路径稳定后，减少重复逻辑。

任务：

1. 移除旧 `appeng.crafting.ledgerPlanner` 启用开关。
2. 让历史 `appeng.crafting.legacyPlanner` 属性不再影响生产 planner 选择，并保留测试证明其被忽略。
3. 删除旧树 planner 本体和只服务旧树 planner 的 tick/simulation 入口。
4. 保留 `LedgerCraftingPlan` 面向公共 API 消费者的兼容方法。
5. 更新内部文档和注释。
6. 运行更广泛的测试。

验证：

- 全量测试。
- `runGametest` smoke。
- 可选：真实 dev world 或真实存档长单手动 smoke/profiling。

退出标准：

- 账本式路径成为默认 planner 和 executor。
- 旧树 planner 运行入口和源码本体被移除。
- `src/main/java` 中不再存在 `legacyPlanner`/`ledgerPlanner` 生产行为残留。

## 建议的第一个实现切片

第一轮编码切片应刻意收窄：

1. 添加账本式 request/task/context 骨架。
2. 添加 extraction-only 和 single-pattern batch planning 测试。
3. 实现足够的 planner 逻辑让这些测试通过。
4. 构建带公共兼容字段的 `LedgerCraftingPlan`。
5. 先把账本式 planner 接到仅测试使用的私有开关后面。

这样可以先站稳第一块地面，不立刻触碰 CPU 执行层。
