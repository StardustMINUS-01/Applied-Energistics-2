# Crafting V2 迁移状态

## 1. 当前目标

围绕 GTNH 风格的账本式 request 模型重构 AE2 1.21.1 自动合成系统：
请求账本、resolver task、partial/full refund、task tree，并最终接管 planning 和 CPU execution。

## 2. 已确认的设计决策

- 以 GTNH AE2 Crafting V2 逻辑作为参考模型，但按当前 AE2 1.21.1 NeoForge 代码结构重建，不直接复制变量名或实现细节。
- 做深度迁移，不只是局部优化 `CraftingCalculation`。
- 尽量保持当前公共 API 边界稳定，尤其是 `ICraftingService.beginCraftingCalculation`、`ICraftingPlan` 和 `submitJob`。
- Phase 1 实现账本式 planner，并输出兼容当前 `ICraftingPlan` 的计划结果。
- Phase 2 让 CPU execution 消费 task tree，不再只依赖聚合后的 `patternTimes`。
- Phase 3 优化调度，并清理或降级旧路径。
- 每个阶段都必须先补测试，再改实现。
- Crafting V2 只作为迁移项目名和阶段名保留；新增实现类和方法不使用 `V2` 后缀，改用账本语义或职责语义命名，例如 `CraftingRequest`、`CraftingPlanningContext`、`CraftingTask`、`CraftingResolver`、`LedgerCraftingPlan`、`ExecutingLedgerCraftingJob`。
- 这个文件是上下文压缩后的连续性来源。如果本文件和对话记忆冲突，以本文件为准，并重新检查相关代码。

## 3. 当前阶段

Phase 1-4 核心迁移已完成，当前进入迁移后收尾验证和性能基准准备：

- Phase 1 planner 已默认接管 `CraftingCalculation`，生产入口直接构建并运行账本式 planner。
- Phase 2 ledger task-tree execution 已接管 `LedgerCraftingPlan`，覆盖提交、tick、insert、NBT、取消 dump、provider busy skip 和 cursor 轮转调度。
- Phase 3 已完成 provider busy 预筛、cursor 轮转、同 provider 重复 push、input-blocked 短路、相邻重复 pattern task grouping 和 planner/executor profiling counters。
- Phase 4 已完成旧 `appeng.crafting.ledgerPlanner` 开关清理、历史 `appeng.crafting.legacyPlanner` 属性退役、旧树 planner 运行入口和源码本体删除。

## 4. 已完成事项

- 确认当前项目为 Minecraft 1.21.1 + NeoForge 21.1.169。
- 梳理了当前自动合成 planning 和 execution：
  - `CraftingCalculation`
  - `CraftingTreeNode`
  - `CraftingTreeProcess`
  - `CraftingSimulationState`
  - `CraftingCpuLogic`
  - `ExecutingCraftingJob`
  - `CraftingPlanSummary`
- 对比了当前 AE2 和 `Applied-Energistics-2-Unofficial-master` 下的 GTNH AE2 参考实现。
- 确认 GTNH 实际使用 `CraftingJobV2` 作为主 crafting job 实现。
- 选择深度迁移路线：planner 和 CPU execution 都逐步迁移到 V2 task tree 语义。
- 创建了迁移状态文件。
- 写入了 Crafting V2 设计文档。
- 写入了分阶段迁移计划。
- 按用户要求把迁移文档改写为简体中文。
- 完成中文文档自检，未发现英文旧标题或占位符残留。
- 按用户明确要求，更新设计和迁移计划：新增实现类/方法不使用 `V2` 后缀。
- Phase 0 新增测试支架：`ProcessingPatternBuilder.addCountingPreciseInput(...)`，用于观察 planner 是否按请求数量重复查询 pattern 输入。
- Phase 0 新增 planner 测试：
  - `testLargeSinglePathOrder`
  - `testLargeMultiplePathOrder`
  - `testLargeMultiplePathOrderUsesBatchPlanning`
- Phase 1 第一片实现：在 `CraftingTreeNode` 多路径分支中，为非 `limitsQuantity()` pattern 增加基于 `ChildCraftingSimulationState` 的批量尝试；失败的 child state 不应用到父库存，作为 partial/full refund 语义的过渡落点。`limitsQuantity()` pattern 仍保留逐个执行以保护容器物品和递归输出复用行为。
- Phase 1 新增账本核心 RED 测试：`CraftingRequestTest`，覆盖 request fulfilled/remaining 不变量、partial refund、full refund 和逆序回滚。
- Phase 1 新增 context RED 测试：`CraftingPlanningContextTest`，覆盖网络提取贡献的 partial/full refund 会同时恢复可用库存和 `usedItems`。
- Phase 1 新增 plan 兼容层 RED 测试：`LedgerCraftingPlanTest`，覆盖 `ICraftingPlan` 字段和内部 task 列表。
- Phase 1 新增 extraction-only planner RED 测试：`LedgerCraftingPlannerTest`，覆盖只从库存提取成功和库存不足时报告 missing。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `plansSinglePatternRequestInOneBatch`，要求普通非复杂 pattern 一次性批量规划并记录 task。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `refundsUnresolvedOverRequestWhenMultiplePatternsShareOutput`，防止多路径 over-request 的 unresolved remainder 错误进入 missing。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 emitter resolver 测试，要求 emitable 请求填充 `emittedItems` 且不消耗网络库存。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增账本式 `CRAFT_LESS` 测试，要求返回实际可满足数量且不把剩余量作为 missing。
- Phase 1 实现最小 `LedgerCraftingPlanner`，当前支持 extraction-only plan 和 missing reporting，输出 `LedgerCraftingPlan`。
- Phase 1 扩展 `LedgerCraftingPlanner`：支持普通单 pattern 批量解析，递归请求输入，按实际可满足输入计算 `maxTimes`，并 refund 过量子请求；新增 `PatternCraftingTask` 记录 pattern 和执行次数。
- Phase 1 实现最小账本核心类：
  - `appeng.crafting.ledger.CraftingRequest`
  - `appeng.crafting.ledger.CraftingContribution`
- Phase 1 实现最小 `CraftingPlanningContext`，支持网络提取贡献的 partial/full refund。
- Phase 1 实现最小兼容 plan/task 类型：
  - `appeng.crafting.ledger.LedgerCraftingPlan`
  - `appeng.crafting.ledger.CraftingTask`

## 5. 正在做的事项

- 当前没有必须继续推进的迁移开发项；后续工作转为可选增强验证和性能基准。
- 若继续推进，建议先做真实存档长单压测，再根据 profiling 结果决定是否实现更激进的 ready queue/provider grouping。

## 6. 未完成事项

- 更细的 planner/executor profiling 输出入口和真实长单压测脚本仍可继续增强。
- 真实 dev world / 真实存档长单 smoke test 尚未人工执行；`runGametest` smoke 已通过。
- 当前 workspace 不是 Git repository，无法创建 git worktree；本轮迁移在当前目录原地推进。

## 7. 关键文件索引

当前 AE2 代码：

- `src/main/java/appeng/crafting/CraftingCalculation.java`
- `src/main/java/appeng/crafting/CraftingPlan.java`
- `src/main/java/appeng/crafting/inv/CraftingSimulationState.java`
- `src/main/java/appeng/crafting/inv/NetworkCraftingSimulationState.java`
- `src/main/java/appeng/crafting/ledger/CraftingRequest.java`
- `src/main/java/appeng/crafting/ledger/CraftingPlanningContext.java`
- `src/main/java/appeng/crafting/ledger/CraftingPlanningStats.java`
- `src/main/java/appeng/crafting/ledger/LedgerCraftingPlanner.java`
- `src/main/java/appeng/crafting/ledger/LedgerCraftingPlan.java`
- `src/main/java/appeng/crafting/ledger/PatternCraftingTask.java`
- `src/main/java/appeng/crafting/execution/CraftingCpuLogic.java`
- `src/main/java/appeng/crafting/execution/ExecutingLedgerCraftingJob.java`
- `src/main/java/appeng/crafting/execution/LedgerExecutionStats.java`
- `src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java`
- `src/main/java/appeng/crafting/execution/CraftingCpuHelper.java`
- `src/main/java/appeng/me/service/CraftingService.java`
- `src/main/java/appeng/me/service/helpers/NetworkCraftingProviders.java`
- `src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java`
- `src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java`
- `src/test/java/appeng/crafting/simulation/CraftingSimulationTest.java`
- `src/test/java/appeng/crafting/ledger/LedgerCraftingPlannerTest.java`
- `src/test/java/appeng/crafting/execution/CraftingCpuLogicTest.java`
- `src/test/java/appeng/crafting/execution/ExecutingLedgerCraftingJobTest.java`
- `src/test/java/appeng/crafting/simulation/helpers/SimulationEnv.java`

GTNH 参考代码：

- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingJobV2.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingContext.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingRequest.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingCalculations.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/CraftingTask.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/ExtractItemResolver.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/CraftableItemResolver.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/EmitableItemResolver.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/SimulateMissingItemResolver.java`
- `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/IgnoreMissingItemResolver.java`

迁移文档：

- `docs/crafting-v2-migration/STATE.md`
- `docs/crafting-v2-migration/DESIGN.md`
- `docs/crafting-v2-migration/MIGRATION_PLAN.md`
- `docs/crafting-v2-migration/GTNH_AE2_CRAFTING_RESEARCH.md`
- `docs/crafting-v2-migration/GTL_ME_PATTERN_BUFFER_BULK_RESEARCH.md`

## 8. 风险和待验证点

- 容器物品和合成余数必须保持现有行为。
- 输入同时也是输出的递归 pattern 不能导致无限递归。
- fuzzy 替代必须保持合法 slot 语义。
- 多路径失败后，必须先尝试同级其他路径，再退到缺失物品模拟。
- partial/full refund 必须一致地恢复模拟库存、副产物、emitted items 和 pattern counts。
- 普通非复杂 pattern 的大订单不能随请求数量线性退化。
- complex crafting pattern 第一版可以保留按数量展开，以保证正确性。
- Phase 1 中现有 `ICraftingPlan` 消费者必须继续工作。
- Phase 2 开始后，CPU NBT persistence 必须能跨世界保存/读取恢复。

## 9. 最近一次验证命令和结果

- 2026-06-23 完成审计最新验证：
  - 文档 stale fallback 扫描：`rg -n "继续交给旧执行器|第二阶段引入 V2|旧 planner 可以|debug fallback|旧树 planner 仅|代码本体仍保留|非 V2 plan" docs/crafting-v2-migration/DESIGN.md docs/crafting-v2-migration/MIGRATION_PLAN.md -S`
    - 结果：无命中。
  - 旧树源码符号扫描：`rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
    - 结果：无命中。
  - 旧开关/命名扫描：`rg -n "legacyPlanner|ledgerPlanner|CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
    - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 中验证历史 `appeng.crafting.legacyPlanner` 属性被忽略的测试代码。
  - 全量单元测试：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
    - 结果：成功，`BUILD SUCCESSFUL in 3s`，9 个 task 全部 up-to-date。
  - game-test smoke：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon runGametest`
    - 结果：成功，`BUILD SUCCESSFUL in 14s`；GameTest 输出 `68 GAME TESTS COMPLETE` 和 `All 68 required tests passed :)`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈。

- 2026-06-23 文档收尾后最新验证：
  - 旧树源码符号扫描：`rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
    - 结果：无命中。
  - 旧开关/命名扫描：`rg -n "legacyPlanner|ledgerPlanner|CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
    - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 中验证历史 `appeng.crafting.legacyPlanner` 属性被忽略的测试代码。
  - 全量单元测试：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
    - 结果：成功，`BUILD SUCCESSFUL in 3s`，9 个 task 全部 up-to-date。
  - game-test smoke：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon runGametest`
    - 结果：成功，`BUILD SUCCESSFUL in 14s`；GameTest 输出 `68 GAME TESTS COMPLETE` 和 `All 68 required tests passed :)`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈。

- `.\\gradlew.bat test --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：失败，Gradle wrapper 需要下载 `gradle-8.12-bin.zip`，但下载在 10 秒读超时处中断。
- `curl.exe -L --connect-timeout 60 --max-time 600 -o <gradle wrapper cache> https://services.gradle.org/distributions/gradle-8.12-bin.zip`
  - 结果：失败，distribution 跳转到 GitHub 后无法连接 `github.com:443`。本机存在已解压的 Gradle 8.12.1 缓存，下一步先用该本地 Gradle 运行同一测试目标。
- `java -version`
  - 结果：默认 Java 为 17.0.7，项目 `gradle.properties` 声明 `java_version=21`。
- `Get-ChildItem -Recurse -Filter java.exe 'C:\\Program Files\\Java\\jdk-21_windows-x64_bin','C:\\Program Files\\Java\\zulu21.46.19-ca-jre21.0.9-win_x64'`
  - 结果：找到完整 JDK 21：`C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2\\bin\\java.exe`。
- 使用本地 Gradle 8.12.1 和 JDK 21 运行 `test --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：命令 184 秒超时；超时后仍检测到 Gradle/Java 进程存活，下一步先清理或停止本轮 Gradle 进程，再用更长 timeout 和 `--no-daemon` 重试。
- 使用本地 Gradle 8.12.1 和 JDK 21 运行 `gradle --stop`
  - 结果：Gradle 报告停止 2 个 daemon；`jps` 仍显示若干 Gradle daemon 条目，下一步用 `--no-daemon` 重跑测试并观察是否完成。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 3m 23s`，11 个 task 中 9 个执行、2 个 up-to-date。输出中有非 Git 仓库导致版本检测 fallback 的堆栈和既有 deprecation warnings，但测试目标通过。
- 添加 Phase 0 测试后运行同一命令
  - 结果：预期 RED，14 个测试完成 1 个失败；失败测试为 `testLargeMultiplePathOrderUsesBatchPlanning`，实际 multi-path input query 为 521，期望不超过 32，证明旧多路径 planner 会随请求数量逐份查询。
- Phase 1 第一片实现后运行同一命令
  - 结果：仍失败同一测试，但实际 multi-path input query 已从 521 降到 34；失败原因是测试阈值 32 未考虑 child node 构造和批量探测的固定开销。已把断言调整为 `< 128`，仍能防止旧线性行为。
