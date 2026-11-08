# AE2 原生合成流程与增殖循环接入分析

日期：2026-11-08。分支：feature/cyclic-recipes-1.20.1。

## 核对基线与范围

整合包实际安装 appliedenergistics2-forge-15.4.10.jar，项目编译依赖 15.2.16。此次以官方 forge/v15.4.10 源码为主，并对整合包 jar 的 CraftingCalculation、CraftingTreeNode、CraftingCpuLogic、ExecutingCraftingJob、ChildCraftingSimulationState 做 javap 字节码交叉核对。旧调研的 15.2.16 结论不直接替代实际运行版本。尚未启动 Forge 世界；其他附属模组 Mixin 对运行时的影响仍需验收。

用户范围：已有启动材料，只处理确定可增殖的循环。交付目标可能只有 A、只有 B，或者同一上游任务同时需要 A、B；B 在仅请求 A 时可以只是中间材料。启动材料的获取配方不纳入本功能。是否在完成后保留额外周转储备，应与完成当前需求所必需的临时储备区分，不应擅自增加交付目标。

## 原生流程

1. CraftingCalculation 接收一个最终 GenericStack 目标，建立模拟库存和需求树。尝试正常规划，按策略可能减少数量，失败后运行缺料模拟。runCraftAttempt 会 ignore(output)：不将网络中同种最终目标直接抵扣成此次新制作任务，但这也使目标材料自身作为循环启动材料时需要专门处理。
2. CraftingTreeNode.request 先尝试从当前模拟库存取得有效输入，再寻找网络发布的配方；支持输入替代。buildChildPatterns 调用祖先 notRecursive，候选配方的输入或输出与祖先需求相遇时可能被拒绝。这是树形展开的防递归约束，不是带种子的增殖求解器。
3. CraftingTreeProcess.request 先递归取得全部输入，再将配方产物加入模拟库存，记录配方次数。直接同配方输入输出重叠、容器返还等情况会限为逐次模拟；这不等于支持跨机器 A/B 循环。不能把官方“不支持递归”的概述解释为一切返还配方都不支持。
4. CraftingSimulationState 记录模拟变化、配方次数和库存最大缺口，生成 CraftingPlan。计划包含一个 finalOutput、usedItems、missingItems、emittedItems、patternTimes 和字节开销，没有通用的循环阶段/多目标配额结构。
5. 提交到 CPU 时，CraftingCpuHelper.tryExtractInitialItems 从 ME 网络真实提取 usedItems 到 CPU 内部库存。这样成功占用的启动材料不再留在公共库存给另一个 CPU 重复领取；材料竞争仍需提交阶段复核。
6. CPU 按任务表尝试从内部库存取齐输入，通过原生 craftingService.getProviders(details) 和 provider.pushPattern 投料，成功后记录等待回收的输出。ExecutingCraftingJob 的任务表是配方到剩余次数的 HashMap，不保留规划模拟的完整先后顺序。
7. 回流时，CraftingCpuLogic.insert 只接受当前等待的材料；非最终目标进入内部库存供后续任务使用，与 finalOutput 同种的材料直接进入最终交付处理并扣 remainingAmount。达到目标即可 finishJob，清理等待/任务并尝试退回内部剩余库存；这不是“所有循环步骤完成”的判断。

## 对本项目最重要的发现

### 不能只修改递归判断

即使放行 A→B→A，也可能无限展开，批量倒推还可能一次索取所有循环输入，忽略小批次周转。必须有数量有界、库存逐步非负的前向模拟；规划中的库存复用不能伪装成不存在的原料。

### 不能只提交配方次数

原生 CPU 不按前向模拟的步骤顺序执行。仅有总次数无法保证带分支、多路消耗的循环仍按可行顺序运行。特别是最终目标也属于循环时，中途返还的 A 会被原生交付路径当成成品，可能让下轮生产 B 缺少 A，甚至提前完成而丢弃剩余循环任务。

### 可优先复用原生 CPU 和路由

此前“必须新增独立控制器”的表述应调整为候选方案，而非已证明的必要条件。CPU 已有内部库存、真实耗材提取、供应器路由、待回流记录与任务持久化，可以研究在其上增加受限循环状态。是否足以可靠复用，需验证回流归属、阶段投料约束、取消、重载和附属供应器行为；当前不能承诺不需要新执行组件。

### 多种需求应从实际任务提取

AE2 的普通计划只有一个最终目标，但该目标的配方树能同时产生 A、B 的中间需求。新规划层应合并同一任务中进入循环分量的需求向量，扣除真正可用的库存，计算需新增的产量，不要求用户总是手工填 A/B。两个独立 CPU 任务不能默认为同一个计划共享周转材料。仅请求 A 时，B 的交付目标为零，仍可作为内部周转库存使用。

## 建议接入顺序（尚未实现）

