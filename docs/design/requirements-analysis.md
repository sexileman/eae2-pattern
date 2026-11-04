# Quickly Pattern 需求分析与技术方案

> 依据 `1.需求.txt`（9 条）逐条拆解。目标：Minecraft 1.20.1 + Forge 47.4.16 的 JEI × AE2 附属模组。
> 本文中标注 **【待核对】** 的 API 签名，必须在拿到真实 jar（JEI 15.x / AE2 15.2.x for 1.20.1）源码后逐一确认，不要凭记忆编码。

---

## 0. 一句话结论

需求本质是：**把 JEI 里"某个工作方块能做什么"这份配方数据，批量转换成 AE2 的已编码样板，并原子性地写进 ME 存储网络。**

这件事在技术上完全可行，但有三个真正的硬骨头，其余都是常规工作量：

1. **跨模组配方通用读取**：JEI 的配方数据是按 category 私有布局组织的，要把"任何 category"的原料/产物抽成 AE2 需要的 `(物品, 数量)` 列表，必须做适配层，不可能 100% 覆盖。
2. **客户端/服务端边界**：JEI 只在客户端存在，AE 网络在服务端。整个"采集 → 编码 → 入库"必须切成"客户端采集 + 服务端校验入库"两段，且服务端可能根本没装 JEI。
3. **事务与不吞物品**：空白样板被编码消耗后若网络写入失败，物品不能凭空消失；必须有预检 + 回滚 + 兜底归还。

---

## 1. 需求逐条拆解

| # | 原始需求 | 可行性 | 实现要点 | 风险 |
|---|---|---|---|---|
| 1 | 1.20.1 / Forge 47.4.16 上开发 JEI+AE2 附属 | 高 | Java 17，MDK + `compileOnly`/`implementation` 引 JEI、AE2；`mods.toml` 声明依赖，JEI 依赖需 `side="CLIENT"` | 版本锁定与跨版本兼容 |
| 2 | 按 JEI 工作方块支持的配方列表，一键生成全部样板并存入 AE2 网络 | 中高 | 催化剂反向查询 → 配方枚举 → 映射 → 编码 → 网络插入 | 配方形态千奇百怪，映射不可能全覆盖 |
| 3 | 配方数 > 网络空白样板数时提示 | 高 | `MEStorage` SIMULATE 统计空白样板数量，与待生成数比对 | 只是提示，还是阻断？见 §10-Q1 |
| 4 | AE2 存储不足以放下生成后的样板时停止继续 | 中高 | 预检 SIMULATE 可写入量；运行中逐条 REAL 插入，失败即停 + 回滚 | 细胞分区/过滤会导致"看着有空间却写不进" |
| 5 | 区分合成样板 / 处理样板 | 中 | recipe category → 样板类型的显式映射表 + 兜底启发式 | 判定错了样板就是废的，必须可配置可纠正 |
| 6 | 生成前预览，支持剔除 | 高 | 客户端 `Screen`：配方网格 + 勾选 + 统计 + 生成按钮 | 大列表（数千条）渲染性能 |
| 7 | 生成支持配置，过滤掉配方中"不消耗的物品" | 中 | 角色过滤（CATALYST/OUTPUT 复现）+ 物品黑/白名单 + 配置开关 | 需求语义歧义，见 §4 |
| 8 | JEI 配方页催化剂按 U 展示该方块全部配方，并在此处提供一列按钮触发本功能 | 中 | 原生"对催化剂按 U"JEI 已有；"加一列按钮"需注入 JEI 配方界面 | JEI 是否有官方扩展点不确定，可能要 Mixin |

注意 #8 的措辞：**前半句是 JEI 原生行为**（对催化剂槽按 U 已经能列出该方块所有配方）。模组真正要加的只有"一列按钮"，把"U 查配方"升级成"一键量产"。这决定了工作量重心在 GUI 注入而不是配方查询。

---

## 2. 目标环境与依赖

- Minecraft 1.20.1，Java 17，Forge 47.4.16（1.20.1 分支）
- JEI 1.20.1 分支（15.x，建议锁定单一构建，如 15.3.x）—— 纯客户端模组
- AE2 1.20.1 分支（15.2.x）
- 构建：ForgeGradle（MDK 47.x），Mixin 由 Forge 自带
- 依赖声明要点：
  - JEI 必须声明 `side = "CLIENT"`，否则专用服务器因缺失 JEI 拒绝加载
  - `displayTest = IGNORE_SERVER_VERSION`（版本不符时不阻断客户端进服）
  - 服务端只装 AE2 + 本模组也必须能跑（本模组服务端不引用 JEI 类，靠 `DistExecutor`/`@OnlyIn` 隔离）
  - Mixin 配置与 `mixins.json` 只作用于客户端时，务必用 `client` 侧配置，避免服务端类加载崩溃