- 调整阈值后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 13s`，`CraftingSimulationTest` 14 个测试全部通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- `rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2" docs\\crafting-v2-migration src\\main\\java src\\test\\java -S`
  - 结果：无命中，计划中的新增实现类型名未使用 `V2` 后缀。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.CraftingRequestTest`
  - 结果：预期 RED，`compileTestJava` 失败；缺少 `CraftingRequest` 和 `CraftingContribution`。
- 实现 `CraftingRequest` 和 `CraftingContribution` 后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.CraftingPlanningContextTest`
  - 结果：预期 RED，`compileTestJava` 失败；缺少 `CraftingPlanningContext`。
- 实现 `CraftingPlanningContext` 后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlanTest`
  - 结果：预期 RED，`compileTestJava` 失败；缺少 `LedgerCraftingPlan` 和 `CraftingTask`。
- 实现 `LedgerCraftingPlan` 和 `CraftingTask` 后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest`
  - 结果：预期 RED，`compileTestJava` 失败；缺少 `LedgerCraftingPlanner`。
- 实现 `LedgerCraftingPlanner` 后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 扩展 single-pattern batch 测试后运行同一命令
  - 结果：先因 `LedgerCraftingPlanner` 缺少 `(KeyCounter, Map<AEKey, List<IPatternDetails>>)` 构造器而 RED；实现 pattern resolver 后成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包当前测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 扩展多路径 over-request 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，4 个测试中 1 个失败；失败点为 `plan.simulation()` 为 true，说明子请求 over-request 的 unresolved remainder 被提前写入了全局 missing。
- 修正 missing 写入条件后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 10s`。多路径 over-request 不再污染 `missingItems`；输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 修正后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 扩展 emitter 和 `CRAFT_LESS` 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，`compileTestJava` 失败；`LedgerCraftingPlanner` 缺少 `(KeyCounter, Map<AEKey, List<IPatternDetails>>, Set<AEKey>)` 构造器。
- 实现 emitter resolver 和 `CRAFT_LESS` 输出缩减后运行同一命令
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `reportMissingItemsReportsLimitingPatternInputRemainder`，要求 `REPORT_MISSING_ITEMS` 报告限制 pattern 的输入缺口。
- 扩展 `REPORT_MISSING_ITEMS` 输入缺口测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，7 个测试中 1 个失败；失败点为 `plan.simulation()` 仍为 false，说明 pattern 子请求缺口没有进入 `missingItems`。
- 引入 pattern 分支 candidate missing 聚合后运行 `LedgerCraftingPlannerTest`
  - 结果：先修复了 `REPORT_MISSING_ITEMS` 输入缺口，但触发 `craftLessReturnsLargestLedgerSatisfiedAmountWithoutMissingRemainder` 回归；失败点为 `CRAFT_LESS` 在已有部分满足时仍因剩余缺口被标记为 simulation。
- 对 `CRAFT_LESS` 使用有效 missing 视图：当根请求已有 fulfilled 数量时，最终 plan 输出实际满足数量并清空剩余缺口视图；随后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。`REPORT_MISSING_ITEMS` 会报告限制输入缺口，`CRAFT_LESS` 保持返回最大可满足数量且不暴露剩余 missing。
- 再次扫描计划中的 `*V2` 类型名
  - 命令：`rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2" docs\\crafting-v2-migration src\\main\\java src\\test\\java -S`
  - 结果：只命中 `STATE.md` 中记录过的验证命令本身，新增实现代码没有使用 `V2` 后缀。
- `Get-Process java`
  - 结果：无遗留 Java/Gradle 测试进程。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`，账本包当前测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- `git status --short`
  - 结果：失败，因为当前 workspace 不是 git repository。
- `git rev-parse --git-dir; git rev-parse --git-common-dir; git branch --show-current`
  - 结果：失败，因为当前 workspace 不是 git repository；因此无法按 git worktree 隔离工作区。
- 对当前 planner、plan、UI summary 和 CPU execution 集成点做过搜索。
  - 结果：确认了主要集成点。
- 对 `docs/crafting-v2-migration` 做 marker scan。
  - 结果：曾发现过期标签，已修正。
- 对 `docs/crafting-v2-migration` 做最终 marker scan。
  - 结果：无命中。
- 对 `docs/crafting-v2-migration` 搜索旧英文标题和占位符。
  - 结果：无命中。
- `Get-ChildItem -Force docs\\crafting-v2-migration`
  - 结果：确认 `STATE.md`、`DESIGN.md` 和 `MIGRATION_PLAN.md` 存在。
- `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `usesStoredAlternativePatternInputWhenPrimaryInputIsMissing`，要求 pattern 主输入缺失但替代输入在库存中可用时，账本 planner 消耗替代输入并完成计划。
- 扩展替代输入测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，8 个测试中 1 个失败；失败点为 `plan.simulation()` 为 true，说明当前账本 planner 仍只请求 `getPossibleInputs()[0]`，没有使用库存中的替代输入。
- 实现账本式 pattern 输入槽候选解析：每个输入槽先从所有 `getPossibleInputs()` 候选中提取可用库存，剩余单位只递归请求主输入；同时把 child refund 从原始物品数量改为按输入槽单位回滚。
- 实现后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 10s`。替代输入库存可被消耗，主输入不再被错误报告 missing。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 替代输入实现后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `usesRemainingItemsFromChildPatternForSiblingInputs`，要求子 pattern 产生的容器余数/remaining item 回到工作库存，并能被同级后续输入消耗。
- 扩展 remaining item 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，9 个测试中 1 个失败；失败点为 `plan.simulation()` 为 true，说明当前账本 planner 没有把 child pattern 的 remaining item 写回 planning context。
- 第一版 remaining item 注入后再次运行 `LedgerCraftingPlannerTest`
  - 结果：失败点转移到 `usedItems().get(bucket)`，说明 working inventory 和初始网络库存共用同一个 `availableItems`，导致合成过程中产生的桶被误记为初始网络消耗。
- 将 `CraftingPlanningContext` 拆分为初始网络库存 `availableItems` 和合成中间库存 `workingItems`；提取时先消耗 `workingItems` 且不写入 `usedItems`，再消耗网络库存。
- 修复后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。remaining item 可供同级输入使用，且不会污染 `usedItems`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- remaining item 修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `reportsMissingWhenPatternWouldRecursivelyRequireItsOwnOutput`，要求自递归 pattern 安全停止并报告当前请求缺口。
- 扩展递归 guard 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，10 个测试中 1 个失败；失败点为 `plan.simulation()` 为 false，说明递归 guard 命中后直接返回，没有把当前请求剩余量写入候选 missing 账本。
- 修正递归 guard：当同一 `AEKey` 已在父请求链中时，把当前 request 的剩余量写入传入的 missing 账本，再停止进入该分支。
- 修正后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 10s`。自递归 pattern 不会产出 task，并会报告当前请求缺口。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 递归 guard 修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `usesByproductsFromChildPatternForSiblingInputs`，要求子 pattern 的非主输出副产物进入工作库存，并能被父级同级输入消耗。
- 扩展副产物测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，11 个测试中 1 个失败；失败点为 `plan.simulation()` 为 true，说明当前账本 planner 还没有把 pattern byproduct 写回 `workingItems`。
- 实现副产物注入：pattern 确认 `maxTimes` 后，将不匹配当前请求 key 的输出按次数写入 `CraftingPlanningContext` 的 `workingItems`。
- 实现后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。副产物可供父级同级输入使用，且不污染 `usedItems`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 副产物修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `reusesExcessPrimaryOutputFromChildPatternForSiblingInputs`，要求子 pattern 主输出超过当前 child request 需求时，多余主输出进入工作库存并可被同级输入复用。
- 扩展主输出过量测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，12 个测试中 1 个失败；失败点为 `plan.simulation()` 为 true，说明当前账本 planner 只注入非请求 byproduct，没有注入未用于满足当前请求的 primary output 余量。
- 将 pattern 输出结算从单纯 byproduct 注入扩展为剩余输出结算：当前请求实际消耗的输出数量先扣除，未消耗的匹配输出和所有非匹配输出都写入 `workingItems`。
- 实现后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。子 pattern 的多余主输出可被同级输入复用。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 主输出余量修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `refundsChildPatternWorkWhenSiblingInputLimitsParentPattern`，要求父 pattern 被限制输入缩单后，先前过量解析出的子 pattern 消耗和 `patternTimes` 随 child request refund 一起回滚。
- 扩展 child pattern partial refund 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，13 个测试中 1 个失败；失败点为 `usedItems().get(cobblestone)` 仍为 10 而不是 4，说明 pattern fulfillment 的贡献目前是空回调，父级 refund 没有回滚子 pattern 工作。
- 为 pattern fulfillment 增加 `PatternContribution`：父级对 child request 做 partial refund 时，按剩余 pattern 执行次数回滚子输入请求，并同步减少 `patternTimes`。
- 实现后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。父级缩单会把子 pattern 的初始库存消耗和 `patternTimes` 一起退回。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- child pattern partial refund 修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试和 `CraftingSimulationTest` 均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- Phase 1 扩展 `CraftingSimulationTest`：新增 `testLedgerPlannerToggleUsesLedgerPlan`，要求设置 `appeng.crafting.ledgerPlanner=true` 后，`CraftingCalculation` 返回 `LedgerCraftingPlan`，作为账本 planner 接入真实计算入口的本地/测试开关。
- 扩展 ledger planner toggle 测试后运行该单测
  - 结果：预期 RED，失败点为返回的 plan 仍是旧 `CraftingPlan`，说明 `CraftingCalculation` 尚未读取 toggle 或调用账本 planner。
- 在 `CraftingCalculation` 中加入默认关闭的本地/测试开关 `appeng.crafting.ledgerPlanner`；开关开启时，从 grid 快照收集初始库存、craftable pattern map 和 emitter set，调用 `LedgerCraftingPlanner` 并直接返回兼容 `ICraftingPlan` 的 `LedgerCraftingPlan`。
- 实现后运行 `CraftingSimulationTest.testLedgerPlannerToggleUsesLedgerPlan`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`。打开 toggle 时真实 `CraftingCalculation` 入口会返回 `LedgerCraftingPlan`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- ledger planner toggle 接入后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`，账本包测试、旧 `CraftingSimulationTest` 和新增 toggle 接入测试均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 命名约束扫描：`rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner|V2" src\\main\\java src\\test\\java -S`
  - 结果：无命中，新增源码和测试没有使用 `V2` 后缀实现名；文档中仍只保留项目名、历史记录和 GTNH 参考路径中的 `V2`。
- Phase 1 扩展 `LedgerCraftingPlannerTest`：新增 `marksMultiplePathsWhenNestedInputHasMultiplePatterns`，要求嵌套输入存在多个 pattern provider 时，兼容字段 `ICraftingPlan.multiplePaths()` 也为 true。
- 扩展嵌套 multiplePaths 测试后运行 `LedgerCraftingPlannerTest`
  - 结果：预期 RED，14 个测试中 1 个失败；失败点为 `plan.multiplePaths()` 为 false，说明当前账本 planner 只检查根输出 pattern 数量，没有在递归解析中累计嵌套多路径。
- 在 `LedgerCraftingPlanner` 的一次 planning 内新增 `PlanningFlags`，递归解析任意 request 时只要发现候选 pattern 数量大于 1，就累计设置 `multiplePaths`。
- 实现后运行 `LedgerCraftingPlannerTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。嵌套多路径会正确反映到 `ICraftingPlan.multiplePaths()`。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
- 嵌套 `multiplePaths` 修复后再次运行账本包测试和现有 `CraftingSimulationTest` 的组合验证
  - 命令：`$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-21_windows-x64_bin\\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`，账本包测试、旧 `CraftingSimulationTest` 和新增 toggle 接入测试均通过。输出仍包含非 Git 仓库导致版本检测 fallback 的堆栈。
## 10. 最新工作记录（2026-06-23）

- 继续 Phase 1 byte accounting 缺口。
- 按 TDD 先在 `LedgerCraftingPlannerTest` 新增 `accountsBytesForExtractionOnlyRequest`，要求 extraction-only 计划按旧路径基础语义计算 bytes：根请求节点开销 8，加 6 个 item 的栈字节 6，总计 14。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest`
- 结果：
  - 预期 RED：`accountsBytesForExtractionOnlyRequest()` 失败，断言位置 `LedgerCraftingPlannerTest.java:50`，当前 `LedgerCraftingPlanner` 仍返回 `bytes == 0`。
  - 仍有非 Git repository 导致的版本检测 fallback 堆栈，属于已知环境噪声。
- GREEN 实现：
  - 在 `LedgerCraftingPlanner` 的一次 planning 状态中加入 `PlanningStats`，根请求和 pattern 输入请求计入节点开销与栈字节，最终按 `ceil` 输出到 `LedgerCraftingPlan.bytes()`。
  - 同时把当前兼容字段里的 `patternTimes` 次数计入 craft bytes，先覆盖旧路径 `CraftingTreeProcess.request(...).addBytes(times)` 的基础语义。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest`
- 结果：
  - GREEN：`BUILD SUCCESSFUL in 11s`。
  - 仍有非 Git repository 导致的版本检测 fallback 堆栈，属于已知环境噪声。
- 组合回归验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：`BUILD SUCCESSFUL in 12s`，账本包测试和现有 `CraftingSimulationTest` 均通过。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在。
- 继续 byte accounting 的容器物品场景：
  - 新增 `accountsBytesForContainerItemsReturnedByPatternInputs`，要求 water bucket 输入返回 empty bucket 时，bytes 包含 container item bytes。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest`
  - 结果：预期 RED，`expected: 32L but was: 28L`，缺口正是 4 个 empty bucket 的 container item bytes。
- GREEN 实现：
  - `ChildRequest.insertRemainingItems(...)` 在把实际 remaining item 写回 working inventory 时，同步调用 `PlanningStats.addStackBytes(...)` 记录容器返回物字节。
  - 单项验证：`LedgerCraftingPlannerTest` 通过，`BUILD SUCCESSFUL in 11s`。
  - 组合验证：`test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest` 通过，`BUILD SUCCESSFUL in 11s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在。
- 继续 fuzzy craftable 输入场景：
  - 新增 `usesFuzzyCraftablePatternForPatternInput`，根 pattern 需要“任意钻石镐”输入，只有“受损钻石镐”的 child pattern 可合成时，账本 planner 应能通过 fuzzy craftable 选择 child pattern。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest`
  - 结果：预期 RED，`plan.simulation()` 为 true，说明当前账本 planner 仍把输入视为缺失，没有找到 fuzzy craftable pattern。
