# 方案：基于 AE2 样板编码终端实现

> 源码依据：AppliedEnergistics/Applied-Energistics-2 仓库 `forge/1.20.1` 分支
> - `appeng/menu/me/items/PatternEncodingTermMenu.java`
> - `appeng/api/crafting/PatternDetailsHelper.java`
> - `appeng/crafting/pattern/AEProcessingPattern.java`
> - `appeng/api/networking/storage/IStorageService.java`、`appeng/api/storage/MEStorage.java`
>
> 该分支是 1.20.1 的头部代码（15.3.x 期）。若实际对接 15.2.x，个别类（尤其切石/锻造样板）可能不存在，需按实际 jar 复核，见 §7。

---

## 1. 结论

**能，而且比自建一套更省事。** 但"基于它"必须精确拆开：

- **要复用的**：它的**编码 API**（`PatternDetailsHelper`）、它的**状态对象**（`PatternEncodingLogic` + `ConfigInventory`）、它的**网络与权限上下文**（`IPatternTerminalMenuHost` / `MEStorageMenu`）。
- **不要复用的**：它的**菜单实例**。`PatternEncodingTermMenu` 是"一次编一条、人肉填槽"的 UI 状态机，大批量生成时驱动它只会和同步、槽位占用打架。

一句话：**借逻辑与上下文，不借实例。**

---

## 2. 已核对的 AE2 1.20.1 事实

| # | 事实（源码可查） | 对方案的影响 |
|---|---|---|
| 1 | 样板实际是 **4 类**，不是 2 类：`AEItems.CRAFTING_PATTERN` / `PROCESSING_PATTERN` / `STONECUTTING_PATTERN` / `SMITHING_TABLE_PATTERN`；`EncodingMode` 枚举为 `CRAFTING / PROCESSING / SMITHING_TABLE / STONECUTTING` | 需求 #5"区分合成/处理"要扩成 4 分类（是否都做见 §6） |
| 2 | 编码入口是公开 API，共 4 个 encode 方法（签名见 §2.1） | 不需要 Mixin 就能编码，这是关键 |
| 3 | **合成 / 切石 / 锻造样板都强制要求传入真实的 recipe 对象**，只有处理样板不需要 | 需求 #5 的真正难点在这里：拿不到 recipe 就编不出来 |
| 4 | 处理样板槽位上限 `MAX_INPUT_SLOTS = 9 * 9`（81）、`MAX_OUTPUT_SLOTS = 3 * 9`（27） | 上一版分析里"9 输入 / 3 输出"的猜测是错的；绝大多数模组机器配方都能做处理样板，"跳过"规则可以更宽松 |
| 5 | 终端 `encode()` **只把结果写进"已编码样板槽"，不写网络**，并消耗一个空白样板 | "自动存入网络"这步终端不做，必须我们自己 `MEStorage.insert` |
| 6 | 槽位数据是 `ConfigInventory`：`encodingLogic.getEncodedInputInv()` / `getEncodedOutputInv()`；空白样板与已编码样板各一个 `InternalInventory` | 可以直接程序化填充，不必经过 GUI |
| 7 | 1.20.1 已无 `IStorageGrid`，改为 `IStorageService`：`MEStorage getInventory()`、`KeyCounter getCachedInventory()`（每 tick 最多刷新一次） | 上一版标注的【待核对】确认：用 `IStorageService` |
| 8 | `MEStorage#insert(AEKey what, long amount, Actionable mode, IActionSource source)` | 写入接口确认，SIMULATE/REAL 语义与预检方案一致 |
| 9 | `PatternEncodingTermMenu` 只接受实现 `IPatternTerminalMenuHost` 的 host，并 `host.getLogic()` 取 `PatternEncodingLogic` | 我们自建方块只要实现这个接口，就白拿整套上下文 |
| 10 | `clearPattern()` 能把"已编码样板"还原成空白样板（`AEItems.BLANK_PATTERN.stack(count)`） | 回滚有官方兜底手段 |

### 2.1 编码 API 精确签名（`appeng.api.crafting.PatternDetailsHelper`）

