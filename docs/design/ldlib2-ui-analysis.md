# 用 LDLib2 重做预览界面的可行性分析

写于 1.8.3 之后（当前预览界面是手绘 `UiKit` + 单一 `layout()`）。本文只做分析与结论，**没有改动代码**。

---

## 0. 结论先说

| 问题 | 结论 |
|---|---|
| 技术上能不能套用？ | **能**。LDLib2 有 `ModularUIScreen`（纯客户端 Screen 宿主），组件覆盖我们全部控件，布局是 flex/grid，样式表里正好有 `StylesheetManager.MODERN` —— 就是你喜欢的那套 ModernUI 观感。 |
| 建议现在就换成硬依赖吗？ | **不建议**。1.20.1 只有社区移植版、**强制依赖 Kotlin for Forge**、**双端都必须装**、且**没有 Maven**。为一个纯客户端界面把依赖面从 `AE2 + JEI` 扩到 `+LDLib2 +KFF`，代价明显大于收益。 |
| 那该怎么做？ | 走两步：①现在把 `UiKit` 按 LDLib2 的**概念**重构（主题令牌 + 容器式布局），零新依赖、为将来迁移铺路；②真要接，就做成**可选软集成**（检测到 `ldlib2` 才启用一套 `ModularUIScreen` 实现，否则回退现在的 UiKit）。 |

---

## 1. 核实过的事实

来源：社区移植版 jar 的 `META-INF/mods.toml`、jar 内容清单、sources jar，以及官方文档。