- GREEN 实现：
  - `LedgerCraftingPlanner` 新增可选 `Level` 上下文；`CraftingCalculation` 的 ledger toggle 入口传入真实 `level`。
  - pattern 输入精确 key 无可用 pattern 时，planner 会在可合成 pattern key 中寻找 `FuzzyMode.IGNORE_ALL` 等价项，并用 `IInput.isValid(...)` 过滤。
  - 单项验证：`LedgerCraftingPlannerTest` 通过，`BUILD SUCCESSFUL in 12s`。
  - 组合验证：`test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.simulation.CraftingSimulationTest` 通过，`BUILD SUCCESSFUL in 11s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在。
- Phase 2 启动：账本执行 job 骨架。
  - 新增 RED 测试 `ExecutingLedgerCraftingJobTest.preservesLedgerTaskOrderWithoutAggregatingPatternTimes`，要求执行 job 从 `LedgerCraftingPlan.tasks()` 保留任务顺序，不把相同 pattern 聚合成单个 `patternTimes` 计数。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.ExecutingLedgerCraftingJobTest`
  - 结果：预期 RED，`compileTestJava` 失败，缺少 `ExecutingLedgerCraftingJob` 类。
- GREEN 实现：
  - 新增 `appeng.crafting.execution.ExecutingLedgerCraftingJob` 骨架，从 `LedgerCraftingPlan.tasks()` 建立按账本顺序排列的 `TaskProgress` 列表。
  - 当前骨架已保存 `finalOutput`、`remainingAmount`、`waitingFor`、`timeTracker`、`link`、`playerId` 和 suspended 状态；尚未接入 `CraftingCpuLogic` tick 执行。
  - 单项验证：`ExecutingLedgerCraftingJobTest` 通过，`BUILD SUCCESSFUL in 13s`。
- 组合验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests appeng.crafting.execution.ExecutingLedgerCraftingJobTest --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：`BUILD SUCCESSFUL in 13s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在。
- Phase 2 CPU 提交接入：
  - 新增 RED 测试 `CraftingCpuLogicTest.submitsLedgerPlanAsLedgerJob`，要求 `CraftingCpuLogic.trySubmitJob(...)` 接收 `LedgerCraftingPlan` 时创建账本执行 job。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`compileTestJava` 失败，缺少 `CraftingCpuLogic.isLedgerJobActive()`，说明 CPU logic 还没有 ledger job 状态槽。
- GREEN 实现：
  - `CraftingCpuLogic` 新增 `ExecutingLedgerCraftingJob ledgerJob` 状态槽。
  - `trySubmitJob(...)` 遇到 `LedgerCraftingPlan` 时创建 `ExecutingLedgerCraftingJob`；旧 `ICraftingPlan` 仍创建 `ExecutingCraftingJob`。
  - `hasJob()`、final output、elapsed tracker、waiting/pending/suspended 查询和 cancel 收尾开始识别 ledger job。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 14s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在；编译输出中有既有 deprecated API 警告。
- Phase 2 ledger task 执行：
  - 新增 RED 测试 `CraftingCpuLogicTest.executesNextLedgerPatternTask`，要求 ledger job 激活后 `executeCrafting(1, ...)` 能按 ledger task 推送下一个 pattern，并把预期输出写入 `waitingFor`。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`expected: 1 but was: 0`，当前 `executeCrafting` 仍只读取旧 `ExecutingCraftingJob`。
- GREEN 实现：
  - `ExecutingLedgerCraftingJob.TaskProgress` 改为可变剩余次数。
  - `CraftingCpuLogic.executeCrafting(...)` 在旧 job 为空但 ledger job 存在时，调用 ledger task 执行分支。
  - ledger 执行分支复用 `CraftingCpuHelper.extractPatternInputs(...)`、provider `pushPattern(...)` 和 existing power check，成功推送后把 expected outputs/container items 写入 `waitingFor` 并减少当前 task 次数。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 15s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在；编译输出中有既有 deprecated API 警告。
- 组合验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：`BUILD SUCCESSFUL in 12s`。
  - 已知噪声：非 Git repository 导致的版本检测 fallback 堆栈仍存在。

## 11. 最新工作记录（2026-06-23，继续 Phase 2）

- 按连续性要求重新读取 `STATE.md`、`DESIGN.md`、`MIGRATION_PLAN.md` 以及最近修改的 ledger planner / execution 源码和测试。
- 复现当前 RED：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.executesNextLedgerPatternTask()` 在 `CraftingCpuLogicTest.java:100` 失败；ledger pattern 输出通过 `insert(...)` 回到 CPU 后，`logic.hasJob()` 仍为 true。
  - 根因定位：`CraftingCpuLogic.insert(...)` 的入口和完成语义仍只处理旧 `ExecutingCraftingJob job`，没有处理 `ExecutingLedgerCraftingJob ledgerJob` 的 `waitingFor`、最终输出交付、`remainingAmount` 递减和 `finishLedgerJob(true)`。
- GREEN 实现（待验证）：
  - `CraftingCpuLogic.insert(...)` 在 `ledgerJob != null` 时转入账本执行 job 专用插入分支。
  - 新增 `insertIntoLedgerJob(...)`，复用旧 executor 的语义：只接受 `waitingFor` 中的物品；`MODULATE` 时扣除等待账本并更新时间追踪；最终输出通过 `link.insert(...)` 交付给 requester，递减 `remainingAmount`，归零后调用 `finishLedgerJob(true)` 并清空 CPU 输出显示；非最终输出写回 CPU inventory。
- 单项 GREEN 验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 20s`；`executesNextLedgerPatternTask` 覆盖的最终输出插回后，ledger job 会结束并清空 active 状态。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- 组合回归验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`；账本 planner、账本/旧执行测试和现有 simulation 测试均通过。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在。
- 按用户要求调整推进节奏：后续 Phase 2 将以较大的连续能力切片推进，优先批量覆盖 NBT 持久化、取消/倾倒安全和多步 task execution，但仍保持 RED/GREEN 验证和每个实质步骤更新本文件。
- Phase 2 ledger NBT 持久化：
  - 新增 RED 测试 `CraftingCpuLogicTest.restoresLedgerJobFromNbt`，使用真实 `PatternDetailsHelper.encodeProcessingPattern(...)` 创建可序列化 pattern，提交 2 次 ledger task，执行 1 次后写入 NBT，再读到新的 `CraftingCpuLogic`。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.java:151` 处 `restored.hasJob()` 为 false；根因是 `CraftingCpuLogic.writeToNBT/readFromNBT` 只保存/恢复旧 `job`，账本执行 job 尚未持久化。
- GREEN 实现：
  - `ExecutingLedgerCraftingJob` 新增 NBT 构造器和 `writeToNBT(...)`，保存/恢复 link、final output、waitingFor、timeTracker、remainingAmount、suspended、playerId，以及保序的 task 列表。
  - `CraftingCpuLogic.readFromNBT/writeToNBT` 新增 `ledgerJob` 标签支持；恢复后更新 CPU 显示输出；`getLastLink()` 开始返回 ledger job link。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 18s`。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- Phase 2 ledger tick 调度：
  - 新增 RED 测试 `CraftingCpuLogicTest.tickCraftingLogicExecutesLedgerTask`，要求提交 ledger plan 后，仅调用真实 `tickCraftingLogic(...)` 也能按 CPU op 限制推送 ledger pattern，并写入 `waitingFor`。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.java:196` 失败，`waitingFor` 仍为 0；根因是 `CraftingCpuLogic.tickCraftingLogic(...)` 当前 ledger job 分支只检查取消然后直接返回，没有进入调度循环。
- GREEN 实现：
  - `CraftingCpuLogic.tickCraftingLogic(...)` 将 ledger job 的取消和 suspended 检查接入旧调度循环，随后统一使用 `executeCrafting(...)` 推进任务；CPU 三 tick op 限制保持不变。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 18s`。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- Phase 2 ledger dump 安全：
  - 新增 RED 测试 `CraftingCpuLogicTest.storeItemsRejectsActiveLedgerJob`，要求账本 job 活跃时外部不能调用 `storeItems()` 倾倒 CPU inventory，避免执行中输入被提前倒回网络。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.java:223` 失败；根因是 `storeItems()` 的 precondition 只检查旧 `job == null`，没有检查 `ledgerJob == null`。
- GREEN 实现：
  - `CraftingCpuLogic.storeItems()` 的 precondition 改为同时要求 `job == null && ledgerJob == null`，账本执行中不会被外部倾倒 CPU inventory。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 15s`。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- 本批 Phase 2 组合回归验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`；账本 planner、账本/旧 execution 测试和现有 simulation 测试均通过。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在。
- 当前 Phase 2 已覆盖：
  - `LedgerCraftingPlan` 提交为账本执行 job。
  - 账本 task 顺序保留，不依赖聚合后的 `patternTimes` 驱动执行。
  - `executeCrafting(...)` 和真实 `tickCraftingLogic(...)` 均可推进账本 task。
  - pattern 输出通过 `waitingFor` 回收，最终输出交付后完成 job。
  - 账本 job 可保存/读取 NBT 并恢复 waiting/pending 状态。
  - 活跃账本 job 阻止外部直接倾倒 CPU inventory。
- Phase 2 下一步：
  - 增加多步账本 job 的端到端 tick/insert 完成测试。
  - 增加取消后 dump 行为测试，确认不复制、不丢失已在 CPU inventory 中的输入。
  - 扩展 provider busy / 多 task 调度测试，为 Phase 3 ready queue 优化做基线。
- 命名约束扫描：
  - 命令：`rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner|V2" src\main\java src\test\java -S`
  - 结果：无命中；新增源码和测试没有使用 `V2` 后缀实现名。
- 多步账本执行基线：
  - 新增测试 `CraftingCpuLogicTest.completesMultiStepLedgerJobThroughTicks`，覆盖 child task -> intermediate 回到 CPU inventory -> parent task -> final output 完成 job 的端到端 tick/insert 流程。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`；当前实现已能完成简单多步账本 job。
- Phase 2 ledger op limit：
  - 新增 RED 测试 `CraftingCpuLogicTest.executeLedgerCraftingRespectsMaxPatternsAcrossCompletedTasks`，要求两个独立账本 task 均只剩 1 次时，`executeCrafting(1, ...)` 只能推送 1 个 pattern。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.java:329` 失败；根因是 ledger 执行分支在 task 执行完时先 `continue taskLoop`，跳过了 `pushedPatterns == maxPatterns` 检查，可能单次调用超过 CPU op 上限。
- GREEN 实现：
  - 调整 ledger 执行分支循环控制：task 完成后先移除；如果已经达到 `maxPatterns`，立即跳出本轮执行；否则再继续下一个 task。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 14s`。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- Phase 2 op-limit 组合回归验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`；账本 planner、账本/旧 execution 测试和现有 simulation 测试均通过。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在。
- Phase 3 provider busy 预筛：
  - 新增 RED 测试 `CraftingCpuLogicTest.skipsInputExtractionForLedgerTaskWhenProvidersAreBusy`，要求某个 ledger task 的所有 provider 都 busy 时，executor 不查询该 task 的输入，也不抽取/回滚输入，并继续尝试后续可执行 task。
  - 验证命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：预期 RED，`CraftingCpuLogicTest.java:385` 失败；根因是 ledger 执行分支当前在检查 provider busy 前已经调用 `CraftingCpuHelper.extractPatternInputs(...)`，导致 busy task 仍产生输入扫描/抽取/回滚开销。
- GREEN 实现：
  - ledger 执行分支在抽取 pattern 输入前先收集 provider 并检查是否存在非 busy provider；如果全部 busy，直接跳过该 task，避免输入查询/抽取/回滚，并继续尝试后续 task。
  - 单项验证：`CraftingCpuLogicTest` 通过，`BUILD SUCCESSFUL in 14s`。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在；既有 deprecated API 警告仍存在。
- Phase 3 provider busy 组合回归验证：
  - 命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`；账本 planner、账本/旧 execution 测试和现有 simulation 测试均通过。
  - 已知噪声：非 Git repository 导致版本检测 fallback 堆栈仍存在。
- 当前下一步：
  - 继续 Phase 3 ready queue / provider-aware 调度：减少每 tick 从头扫描全部 task 的开销，并优先执行输入已在 CPU inventory 且 provider 可用的 task。
  - 补取消后 dump 行为测试，确认取消账本 job 后 CPU inventory 中已抽取输入能安全回到网络。
  - 在 ready queue 稳定后，再推进 Phase 4：默认启用账本 planner/executor，旧路径标记为 fallback 或清理。

## 12. 最新工作记录（2026-06-23，Phase 4 默认 planner 切换）

- 按连续性要求重新读取了 `STATE.md`、`DESIGN.md`、`MIGRATION_PLAN.md`，以及最近修改的 `CraftingCalculation`、`CraftingCpuLogic`、`CraftingSimulationTest`、`CraftingCpuLogicTest`。
- 根据用户要求，步子稍微迈大：在 Phase 3 provider busy 已通过的基础上，先推进 Phase 4 的默认 planner 切换，但仍保留旧 planner 显式回退。
- 新增/调整测试：
  - `CraftingSimulationTest.testDefaultPlannerUsesLedgerPlan`：不设置任何开关时，`env.runSimulation(...)` 应返回 `LedgerCraftingPlan`。
  - `CraftingSimulationTest.testLegacyPlannerPropertyUsesTreePlan`：设置 `appeng.crafting.legacyPlanner=true` 时，应回退到旧树 planner，返回非 `LedgerCraftingPlan`。
- RED 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest.testDefaultPlannerUsesLedgerPlan`
  - 结果：预期失败，`CraftingSimulationTest.java:55` 断言失败，说明默认仍走旧 planner；非 Git repository 版本 fallback 堆栈仍为已知噪声。
- 下一步：修改 `CraftingCalculation`，使账本 planner 成为默认路径；新增 `appeng.crafting.legacyPlanner=true` 作为旧 planner fallback。保留 `appeng.crafting.ledgerPlanner=true` 的兼容语义，但不再需要它启用默认路径。

## 13. 最新工作记录（2026-06-23，Phase 4 默认 planner GREEN）

- 实现变更：
  - `CraftingCalculation` 不再要求 `appeng.crafting.ledgerPlanner=true` 才启用账本 planner。
  - 默认路径改为账本 planner。
  - 新增 `appeng.crafting.legacyPlanner=true` 作为旧树 planner 的显式 fallback。
