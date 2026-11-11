# 代码审查记录（2026.11.2-beta.1）

本文件是 2026-11-11 对 `feature/cyclic-recipes-1.20.1` 分支的一次通读记录，供后续按条目修复。

| 项 | 值 |
|---|---|
| 分支 | `feature/cyclic-recipes-1.20.1` |
| 审查时 HEAD | `4188a24` build: adopt calendar beta version 2026.11.2-beta.1 |
| 版本 | `2026.11.2-beta.1` |
| 规模 | 158 个 Java 文件 / 11,539 行，`src/test` 7 套离线检查 |
| 构建 | `gradle build` BUILD SUCCESSFUL，`Passed 7104 regression checks` |
| 产物 | `build/libs/eae2-pattern-1.20.1-2026.11.2-beta.1.jar`（595,494 字节） |
| 未覆盖 | 未在游戏内实测（开发环境无法加载 AE2 的 mixin：`ae2.mixins.refmap.json` 在生产 SRG 名下注入失败），所有结论均来自源码与离线检查 |

问题编号 `R-01 … R-17`，按严重度排列。修复后请在同一张表里更新状态。

| 编号 | 级别 | 一句话 | 状态 |
|---|---|---|---|
| R-01 | P0 | 循环在 AE2 计算线程访问网络存储 | 待修 |
| R-02 | P0 | 处理样板全采信客户端输入/输出 | 待修 |
| R-03 | P0 | 通配符的 AE2 mixin 无门控且 required | 待修 |
| R-04 | P0 | `insert` 拦截吞掉非账本物品 | 待修 |
| R-05 | P1 | 任一运行时异常取消整单循环 | 待修 |
| R-06 | P1 | 选项/计数变化直接抛异常 | 待修 |
| R-07 | P1 | 等回流无超时，可能永久挂起 | 待修 |
| R-08 | P1 | 配置命令补全出来的键无法解析 | 待修 |
| R-09 | P1 | 旧网络状态覆盖新状态 | 待修 |
| R-10 | P1 | 同 requestId 不同规则的后续分块被丢弃 | 待修 |
| R-11 | P1 | 预览失败永久禁用该行 | 待修 |
| R-12 | P2 | 供应器扫描为每 tick 热点 | 待修 |
| R-13 | P2 | `MAX_QUANTITY` 让操作数上限失效 | 待修 |
| R-14 | P2 | 通配符服务端全量扫描可被反复触发 | 待修 |
| R-15 | P2 | 窄面板布局与文本截断边界 | 待修 |
| R-16 | P2 | `StopReason` 按 ordinal 传递且未校验 | 待修 |
| R-17 | P2 | 通配符运行时缓存非同步 | 待修 |

---

## P0

### R-01 循环在 AE2 计算线程访问网络存储

- 位置：`cycle/GrowthFallback.java:37`、`:73`、`:104`
- 现象：`replace()` 里读 `node.getGrid().getCraftingService()`、对 `stock` 调 `extract(..., Actionable.SIMULATE)` 探料。
- 根因：整条链路起点是 `mixin/CraftingCycleCalculationMixin`（`CraftingCalculation.run` RETURN），而 `CraftingCalculation` 跑在 AE2 的**计算线程池**上，不是主线程；AE2 的存储实现不是线程安全的。
- 影响：与主线程 tick 并发时可能 NPE / `ConcurrentModificationException`，表现为“规划偶发失败”或线程池里的异常堆栈。
- 建议：在提交计算任务前（主线程）对 `IStorageService.getCachedInventory()` 做一次快照，把不可变 `Map<AEKey,Long>` 传进计算线程；`GrowthFallback` 只读快照，`extract` 只保留在 CPU 的 tick 路径里。

### R-02 处理样板全采信客户端输入/输出