1. 保持普通配方走原生路径。针对实际请求所触达的循环候选，有界发现网络已发布的相关配方与材料；不在每帧扫描全部 JEI。
2. 增加纯数量规划器：精确物品/流体/NBT、配方整数次数、外部耗材、启动占用、阶段库存及目标数量。先支持确定性、无替代歧义的有限循环，再扩展复杂分支。正增长不代表所有目标都可达，也不代表增长率相同。
3. 将可行计划接回原生任务：研究以 CPU 内部库存占用启动材料，增加阶段/配额元数据和受限投料。回流先满足剩余内部需求，超出部分才允许交付；非循环任务不走此逻辑。
4. 完成条件同时检查实际交付目标、必要内部步骤和在途材料；不能单看第一次回流或某一材料总产出。保存阶段、配额和在途状态，重载时不能重复投料或重复计数。
5. 验证取消、存储满、缺外部材料、掉电、供应器阻塞、区块卸载、两个 CPU 同时请求及 ExtendedAE/GTL 总成适配。AE2 原生 API 路由可以复用，但不等于附属实现已通过验收。

本次只增加分析文档，不改变运行时代码，不发布新的功能 jar。

## 官方源码

- [CraftingCalculation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/CraftingCalculation.java)：runCraftAttempt、ignore(output)。
- [CraftingTreeNode](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/CraftingTreeNode.java)：request、buildChildPatterns、notRecursive。
- [CraftingTreeProcess](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/CraftingTreeProcess.java)：updateLimitQty、request。
- [CraftingSimulationState](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/inv/CraftingSimulationState.java)：requiredExtract、ignore、buildCraftingPlan。
- [CraftingPlan](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/CraftingPlan.java)：计划结构。
- [CraftingCpuHelper](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/execution/CraftingCpuHelper.java)：tryExtractInitialItems、extractPatternInputs。
- [CraftingCpuLogic](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java)：trySubmitJob、executeCrafting、insert、finishJob、持久化。
- [ExecutingCraftingJob](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java)：任务次数、单最终目标、等待库存。
- [1.20.1 自动合成指南](https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/autocrafting/)：普通自动合成不理解递归生产的概述。


## 附属模组的合并投料与循环批次（追加）

核查整合包提供的 extendedae_plus-1.5.3.jar：

- PatternProviderLogicDoublingMixin 将供应器智能倍增开关和计算后的倍率上限标到原生 AEProcessingPattern 上。
- autopattern.CraftingSimulationStateMixin.onBuildCraftingPlan 在计划构建处重写配方次数表：满足智能倍增条件时，把处理配方包装为 ScaledProcessingPattern。未受限分支可把 N 次改为倍率 N 的一次；受限时拆为若干整批与余数，另有按供应器数分配分支。不是所有配方都无条件合并。
- ScaledProcessingPattern/ScaledInput 放大原输入及输出数量。CraftingServiceGetProvidersMixin 与 PatternProviderLogicContainsRedirectMixin 用 getOriginal 将虚拟放大配方映射回供应器识别的原配方，避免找不到机器。仅查看名字或修改次数而不放大耗材与等待产物是不正确的。
- 模组包含上述能力不等于当前存档已启用该开关，也不等于实际机器可一次容纳任意数量。未在此检查运行时开关与世界中的供应器配置。

原生 executeCrafting 每次成功推送一个 pattern 任务就将次数减一，并按本次配方详情登记等待产物；多个推送可发生在相近 tick。若一个样板每份输出 10A，需求 10000A，在无库存抵扣/其他路径的简化前提下是 1000 份配方。智能倍增可将 1000 份输入与 10000A 的期望输出包装成一次逻辑推送；物品传输、网络包和 GT 机器加工不保证只执行一次，容器容量、并行和部分接收仍是独立限制。若需求不是 10 的整数倍，除非有不同配方，否则不能凭合并投料改变最小配方单位。

### 对循环实现的约束

全计划按同配方总次数合并，可能把不同循环阶段压成一个大批次，破坏小种子周转。例如仅有 1A，配方 A→2B、B→2A 时，不能因最终需要 10000A 就第一批要求数千 A。已有计划的总次数仍是必要数据，但投料必须按当前阶段真实可用库存执行。

循环任务的单批次数上限应取：剩余计划次数、当前真实可用输入允许的次数、必须留给其他阶段/目标的配额，以及供应器已配置的可用批次限制的最小值。物品与流体按各自原始整数单位计算；不能把尚未返回的产物计入当前库存。阶段内可以大批合并，回流后增长可支持更大的下一批，不应退化为全部逐份发送。

后续优先研究：非循环任务继续使用原有智能倍增；循环部分保留原配方身份与阶段信息，限制或阻止跨阶段整单合并，只合并当前可安全执行的阶段。不得全局关闭用户供应器的智能倍增开关。若附属模组已包装配方，识别原配方与倍率后统一计算，防止重复放大。任务重载时还要验证原定义、包装倍率、阶段、剩余次数和在途数量都能正确恢复；仅持久化原样板 ID 不足以代表整个循环状态。

字节码核查记录在本地 .tools/compat/eap-scale-plan-review.txt、eap-scaled-review.txt、addon-batch-review.txt。此次仍仅为分析，未声称 ExtendedAE、GTLCore、MAE2 的所有相关路径已经完成兼容验证。