- GREEN 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest.testDefaultPlannerUsesLedgerPlan --tests appeng.crafting.simulation.CraftingSimulationTest.testLegacyPlannerPropertyUsesTreePlan`
  - 结果：成功，`BUILD SUCCESSFUL in 16s`。非 Git repository 版本 fallback 堆栈和既有 deprecation 警告仍为已知噪声。
- 下一步：运行完整 `CraftingSimulationTest` 以及 ledger/execution 组合回归；通过后继续补取消后 dump 行为测试，随后推进 Phase 3 ready/provider-aware 调度。

## 14. 最新工作记录（2026-06-23，Phase 4 默认切换后的回归定位）

- 组合回归命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：失败，54 个测试执行，9 个失败，集中在 `CraftingSimulationTest` 的默认账本 planner 路径。
- 根因定位：
  - `CraftingCalculation.runLedgerPlan` 只通过 `craftingService.getCraftables(...)` 枚举 emitter，漏掉“只能发射、不是 craftable output”的输入，例如 dirt/water/source item。
  - 账本默认路径没有复刻旧 `ChildCraftingSimulationState.ignore(output)` 语义，导致目标输出已有库存时直接提取，递归自合成测试被短路为成功。
  - 失败型 simulation 的 `patternTimes` 需要表达“为完整请求尝试过/需要展示的 pattern 次数”；当前账本 planner 只记录实际可执行次数，因此 missing plan 的 UI 兼容字段不足。
- 下一步：修正 emitter 候选收集、目标输出库存忽略，以及为 `REPORT_MISSING_ITEMS` 的失败计划输出 attempted pattern counts；随后重跑组合回归。

## 15. 最新工作记录（2026-06-23，Phase 4 默认 planner 回归通过）

- 完成默认 planner 切换后的账本 planner 语义补齐：
  - `CraftingCalculation` 默认使用账本 planner；`appeng.crafting.legacyPlanner=true` 作为旧树 planner fallback。
  - 账本默认路径复刻旧 planner 的目标输出库存忽略语义：构建初始库存时跳过 requested output，避免递归自合成被已有库存短路。
  - emitter 收集改为扫描 pattern 输入候选并调用 `craftingService.canEmitFor(...)`，支持“只可发射、不是 craftable output”的输入。
  - 失败计划保留 attempted `patternTimes` 作为 UI/兼容展示字段；实际可执行 task/tree 仍只包含可执行工作。
  - 带 remaining item 的 pattern 采用逐次规划，保证桶、损伤工具等返回物能参与下一次合成。
  - 输入提取按模板数量的完整倍数提取，避免 1000mb 水输入抽走 3500mb 这类非完整倍数。
  - planning byte 统计对请求节点固定开销按 key 去重，避免容器循环把 CPU byte 虚高。
  - fuzzy/damageable 输入可从工作库存中按 `IInput.isValid(...)` 复用返回的变体。
- 测试更新：
  - `CraftingSimulationTest` 默认 planner 测试改为默认返回 `LedgerCraftingPlan`。
  - 新增旧 planner fallback 测试：`appeng.crafting.legacyPlanner=true` 返回旧树 plan。
  - 更新 damaged-output 的 byte 断言：账本懒解析不会为不可用分支额外增加节点字节。
  - `CraftingPlanAssert` 的 KeyCounter 断言增加 key 描述，便于后续失败定位。
  - `LedgerCraftingPlannerTest` 中缺失计划的 `patternTimes` 断言改为 attempted 语义；`CRAFT_LESS` 和成功计划仍断言实际可执行次数。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
  - 已知噪声：workspace 不是 Git repository 导致版本 fallback 堆栈；既有 deprecated API 警告。
- 下一步：继续 Phase 2/3 收尾，补 ledger job 取消后 dump 行为测试，确认取消不会复制或丢失 CPU inventory 中已抽取输入。

## 16. 最新工作记录（2026-06-23，ledger cancel dump 覆盖）

- 新增 execution 防回归测试：
  - `CraftingCpuLogicTest.cancelLedgerJobDumpsStoredInputsBackToNetwork`
  - 覆盖提交 `LedgerCraftingPlan` 后，取消 active ledger job 时，CPU inventory 中已经从网络抽取但尚未推给 provider 的输入会通过 `storeItems()` 回插网络；同时确认 job 状态清空、CPU 本地库存清空、不会重复插入。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 15s`。该测试是防回归基线，当前实现已满足。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
- 下一步：继续 Phase 3 调度优化，重点是减少 ledger executor 每 tick 从头扫描 task 列表的开销，建立 ready/provider-aware 的轻量推进机制。

## 17. 最新工作记录（2026-06-23，Phase 3 ledger executor cursor 调度）

- 新增 RED 测试：
  - `CraftingCpuLogicTest.ledgerExecutionContinuesAfterLastScannedTaskWhenEarlierProviderIsBusy`
  - 场景：task 列表为 busy、ready1、ready2，连续两次 `executeCrafting(1, ...)` 应第一次跳过 busy 推 ready1，第二次从上次扫描位置继续推 ready2，而不是再次从头检查 busy。
  - RED 结果：`busyProvider.isBusy()` 被调用 2 次，说明旧实现每次从头扫描。
- GREEN 实现：
  - `ExecutingLedgerCraftingJob` 新增 `nextTaskIndex` cursor，并写入/读取 NBT。
  - `CraftingCpuLogic.executeLedgerCrafting(...)` 改为从 cursor 开始循环扫描 task；busy task 会推进 cursor，成功/删除 task 后 cursor 落到后继位置。
  - 保留 CPU `maxPatterns` 限制、provider busy 预筛、输入回滚和 waitingFor 行为。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.ledgerExecutionContinuesAfterLastScannedTaskWhenEarlierProviderIsBusy`
  - 结果：成功，`BUILD SUCCESSFUL in 15s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
- 当前阶段状态：
  - Phase 1 planner 已默认接管并保留旧 planner fallback。
  - Phase 2 ledger executor 已覆盖提交、执行、tick、insert、NBT、cancel dump。
  - Phase 3 已完成 provider busy 预筛和 cursor 轮转扫描；尚未实现更重的 ready queue / provider grouping / 批量 push。
- 下一步建议：
  - 继续 Phase 3：为 provider-aware grouping 或 profiling counter 补测试。
  - 或进入 Phase 4 cleanup：把旧 planner 路径明确标为 fallback-only，清理 `appeng.crafting.ledgerPlanner` 相关旧文档/测试命名。

## 18. 最新工作记录（2026-06-23，Phase 4 fallback cleanup）

- 清理旧开关引用：
  - 源码中不再存在 `appeng.crafting.ledgerPlanner` 行为开关。
  - `CraftingSimulationTest` 移除了对旧 `appeng.crafting.ledgerPlanner` 属性的保存/清理，避免误导；当前只保留 `appeng.crafting.legacyPlanner=true` fallback 测试。
- 命名约束扫描：
  - `rg -n "ledgerPlanner|legacyPlanner" src/main/java src/test/java -S`
  - 结果：只剩 `CraftingCalculation` 的 `appeng.crafting.legacyPlanner` fallback 和对应测试。
  - `rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：无命中。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
- 下一步：运行更宽的测试面；若通过，Phase 4 可继续收敛旧 planner fallback-only 标记和内部文档。

## 19. 最新工作记录（2026-06-23，全量测试通过）

- 更宽验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 20s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；不影响测试结果。
- 当前可认为已稳定的范围：
  - 账本 planner 已成为默认 planner。
  - 旧树 planner 仅通过 `appeng.crafting.legacyPlanner=true` 作为 fallback。
  - ledger task-tree execution 已接管 `LedgerCraftingPlan`。
  - ledger execution 已覆盖 NBT、tick、insert、cancel dump、provider busy skip、cursor 轮转调度。
  - 全量测试在当前 workspace 通过。
- 尚未彻底完成的迁移尾项：
  - Phase 3 更进一步的 provider-aware grouping、批量 push、profiling counter 尚未实现。
  - Phase 4 还未移除旧 planner 代码本体；目前是 fallback-only。
  - 真实 dev world smoke test 尚未执行。

## 20. 最新工作记录（2026-06-23，Phase 3 profiling counters）

- 按连续性要求重新读取了 `STATE.md`、`DESIGN.md`、`MIGRATION_PLAN.md`，以及最近修改的 `LedgerCraftingPlanner`、`CraftingCpuLogic`、`ExecutingLedgerCraftingJob` 和相关测试。
- 新增 RED 测试：
  - `LedgerCraftingPlannerTest.exposesPlanningStatsForLargeBatchedPatternRequest`
  - `CraftingCpuLogicTest.exposesLedgerExecutionStatsForProviderScheduling`
  - RED 结果：`compileTestJava` 失败，缺少 `LedgerCraftingPlan.planningStats()` 和 `CraftingCpuLogic.getLedgerExecutionStats()`，说明观测接口尚未存在。
- GREEN 实现：
  - 新增 `CraftingPlanningStats`，并让 `LedgerCraftingPlan` 携带 planner profiling 视图；保留旧构造器以兼容现有测试和调用点。
  - `LedgerCraftingPlanner` 统计 requested amount、request resolutions、unique request keys、pattern attempts 和 planned tasks。
  - 新增 package-private `LedgerExecutionStats`，用于 ledger executor 内部/测试观测。
  - `ExecutingLedgerCraftingJob` 累计 provider lookups、provider busy checks、busy provider skips、input extractions、pattern pushes 和 completed tasks，并写入/读取 NBT。
  - `CraftingCpuLogic.getLedgerExecutionStats()` 暴露当前账本 job 的执行计数快照。
  - 扩展 `CraftingCpuLogicTest.restoresLedgerJobFromNbt`，确认 execution counters 会随 ledger job NBT 恢复。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest.exposesPlanningStatsForLargeBatchedPatternRequest --tests appeng.crafting.execution.CraftingCpuLogicTest.exposesLedgerExecutionStatsForProviderScheduling`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*"`
  - 结果：成功，`BUILD SUCCESSFUL in 15s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 18s`。
  - `rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：无命中；新增源码和测试没有使用 `V2` 后缀实现名。
  - `rg -n "ledgerPlanner|legacyPlanner" src/main/java src/test/java -S`
  - 结果：只剩 `CraftingCalculation` 的 `appeng.crafting.legacyPlanner` fallback 和对应测试；旧 `ledgerPlanner` 开关未回归。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - 继续 Phase 3 provider-aware grouping 或 ready queue。
  - 也可继续 Phase 4 fallback-only 文档/注释收敛和旧 planner 代码清理决策。

## 21. 最新工作记录（2026-06-23，Phase 3 输入未就绪 skip 计数）

- 在 profiling counters 基础上继续推进 ready/provider-aware 调度前置能力。
- 新增 RED 测试：
  - `CraftingCpuLogicTest.skipsLedgerTaskWhenInputsAreNotReadyAndContinuesToReadyTask`
  - 场景：第一个 ledger task 的 provider 可用但输入尚未回到 CPU inventory，第二个 task 输入已就绪；executor 应跳过未就绪 task，继续推送 ready task，并记录输入未就绪 skip。
  - RED 结果：`compileTestJava` 失败，缺少 `LedgerExecutionStats.inputUnavailableSkips()`。
- GREEN 实现：
  - `LedgerExecutionStats` 新增 `inputUnavailableSkips`。
  - `ExecutingLedgerCraftingJob` 新增同名计数并写入/读取 NBT。
  - `CraftingCpuLogic.executeLedgerCrafting(...)` 将 `inputExtractions` 的语义收窄为“实际拿到可推送 crafting container 的次数”；当 `extractPatternInputs(...)` 返回 null 时，改为累计 `inputUnavailableSkips`。
  - 该改动保持后续 task 扫描行为不变，先提供 ready queue 所需的观测和语义分层。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.skipsLedgerTaskWhenInputsAreNotReadyAndContinuesToReadyTask`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - 继续 Phase 3：考虑用更明确的 ready queue/blocked input 重新激活机制，减少未就绪 task 在后续 tick 里反复做输入检查。
  - 或继续 Phase 4：旧 planner fallback-only 注释/文档收敛。

## 22. 最新工作记录（2026-06-23，Phase 3 blocked input 重复扫描抑制）

- 在 `inputUnavailableSkips` 基础上继续推进 ready 调度前置能力：输入未就绪的 ledger task 在 CPU 本地库存没有变化前，不再反复做 pattern input 查询。
- 新增/扩展测试：
  - `CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskInputsUntilInventoryChanges`
    - 第一次执行时 blocked task 因输入缺失被跳过，ready task 被推送。
    - 第二次执行前 CPU inventory 没有变化，blocked task 不再查询 `getPossibleInputs()`。
  - `CraftingCpuLogicTest.rechecksBlockedLedgerTaskInputsAfterInventoryChanges`
    - 中间产物插入 CPU inventory 后，blocked task 会重新检查输入并成功推送。
- RED 验证：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskInputsUntilInventoryChanges`
  - 结果：预期失败，`compileTestJava` 报缺少 `LedgerExecutionStats.inputBlockedSkips()`。
- GREEN 实现：
  - `LedgerExecutionStats` 新增 `inputBlockedSkips`。
  - `ExecutingLedgerCraftingJob.TaskProgress` 新增输入阻塞序号记录：`markInputUnavailable(...)`、`isInputBlocked(...)`、`clearInputUnavailable()`。
  - `CraftingCpuLogic` 新增 `cpuInventoryChangeSerial`，当非最终输出真实插入 CPU inventory 时递增。
  - `executeLedgerCrafting(...)` 在尝试抽取输入前检查 task 是否仍处于同一库存序号的输入阻塞状态；若是，直接跳过并累计 `inputBlockedSkips`。
  - 当输入成功抽取时清除该 task 的 blocked 标记；当输入仍不可用时刷新 blocked 标记。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskInputsUntilInventoryChanges`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskInputsUntilInventoryChanges --tests appeng.crafting.execution.CraftingCpuLogicTest.rechecksBlockedLedgerTaskInputsAfterInventoryChanges`
  - 结果：成功，`BUILD SUCCESSFUL in 16s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 15s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 22s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - Phase 3 可继续推进更完整的 ready queue/provider grouping。
  - Phase 4 可继续旧 planner fallback-only 文档/注释收敛，或评估是否移除旧 planner 本体。

## 23. 最新工作记录（2026-06-23，Phase 3 同 provider 重复 push）

