# EMI-Patternizer 与 EAE2 Pattern 实现对比

日期：2026-11-08。静态源码审查，不是两个模组的游戏内性能对跑。

参考项目：link-fgfgui/EMI-Patternizer，master 的 6413da5b48453cbcca88d2c26027e7c220d4cfdd，版本 1.2.0；Minecraft 1.21.1 / NeoForge 21.1.219 / AE2 19.2.17 / EMI 1.1.22。
本项目：feature/cyclic-recipes-1.20.1 的 9eb7d0d，Minecraft 1.20.1 / Forge / AE2 15.x / JEI。下述编码问题在 origin/1.20.1 现有编码路径中也存在，不是循环检测新引入。

## 核心结论

两者用途不同：EMI-Patternizer 是用户选好的目标依赖树到原生终端的操作自动化；我们是单机器类别的采集、规则筛选和服务端批量编码。其最值得借鉴的是目标配方树入口、原生传输处理器和已安装供应器样板的读取思路。不能直接迁移 1.21 API，更不能照搬按输出注册 ID 的去重。

本项目存在两项优先修复的正确性问题：将容器返还等同于不消耗；不支持的材料静默跳过。其余问题涉及已有样板检查覆盖、候选材料搜索、概率信息可信度、耗材预检精度和采集阶段的响应性。

## 实现方式对比

| 维度 | EMI-Patternizer | 本项目 |
| --- | --- | --- |
| 入口 | 快捷键读取 EMI BoM.tree.goal | JEI 工作方块类别入口；独立通配符编辑 |
| 配方范围 | 遍历用户选定的 MaterialNode 依赖树 | 采集当前机器类别；不是目标物品的整条前置链 |
| 配方写入 | EmiRecipeFiller.performFill，再调用终端编码，再 QUICK_MOVE | 客户端描述输入输出；服务端 PatternDetailsHelper 编码并插入 ME |
| 调度 | 每条安排多个 delayedExecutor 操作，靠固定延迟推进 | 服务端预算分片、读写状态与失败报告 |
| 原生材料语义 | 借助 EMI 已注册的传输处理器与终端模式 | GT/JEI 数据读取、原生配方形状重建及本项目的过滤策略 |
| 已有样板 | 读取原生/扩展样板管理终端，记忆输出 ID | 检查 ME 存储中的已编码物品键，含编码元数据影响 |
| 个性功能 | 目标配方树、AE2/RS/部分无线终端适配 | 输入输出过滤、标签/材料/形态分组、概率详情、机器绑定通配符、GTL/ExtendedAE 供应器适配 |
| 网络依赖 | 通过既有终端填充/编码/点击流程 | 两端模组和本项目协议；网络写入由本项目服务端负责 |

## 我们的问题，按优先级排列

### P1：容器返还被误认为整项不消耗（已确认代码路径）

IngredientFilter.shouldKeep 在 strip_container_items=true（默认）时，依据 item.hasCraftingRemainingItem 删除整个输入。

例如水桶返还空桶，水本身仍被消耗；消耗型机器配方中的工具也可能复用同一个带返还行为的物品。对处理样板直接删除这些输入，会缺少机器实际需要的材料；若删掉全部输入则整个配方失败。这与此前出现的“过滤后无输入”故障同属这一风险路径，但不能只凭旧截图断定每次都由它导致。

原生工作台编码重建了服务端网格，部分情况下又会绕过这份过滤列表，因此同一个全局过滤选项对不同样板类型还存在行为差异。

建议：优先遵循配方实际 consumed/chance/remainder 信息；容器返还与不消耗工具分开建模。水桶可以保留为真实输入，或在明确支持的原生流体替代路径转换，不能只删除。显式黑名单继续保留，但应提示其对完整配方的影响。

证据：[IngredientFilter.java:43](../../src/main/java/io/github/sexileman/eae2pattern/pattern/IngredientFilter.java)、[PatternEncoder.java:98](../../src/main/java/io/github/sexileman/eae2pattern/pattern/PatternEncoder.java)。

### P1：未知材料存在静默省略路径（已确认代码路径）

JeiRecipeCollector.toIngredients 对非空槽位得到 spec=null 时 continue；只识别 ItemStack/Forge FluidStack。PatternEncoder.toGenericStacks 在某个材料无法转换为 AEKey 时同样略过；只有全部输入/输出为空时才明确失败。