> 这类"客户端模组的数据要落到服务端机器"的写法，最典型的参照物是 JEI 的 `IClientHelper` 与各类"服务端校验 + 客户端渲染"的分层模组；本模组的架构同理。

---

## 3. 关键技术点

### 3.1 从 JEI 枚举"某工作方块的全部配方"

入口：`IJeiRuntime.getRecipeManager()`（从 `IModPlugin.registerRuntime` 拿）。

```
IRecipeManager mgr
  ├─ 反向：给物品找它作为催化剂的配方类型
  │    mgr.createRecipeCatalystLookup(RecipeIngredientRole.CATALYST)     【待核对：JEI 15 是否公开】
  │        .limitFocus(IIngredientFocus.of(itemStack))
  │        .get()  → List<RecipeType<?>> / List<RecipeCategory<?>>
  └─ 正向：按类型取配方
       mgr.createRecipeLookup(recipeType).includeHidden().limitFocus(...).get() → Stream<RecipeHolder<?>>
```

**如果 JEI 15.x 没有公开催化剂反查**，退路是遍历 `mgr.getRecipeCatalysts(...)`/注册表快照自建 `item → categories` 索引（一次性构建，放客户端缓存）。这是必须提前验证的第一件事，因为它决定 §3.6 的入口设计。

取得 `RecipeHolder` 之后，**原料提取**是真正的工作量所在，候选依次为：

1. `IRecipeCategory#createRecipeSlotsView(...)` / `getRecipeSlotsView(...)` → `IRecipeSlotsView` → `getSlotViews()` → `IRecipeSlotView`
   再从 `IRecipeSlotView#getAllIngredients()` / `getDisplayedIngredient()` 拿到 `ITypedIngredient<?>`，配合 `RecipeIngredientRole`（INPUT / CATALYST / OUTPUT）区分角色。**【待核对：15.x 具体方法名】**
2. 兜底：`mgr.createRecipeLayoutDrawableOrShowError(...)` 拿到 `IRecipeLayoutDrawable`，再从它的 slots 读（慢，但兼容性最好）。
3. 对 top-N 常用 category 写专用适配器（工作台、熔炉、烟熏、高炉、营火、锻造台、酿造台 + 常见模组机器）。

**结论：必须设计 `IRecipeAdapter` 适配层**，签名大致：

```java
interface IRecipeAdapter<T> {
    boolean supports(RecipeCategory<?> category);
    PatternType patternType();                       // CRAFTING / PROCESSING / SKIP
    List<KeyStack> inputs(T recipe);                 // 已归一化为 AE 可接受的 (AEKey, amount)
    List<KeyStack> outputs(T recipe);
    boolean allowSubstitutes(T recipe);
    String unsupportedReason(T recipe);              // null = 可用
}
```

"跳过"必须是**一等公民**：产物无法枚举（如随机产出但 JEI 没声明概率）、输入超过 AE 样板槽位、依赖工具耐久、特殊 NBT 等，都要能给出可读原因，在预览页展示而不是静默丢弃。

### 3.2 配方 → 样板类型的映射规则

AE2 1.20.1 两类样板的表达能力不同，判定必须是**显式映射优先、启发式兜底、用户可覆盖**：

| 判定 | 规则 |
|---|---|
| 合成样板 | category 对应"工作台类/有 3×3 布局/位置敏感"（工作台、合成器、部分模组合成机）；输入 ≤ 9 格、恰好 1 个产物、每个输入为可堆叠物品 |
| 处理样板 | 其余全部（熔炉、烟熏、高炉、酿造、锻造、模组加工机）；输入/输出槽位数受限（1.20.1 具体上限 **【待核对】**） |
| 跳过 | 输入格数越界、产物为概率/多选一且 JEI 未声明、需要非物品资源（能量/流体，视是否做流体扩展）、配方含"仅显示"的伪槽 |