- 继续 Phase 3 provider-aware/batch push 优化。
- 代码阅读结论：
  - `PatternProviderLogic.pushPattern(...)` 会根据 send list、节点活跃、锁模式、目标可接受性等条件决定是否接受。
  - `PatternProviderLogic.isBusy()` 反映 send list 等忙碌状态；因此 executor 若每次 push 前都重新检查 `isBusy()`，可以保守地对同一 provider 做重复 push。
  - 该优化只应用在 ledger executor 路径，旧 executor 保持不动。
- 新增 RED 测试：
  - `CraftingCpuLogicTest.executeLedgerCraftingCanPushSameTaskRepeatedlyToSameAvailableProvider`
  - 场景：同一个 ledger task 剩余 3 次，只有一个 provider，provider 始终不 busy 且每次 `pushPattern` 成功；`executeCrafting(3, ...)` 应在一次调用内推送 3 次。
  - RED 结果：断言失败，旧实现只推送 1 次。
- GREEN 实现：
  - `CraftingCpuLogic.executeLedgerCrafting(...)` 的 ledger provider 分支改为 provider 内 `while` 循环。
  - 每次 push 前都重新检查 `provider.isBusy()` 和能量模拟。
  - 每次成功 push 后：
    - 累计 `patternPushes`；
    - 更新 `waitingFor` 和 `timeTracker`；
    - 递减 task 剩余次数；
    - 若 CPU op 额度未耗尽且 task 仍有剩余，则准备下一份 pattern 输入并继续尝试同一 provider。
  - 若 task 尚未完成但本轮已有进展，则推进 cursor 到后续 task，避免长期偏向同一 task。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.executeLedgerCraftingCanPushSameTaskRepeatedlyToSameAvailableProvider`
  - 结果：成功，`BUILD SUCCESSFUL in 20s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 20s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - Phase 3 还可继续 provider grouping/ready queue 压力测试。
  - Phase 4 可开始收敛 fallback-only 文档和旧 planner 清理策略。

## 24. 最新工作记录（2026-06-23，Phase 3 输入阻塞短路）

- 继续 Phase 3 execution 热路径优化，目标是减少大订单中“剩余 task 全部等待中间产物”时的重复扫描。
- 新增 RED 测试：
  - `CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskProvidersUntilInventoryChanges`
    - 场景：某 ledger task 已确认输入未就绪，CPU inventory 未变化前再次执行时，不应再查询该 task 的 provider。
    - RED 预期：旧实现会先查 provider，再判断 input blocked，导致 provider lookup/isBusy 被重复触发。
  - `CraftingCpuLogicTest.shortCircuitsLedgerExecutionWhenEveryRemainingTaskWaitsForInputs`
    - 场景：所有剩余 task 都因输入未就绪被阻塞，下一次同一 CPU inventory 序号下执行应直接短路返回。
    - RED 结果：`compileTestJava` 失败，缺少 `LedgerExecutionStats.inputBlockedShortCircuits()`。
- GREEN 实现：
  - `CraftingCpuLogic.executeLedgerCrafting(...)` 将已知 input-blocked 检查移动到 provider lookup 之前，避免无意义 provider 查询。
  - `ExecutingLedgerCraftingJob` 新增 `allInputsBlockedAtSerial` 运行期标记；当一整轮扫描没有推进，且所有阻塞原因都只是输入未就绪/已知输入阻塞时，记录当前 `cpuInventoryChangeSerial`。
  - 后续同一 `cpuInventoryChangeSerial` 下再次执行时直接返回 0，并累计 `inputBlockedShortCircuits`。
  - provider busy、能量不足、provider 拒绝 push 等非输入原因不会触发该短路，避免掩盖外部状态变化。
  - `LedgerExecutionStats` 和 ledger job NBT stats 新增 `inputBlockedShortCircuits`，用于测试和 profiling 观察。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotRecheckBlockedLedgerTaskProvidersUntilInventoryChanges --tests appeng.crafting.execution.CraftingCpuLogicTest.shortCircuitsLedgerExecutionWhenEveryRemainingTaskWaitsForInputs`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.execution.*"`
  - 结果：成功，`BUILD SUCCESSFUL in 15s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - 跑全量 `test`，再更新本节或新增记录。
  - 若全量通过，继续 Phase 3 provider grouping/pressure evidence，或推进 Phase 4 fallback-only 收敛。

## 25. 最新验证记录（2026-06-23，Phase 3 输入阻塞短路后全量测试）

- 全量验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 22s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 当前阶段判断：
  - Phase 3 已新增 input-blocked provider lookup 前置跳过和整轮输入阻塞短路。
  - 下一步继续 Phase 3 provider grouping：合并 ledger executor 中相同 pattern 的重复 task，降低 provider lookup 和扫描成本。

## 26. 最新工作记录（2026-06-23，Phase 3 相邻重复 pattern task grouping）

- 继续 Phase 3 provider grouping/pressure evidence。
- 新增 RED 测试：
  - `CraftingCpuLogicTest.groupsDuplicateLedgerPatternTasksBeforeExecution`
  - 场景：`LedgerCraftingPlan` 的 task list 中存在两个相邻且相同 pattern 的 `PatternCraftingTask(pattern, 1)`；executor 应在提交时合并成剩余 2 次的同一执行 task，并利用同 provider 重复 push 在一次 `executeCrafting(2, ...)` 内完成。
  - RED 结果：断言失败，旧实现会对同一 pattern 做两次 provider lookup。
- GREEN 实现：
  - `ExecutingLedgerCraftingJob` 新增 `addPatternTask(...)`，在构建/恢复执行 task 时合并相邻重复 pattern。
  - pattern 比较先走对象 identity；只有对象不同时才尝试比较 `getDefinition()`，并对不支持 definition 的测试/特殊 pattern 保守返回不相同。
  - 初版曾错误合并非相邻重复 pattern，导致 `ExecutingLedgerCraftingJobTest.preservesLedgerTaskOrderWithoutAggregatingPatternTimes` 失败；已修正为只合并相邻重复 task，保留 task tree 顺序语义。
  - `timeTracker` 的 expected output 统计仍按原始 task 次数累加，避免改变 UI/进度语义。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.groupsDuplicateLedgerPatternTasksBeforeExecution`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.execution.*"`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 下一步：
  - 跑全量 `test`。
  - 继续 Phase 3 压力测试/统计证明，或推进 Phase 4 fallback-only 收敛。

## 27. 最新验证记录（2026-06-23，Phase 3 grouping 后全量测试）

- 全量验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 当前阶段判断：
  - Phase 3 已具备 provider busy 预筛、cursor 轮转、同 provider 重复 push、input-blocked provider 前置跳过、整轮输入阻塞短路、相邻重复 pattern task grouping。
  - 仍未完成真实 dev world smoke test。
  - Phase 4 旧 planner 本体仍保留为 `appeng.crafting.legacyPlanner=true` fallback-only。

## 28. 最新工作记录（2026-06-23，Phase 4 断开旧 planner fallback 入口）

- 继续 Phase 4 旧路径退役。
- 新增/修改 RED 测试：
  - `CraftingSimulationTest.testLegacyPlannerPropertyIsIgnored`
  - 场景：即使设置历史属性 `appeng.crafting.legacyPlanner=true`，`CraftingCalculation` 也必须继续返回 `LedgerCraftingPlan`。
  - RED 结果：旧实现仍会切到树 planner，断言失败。
- GREEN 实现：
  - `CraftingCalculation.run()` 改为无条件调用账本 planner。
  - 移除 `LEGACY_PLANNER_PROPERTY` 和 `isLegacyPlannerEnabled()`，旧系统属性不再影响运行路径。
  - 旧树 planner 相关源码暂未删除，但已不再通过 `CraftingCalculation` 运行入口接管计划计算。
- 扫描结果：
  - `rg -n "legacyPlanner|ledgerPlanner" src/main/java src/test/java -S`
  - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 保存/设置该历史属性以验证其被忽略。
  - `rg -n "CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：无命中。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest.testLegacyPlannerPropertyIsIgnored`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 13s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 当前阶段判断：
  - 账本 planner 和 ledger task-tree executor 已成为唯一运行入口。
  - 旧树 planner 类本体仍在源码中，作为后续源码清理对象；真实 dev world smoke test 仍未执行。

## 29. 最新工作记录（2026-06-23，Phase 4 删除旧树 planner 本体）

- 继续 Phase 4 旧路径退役和源码收敛。
- 清理内容：
  - `CraftingCalculation` 收窄为账本 planner 入口，移除旧树 planner 的暂停/分片模拟、二分 `CRAFT_LESS` 重算、missing 汇总、旧日志 attempt 等死代码。
  - 删除旧树 planner 类：
    - `src/main/java/appeng/crafting/CraftingTreeNode.java`
    - `src/main/java/appeng/crafting/CraftingTreeProcess.java`
    - `src/main/java/appeng/crafting/CraftBranchFailure.java`
  - 移除 `CraftingSimulationState.buildCraftingPlan(...)`，该方法只服务旧树 planner。
  - 移除 `TickHandler` 中旧 crafting simulation tick 队列和 `registerCraftingSimulation(...)`。
  - `SimulationEnv.runSimulation(...)` 改为直接同步调用账本 `CraftingCalculation.run()`。
- 扫描结果：
  - `rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
  - 结果：无命中。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.simulation.CraftingSimulationTest.testDefaultPlannerUsesLedgerPlan --tests appeng.crafting.simulation.CraftingSimulationTest.testLegacyPlannerPropertyIsIgnored`
  - 结果：成功，`BUILD SUCCESSFUL in 16s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*" --tests "appeng.crafting.execution.*" --tests appeng.crafting.simulation.CraftingSimulationTest`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 21s`。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈；既有 deprecated API 警告不属于本次变更。
- 当前阶段判断：
  - 旧树 planner 运行入口和类本体已删除。
  - 当前剩余主要验证缺口是真实 dev world smoke test。

## 30. 最新验证记录（2026-06-23，game-test smoke）

- 旧符号/命名扫描：
  - `rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
  - 结果：无命中。
  - `rg -n "legacyPlanner|ledgerPlanner|CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 中验证历史 `appeng.crafting.legacyPlanner` 属性被忽略的测试代码。
- Gradle run 配置检查：
  - `gradle tasks --all` 曾因 Spotless 任务创建阶段网络 `Connection reset` 失败，未作为代码失败处理。
  - 本地扫描 `build.gradle` 后确认存在 `gametest` run 配置，类型为 `gameTestServer`。
- game-test smoke 命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon runGametest`
  - 结果：成功，`BUILD SUCCESSFUL in 22s`。
  - Minecraft/NeoForge 服务端成功启动并关闭。
  - GameTest 输出：`68 GAME TESTS COMPLETE`，`All 68 required tests passed :)`。
  - 已知噪声：`server.properties` 首次不存在会打印加载失败堆栈；随后服务端继续正常启动并完成 game tests。workspace 不是 Git repository，Gradle 版本检测仍打印 fallback 堆栈。
- 当前阶段判断：
  - Phase 1-4 的核心迁移、execution 接管、调度优化、旧树 planner 入口/本体清理均已完成并通过单元测试、主链组合测试、全量测试和 game-test smoke。
  - 后续若继续，可转入更细的 profiling/性能基准或人工真实存档长单压测；这属于迁移完成后的增强验证，不再是当前迁移的阻塞项。

## 31. 最新工作记录（2026-06-23，文档和状态收口）

- 根据当前源码事实同步迁移文档：
  - `DESIGN.md` 的 CPU 执行数据流已从“第二阶段引入执行”改为“账本式 planner/executor 已接管生产路径”。
  - `DESIGN.md` 的兼容策略已说明：`LedgerCraftingPlan` 继续暴露公共聚合字段，但账本式执行不再由 `patternTimes` 驱动。
  - `MIGRATION_PLAN.md` 的 Phase 2-4 已更新为当前完成形态：账本式 job 接管、调度热路径优化、旧树 planner 入口和源码本体删除。
  - `STATE.md` 顶部当前阶段、正在做、未完成事项和关键文件索引已同步，避免上下文恢复时误判旧 planner 仍为 fallback-only。
- 文档/源码扫描：
  - `rg -n "继续交给旧执行器|第二阶段引入 V2|旧 planner 可以|debug fallback|旧树 planner 仅|代码本体仍保留|非 V2 plan" docs/crafting-v2-migration/DESIGN.md docs/crafting-v2-migration/MIGRATION_PLAN.md -S`
  - 结果：无命中。
  - `rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
  - 结果：无命中。
  - `rg -n "legacyPlanner|ledgerPlanner|CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 中验证历史 `appeng.crafting.legacyPlanner` 属性被忽略的测试代码。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 3s`，9 个 task 全部 up-to-date。文档收口后又复跑一次同一命令，结果仍为 `BUILD SUCCESSFUL in 3s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon runGametest`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`；GameTest 输出 `68 GAME TESTS COMPLETE` 和 `All 68 required tests passed :)`。
- 当前阶段判断：
  - Crafting V2 核心迁移已经按计划完成；当前不再有必须阻塞迁移完成的开发项。
  - 后续推荐转入真实存档长单压测、性能 profile 输出增强和更激进的 ready queue/provider grouping 优化。
  - 已知噪声：workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈。

## 32. 最新完成审计（2026-06-23，目标完成确认）

- 本轮先重新读取并核对：
  - `docs/crafting-v2-migration/STATE.md`
  - `docs/crafting-v2-migration/DESIGN.md`
  - `docs/crafting-v2-migration/MIGRATION_PLAN.md`
  - `CraftingCalculation.java`
  - `CraftingCpuLogic.java`
  - `ExecutingLedgerCraftingJob.java`
- 对目标要求逐项审计：
  - 账本式 planner：`CraftingCalculation.run()` 当前只调用 `runLedgerPlan()`；`runLedgerPlan()` 收集初始库存、craftable pattern map、emitter set 并调用 `LedgerCraftingPlanner`。
  - task tree execution：`CraftingCpuLogic.trySubmitJob(...)` 对 `LedgerCraftingPlan` 创建 `ExecutingLedgerCraftingJob`；ledger job 按 `plan.tasks()` 构建执行 task。
  - CPU 执行接管：`CraftingCpuLogic` 覆盖 ledger submit、tick、insert、cancel、NBT read/write、pending output/menu 查询、job owner notification。
  - 调度优化：ledger executor 已具备 provider busy 预筛、cursor 轮转、同 provider 重复 push、input blocked 跳过/短路、相邻重复 pattern task grouping 和 execution stats。
  - 旧路径退役：旧树 planner 类和旧 simulation tick 入口已删除；历史 `legacyPlanner` 属性仅在测试里验证被忽略；`ledgerPlanner` 行为开关无生产源码残留。
  - 命名约束：生产源码没有 `CraftingPlanV2`、`CraftingRequestV2`、`CraftingContextV2`、`CraftingTaskV2`、`CraftingResolverV2`、`ExecutingCraftingJobV2`、`LedgerCraftingPlannerV2`、`V2Planner` 等实现名。
  - 文档连续性：`STATE.md`、`DESIGN.md`、`MIGRATION_PLAN.md` 已同步为当前完成形态。