- 位置：`pattern/PatternEncoder.java:171-178`（`encodeProcessing`）
- 现象：直接使用 `spec.outputs()` 与客户端上报的 `inputs`，`spec.recipeId()` 只出现在异常文案里，**连配方都不查**。
- 对比：`encodeCrafting`（`:97-110`）与 `encodeStonecutting`（`:180+`）都会 `lookupRecipe` 并忽略客户端的材料列表。
- 影响：改包即可生成任意「输入 → 输出」的处理样板，公共服上是无限刷物品通道；样板还会进 AE 网络被自动化反复触发。
- 根因：GT/GTL 的处理材料挂在 capability 上（`GTRecipe.inputs/outputs` 为 `Map<RecipeCapability, List<Content>>`），`Recipe#getIngredients()` 基本为空，当初只能让客户端上报。
- 建议分级校验（可组合）：
  1. 廉价档：`recipeId` 必须存在且 `RecipeType` 与请求的机器类别一致；
  2. GT 档：复用 `jei/GtChanceReader` 的反射骨架读 `GTRecipe` 的 `getOutputContents(ItemRecipeCapability) / (FluidRecipeCapability)`，校验客户端 outputs 是其子集；
  3. 兜底档：新增 `limits.trust_client_patterns`（默认 `true`，公共服可关），关闭后只接受服务端能解析出来的条目。

### R-03 通配符的 AE2 mixin 无门控且 required

- 位置：`mixin/UniversalMixinPlugin.java:284-285`；`src/main/resources/eae2_pattern.mixins.json`（`required: true`）；`mixin/MachineWildcardProviderMixin`
- 现象：`PatternProviderLogic#updatePatterns` 处的注入没有任何字节码探测门控（同一插件里 cycle 那一批有 `CycleRuntimeGate` 门控），而 mixin 配置是硬性必需的。
- 影响：AE2 小版本改动方法签名/描述符，客户端启动阶段直接崩，且没有降级路径。
- 建议：把该注入纳入门控（探测目标方法 + 描述符存在才登记），并把这条 mixin 改为 `required: false` 的独立配置或用 `@Pseudo`/软失败，保证“注入失败只丢功能”。

### R-04 `insert` 拦截吞掉非账本物品

- 位置：`mixin/GrowthCpuMixin.java:94`
- 现象：HEAD 处拦截 `insert`，凡不在账本内的物品一律返回 0。
- 影响：副产物、玩家误投进 CPU 的物品被静默吞掉（玩家还能看见“投进去了”）。
- 建议：非账本物品直接放行给原生实现（`cir.setReturnValue` 只在账本命中时使用），或者把未匹配的物品按原逻辑 `insert` 回网络。

## P1

### R-05 任一运行时异常取消整单循环

- 位置：`mixin/GrowthCpuMixin.java:83-89`、`:120-124`
- 现象：捕获 `RuntimeException` 后直接 `cancel()`。
- 影响：一次偶发失败（例如供应器返回 null）整单作废，玩家只看到“失败”。
- 建议：区分“可重试”（下一次 tick 再试，带退避）与“不可恢复”，并把取消原因写进 `GenerationReport`/聊天诊断。

### R-06 选项/计数变化直接抛异常

- 位置：`cycle/GrowthRun.java:116`（`Cycle input alternatives changed`）、`:138`（任务计数不一致）
- 影响：被上层 catch 后静默取消，日志里不显眼。
- 建议：这两处改为带上下文的 `cancel(reason)`，原因走聊天/日志。

### R-07 等回流无超时

- 位置：`cycle/GrowthRun.java:104`
- 现象：等待产物回流没有时间上限。
- 影响：机器被拆、配方被改、产物被别处抽走 → 该 CPU 永久挂起（还需要手动拆 CPU 才能恢复）。
- 建议：加可配置超时（默认几分钟）+ 超时后走 `deliver()` 的失败分支退库存。

### R-08 配置命令补全出来的键无法解析

- 位置：`command/EAE2PatternCommands.java:77-98`（参数用 `StringArgumentType.word()`）、补全表 `:216-228` 给的是全路径
- 现象：`word()` 不接受 `.`；玩家按 Tab 选完 `filters.ignored_stacks` 回车即解析失败。
- 建议：改用 `StringArgumentType.greedyString()`（或 `string()`），保留短名兜底。