因此，“有效 A + 不支持的 B → C”可能被采集或编码为“A → C”。这不会让机器凭空完成配方，却会生成缺料、无法运行的样板，用户很难从成功编码提示发现问题。输出被省略还会影响副产物和概率策略。

建议：任何必要非空输入无法表示时整条拒绝，报告具体槽位/类型；未知输出也必须明确拒绝或由用户显式选择忽略，不能悄悄改配方。服务端未知注册 ID 应拒绝而不是少编码一项。GT 服务端可读取的配方优先使用原始数据核对。

证据：[JeiRecipeCollector.java:231](../../src/main/java/io/github/sexileman/eae2pattern/compat/jei/JeiRecipeCollector.java)、[PatternEncoder.java:293](../../src/main/java/io/github/sexileman/eae2pattern/pattern/PatternEncoder.java)。

### P2：已有样板检查覆盖有限，不是全网络语义去重

PatternInspection 只查询存储服务中物品键的数量，不读取已放进供应器的样板。当前中文按钮明确写的是“ME 存储中的已有样板”，因此这是覆盖范围限制，不是按钮虚假承诺。

已有样板若由不同玩家或其他终端编码，encodePlayer、gtlcore 等元数据不同，也可能导致物品键不相等。编码时附元数据和不附元数据的两个键，仍不能覆盖所有来源。

EMI-Patternizer 读取样板管理终端，可以覆盖供应器内的样板；这点值得借鉴。不过它仅记忆输出 ID，本项目不应采用这个去重粒度。

建议：区分“ME 库存已有”和“已安装可用配方”；建立样板语义签名，包含类型、形状/槽序、材料/NBT/数量、输出及替代策略，GT 还需保留可得的机器类型身份。忽略编码者等展示元数据。提供方/规则变化后失效，按需限预算读取。

### P2：候选材料被过早压成第一个具体堆叠

采集与原生网格重建多处使用 getIngredients/findFirst 或 options[0]。这使列表搜索、材料分组和精确筛选只看到代表材料，用户输入另一个合法替代材料时可能搜不到配方。动态规则的替代材料匹配也不能等同于完整候选组匹配。

这不意味着应该把所有候选平铺为并列消耗输入。建议保留“一个槽位的一组候选”，用于搜索与选择；最终处理样板绑定用户选定的具体材料，工作台替代沿用 AE2 原生能力。EMI 的树选择与传输处理器可作为交互参考，不能据此宣称它一定完整处理所有 GT 特殊语义。

### P2：概率信息“未识别”被当作“必定”

ChanceReader 在 tooltip 缺失、不可读或不匹配时返回 1；GT 有专用数据读取，但不适用于所有第三方模组。对输入，多要求材料通常较保守；对输出，将未知概率当必定可能让 AE2 永久等待未必出现的产物。

建议增加“确定/概率/未知”的来源可信度；未知输出在预览中提示或需显式确认。不要把未知概率默认为 100% 描述成普遍安全。

### P2：普通批次预检是请求张数上界，未必是最终有效张数

PatternWriter.start 用 specs.size 与空白库存比较，然后逐条编码。若部分条目会在服务端编码失败，预检可能要求多于实际有效张数的空白，从而保守拒绝原本可编码的有效子集。不会因此多扣耗材，但不是精确有效结果数。

建议使用有限预算的准备阶段，先生成/验证有效结果及诊断，再核对准确耗材，再事务写入；注意准备结果的内存预算，不能无界缓存成品 NBT。

### P2：采集开始时仍有同步全量工作

Collector 构造时 createRecipeLookup(...).get().filter(...).toList() 先遍历类别，之后才进入分片采集。超大类别仍可能在开页时短暂停顿；每 tick 采集预算并不能约束这段初始化。

建议按 JEI 运行时契约评估增量遍历或限额快照，达到采集上限时及时终止，而不是继续消费余下条目；保留取消/进度反馈。没有对真实整合包测量耗时，故不报告具体卡顿毫秒数。

### 兼容范围：原生终端与无线扩展

我们仅匹配 WirelessTerminalItem 与特定无线接入点实体的路径，某些扩展终端/跨维度或额外范围实现未必覆盖。EMI-Patternizer 直接操作实际终端，在已有适配和原生填充支持时可继承其行为。

这是需要用用户安装的终端版本验证的兼容范围，不能据此认定所有扩展无线终端都失效。建议按 AE2/终端库公开 API 适配，优先保持服务端权限、供电及范围规则。