- 扫描验证：
  - `rg -n "继续交给旧执行器|第二阶段引入 V2|旧 planner 可以|debug fallback|旧树 planner 仅|代码本体仍保留|非 V2 plan" docs/crafting-v2-migration/DESIGN.md docs/crafting-v2-migration/MIGRATION_PLAN.md -S`
  - 结果：无命中。
  - `rg -n "CraftingTreeNode|CraftingTreeProcess|CraftBranchFailure|buildCraftingPlan\(|runCraftAttempt|computePlan\(|registerCraftingSimulation|craftingJobs|simulateFor\(" src/main/java src/test/java -S`
  - 结果：无命中。
  - `rg -n "legacyPlanner|ledgerPlanner|CraftingPlanV2|CraftingRequestV2|CraftingContextV2|CraftingTaskV2|CraftingResolverV2|ExecutingCraftingJobV2|LedgerCraftingPlannerV2|V2Planner" src/main/java src/test/java -S`
  - 结果：`src/main/java` 无命中；`src/test/java` 只剩 `CraftingSimulationTest` 中验证历史 `appeng.crafting.legacyPlanner` 属性被忽略的测试代码。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 3s`，9 个 task 全部 up-to-date。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon runGametest`
  - 结果：成功，`BUILD SUCCESSFUL in 14s`；GameTest 输出 `68 GAME TESTS COMPLETE` 和 `All 68 required tests passed :)`。
- 当前结论：
  - 按 `docs/crafting-v2-migration` 的状态、设计和迁移计划，AE2 Crafting V2 深度迁移的必需范围已完成。
  - 当前无必须继续推进的迁移阻塞项；剩余是真实存档长单 profiling/压测和进一步调度优化等增强项。

## 33. 最新工作记录（2026-06-23，5000 万物品格雷风格订单基准）

- 新增测试：
  - `LedgerCraftingPlannerTest.plansGregtechScaleFiftyMillionItemOrderByGraphShape`
- 测试场景：
  - 最终订单：`50_000_000` 个最终机器物品。
  - 配方图模拟格雷包常见多层结构：机器本体依赖板、线路、框架；线路继续依赖线缆和板；板、线缆、框架继续依赖基础材料。
  - 物品量级：
    - 根 pattern：`50_000_000` 次。
    - circuit pattern：`100_000_000` 次。
    - plate pattern：`300_000_000` 次。
    - frame pattern：`50_000_000` 次。
    - wire pattern：`200_000_000` 次。
    - 基础材料消耗：`700_000_000` raw metal、`200_000_000` raw wire metal。
- 测试断言：
  - 计划必须成功且无 missing。
  - `patternTimes` 必须按上述量级精确记录。
  - `planningStats.patternAttempts()` 必须为 `6`，`plannedTasks()` 必须为 `6`，证明 planner 按依赖图形状批量规划，而不是随 5000 万请求线性展开 task/attempt。
  - task list size 必须为 `6`。
- 观测输出：
  - XML 报告中 `system-out` 记录：`fiftyMillionPlanningMillis=5, patternAttempts=6, plannedTasks=6`。
  - 注意：该毫秒数是本机单次单元测试环境观测，不是严格 JMH 基准；稳定结论应以 task/attempt 计数为主。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest.plansGregtechScaleFiftyMillionItemOrderByGraphShape`
  - 结果：成功，`BUILD SUCCESSFUL in 11s`。
  - 报告读取：`Select-String -Path 'build/test-results/test/TEST-appeng.crafting.ledger.LedgerCraftingPlannerTest.xml' -Pattern 'fiftyMillion' -Context 0,1`
  - 结果：命中 `fiftyMillionPlanningMillis=5, patternAttempts=6, plannedTasks=6`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*"`
  - 结果：成功，`BUILD SUCCESSFUL in 12s`。
- 已知噪声：
  - workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈。

## 34. 最新工作记录（2026-06-23，材料不足时 CRAFT_LESS 对照基准）

- 新增测试：
  - `LedgerCraftingPlannerTest.comparesInsufficientGregtechScaleCraftLessWithBinaryRetryStrategy`
- 测试场景：
  - 最终订单：请求 `50_000_000` 个最终机器物品。
  - 材料上限：基础材料只够合成 `25_000_000` 个最终机器物品。
  - 配方图仍采用格雷包常见多层结构：机器本体依赖线路和框架；线路依赖线缆和板；板、线缆、框架分别依赖独立基础材料，避免多个分支抢同一原料导致可合成量被额外压低。
- 对照方式：
  - 新账本算法直接使用 `CalculationStrategy.CRAFT_LESS`，由请求账本自然返回最大可满足数量。
  - 旧二分策略因旧树 planner 源码已删除，当前用同一个 `LedgerCraftingPlanner` 模拟旧 `CRAFT_LESS` 外壳：先按完整请求用 `REPORT_MISSING_ITEMS` 规划一次，失败后对数量做二分，每次重新跑完整 planner。
  - 这个对照低估真实旧树 planner 成本，因为它只模拟“二分重跑次数”外壳，没有恢复旧树 planner 自身的递归树、分支回滚和逐份退化开销。
- 观测输出：
  - 专项测试单独运行后 XML 报告记录：`insufficientFiftyMillionLedgerMillis=5, ledgerPatternAttempts=5, ledgerPlannedTasks=5, binaryRetryMillis=5, binaryPlanRuns=26, binaryPatternAttempts=130`。
  - `ledger.*` 专项全量运行后 XML 报告记录：`fiftyMillionPlanningMillis=0, patternAttempts=6, plannedTasks=6` 和 `insufficientFiftyMillionLedgerMillis=0, ledgerPatternAttempts=5, ledgerPlannedTasks=5, binaryRetryMillis=3, binaryPlanRuns=26, binaryPatternAttempts=130`。
  - 单次 JUnit 毫秒数受 JIT、缓存、系统调度和测试顺序影响很大，不作为严格性能结论；稳定结论是账本算法只做 `1` 次规划、`5` 次 pattern attempt、`5` 个 planned task，而二分重跑外壳需要 `26` 次 planner run、累计 `130` 次 pattern attempt。
- 验证命令：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests appeng.crafting.ledger.LedgerCraftingPlannerTest.comparesInsufficientGregtechScaleCraftLessWithBinaryRetryStrategy`
  - 结果：成功，`BUILD SUCCESSFUL in 10s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test --tests "appeng.crafting.ledger.*"`
  - 结果：成功，`BUILD SUCCESSFUL in 10s`。
  - `Select-String -Path 'build/test-results/test/TEST-appeng.crafting.ledger.LedgerCraftingPlannerTest.xml' -Pattern 'insufficientFiftyMillion|fiftyMillion' -Context 0,1`
  - 结果：命中上述 `fiftyMillion...` 和 `insufficientFiftyMillion...` 输出。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; <本地 Gradle 8.12.1> --no-daemon test`
  - 结果：成功，`BUILD SUCCESSFUL in 17s`。
- 已知噪声：
  - workspace 不是 Git repository，Gradle 版本检测打印 fallback 堆栈。

## 35. 最新工作记录（2026-06-23，GTNH AE2 自动合成全链路调研）

- 新增调研文档：
  - `docs/crafting-v2-migration/GTNH_AE2_CRAFTING_RESEARCH.md`
- 调研范围：
  - 不只覆盖下单计算，还覆盖完整合成链路：请求发起、异步计划、确认界面、CPU 选择、提交、初始抽取、pattern 推送、waitingFor、完成/取消、NBT 持久化和 UI 状态。