```java
public static ItemStack encodeProcessingPattern(GenericStack[] in, GenericStack[] out)

public static ItemStack encodeCraftingPattern(CraftingRecipe recipe, ItemStack[] in,
        ItemStack out, boolean allowSubstitutes, boolean allowFluidSubstitutes)

public static ItemStack encodeStonecuttingPattern(StonecutterRecipe recipe, AEItemKey in,
        AEItemKey out, boolean allowSubstitutes)

public static ItemStack encodeSmithingTablePattern(SmithingRecipe recipe, AEItemKey template,
        AEItemKey base, AEItemKey addition, AEItemKey out, boolean allowSubstitutes)

public static boolean isEncodedPattern(ItemStack stack)
public static IPatternDetails decodePattern(ItemStack stack, Level level)          // 另有 (…, boolean autoRecovery)
public static void registerDecoder(IPatternDetailsDecoder decoder)                 // 可扩展
```

注意：**没有** `Level` 参数（上一版分析里猜的 `encodeProcessingPattern(…, Level)` 是错的）。

### 2.2 终端 `encode()` 的实际语义（可直接照抄的逻辑）

`PatternEncodingTermMenu#encode()` 的骨架：

```java
ItemStack encodedPattern = encodePattern();          // switch(mode) → 4 个 encodeXxx
if (encodedPattern != null) {
    var encodeOutput = this.encodedPatternSlot.getItem();
    if (!encodeOutput.isEmpty() && !isEncodedPattern(encodeOutput) && !BLANK_PATTERN.isSameAs(encodeOutput)) return;
    else if (encodeOutput.isEmpty()) {
        var blankPattern = this.blankPatternSlot.getItem();
        if (!isPattern(blankPattern)) return;        // 没空白样板 → 什么都不做
        blankPattern.shrink(1);                      // 消耗 1 个
        if (blankPattern.getCount() <= 0) this.blankPatternSlot.set(ItemStack.EMPTY);
    }
    this.encodedPatternSlot.set(encodedPattern);     // ← 只进这个槽，不写网络
} else {
    clearPattern();
}
```

处理模式的关键约束：

```java
if (outputs[0] == null) { return null; }   // 第一输出槽必须是主产物
```

合成模式的关键约束：

```java
private ItemStack getEncodedCraftingIngredient(int slot) {
    var what = encodedInputsInv.getKey(slot);
    if (what == null) return ItemStack.EMPTY;
    else if (what instanceof AEItemKey itemKey) return itemKey.toStack(1);
    else return null;        // 槽里是非物品（流体）→ 整个合成样板编码失败
}
```

**结论：合成样板的 3×3 网格里只能有物品**，流体只能靠 `allowFluidSubstitutes` 通过替代品机制进入。

---

## 3. 三层复用模型

```
┌─ L1 编码引擎 ──────────────────────────────── 直接调 PatternDetailsHelper（公开 API，零 Mixin）
├─ L2 上下文（网络/权限/空白样板/模式） ──────── IPatternTerminalMenuHost + PatternEncodingLogic
└─ L3 写入与事务 ────────────────────────────── IStorageService.getInventory() → MEStorage.insert
```

### L1：编码引擎

自己写一个 `PatternEncoder`，把"配方 → 样板 ItemStack"这段做对，**不碰 AE2 菜单**：

```java
ItemStack encode(PatternSpec spec, Level level) {
    return switch (spec.kind()) {
        case CRAFTING -> PatternDetailsHelper.encodeCraftingPattern(
                spec.craftingRecipe(), spec.grid9(), spec.output(),
                spec.allowSubstitutes(), spec.allowFluidSubstitutes());
        case PROCESSING -> PatternDetailsHelper.encodeProcessingPattern(spec.inputs(), spec.outputs());
        case STONECUTTING -> PatternDetailsHelper.encodeStonecuttingPattern(
                spec.stonecutterRecipe(), spec.inKey(), spec.outKey(), spec.allowSubstitutes());
        case SMITHING -> PatternDetailsHelper.encodeSmithingTablePattern(
                spec.smithingRecipe(), spec.template(), spec.base(), spec.addition(), spec.outKey(),
                spec.allowSubstitutes());
    };
}
```

L1 要自己承担的校验（终端在 UI 层做的那些）：
- 至少一个输入非空
- 处理样板 `outputs[0]` 非空
- 合成样板 9 格中不得出现非物品
- 输入/输出槽位数 ≤ `MAX_INPUT_SLOTS` / `MAX_OUTPUT_SLOTS`
- 同 key 合并（AE2 内部 `condenseStacks` 会合并且保留顺序，但我们传参时最好自己先合并，避免重复 key 出现意外语义）