需要落成数据文件（`config/quickpattern/pattern_types.json` 或 datapack）：`category id → CRAFTING | PROCESSING | SKIP`，让整合包作者能修。**这比任何启发式都重要**——整合包有几百个 category，靠猜必然出错。

同理，合成样板的 "allowSubstitutes"（矿物词典/标签替换）应做成 category 级配置，默认开。

### 3.3 编码 API（AE2）

> **已核对**：本节结论已按 AE2 `forge/1.20.1` 源码更新，详见 `3.基于样板编码终端.md` §2。

- `appeng.api.crafting.PatternDetailsHelper`（公开 API，无需 Mixin）：
  - `encodeProcessingPattern(GenericStack[] in, GenericStack[] out)` —— 不需要 `Level`
  - `encodeCraftingPattern(CraftingRecipe recipe, ItemStack[] in, ItemStack out, boolean allowSubstitutes, boolean allowFluidSubstitutes)` —— **必须传真实 recipe 对象**
  - `encodeStonecuttingPattern(StonecutterRecipe, AEItemKey in, AEItemKey out, boolean allowSubstitutes)`
  - `encodeSmithingTablePattern(SmithingRecipe, AEItemKey template, AEItemKey base, AEItemKey addition, AEItemKey out, boolean allowSubstitutes)`
  - `isEncodedPattern(ItemStack)` / `decodePattern(ItemStack, Level[, boolean autoRecovery])` / `registerDecoder(IPatternDetailsDecoder)`
- 产物物品：`AEItems.CRAFTING_PATTERN` / `PROCESSING_PATTERN` / `STONECUTTING_PATTERN` / `SMITHING_TABLE_PATTERN`；原料：`AEItems.BLANK_PATTERN`
- 处理样板槽位上限：`AEProcessingPattern.MAX_INPUT_SLOTS = 81`、`MAX_OUTPUT_SLOTS = 27`（不是 9/3）
- 关键：**合成样板在 1.20.1 会绑定 recipe id**，编码时拿不到真实 `CraftingRecipe` 对象就只能退回处理样板或标记为不可生成。这条直接决定"工作台配方能不能做成合成样板"。

### 3.4 与 AE2 网络交互

取网格（`IGrid`）有三条路线，建议 A：

| 方案 | 说明 | 取舍 |
|---|---|---|
| A. 自建"样板量产终端"（方块/线缆部件，持 `IGridNode`） | 与 AE2 样板编码终端同构，安全/权限/动作来源都正规 | **推荐**：可靠、可做 ME 权限校验、可做进度条 |
| B. 继承 `WirelessTerminalItem` 的自定义无线终端 | 便携，可接 Curios | 多一层安全与绑定逻辑，v2 再做 |
| C. 无线接入点 + 网络链接卡 | 改动小 | 语义绕，玩家理解成本高 |

写入：

```java
// 1) 预检
long blankCount   = storage.extract(AEItemKey.of(AEItems.BLANK_PATTERN), Long.MAX_VALUE, Actionable.SIMULATE, src);
long canAccept    = storage.insert(patternKey, 1, Actionable.SIMULATE, src);   // 逐类估算
// 2) 执行：一条一条来
for (pattern : selected) {
    if (storage.extract(blankKey, 1, Actionable.SIMULATE, src) < 1) { stop("空白样板不足"); }
    ItemStack encoded = encode(pattern);                 // 失败 → 记 skip
    storage.extract(blankKey, 1, Actionable.REAL, src);
    if (storage.insert(AEItemKey.of(encoded), 1, Actionable.SIMULATE, src) < 1) {
        giveBack(encoded);  // 或 rollback；绝不 void
        stop("存储空间不足");
    }
    storage.insert(AEItemKey.of(encoded), 1, Actionable.REAL, src);
}
```

要点：
- `IStorageService#getInventory()` 返回 `MEStorage` 【待核对：1.20.1 中是否已由 `IStorageGrid` 更名】；`insert/extract` 的 `IActionSource` 用玩家源（`PlayerSource`/`ActionSource`），这样 ME 安全终端/审计能识别是谁在操作。
- 分区、过滤、`void excess` 的细胞会让 SIMULATE 与 REAL 不一致 → 因此**必须逐条 REAL 并随时可停**，不能只做批量预估。这正是需求 #4"停止继续"的正确实现方式。
- 回滚策略：记录本轮已插入的样板，失败时按序 `extract` 回来并还给玩家；被编码消耗的空白样板无法还原成空白，只能把**已编码样板**还给玩家（背包满则掉落）。**任何路径都不允许吞物品。**