| 项 | 值 | 出处 |
|---|---|---|
| 官方支持版本 | **1.21.1 及以上**（原作者 KilaBash 精力有限） | [mcmod 移植页](https://www.mcmod.cn/class/31732.html)、[官方文档](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/modularui.html) |
| 1.20.1 Forge | **非官方移植**：`weiliangyan/LDLib2-1.20.1-Forge`，最新 `v2.2.27-forge.1.20.1-r8` | [GitHub](https://github.com/weiliangyan/LDLib2-1.20.1-Forge) |
| modId / 版本 | `ldlib2` / `2.2.27+forge.1.20.1` | jar 内 mods.toml |
| 许可 | **LGPL-3.0** | mods.toml、仓库 LICENSE |
| 硬依赖 | forge `[47,)`、minecraft `[1.20.1]`、**kotlinforforge `[4.11.0,)`** | mods.toml `[[dependencies.ldlib2]]` |
| 双端要求 | 客户端与服务端**都要装且版本一致**（有网络通道） | 移植版 README |
| 分发 | **没有 Maven**，只能 Curse Maven 或本地 jar | 移植版 README |
| 体积 | 主 jar **4.9 MB**（`-all` 4.8 MB），**520 个 UI 类** | jar 实测 |
| mixin | 自带 `ldlib2.mixins.json` | mods.toml |
| 与 GT 的关系 | GT 1.20.1 用的是 **LDLib 第一代**（`com.lowdragmc.ldlib`）；移植版声称可与一代共存 | 移植版 README |

### 关键 API（已核对源码，不是照抄文档）

```java
// 纯客户端宿主：这就是我们需要的那个
@OnlyIn(Dist.CLIENT)
public class ModularUIScreen extends Screen { public final ModularUI modularUI; /* leftPos/topPos ... */ }

public static ModularUI of(UI ui);                    // 客户端独占
public static ModularUI of(UI ui, @Nullable Player p); // 与服务端同步时用

Minecraft.getInstance().setScreen(new ModularUIScreen(ModularUI.of(UI.of(root)), Component.literal("...")));
```

- `ModularUI`：管理 UI 生命周期、输入、样式、渲染、双端同步（[文档](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/modularui.html)）。
- 布局：`gui/ui/layout/` 里是 **Taffy / Yoga**（CSS flex + grid），不是手算坐标。
- 样式：`gui/ui/style/` 有 `ColorProperty`、`DimensionProperty`、`GridProperty`… 加 `StylesheetManager`，其中 `MODERN` / `MODERN_MERGED` / `MC` / `LIGHT` 就是现成的主题。
- 组件（`gui/ui/elements/`，实测 30 个）：`Label`、`Button`、`Switch`/`Toggle`、`TextField`、`TextArea`、`ItemSlot`、`FluidSlot`、`Scroller`、`ScrollerView`、`VirtualScrollerView`、`TreeList`、`TabView`、`SplitView`、`FoldableRegion`、`Selector`、`ProgressBar`、`SearchComponent`、`TagField`、`Dialog`、`Menu`、`InventorySlots`…
- `ItemSlot` 支持 `setItem(ItemStack)`，并且**自带 JEI / EMI / REI 集成**（源码里直接 import 了三家的 API）。
- 附带：游戏内 UI 编辑器 `/ldlib2_ui_editor`、XML UI、数据绑定（[快速开始](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/getting_start.html)）。

---

## 2. 我们的界面怎么映射过去

| 现在的实现（`PreviewScreen` + `UiKit`） | LDLib2 里对应的东西 | 迁移难度 |
|---|---|---|
| 手绘圆角面板 / 卡片 | `UIElement` + `style(s -> s.background(...))` + 样式表 class | 低（纯声明） |
| `layout()` 手算全部矩形 | flex/grid 布局（`row`/`column`/`gap`/`flex`） | 中（要重排一遍） |
| 两个 `EditBox` + 自绘 placeholder + `?` | `TextField` + `Label` 占位 + tooltip 事件 | 低 |
| 6 个条件复选框 | `Switch` / `Toggle` / `ToggleGroupElement` | 低 |
| 16 行配方列表 + 斑马纹 + 徽章 | `ScrollerView`（或 `VirtualScrollerView`）里放自定义 row 元素 | 中（行元素要自己写） |
| 行内物品图标 | `ItemSlot`（可缩放） | 低 |
| 3×3 输入网格 | 9 个 `ItemSlot` + grid 布局 | 低 |
| 倍率 7 格 | `Selector` / `ToggleGroupElement` | 低 |
| 翻页 + 页码 | `Button` ×2 + `Label` | 低 |
| 生成 / 取消 | `Button`（还有现成的 hover/disabled 状态） | 低 |
| 生成结果提示条 | `Label` + 样式 + 定时隐藏 | 低 |
| 搜索语法解析 `ItemSearchQuery` | **保持我们自己的代码**（这是业务逻辑，不是 UI 库的事） | 无 |

结论：**组件层面完全够用**，而且观感直接落到 `MODERN` 样式表。真正的工作量在"重排布局 + 重写 16 行列表的行元素 + 重新回归测试"。

---

## 3. 三种接入方案

### 方案 A：硬依赖 LDLib2（把 `PreviewScreen` 整体重写成 `ModularUIScreen`）

```java
public class PreviewScreen extends ModularUIScreen {
    public PreviewScreen(RecipePlan plan) {
        super(ModularUI.of(UI.of(buildRoot(plan), StylesheetManager.MODERN)), plan.machineName());
    }
}
```

- **好处**：布局/样式/滚动/输入框/tooltip 全部交给库；观感与 GT 机器界面一致；以后能用 XML UI 和游戏内编辑器改界面，不用改代码。
- **代价**：
  - 依赖从 `AE2 + JEI` 变成 `AE2 + JEI + LDLib2 + Kotlin for Forge`；
  - **玩家服务端也要装**，而我们的功能是纯客户端预览（服务端只收样板规格）——为了界面逼所有服务器装库，说不过去；
  - 1.20.1 分支是**社区移植**，随社区更新；`mods.toml` 声明的 KFF 版本一旦不匹配就直接崩；
  - 没有 Maven：构建要加 Curse Maven 或提交本地 jar；
  - LGPL-3.0：可以依赖，但**不能** shade 进我们的 jar（现在我们的 jar 156 KB，也不能变成 5 MB）。
- 估算：**6–8 人日**（重写 + 样式调整 + 16 项行为回归 + 与 AE2/JEI 的交互测试）。

### 方案 B：可选软集成（两套实现，运行时二选一）

```java
// 只在这个类里 import LDLib2，类本身只在 ldlib2 存在时才被加载
final class LdlibPreview implements PreviewHost { ... }

static void open(RecipePlan plan) {
    if (ModList.get().isLoaded("ldlib2") && LdlibBridge.available()) {
        LdlibPreview.open(plan);      // 反射/延迟加载，避免 NoClassDefFoundError
    } else {
        Minecraft.getInstance().setScreen(new PreviewScreen(plan)); // 现有实现
    }
}
```

- **好处**：装了 LDLib2 的整合包（GT 系、玩 Modrinth 的）拿到更好看的界面，其他人零感知。
- **代价**：**两套 UI 同时维护**；行元素、过滤联动、生成流程都要写两遍；软集成意味着更新时要同时适配两个实现；还是绕不开"KFF 必装"。
- 估算：**10–12 人日**（A 的 6–8 天 + 桥接层 + 双份回归）。

### 方案 C：只借理念（**当前建议**）

不引入依赖，但把 LDLib2 的设计搬进我们自己的 `UiKit`：

1. **主题令牌**：`UiTheme`（颜色/圆角/间距/字号），对应它的 `Property` 体系 → 将来换样式只改一处。
2. **容器式布局**：`Column` / `Row` / `Grid` 三个小工具（内含 gap/padding/align），替掉现在满屏的坐标算式 → 也是这次排错乱的教训。
3. **样式表**：`panel / card / field / button / badge` 五个具名样式，`style(s -> s.base(...).hover(...))`。
4. **组件化**：`SwitchElement`、`FieldElement`、`ScrollerElement`、`ButtonElement` —— 名字和职责对齐 LDLib2，将来迁移是"替换实现"而不是"重写逻辑"。

- **好处**：零新依赖、jar 还是 150 KB、服务端不用装任何东西；同时把这次"图和代码两套坐标"的根因（手写坐标）结构性地去掉。
- **代价**：视觉上仍是自己画的，没有 `MODERN` 样式表那么"成品化"，也没有游戏内编辑器。
- 估算：**2–3 人日**（重构 + 用新布局重排一次 + 截图脚本同步）。

---

## 4. 风险清单（按严重度）

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 1 | **双端必装 + 版本一致** | 服务器不装库，玩家进不去/连不上 | 方案 C 完全规避；方案 B 只在本地整合包生效 |
| 2 | **Kotlin for Forge 强制依赖** | 整合包多一个 Kotlin 运行时，版本冲突面变大 | 同上 |
| 3 | 1.20.1 为**社区移植** | 上游修复滞后、随时可能停更 | 锁死具体版本；方案 B 可退 |
| 4 | 无 Maven | 构建脚本要引 Curse Maven 或本地 jar，CI 需额外缓存 | 本地 jar + `.gitignore`，或用 Curse Maven 坐标 |
| 5 | 与 GT 用的**第一代 LDLib 共存** | 同生态 mixin 冲突（移植版声明可共存，但需实测） | spike 阶段先验证 |
| 6 | LGPL-3.0 | 只能"依赖"，不能打进我们 jar | 保持外部依赖，不 shade |
| 7 | 迁移回归面大 | 16 项交互（翻页/筛选/倍率/生成/提示…）都要重测 | 先做 spike，再决定是否全量 |

---

## 5. 如果要做，我建议的顺序

1. **1 天 spike（不合并）**：单独分支，只搭"筛选卡 + 20 行列表 + 详情卡"，用 `ModularUIScreen` + `MODERN` 跑起来，验证：
   - 1.20.1 Forge 47.4.16 + KFF 能进游戏；
   - 与 AE2 / JEI / GT 同服不冲突；
   - `ItemSlot` 在 client-only 屏幕上能正常渲染 ItemStack（含 NBT 变体）；
   - 滚动 20 行不卡（`VirtualScrollerView` 是否必需）。
2. spike 通过 → 评估走 A 还是 B（看是否需要"零依赖"）。
3. spike 不通过（mixin 冲突 / 性能 / KFF 不可接受）→ 直接转方案 C，并且把 spike 里学到的样式思想搬回来。

## 6. 我的建议

**先做方案 C，把 spike 放到可选位置。** 理由：

- 我们缺的不是"组件库"，而是**布局不要写死坐标**——这次 1.8.0/1.8.1 的错乱就是手算坐标加字段顺序导致的，`Column/Row/Grid` 能从结构上避免它；
- LDLib2 的强项（样式表、编辑器、数据绑定）只有在**长期大量做界面**时才划算，我们只有这一个界面；
- 让一个纯客户端预览功能去要求"服务器也装 5 MB 库 + Kotlin 运行时"，代价和收益不成比例。

---

## 参考

- [LDLib2 官方文档 — ModularUI](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/modularui.html)
- [LDLib2 官方文档 — Screen and Menu](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/screen_and_menu.html)
- [LDLib2 官方文档 — 快速开始](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/getting_start.html)
- [LDLib2-1.20.1-Forge 移植版](https://github.com/weiliangyan/LDLib2-1.20.1-Forge)
- [mcmod：LowDragLib2 Forge 非官方移植](https://www.mcmod.cn/class/31732.html)
- [LDLib 的 Modrinth 页面（含 LGPL-3.0 许可说明）](https://modrinth.com/mod/ldlib)