### L2：上下文

`IPatternTerminalMenuHost` 就是 AE2 给"任何想当终端的东西"的接口。实现它 → `getLogic()` 返回一个 `PatternEncodingLogic`，后者持有：
- `EncodingMode`（4 种模式）
- `isSubstitution()` / `isFluidSubstitution()`
- `getStonecuttingRecipeId()`
- `getEncodedInputInv()` / `getEncodedOutputInv()`（`ConfigInventory`）
- `getBlankPatternInv()` / `getEncodedPatternInv()`

这条路把我上一版里的开放问题几乎全解决了：

| 上一版的开放问题 | 现在 |
|---|---|
| Q3「网格从哪来？自建方块 / 无线终端 / 链接卡？」 | 实现 `IPatternTerminalMenuHost` 的方块即可，这是 AE2 官方认可的"终端" |
| 权限与安全 | `MEStorageMenu` 体系自带 `getActionSource()` / `getPowerSource()`，ME 安全终端天然生效 |
| 空白样板从哪数 | 终端槽位里的 + 网络里的，两个来源都要算 |
| UI 从零做 | 我们的 Screen 可以照着 AE2 终端布局来，模式/替代品/空白样板槽直接复用 `PatternEncodingLogic` 的状态 |

### L3：写入与事务

```java
var storage = grid.getService(IStorageService.class).getInventory();
IActionSource src = /* 由终端/玩家构造，如 PlayerSource */;

// 预检（注意 getCachedInventory 每 tick 只刷一次，实时性要求高时用 getInventory().getAvailableStacks()）
long blanks = storage.extract(AEItemKey.of(AEItems.BLANK_PATTERN), Long.MAX_VALUE, Actionable.SIMULATE, src);

for (var spec : selected) {
    ItemStack encoded;
    try { encoded = encoder.encode(spec, level); }
    catch (IllegalArgumentException e) { report.skip(spec, e.getMessage()); continue; }

    if (storage.extract(blankKey, 1, Actionable.SIMULATE, src) < 1) { stop("空白样板不足"); break; }
    if (storage.insert(AEItemKey.of(encoded), 1, Actionable.SIMULATE, src) < 1) {
        giveBack(encoded);   // 还给玩家/掉落，绝不 void
        stop("网络存储已满"); break;
    }
    storage.extract(blankKey, 1, Actionable.REAL, src);
    storage.insert(AEItemKey.of(encoded), 1, Actionable.REAL, src);
}
```

回滚兜底：实在需要退回时，`AEItems.BLANK_PATTERN.stack(n)` 是终端 `clearPattern()` 用的官方手法——但**已编码样板无法真正还原成空白**（信息已写入 NBT），只能把已编码样板还给玩家。这一点必须写进提示文案，不能让玩家以为能无损撤销。

---

## 4. 关键技巧：JEI 配方 → 合法 `CraftingRecipe`

这是整条链路最容易卡住的地方，而终端自己已经给出了标准答案。

终端在 `getAndUpdateOutput()` 里是这么做的：

```java
var ic = new TransientCraftingContainer(this, 3, 3);
for (int x = 0; x < ic.getContainerSize(); x++) {
    var stack = getEncodedCraftingIngredient(x);
    if (stack != null) ic.setItem(x, stack);
}
this.currentRecipe = level.getRecipeManager()
        .getRecipeFor(RecipeType.CRAFTING, ic, level).orElse(null);
```

**我们照抄这一步**：把 JEI 给的 3×3 物品摆进 `TransientCraftingContainer`，用 `getRecipeFor` 反查，就拿到了 `encodeCraftingPattern` 需要的 `CraftingRecipe`。

而且我们比终端更强：JEI 的 `RecipeHolder` 里本来就持有确切配方（`RecipeType.CRAFTING` 的 JEI 配方对象就是 `CraftingRecipe` 本身），**可以直接用，不必反查**。反查只在"JEI 配方对象不是 `CraftingRecipe` 实例"时才作为兜底——顺带避免了 `getRecipeFor` 在多配方匹配时选错的风险。

需要反查兜底的典型情况：模组用自定义 `RecipeType` 但 JEI 把它显示成 3×3 合成（部分模组的"合成台"）。这类如果反查失败，只能退化为处理样板。