- 已阅读/对比的 GTNH 关键文件：
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingJobV2.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingContext.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingRequest.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/CraftingCalculations.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/CraftingTask.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/CraftableItemResolver.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/crafting/v2/resolvers/ExtractItemResolver.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/me/cache/CraftingGridCache.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/container/implementations/ContainerCraftConfirm.java`
  - `Applied-Energistics-2-Unofficial-master/src/main/java/appeng/core/sync/packets/PacketCraftingTreeData.java`
- 已阅读/对比的当前关键文件：
  - `src/main/java/appeng/crafting/CraftingCalculation.java`
  - `src/main/java/appeng/crafting/ledger/LedgerCraftingPlanner.java`
  - `src/main/java/appeng/crafting/ledger/CraftingRequest.java`
  - `src/main/java/appeng/crafting/ledger/CraftingPlanningContext.java`
  - `src/main/java/appeng/crafting/execution/CraftingCpuLogic.java`
  - `src/main/java/appeng/crafting/execution/ExecutingLedgerCraftingJob.java`
  - `src/main/java/appeng/me/service/CraftingService.java`
  - `src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java`
  - `src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java`
  - `src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java`
- 主要结论：
  - GTNH 强在完整 resolver 插件体系、substitution/fuzzy group、pattern parent deny-list、复杂 crafting pattern detector、crafting tree UI 序列化、job merge、missing mode、诊断/通知/跟随合成等成熟生态能力。
  - 当前实现已经在普通大订单 planning、`CRAFT_LESS` 一次账本结算和 ledger task-list CPU 执行方面具备较好结构；CPU 执行层没有像 GTNH 那样回落到 `Map<pattern,count>` 聚合执行，而是由 `ExecutingLedgerCraftingJob` 保留 task list、cursor、input-blocked cache 和 profiling counters。
  - 当前主要短板是 resolver 抽象、完整 substitution/fuzzy 语义、复杂配方检测、完整 missing/ignore-missing 执行语义、确认界面 task tree 展示和 busy CPU job merge。
- 复杂度分析已写入文档：
  - GTNH 普通非复杂 planning：约 `O(K + R log R + E + F + B)`，复杂 pattern 为 `O(Q * complexRecipeCost + Q * inputResolutionCost)`。
  - 当前普通 planning：约 `O(P_total_scan + K + E + P + B)`，remaining-input/容器相关保守路径为 `O(Q * E_remaining)`。
  - 两边 CPU 执行完整 job 都必须随真实 pattern push 总数 `M` 增长；当前优化重点是降低调度扫描、provider busy 和 input-blocked 的额外开销。
- 文档自检：
  - `Select-String -Path 'docs/crafting-v2-migration/GTNH_AE2_CRAFTING_RESEARCH.md' -Pattern '时间复杂度|GTNH CPU execution|当前 CPU execution|关键逻辑差距|推荐后续方向|完整流程'`
  - 结果：命中完整流程、关键逻辑差距、时间复杂度、GTNH/当前 CPU execution 和推荐后续方向章节。
  - `rg -n "TODO|TBD|占位|待补" docs/crafting-v2-migration/GTNH_AE2_CRAFTING_RESEARCH.md`
  - 结果：无命中。

## 36. 最新工作记录（2026-06-23，执行层批量外部库存推送）

- 当前目标：
  - 在合成 CPU 执行阶段，为支持 `supportsPushInputsToExternalInventory()` 的样板增加“批量外部库存推送”路径。
  - CPU 只通过能力接口判断 provider，不绑定具体的样板供应器方块类型。
  - 批量 provider 方法只走外部库存推送语义，不走 `ICraftingMachine`，因此分子装配室配方、石切配方、锻造配方等不参与该优化。
- 已确认设计：
  - 若样板不支持推入外部库存，保持原版逐份派发。
  - 若候选 provider 不实现批量能力、busy、能量不足、批量抽取失败或批量推送失败，则回退逐份派发。
  - 批量成功后，CPU 一次登记 N 次总输出和容器物品，task 剩余次数清零并移除。
  - provider 的 `sendList` 继续作为内部缓存和 busy 背压语义。
- 正在做：
  - 先补执行层 RED 测试，再实现接口、批量抽取 helper、CPU 调度分支和样板供应器逻辑。
- 最近一次验证命令和结果：
  - RED：`C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest.bulkPushesExternalInventoryLedgerTaskInOneDispatch --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotBulkPushPatternsThatRejectExternalInventoryInputs`
  - 结果：预期失败，`compileTestJava` 找不到 `IBulkCraftingProvider` 和 `pushPatternBatchToExternalInventory(...)`，证明新增测试先于实现暴露缺口。
  - GREEN：同一命令在新增 `IBulkCraftingProvider`、批量抽取 helper、CPU 批量分支和 `PatternProviderLogic` 批量外部库存路径后通过，`BUILD SUCCESSFUL in 13s`。
  - 回退验证：新增 `fallsBackToSingleDispatchWhenBulkProviderRejectsBatch` 后运行三条批量相关测试，结果成功，`BUILD SUCCESSFUL in 15s`。
  - 执行层回归：`...\gradle-8.12.1\bin\gradle.bat --no-daemon test --tests appeng.crafting.execution.CraftingCpuLogicTest`，结果成功，`BUILD SUCCESSFUL in 12s`。
  - 全量单元测试：`...\gradle-8.12.1\bin\gradle.bat --no-daemon test`，结果成功，`BUILD SUCCESSFUL in 17s`。
  - GameTest 初次验证：`...\gradle-8.12.1\bin\gradle.bat --no-daemon runGametest` 失败，`pattern_provider_faces_round_robin` 和 `blockingmode_subnetwork_chesttest` 失败。根因是普通样板供应器批量接收整批输入会破坏多外部目标 round-robin 和阻挡模式逐份检查语义。
  - 修复：`PatternProviderLogic.pushPatternBatchToExternalInventory(...)` 仅在非阻挡模式且只有一个外部目标时接受批量；否则返回 false，让 CPU 回退逐份派发。
  - GameTest 复验：同一 `runGametest` 命令成功，`68 GAME TESTS COMPLETE`，`All 68 required tests passed :)`，`BUILD SUCCESSFUL in 14s`。
  - 最终全量单元测试：`...\gradle-8.12.1\bin\gradle.bat --no-daemon test`，结果成功，`BUILD SUCCESSFUL in 20s`。

## 37. 最新工作记录（2026-06-23，GTL 一次发完 mixin 调研）

- 调研目标：
  - 回答 GTL 的 `CraftingCpuLogicNewMixin` 是否把“一次发完”泛化给普通样板供应器，或只针对专用 provider。
- 已反汇编的 GTL 类：
  - `org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicNewMixin`
  - `org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicOldMixin`
  - `org.gtlcore.gtlcore.mixin.ae2.logic.PatternProviderLogicMixin`
  - `org.gtlcore.gtlcore.integration.ae2.AEUtils`
  - `org.gtlcore.gtlcore.api.machine.trait.IMEPatternPartMachine`
  - `org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine`
  - `org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine$InternalSlot`
  - `org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine$PendingRefundData`
- 关键结论：
  - GTL 的“一次发完”不是对普通样板供应器的 sendList 语义做泛化，而是在 CPU 执行里特判 `provider instanceof IMEPatternPartMachine`。
  - 只有当样板是 `AEProcessingPattern` 且 provider 是 `IMEPatternPartMachine` 时，CPU 调用 `AEUtils.extractForMEPatternBuffer(pattern, inventory, taskProgress.value, expectedOutputs)` 一次抽取全部剩余次数的输入。
  - 随后 GTL 仍调用普通 `provider.pushPattern(pattern, inputHolder)`；批量能力由 `MEPatternBufferPartMachine.pushPattern(...)` 自己承接。
  - `MEPatternBufferPartMachine` 是 GTL 自己的“ME样板缓存/缓冲部件”，实现 `ICraftingProvider` 和 `IMEPatternPartMachine`。其 `pushPattern` 按样板槽把整批输入写入对应 `InternalSlot` 的物品/流体 map，`isBusy()` 恒为 false。
  - `AEUtils.extractForMEPatternBuffer` 对每个输入槽按 `input.getMultiplier() * taskTimes` 抽取材料，并按 `output.amount() * taskTimes` 一次登记预期输出。
  - `PatternProviderLogicMixin` 只在 `updatePatterns` 后从阻挡模式输入集合中移除格雷集成电路，不是一套普通样板供应器批量 sendList 逻辑。
- 对当前实现的含义：
  - GTL 提供了“专用 provider 自己声明并承接大批量内部缓存”的证据，而不是“普通样板供应器在阻挡模式/多面目标下也安全批量”的证据。
  - 若要完全仿照 GTL，更接近的路线是让未来格雷样板供应器/ME样板缓存类 provider 实现批量能力；普通样板供应器继续保守回退，除非额外实现逐轮阻挡检查和多目标轮询语义。
- 最近一次验证命令和结果：
  - `javap -classpath <gtlcore;appliedenergistics2;gtceu;gtmthings;ldlib> -c -p org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicNewMixin`
    - 结果：确认 `AEProcessingPattern && provider instanceof IMEPatternPartMachine` 时走 `AEUtils.extractForMEPatternBuffer(..., taskProgress.value, ...)`，成功后 task value 置 0；普通 provider 仍逐份抽取和递减 1。
  - `javap -classpath <...> -c -p org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine`
    - 结果：确认 `pushPattern` 写入 `InternalSlot`，`isBusy()` 恒为 false，`checkInput` 只允许物品/流体 key。
  - `javap -classpath <...> -c -p org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine$InternalSlot`
    - 结果：确认 `InternalSlot.pushPattern` 调用 `AEUtils.pushInputsToMEPatternBufferInventory`，最终累加到内部物品/流体库存。

## 38. 最新工作记录（2026-06-24，GTL ME样板总成批量派发结论文档）

- 新增文档：
  - `docs/crafting-v2-migration/GTL_ME_PATTERN_BUFFER_BULK_RESEARCH.md`
- 文档目的：
  - 单独记录 GTL 的“一次发完”优化没有应用到 AE2 普通样板供应器。
  - 明确该优化是格雷科技现代版 **ME样板总成** 的特供 provider 优化。
  - 明确后续可能回退当前普通 `PatternProviderLogic` 上的 `IBulkCraftingProvider` 实现。
- 已确认结论：
  - GTL CPU 批量分支只在 `patternDetails instanceof AEProcessingPattern` 且 `provider instanceof IMEPatternPartMachine` 时触发。
  - 普通 AE2 样板供应器仍走原版逐份 `extractPatternInputs(...)`、`pushPattern(...)`、task 递减 1。
  - `PatternProviderLogicMixin` 只移除格雷集成电路的阻挡模式输入判定，不包含批量 sendList 逻辑。
  - 更贴近 GTL 的后续方向是保留 CPU 侧能力接口，但让普通 AE2 样板供应器回退逐份派发；由未来格雷样板供应器或 ME样板总成类 provider 自己实现批量承接。
- 后续可能回退范围：
  - 从 `PatternProviderLogic` 移除 `IBulkCraftingProvider` 实现。
  - 删除或禁用 `PatternProviderLogic.pushPatternBatchToExternalInventory(...)`。
  - 保留 `IBulkCraftingProvider` 和 CPU 侧能力判断，供专用 provider 实现。
  - 修改测试：普通样板供应器只验证安全回退；批量成功测试改用测试 provider 或未来格雷 provider stub。
- 最近一次验证命令和结果：
  - `rg -n "没有应用到 AE2 普通样板供应器|可能回退|PatternProviderLogic|IMEPatternPartMachine|ME样板总成" docs/crafting-v2-migration/GTL_ME_PATTERN_BUFFER_BULK_RESEARCH.md docs/crafting-v2-migration/STATE.md -S`
    - 结果：命中文档和状态文件中的核心结论、回退范围和关键类名。

## 39. 最新工作记录（2026-06-24，GTM ME样板总成批量派发实现）

- 当前目标：
  - 按 GTL 的“一次发完”语义，为格雷科技现代版 1.21.1 的 **ME样板总成** 接入合成 CPU 批量派发。
  - 不把该优化泛化到 AE2 普通样板供应器。
- 已确认的 GTM 1.21.1 源码结论：
  - `com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine` 直接实现 `ICraftingProvider`，没有 GTL 的 `IMEPatternPartMachine` 接口名。
  - `pushPattern(IPatternDetails, KeyCounter[])` 会检查结构、ME 节点、样板槽和输入类型，然后把整批输入写入对应内部槽。
  - `InternalSlot.pushPattern(...)` 调用 `patternDetails.pushInputsToExternalInventory(inputHolder, this::add)`，把物品/流体累加进内部库存。
  - `isBusy()` 恒为 `false`。
  - 简体中文翻译为 `ME样板总成`，镜像为 `ME样板总成镜像`；镜像本身不实现 `ICraftingProvider`。
- 已完成事项：
  - 新增执行层测试 `bulkPushesGregTechModernPatternBufferInOneDispatch`，用 GTM 同包名 stub 验证 `ME样板总成` 一次接收剩余 N 次输入。
  - 新增执行层测试 `doesNotBatchPlainProviderThatDoesNotDeclareBulkCapability`，验证未声明批量能力的普通 provider 仍逐份派发。
  - `CraftingCpuLogic` 新增格雷 ME样板总成识别：通过类名匹配 GTM 1.21.1 类型，避免硬依赖 GTM 编译类。
  - 对格雷 ME样板总成的批量分支限定为 `AEProcessingPattern`，并调用 provider 原生 `pushPattern(...)` 承接整批输入，贴近 GTL 语义。
  - `PatternProviderLogic` 已移除 `IBulkCraftingProvider` 实现和普通样板供应器批量外部库存推送方法，使 AE2 普通样板供应器回到逐份派发语义。
- 正在做的事项：
  - 当前 GTM ME样板总成批量派发落地与验证已完成；后续可继续做真实整合包联调或进一步扩展格雷 provider 类型。
- 风险和待验证点：
  - 已通过 `runGametest` 确认普通样板供应器相关 GameTest 无回归。
  - `IBulkCraftingProvider` 仍保留为未来第三方/专用 provider 的显式批量能力接口。
  - 尚未在真实新整合包运行环境中联调；当前通过 GTM 同包名测试 stub 验证无硬依赖识别与批量输入语义。
- 最近一次验证命令和结果：
  - RED：`test --tests appeng.crafting.execution.CraftingCpuLogicTest.bulkPushesGregTechModernPatternBufferInOneDispatch --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotBatchPlainProviderThatDoesNotDeclareBulkCapability`
    - 结果：预期失败，`bulkPushesGregTechModernPatternBufferInOneDispatch` 中 `provider.lastInputHolder[0]` 只有 `1` 份输入，说明 CPU 尚未识别 GTM `MEPatternBufferPartMachine` 批量路径。
  - GREEN：同一命令在新增格雷 ME样板总成识别、回退普通 `PatternProviderLogic` 批量实现、并将 GTM 测试样板改为真实 `AEProcessingPattern` 后通过。
    - 结果：`BUILD SUCCESSFUL in 13s`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - 执行层全类回归：`test --tests appeng.crafting.execution.CraftingCpuLogicTest`
    - 结果：`BUILD SUCCESSFUL in 12s`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - 全量单元测试：`test`
    - 结果：`BUILD SUCCESSFUL in 25s`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - GameTest smoke：`runGametest`
    - 结果：`BUILD SUCCESSFUL in 14s`，输出 `68 GAME TESTS COMPLETE` 和 `All 68 required tests passed :)`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - 文档/代码自检：
    - `rg -n "PatternProviderLogic implements .*IBulkCraftingProvider|pushPatternBatchToExternalInventory" src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java src/main/java src/test/java -S`
      - 结果：普通 `PatternProviderLogic` 无命中；仅剩 CPU 调用点、`IBulkCraftingProvider` 接口和测试专用 provider。
    - `rg -n "GTM 1\\.21\\.1|ME样板总成|MEPatternBufferPartMachine|AEProcessingPattern|PatternProviderLogic.*不实现" docs/crafting-v2-migration/GTL_ME_PATTERN_BUFFER_BULK_RESEARCH.md docs/crafting-v2-migration/STATE.md -S`
      - 结果：命中文档和状态文件中的 GTM 格雷特供批量派发结论；后续第 40 条已进一步明确不做 GTL 适配。

## 40. 最新工作记录（2026-06-24，缩减 GTL 适配语义）

- 当前目标：
  - 不再对 GTL 的 `IMEPatternPartMachine` 或 GTL 类名做运行时适配。
  - 批量特判只服务新整合包中实际使用的 GTM 1.21.1 `ME样板总成`。
- 已完成事项：
  - 新增测试接口 stub：`src/test/java/org/gtlcore/gtlcore/api/machine/trait/IMEPatternPartMachine.java`。
  - 新增执行层 RED 测试：`CraftingCpuLogicTest.doesNotBatchGtlPatternPartInterface`，验证只实现 GTL 接口的 provider 不应触发一次发完。
  - `CraftingCpuLogic` 已移除 GTL 接口和 GTLCore 类名匹配，只保留 GTM 1.21.1 `com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine`。
  - `CraftingCpuLogic` 不再递归匹配 provider 接口名，GTM 特判只检查类/父类名。
  - `GTL_ME_PATTERN_BUFFER_BULK_RESEARCH.md` 已更新当前落地语义：不是 GTL 兼容层，只服务新整合包的 GTM 1.21.1 **ME样板总成**。
- 正在做的事项：
  - 当前 GTL 适配语义缩减已完成；后续仅需在真实新整合包环境中联调 GTM 1.21.1 `ME样板总成`。
- 最近一次验证命令和结果：
  - RED：`test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotBatchGtlPatternPartInterface`
    - 结果：预期失败，断言位置 `CraftingCpuLogicTest.java:234`；当前输入 holder 仍为 50 份，说明 GTL 接口名仍触发了批量路径。
  - GREEN：`test --tests appeng.crafting.execution.CraftingCpuLogicTest.doesNotBatchGtlPatternPartInterface --tests appeng.crafting.execution.CraftingCpuLogicTest.bulkPushesGregTechModernPatternBufferInOneDispatch`
    - 结果：`BUILD SUCCESSFUL in 21s`；GTL 接口不再批量，GTM 1.21.1 `ME样板总成` 批量仍通过。已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - 执行层全类回归：`test --tests appeng.crafting.execution.CraftingCpuLogicTest`
    - 结果：`BUILD SUCCESSFUL in 13s`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。
  - 源码自检：`rg -n "org\\.gtlcore|IMEPatternPartMachine|GTLCore|GTL 适配|GTM/GTL|GREGTECH_PATTERN_BUFFER_TYPES|isGregTechPatternBufferProvider" src/main/java/appeng/crafting/execution/CraftingCpuLogic.java src/main/java -S`
    - 结果：无命中，`src/main/java` 不再包含 GTL 运行时适配匹配项。
  - 源码自检：`rg -n "GREGTECH_MODERN_PATTERN_BUFFER_TYPES|isGregTechModernPatternBufferProvider|com\\.gregtechceu\\.gtceu\\.integration\\.ae2\\.machine\\.MEPatternBufferPartMachine" src/main/java/appeng/crafting/execution/CraftingCpuLogic.java -S`
    - 结果：命中 GTM 1.21.1 `MEPatternBufferPartMachine` 唯一特判。
  - 全量单元测试：`test`
    - 结果：`BUILD SUCCESSFUL in 23s`；已知噪声仍是非 Git 仓库导致的版本检测 fallback 堆栈。

## 41. 最新工作记录（2026-06-24，GTL AE2 mixin 全量调研）

- 当前目标：
  - 回答“GTL 还写了什么针对 AE2 的 mixin”。
  - 把 GTL Core 中直接针对 AE2 核心、ExtendedAE、GTM AE 集成的 mixin 分组整理，说明它们改了什么以及对合成全过程的意义。
- 新增文档：
  - `docs/crafting-v2-migration/GTL_AE2_MIXIN_RESEARCH.md`
- 已确认结论：
  - GTL 针对 AE2 核心的 mixin 不只包括一次发完，还覆盖合成计算、合成 CPU 执行、CraftingService 降频、StorageService 降频、存储容量/大数字、IO端口、样板编码终端、客户端模型 hook。
  - 合成执行层的一次发完仍然只针对 `AEProcessingPattern && provider instanceof IMEPatternPartMachine`，也就是 GTL/GTM 的 **ME样板总成** 类 provider；普通 AE2 样板供应器仍是逐份派发。
  - 合成计算层还有三类值得参考的优化：处理样板候选收窄、精确库存优先 fuzzy 查找、弱化原版分片模拟调度。
  - 大数字相关优化包括：创造存储元件返回 `Long.MAX_VALUE`、通用槽容量提升到 `Integer.MAX_VALUE`、`AEKey2LongMap.addTo` 饱和加法、`AEItemKey.getFuzzySearchMaxValue` 缓存。
  - 服务/tick 优化包括：`CraftingService` 和 `StorageService` 按配置间隔降频更新。
  - 样板编码终端新增 x2/x3/x5/÷2/÷3/÷5 倍率按钮，属于大配方编辑体验优化。
  - `extendedae.*` 和 `gtm.ae.*` mixin 属于 AE2 生态集成，不是直接 AE2 核心 CPU，但会影响高级样板供应器、ME输入/输出总线、ME输入/输出仓、保持供给总线/仓和格雷机器与 AE 网络交互的后半段吞吐。
- 当前建议：
  - 当前项目继续保持普通 AE2 样板供应器逐份派发。
  - GTM 1.21.1 **ME样板总成** 批量派发已经落地，后续调研不再把它列为待借鉴重点，只作为 GTL 已验证路线的背景依据。
  - 后续可优先评估 GTL 的处理样板候选收窄、精确库存优先 fuzzy 查找、服务降频和大数字饱和加法。
- 最近一次验证命令和结果：
  - `jar tf gtlcore-1.2.2.2-fix3.jar | Select-String '^org/gtlcore/gtlcore/mixin/ae2/.+\.class$'`
    - 结果：确认 GTL Core 中直接 AE2 核心相关 mixin class 列表。
  - `javap -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicNewMixin`
    - 结果：确认普通 provider 逐份执行，只有 `AEProcessingPattern && provider instanceof IMEPatternPartMachine` 时批量抽取剩余 task 次数。
  - `javap -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.service.StorageServiceMixin`
    - 结果：确认存储缓存更新按 `ae2StorageServiceUpdateInterval` 降频。
  - `javap -classpath <mods classpath> -p -c org.gtlcore.gtlcore.mixin.ae2.gui.ProcessingEncodingPanelMixin`
    - 结果：确认样板编码终端新增 x2/x3/x5/÷2/÷3/÷5 六个倍率按钮。

## 42. 最新工作记录（2026-06-24，处理样板倍率按钮）

- 当前目标：
  - 复刻 GTL 样板编码终端中的处理样板倍率按钮，但按当前 AE2 1.21.1 源码结构直接实现，不使用 mixin，不替换整套样式文件。
- 新增文档：
  - `docs/crafting-v2-migration/PATTERN_ENCODING_MULTIPLIER_DESIGN.md`
- 已确认设计：
  - 只在 **ME样板编码终端** 的 **处理样板** 模式增加 `x2/x3/x5/÷2/÷3/÷5`。
  - 服务端动作按 all-or-nothing 缩放 `encodedInputsInv` 和 `encodedOutputsInv`。
  - 只接受倍率 `2, 3, 5, -2, -3, -5`。
  - 乘法超过 `Integer.MAX_VALUE`、除法不能整除、除法结果小于 1 或非法倍率时不修改任何槽。
- 正在做：
  - 先补服务端缩放逻辑测试，再实现菜单动作和客户端按钮。
- 最近一次验证命令和结果：
  - 尚未运行本功能测试；下一步先添加 RED 测试并运行。

## 43. 最新工作记录（2026-06-24，处理样板倍率按钮落地）

- 当前目标：
  - 按方案 A 直接在当前 AE2 1.21.1 源码中实现处理样板倍率按钮，不使用 mixin，不替换整套样式文件。
- 已完成事项：
  - 新增小文档 `docs/crafting-v2-migration/PATTERN_ENCODING_MULTIPLIER_DESIGN.md`，记录功能范围、服务端语义、客户端布局和验证计划。
  - 新增 `ProcessingPatternScalerTest`，覆盖输入/输出同步缩放、空槽跳过、除法整除校验、乘法溢出保护和非法倍率拒绝。
  - 新增 `ProcessingPatternScaler`，实现 all-or-nothing 缩放逻辑，仅接受 `2, 3, 5, -2, -3, -5`。
  - `PatternEncodingTermMenu` 新增 `scaleProcessingPattern(Integer)` 客户端动作；服务端仅在 `EncodingMode.PROCESSING` 下修改 `encodedInputsInv` 和 `encodedOutputsInv`。
  - 新增 `ProcessingPatternScaleButton`，客户端处理面板新增 `x2/x3/x5/÷2/÷3/÷5` 六个按钮。
  - 更新 `processing.json`，将六个倍率按钮放在处理输出槽右侧、样板槽左侧的窄列中，避免覆盖处理样板输入/输出槽。
  - 更新 `ButtonToolTips` 和 `zh_cn.json`，补充倍率按钮 tooltip 的英文默认文本和简体中文翻译。
- 调试记录：
  - 首次验证在 `compileJava` 阶段失败，原因是 `ProcessingPatternScaleButton` 继承 `AE2Button`，没有 `setVisibility(boolean)` 方法。
  - 已按现有 widget API 改为设置 `scaleButton.visible = visible`，随后专项测试通过。
- 未完成事项：
  - 尚未在真实游戏客户端中人工查看按钮位置；当前通过编译、资源处理和全量测试验证。
- 最近一次验证命令和结果：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat' --no-daemon test --tests appeng.menu.me.items.ProcessingPatternScalerTest`
    - 结果：`BUILD SUCCESSFUL in 12s`。
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat' --no-daemon test`
    - 结果：`BUILD SUCCESSFUL in 16s`。
  - 已知噪声：当前 workspace 不是 Git repository，Gradle 版本探测会打印 `Failed to determine Project version from Git` fallback 堆栈；不影响测试通过。