### 3.5 客户端/服务端边界（架构核心）

```
[客户端]                              [服务端]
JEI 采集配方 ──► 规格列表(JSON/二进制) ──► 校验(样板数/空间) ──► 编码 ──► ME 插入
   ▲  预览/勾选 GUI                                    │
   └──────────── 进度与结果回包 ◄───────────────────────┘
```

- 传"规格"而不是传"已编码 ItemStack"：服务端必须能独立校验（防作弊 + 防止客户端版本不一致产出非法 NBT）。规格 = `{patternType, inputs:[{keyId, amount, nbt?}], outputs:[...], allowSubstitutes}`，`keyId` 用 AE2 的 `AEKey` 序列化到 `FriendlyByteBuf`/NBT。
- 分批：每包 ≤ 200~300 条，避免超过 1MB 包上限；大整合包动辄数千条。
- `SimpleChannel`（`NetworkRegistry.newSimpleChannel`）注册 `C2S_Generate`、`C2S_Preview`、`S2C_Progress`、`S2C_Result`。
- **长任务不要卡服务端 tick**：采集在客户端线程分帧做（每 tick 处理 N 条并更新进度），编码与插入在服务端分帧（例如每 tick 16 条），或者用 AE2 自带的 tick 队列。数千条一次性跑会在低配服务器上造成明显卡顿。

### 3.6 JEI 界面注入（需求 #8 的后半句）

目标：在 JEI 配方页的催化剂列旁，新增一列按钮，点一下打开本模组的预览 GUI（并以该催化剂物品为"当前机器"）。

调研顺序（成本从低到高）：

1. **官方扩展点**：查 JEI 15.x 是否有"配方界面额外的图标按钮 / 可点击区域"注册接口（`IGuiClickableArea`、`IGlobalGuiHandler` 的 extra area、`IRecipeCategory#getIcon` 之类）。有就用，零 Mixin，兼容性最好。**【待核对】**
2. **Mixin 注入** JEI 的配方界面类（`RecipeGui`/`RecipeLayout` 一类），在催化剂列左侧/右侧插入一列按钮。多个 JEI 附属用过这种做法，可行但**绑定 JEI 内部类名**，JEI 更新就可能崩 → 必须 `@Pseudo` + 运行时校验 + 失败降级。
3. **降级方案**：不改 JEI，改为"在任意界面按快捷键 / 在催化剂上按住 Shift 点击 / 自带书签式入口"，至少保证功能可用。

**建议**：M1 阶段就做这个 spike（1 天），结论直接决定 #8 的实现路径和后续排期，不要拖到最后。

---

## 4. 需求 #7 的歧义："过滤掉配方中不消耗的物品"

这句话至少有三种合理解释，实现完全不同，需要产品决策（见 §10-Q2）：

| 解释 | 含义 | 实现 |
|---|---|---|
| A. 过滤"催化剂" | 配方里参与但不变的东西（锤子、模具、桶） | 直接按 `RecipeIngredientRole.CATALYST` 丢弃；**这是默认行为，无需配置** |
| B. 过滤"循环返回物" | 输入了 A、产物里又出现 A（桶↔水桶、空单元↔装满） | 启发式：`inputs ∩ outputs` 的物品从输入中剔除，**需白名单**防止误伤（水桶配方里水桶真的会被消耗形态） |
| C. 物品黑名单 | 全局配置"这些物品永远不进样板" | `config` 列表，最直白 |

**建议同时实现 A（默认）+ C（配置）+ B（可开关，默认关）**，并在预览页高亮"被过滤掉的物品"，让玩家看得见、可反悔。

---

## 5. 主流程（伪代码）

```
onClickGenerate(catalystItem, selectedRecipes):
  specs = selectedRecipes.map(adapter -> toSpec)        // 客户端
  send C2S(specs, terminalPos)
server:
  grid = getGrid(terminalPos); if null -> fail("终端未连接网络")
  if !security.canUse(player) -> fail
  pre = simulate(grid, specs)
  if pre.blank < specs.size -> warn("空白样板不足: 需要 N, 现有 M")   // 需求#3
  if generateAnyway == false -> abort
  for spec in specs:                                    // 需求#4 逐条，失败即停
      ...
  send S2C(result{generated, skipped[], stoppedReason, rollback})
client:
  展示结果报表 + 失败原因列表
```