### R-09 旧网络状态覆盖新状态

- 位置：`net/S2CNetworkStatusPacket`
- 现象：解码后不复核 `requestId`。
- 建议：客户端只接受 `requestId >= 当前记录` 的状态，旧的直接丢弃。

### R-10 同 requestId 不同规则的后续分块被丢弃

- 位置：`net/server/BatchRequestManager.java:69`（丢弃逻辑）、`:155`（只 warn）
- 影响：客户端整单卡到 10 秒超时才失败。
- 建议：规则不一致时回一个明确的错误包（或 `requestId` 冲突包），让客户端立即失败并提示，而不是等超时。

### R-11 预览失败永久禁用该行

- 位置：`client/PatternWorkbenchScreen.java:291`
- 现象：某条预览失败的条目被记为错误并一直禁用。
- 建议：允许重试（重新点击即清错误），或在筛选/刷新后清空错误集合。

## P2

### R-12 供应器扫描为每 tick 热点

- 位置：`cycle/ProviderCursor.java:19`（用 `source != providers` 判等）、`cycle/GrowthRun.java:132-145`（每供应器真实试取料，≤256）
- 建议：改用稳定游标（记录 provider 身份/索引而非集合引用），并对试取料结果做短时缓存。

### R-13 `MAX_QUANTITY` 让操作数上限失效

- 位置：`cycle/CycleLimits.MAX_QUANTITY = Long.MAX_VALUE`
- 影响：`operationLimit` 形同失效，只剩 512 阶段兜底。
- 建议：给一个真实上限（例如 1e9），并让诊断界面显示“被上限截断”。

### R-14 通配符服务端全量扫描可被反复触发

- 位置：`server/MachineWildcardWriter.java:137-148`
- 现象：客户端规则只受长度与 65536 倍率约束，服务端按“全机器配方”展开。
- 建议：服务端独立的上限（展开条数、耗时预算），并按玩家/请求做冷却。

### R-15 窄面板布局与文本截断边界

- 位置：`client/PatternWorkbenchScreen.java:137`、`:140`（宽 < 277 时页脚按钮重叠）、`:616`（`room < 1` 直接不画）、`:780`（按钮省略号无 hover 全文）
- 建议：页脚按可用宽度换行/收缩；`room < 1` 时画到行末并给 tooltip。

### R-16 `StopReason` 按 ordinal 传递且未校验

- 位置：`net/S2CGenerationResultPacket`
- 影响：两端版本不一致时枚举错位，玩家看到错误原因。
- 建议：改为 `writeUtf(name())`/按名解析，未知值降级为 `UNKNOWN`。

### R-17 通配符运行时缓存非同步

- 位置：`wildcard/WildcardRuntime`（静态缓存，hits/misses 非原子）
- 建议：`ConcurrentHashMap` + `LongAdder`，或明确只在服务端线程访问。

---

## 已核验、不是问题

- `en_us.json` / `zh_cn.json` 各 **491 键，集合完全一致**，不存在缺键；真正的 i18n 风险只有 `tr("status." + id)`、`tr("input_mode." + n)` 这类运行时拼接键。
- 当前分支**没有 KeyMapping**：入口只有物品右键（通配符样板）与 JEI 类别上的「+」；早期的 G 键在重构中已移除。
- 合成样板 / 切石样板 / 锻造样板的编码都以服务端反查配方为准（`PatternEncoder:97`、`:180`），客户端列表只用于预览。

## 复核方式

1. `gradle build`（应仍然 `Passed 7104 regression checks`）。
2. 改动涉及 mixin 时，额外确认 `eae2_pattern.mixins.json` 的 `required` 与门控一致。
3. 进游戏实测的项目：循环增殖（缺料时是否真的滚雪球、取消后库存是否退回）、通配符样板在供应商里的展开条数、`/eae2_pattern config set` 是否会话内生效。