## EMI-Patternizer 中不应照搬的实现

- Patternize.EncodedItems 是静态 HashSet<String>，使用输出 getId，不区分 NBT、数量、输入路径与机器。同一输出可存在多种合法机器配方，按输出 ID 去重会漏掉所需样板。
- 在安排 Encode 时立即把输出记为已编码，而不是等待服务端编码及转移成功；填充失败后也未以该结果中止后续编码步骤。可能形成失败后仍记忆成功的状态。
- 固定延迟的填充、编码、转移链不等于服务端确认协议。高延迟/卡顿时风险更大；源码未给出每步服务端成功应答。
- 一次性遍历并排序整棵 EMI 树，为每条配方创建延迟操作。本项目的分片服务端队列更适合大量机器配方；不能以代码体积小推断运行成本更低。
- streamTree 自身没有访问集合，依赖 EMI MaterialNode 提供有限树结构。未证明会在合法 EMI 树中无限递归，不将其作为已复现故障。
- 工作台关闭保护与若干屏幕适配有明确边界，不是所有第三方终端的通用事务框架。

## 值得吸收的功能

1. 目标物品依赖链批量编码：作为“机器全部配方”之外的独立入口，不替换现有工作方块模式。JEI 不等于 EMI 已选树，需重新设计配方选择、替代路径及循环处理。
2. 读取已安装供应器：以真实网络和权限为边界，识别原生/扩展提供方与通配符展开结果。
3. 原生传输处理器思想：建立提取适配层，GT/原生配方优先、JEI 通用回退；回退无法完整表示时拒绝并解释。
4. 备选材料选择：保留槽位候选，而不是仅搜索第一个代表堆叠。

## 建议实施顺序

- 第一批：修正容器/返还语义；非空未知材料整条拒绝；完善具体失败提示及场景回归。
- 第二批：已有样板语义签名、已安装提供方读取、普通批次准确耗材准备阶段。
- 第三批：候选材料模型、按目标依赖链收集、初始化采集性能优化和无线扩展适配。

此次仅提交对比报告，没有直接修改编码语义，也没有将 EMI-Patternizer 的 LGPL 源码复制进本项目。若后续复用代码，应单独处理许可、版本和平台差异。

## 上游证据（固定提交）

- [主编码调度与输出 ID 记忆](https://github.com/link-fgfgui/EMI-Patternizer/blob/6413da5b48453cbcca88d2c26027e7c220d4cfdd/src/main/java/io/github/linkfgfgui/emi_patternizer/Patternize.java#L34)
- [原生终端编码与供应器样板读取](https://github.com/link-fgfgui/EMI-Patternizer/blob/6413da5b48453cbcca88d2c26027e7c220d4cfdd/src/main/java/io/github/linkfgfgui/emi_patternizer/intergrated/appliedenergistics2.java#L35)
- [延迟配置](https://github.com/link-fgfgui/EMI-Patternizer/blob/6413da5b48453cbcca88d2c26027e7c220d4cfdd/src/main/java/io/github/linkfgfgui/emi_patternizer/Config.java)
- [终端预读取](https://github.com/link-fgfgui/EMI-Patternizer/blob/6413da5b48453cbcca88d2c26027e7c220d4cfdd/src/main/java/io/github/linkfgfgui/emi_patternizer/intergrated/PatternAccessPreloader.java)
- [版本与依赖](https://github.com/link-fgfgui/EMI-Patternizer/blob/6413da5b48453cbcca88d2c26027e7c220d4cfdd/gradle.properties)

## 6.4.1 本轮实施状态

- 已落地：原生水桶/流体替代路径保留、停用返还物误删、未知非空材料整条拒绝、普通批次准确预检、存储/已发布定义的有界结构签名匹配、概率可信度提示、类别判断分片和采集截断反馈。
- 谨慎边界：流体替代开关默认不变；不自动将机器处理输入中的水桶转为流体；机器类型提示和其他编码字段不随展示元数据一起删除。
- 尚未引入的结构性能力：完整候选槽位的选择/搜索模型、按目标依赖链编码入口、额外无线终端 API 适配。这些需要独立交互或兼容实现，不通过猜测替换材料来实现。
- 所有改动在 feature/cyclic-recipes-1.20.1 的独立生成优化提交中，循环检测模块和 Mixin 未修改。真实游戏中的流体替代和网络读写仍待验收。