---

## 6. 预览 GUI 设计（需求 #6）

- 顶部：当前工作方块图标 + 名称 + 配方来源 category 列表
- 主体：虚拟化滚动网格（每行：勾选框 / 配方图标 / 名称 / 类型标签 C|P / 状态标签 可生成|跳过+原因）
- 右侧栏：统计（合成 N / 处理 M / 跳过 K）、网络空白样板数量、可用存储估算、预计消耗
- 操作：全选 / 反选 / 按类型筛选 / 仅可选 / 搜索 / 展开查看原料明细
- 底部：`生成` 按钮（条件不满足时置灰 + tooltip 说明原因）

性能：数千条目必须做**虚拟化渲染**（只画可见行），并避免每帧构建 tooltip；确认框对"超过 500 条"给出二次确认。

---

## 7. 配置项草案

```toml
[general]
enabled = true
maxRecipesPerRun = 5000          # 防呆上限
requireNetworkLink = true

[filter]
stripCatalysts = true            # §4-A
stripReturnedInputs = false      # §4-B
blacklist = ["minecraft:bucket", "ae2:empty_storage_cell"]   # §4-C

[pattern]
defaultSubstitutes = true
defaultPatternType = "AUTO"      # AUTO | CRAFTING | PROCESSING
```

外加数据驱动映射：`pattern_types.json`（category → 类型/跳过）、`overrides.json`（单配方强制规则）。

---

## 8. 风险与验证清单（开工前必须先跑通）

| 优先级 | 待验证 | 影响 |
|---|---|---|
| P0 | JEI 15.x 催化剂**反查** `item → categories` 是否公开 | 决定需求 #2 的入口 |
| P0 | `IRecipeSlotsView` 通用读原料的公开路径 | 决定能否通用化，否则只能逐个 category 适配 |
| P0 | `PatternDetailsHelper.encodeCraftingPattern` 在 1.20.1 是否必须绑定 `CraftingRecipe` | 决定"合成样板"上线范围，最坏只能全做处理样板 |
| P0 | 客户端/服务端无 JEI 时本模组能否正常加载 | 服务端崩溃 = 上架即差评 |
| P1 | `IStorageService` 与 `AEItems` 常量确切包名/方法名 | 编译期阻塞 |
| P1 | 处理样板输入/输出槽位上限、概率产物支持 | 决定"跳过"规则的边界 |
| P1 | JEI 配方界面是否有官方按钮扩展点 | 决定 #8 是 Mixin 还是纯 API |
| P2 | 数千条配方在低配端的耗时/内存 | 需要分帧与进度反馈 |

## 9. 里程碑（单人估）

| 阶段 | 内容 | 人日 |
|---|---|---|
| M0 | 环境搭建 + 依赖锁定 + 三项 P0 spike | 3 |
| M1 | JEI 采集 + 适配层骨架 + 规格序列化 | 5 |
| M2 | AE2 编码 + 网络事务（预检/插入/回滚/兜底） | 4 |
| M3 | 预览 GUI（虚拟化 + 勾选 + 统计） | 5 |
| M4 | JEI 按钮注入（API 或 Mixin）+ 降级入口 | 3 |
| M5 | 配置、数据驱动映射、失败报表打磨 | 3 |
| M6 | 兼容测试（AE2 附属、常见 JEI 附属、专用服务器） | 4 |
| 合计 | | **约 27 人日（5~6 周）** |

## 10. 待确认问题

- **Q1** 需求 #3 的"提示"是**仅警告并继续生成能生成的部分**，还是**直接阻断**？建议：警告 + 让玩家选择（默认只生成可生成部分，报表列出跳过项）。
- **Q2** §4 中"不消耗的物品"采用哪种语义？建议 A+C 默认，B 可选。
- **Q3** "存入 AE2 网络"指进 ME 存储（细胞），还是也要**直接下发给样板供应器**？后者是常见真实需求，可作为 v2。
- **Q4** 是否要求支持**流体/能量**配方（AE2 1.20.1 样板能带流体）？范围会显著变大。
- **Q5** 目标是"整合包工具"还是"单人可用"？前者对数据驱动映射和失败可见性的要求高得多。
- **Q6** 合成的判定：是否接受"某些工作台类配方只能退化为处理样板"？这关系到玩家的直觉预期。