## 44. 最新工作记录（2026-06-24，正式构建产物）

- 当前目标：
  - 按用户要求执行一次完整 Gradle 构建，并确认构建产物位置。
- 构建过程：
  - 首次执行 `build` 时，编译和测试相关任务未暴露行为问题，但 `spotlessJavaCheck` 失败。
  - 失败原因是若干既有/近期修改文件未满足 Spotless 格式要求，包括 import 顺序和长行换行。
  - 已执行 `spotlessApply` 自动应用格式修正。
  - 随后重新执行 `build`，构建成功。
- 最近一次验证命令和结果：
  - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat' --no-daemon build`
    - 结果：`BUILD SUCCESSFUL in 23s`，`23 actionable tasks: 10 executed, 13 up-to-date`。
  - `Get-ChildItem -Path 'build\libs' -File | Sort-Object LastWriteTime -Descending`
    - 主 mod jar：`build/libs/appliedenergistics2-0.0.0-SNAPSHOT.jar`，大小 `8260628` bytes。
    - 其他产物：`appliedenergistics2-0.0.0-SNAPSHOT-sources.jar`、`appliedenergistics2-0.0.0-SNAPSHOT-javadoc.jar`、`appliedenergistics2-0.0.0-SNAPSHOT-api.jar`。
- 已知噪声：
  - 当前 workspace 不是 Git repository，Gradle 版本探测会打印 `Failed to determine Project version from Git` fallback 堆栈，因此版本为 `0.0.0-SNAPSHOT`。
  - 构建输出中仍有既有 deprecated API 警告，不影响构建成功。

## 45. 最新工作记录（2026-06-24，准备推送 fork 前的 gitignore）

- 当前目标：
  - 为后续推送到 `StardustMINUS-01/Applied-Energistics-2` 前整理 `.gitignore`，避免把本地构建产物、IDE 状态、日志、Codex/本地辅助状态和 GTNH 参考源码目录一起提交。
- 已完成事项：
  - 保留 AE2 原本“默认忽略根目录所有内容，再白名单放行源码/文档/Gradle/CI 文件”的 `.gitignore` 策略。
  - 显式忽略 `/build/`、`/.gradle/`、`/logs/`、`/run/`、`/out/`、`/.idea/`、`/.vscode/`、`/.superpowers/`、`/.codex/`。
  - 显式忽略本地参考源码目录 `/Applied-Energistics-2-Unofficial-master/`。
  - 保留 `src/`、`docs/`、`guidebook/`、`gradle/`、`gradlew`、`gradlew.bat`、`build.gradle`、`settings.gradle`、`.github/` 等需要推送的内容。
- 验证：
  - 当前目录仍不是 Git repository，因此直接 `git check-ignore --no-index` 会报 `fatal: not a git repository`。
  - 已创建临时空 Git 仓库并复制当前 `.gitignore` 进行规则抽查。
  - 抽查结果：
    - 被忽略：`build/libs/...jar`、`.gradle/caches/foo`、`logs/latest.log`、`Applied-Energistics-2-Unofficial-master/...`、`.idea/workspace.xml`、`.superpowers/state.json`。
    - 未被忽略：`src/main/java/...`、`src/main/resources/...`、`docs/crafting-v2-migration/STATE.md`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`build.gradle`、`.github/workflows/build.yml`。
- 待确认点：
  - 原始 `.gitignore` 中保留了 `!/libs/` 白名单；如果后续不希望提交根目录 `libs/` 下的本地 jar，需要再移除这条白名单或增加更细规则。

## 46. 最新工作记录（2026-06-24，gitignore 敏感本地文件收紧）

- 当前目标：
  - 按用户要求移除根目录 `libs/` 白名单，并确认代理订阅 URL、token、secret 等本地敏感信息不会误入提交。
- 已完成事项：
  - 从 `.gitignore` 移除 `!/libs/`，因此根目录 `libs/` 会被顶层 `/*` 默认忽略。
  - 新增本地敏感配置忽略规则：
    - `/.env`
    - `/.env.*`
    - `/local.properties`
    - `/secrets.properties`
    - `/credentials.properties`
    - `/private.properties`
    - `/clash*.yml`
    - `/clash*.yaml`
    - `/proxy*.yml`
    - `/proxy*.yaml`
    - `/subscription*.txt`
    - `/subscription*.yml`
    - `/subscription*.yaml`
    - `/sub-url*.txt`
- 验证：
  - 使用临时空 Git 仓库复制当前 `.gitignore` 进行规则抽查。
  - 确认会被忽略：`libs/local-dep.jar`、`build/libs/...jar`、`.gradle/caches/foo`、`logs/latest.log`、`Applied-Energistics-2-Unofficial-master/...`、`.idea/workspace.xml`、`.env`、`local.properties`、`secrets.properties`、`clash.yaml`、`subscription.txt`、`sub-url.txt`。
  - 确认不会被忽略：`src/main/java/...`、`src/main/resources/...`、`docs/crafting-v2-migration/STATE.md`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`build.gradle`、`.github/workflows/build.yml`。
  - 敏感内容扫描：
    - 常见代理订阅协议 `ss://`、`ssr://`、`vmess://`、`vless://`、`trojan://`、`hysteria://`、`tuic://` 无命中。
    - 带 token/secret/password/api_key/subscription 等关键词的 URL 形态无命中。
    - 关键词扫描命中的 `subscribe`、`token`、`signingPassword` 等均为源码中的方法名或环境变量/属性名引用，没有发现实际订阅 URL 或明文密钥。

## 47. 最新工作记录（2026-06-24，迁移到 fork 工作树并构建验证）

- 当前目标：
  - 按用户指定流程，把当前非 Git 工作目录中的完整改动套到 fork `StardustMINUS-01/Applied-Energistics-2` 的真实 `1.21.1` 历史上。
  - 严格只使用 fork 远端 `https://github.com/StardustMINUS-01/Applied-Energistics-2.git`，不添加、不推送上游远端。
- 已完成事项：
  - clone fork 到 `E:\MC stuff\Applied-Energistics-2-fork`。
  - clone 后位于远端默认分支 `1.21.1`，并创建工作分支 `crafting-v2-migration`。
  - 使用 `robocopy /MIR` 从 `E:\MC stuff\Applied-Energistics-2-1.21.1` 同步改动到 fork 工作树。
  - 同步时排除了 `.git`、`.gradle`、`.idea`、`.superpowers`、`.codex`、`build`、`logs`、`out`、`run`、`Applied-Energistics-2-Unofficial-master` 等本地目录。
  - 将 fork 工作树的本地 Git 身份设置为 `StardustMINUS-01 <StardustMINUS-01@users.noreply.github.com>`，避免使用环境里的占位身份 `Test User <test@example.com>`。
- 验证：
  - 在 fork 工作树执行完整构建：
    - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat' --no-daemon build`
    - 结果：`BUILD SUCCESSFUL in 1m 7s`，`23 actionable tasks: 23 executed`。
  - 已知噪声：构建仍有既有 deprecated API / unchecked 警告，不影响构建成功。
- 下一步：
  - 提交 `crafting-v2-migration` 分支。
  - push 到 fork 的 `crafting-v2-migration` 分支。
  - 合并回 fork 的 `1.21.1` 分支，并 push `1.21.1`。

## 48. 最新工作记录（2026-06-24，fork 分支推送与默认分支合并）

- 当前目标：
  - 将 `crafting-v2-migration` 工作分支推送到用户 fork，并合并回 fork 默认分支 `1.21.1`。
- 已完成事项：
  - 提交工作分支：
    - commit：`2101c3911 Overhaul AE2 crafting planner and execution`
  - 推送工作分支到 fork：
    - `git push -u origin crafting-v2-migration`
    - 远端：`https://github.com/StardustMINUS-01/Applied-Energistics-2.git`
  - 切回 `1.21.1`，从 `origin/1.21.1` 确认同步后执行非快进合并：
    - merge commit：`bc2432266 Merge crafting v2 migration`
  - 在合并后的 `1.21.1` 上再次运行完整构建：
    - `$env:JAVA_HOME='C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12.1-bin\eumc4uhoysa37zql93vfjkxy0\gradle-8.12.1\bin\gradle.bat' --no-daemon build`
    - 结果：`BUILD SUCCESSFUL in 24s`，`23 actionable tasks: 9 executed, 14 up-to-date`。
  - 推送默认分支到 fork：
    - `git push origin 1.21.1`
    - 结果：`fd8b717a4..bc2432266  1.21.1 -> 1.21.1`
- 远端约束确认：
  - 本次操作只存在并使用 `origin` 远端。
  - `origin` 的 fetch/push URL 均为 `https://github.com/StardustMINUS-01/Applied-Energistics-2.git`。
  - 未添加上游远端，未执行任何上游推送命令。