---

## 5. 两种落地形态

| | 形态 A：自建"批量样板终端"方块 | 形态 B：Mixin 进 AE2 的样板编码终端界面 |
|---|---|---|
| 做法 | 方块实现 `IPatternTerminalMenuHost`，自己开菜单 + Screen，内部复用 `PatternEncodingLogic`，加"批量生成"一列 | Mixin `PatternEncodingTermScreen`，在催化剂/模式按钮区插一列按钮 |
| 改动面 | 纯新增，不碰 AE2 内部 | 绑 AE2 内部 GUI 类名，AE2 小版本就可能崩 |
| 上下文 | 完整（网络/权限/模式/空白样板槽） | 完整（直接复用现有终端） |
| 玩家路径 | 多一个方块，需要合成 | 零成本，打开原版终端就有 |
| 风险 | 要自己写方块/菜单/Screen（工作量中等） | 版本敏感，需 `@Pseudo` + 失败降级 |

**推荐：A 做主干，B 做可选兼容层。** 理由：A 是纯 API 组合，长期维护成本最低；而 JEI 那一列（需求 #8）本身已经提供了"从配方页直达"的入口，B 的"少点一次"收益不足以承担 Mixin 风险。

---

## 6. 需求映射需要你确认的一点

需求 #5 原文只区分"合成样板 / 处理样板"，但 AE2 1.20.1 实际有 4 类。三个选项：

1. **只做 2 类**：切石/锻造配方全部当处理样板（AE2 能跑，但玩家失去"切石机按配方匹配"的语义）。
2. **做 4 类**（推荐）：`EncodingMode` 一一对应，语义最准，代价是每类都要一个 recipe 反查分支。
3. **做 2 类 + 切石/锻造走处理样板并在预览页标注"已降级"**：折中。

我倾向 2，因为切石/锻造样板恰恰是"玩家最懒得手搓一堆"的场景（切石机一个方块几十种产物）。

---

## 7. 版本与坑

1. **版本差异必须先钉死**。`STONECUTTING_PATTERN` / `SMITHING_TABLE_PATTERN`、`MAX_INPUT_SLOTS = 81` 都来自 `forge/1.20.1` 分支头部（15.3.x 期）。如果实际对接 AE2 15.2.x：
   - 可能只有 2 类样板（无切石/锻造）
   - 处理样板槽位上限可能不是 81/27
   → 拿到真实 jar 后第一件事是把 §2 的表格逐行复核。
2. **`ConfigInventory` 只能在服务端写**，且写完要触发同步；`FakeSlot` 的显示依赖 `setActive`/`setIcon`，自己开 Screen 时要注意。
3. **空白样板有两个来源**：终端槽位（`getBlankPatternInv()`）与网络（`MEStorage`）。需求 #3 的"配方数 > 空白样板数"两个都要算，否则玩家放了 10 个在槽里却被告知"没有样板"。
4. **处理样板的主产物顺序有意义**（`outputs[0]` 是 primary），JEI 的显示顺序要原样保留，否则副产物会变主产物。
5. **合成样板绑定 recipe id**：整合包改配方后旧样板失效，AE2 有 `autoRecovery`。我们生成时若用的是 JEI 的 `RecipeHolder`，其 id 与当前注入数据一致，风险较低；但仍应在结果报表里提示"配方变更后需重新生成"。
6. **编码本身是纯计算**，可以在服务端批量执行、不必分帧；真正的性能瓶颈在 JEI 采集（客户端）与 GUI 渲染（虚拟化）。

---

## 8. 更新后的一句话架构

```
[JEI 采集] 客户端：workstation → categories → recipes → PatternSpec(含 recipe 引用)
     │
     ▼ C2S（分片，规格而非 NBT）
[服务端] 批量样板终端（IPatternTerminalMenuHost）
     ├─ L2：PatternEncodingLogic 提供 mode / substitution / 空白样板库存
     ├─ L1：PatternEncoder → PatternDetailsHelper.encodeCrafting/Processing/Stonecutting/Smithing
     └─ L3：事务（SIMULATE 预检 → 逐条 REAL → 失败即停 → 绝不 void）
     │
     ▼ S2C（进度 + 结果报表）
[客户端] 预览 GUI 显示 生成数 / 跳过数与原因 / 停止原因
```
