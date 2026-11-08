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
