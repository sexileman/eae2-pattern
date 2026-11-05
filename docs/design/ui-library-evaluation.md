# 预览界面要不要直接用现成的 UI 库

结论先说：**建议用 ModularUI（jarJar 内嵌），因为你的整合包就是 GT 系，界面会和 GT 机器完全同族**。
如果不想要额外依赖，退而求其次用 **AE2 自带的控件体系**（AE2 已经是硬依赖，零新增）。
两者都必须放弃"我自己画控件"这条路 —— 这也是你说"怎么改都不对"的根因：我一直在重画，而不是复用一套成熟控件。

## 候选对照

| 方案 | 1.20.1 Forge | 依赖 | 许可 | 控件完整度 | 结论 |
|---|---|---|---|---|---|
| **ModularUI 3.2.0** | ✅ | `jarJar` 内嵌，玩家无需安装 | LGPL-3.0 | 窗口/面板/列表/分页/文本框/滑条/对话框/物品槽/进度条/JEI 幽灵槽 | **推荐** |
| **AE2 自带控件** | ✅ | 已有依赖（AE2 15.2.16） | LGPL-3.0 | 面板、按钮、文本框、复选框、滚动条、物品槽、Tab | 备选，风格最"AE2" |
| CubeUI 0.1.0 | ✅ | 无 | MIT | 主题引擎 + 9-slice + 基础控件 | 太新（首个 beta），控件少 |
| LDLib2 1.20.1 移植 | ✅ | 强制 KotlinForForge，双端必装，无 Maven | LGPL-3.0 | 非常全（flex/grid 布局、虚拟列表、编辑器） | 依赖太重，见 `ldlib2-ui-analysis.md` |

## ModularUI 的硬事实（已核实）

- Maven：`https://maven.gtceu.com`，坐标 `brachy.modularui:modularui-mc1.20.1:3.2.0`（`maven-metadata.xml` 里 `release=3.2.0`，`latest=3.3.1-SNAPSHOT`）。
- 它的 `META-INF/mods.toml`：`modId="modularui"`、`version="3.2.0"`、`license="LGPL-3.0 license"`、依赖 forge `[47,)` + minecraft `1.20.1`、`side` 未限定（客户端可用）。
- 它自己就带 jarJar：`META-INF/jarjar/EvalEx-3.6.0.jar`、`mixinextras-forge-0.5.3.jar`。
- **GTCEu 1.20.1 正是用它**：`gradle/forge.versions.toml` 里 `mui = "3.3.1-SNAPSHOT"`、`mui = { module = "brachy.modularui:modularui-mc1.20.1" }`，`dependencies.gradle` 里 `jarJar(modImplementation(forge.mui.get()))`。
  → 也就是说：**玩家正在玩的 GT 机器界面，就是这套库画的**。我们的预览界面用同一套库、同一套主题，界面语言天然一致。
- 主题是 JSON（`assets/modularui/themes/{vanilla,vanilla_dark,context_menu}.json`），`vanilla_dark` 只有四项：
  `color: 0x444444`、`textColor: 0xEEEEEE`、`scrollbar.color: 0x666666`、`scrollbar.textColor: 0xCCCCCC`。
  即 GT 那种"深灰面板 + 浅灰字"，其余颜色/贴图由 `assets/modularui/textures/gui/**` 提供，且**可被资源包覆盖**（主题是资源包级 JSON）。
- 贴图清单（35 张）里有我们要的一切：`background/background.png`、`vanilla_background.png`、`slot/item.png`、`slot/fluid.png`、`widgets/base_button.png`、`widgets/mc_button{,_hovered,_disabled}.png`、`icons/{checkmark,arrow_*,cross}.png`、`tab/tabs_{top,bottom,left,right}.png`、`icons.png`（大图标表）。
- 控件类（已从 jar 里列出来）：`ButtonWidget`、`CycleButtonWidget`、`TextFieldWidget`、`ListWidget`、`PageButton`、`SliderWidget`、`ProgressWidget`、`Dialog`、`Expandable`、`ItemDisplayWidget`、`SlotGroupWidget`、`SortButtons`、`ScrollingTextWidget`、`PagedWidget`、`RichTextWidget`。

## AE2 自带控件（零新增依赖）

AE2 15.2.16 的 `assets/ae2/textures/guis/` 里有整套：`background.png`、`terminal.png`、`checkbox.png`、`text_field.png`、`states.png`（按钮/滚动条/图标表）、`extra_panels.png`（Tab）、`pattern.png`、`pattern_modes.png`、`jei.png`。
Java 侧是 `appeng.client.gui.AEBaseScreen` + `WidgetContainer` + `Icon` + `AEButton` / `TabButton` / `Scrollbar`。
好处：零新依赖、和 AE2 终端像素级同族、玩家不用装任何东西。
坏处：AE2 的控件是"够用"而非"好看"，列表/分页这类复杂控件得自己拼。

## 建议的落地顺序

1. 先按 **ModularUI** 做：`build.gradle` 加 `maven { url 'https://maven.gtceu.com' }` + `jarJar(modImplementation("brachy.modularui:modularui-mc1.20.1:3.2.0"))`，`mods.toml` 里加 `modularui` 依赖（`BOTH`，`mandatory=false`，JarJar 会带上）。
2. `PreviewScreen` 改成 `ModularScreen` + `Panel` 组装：
   - 左：`Panel` + 两个 `TextFieldWidget` + 6 个 `ToggleButtonWidget` + 说明文本；
   - 中：`ListWidget`（自定义 `IListWidgetSlot` 行）或 `PagedWidget` + `PageButton`；
   - 右：`ItemDisplayWidget` 组 + `RichTextWidget`；
   - 下：`ButtonWidget`（生成）、`ButtonWidget`（取消）、`CycleButtonWidget`（倍率）。
3. 主题先用 `vanilla_dark`（GT 观感）；若你想要别的配色，写一份自己的 `themes/eae2_pattern.json` 继承 `vanilla_dark` 只改 `color`/`textColor`，随资源包生效。
4. 跑 `gradle build`，把 jar 给你在游戏里实测；截图问题从此不存在 —— 因为不再有"我画的图"和"游戏里的图"两套东西。

## 风险

- LGPL-3.0 的 jarJar 是 GTCEu 已在做的事（没有争议地随包分发），但**我们不能改它的源码**；要改就走 mixin/子类。
- `3.3.1-SNAPSHOT` 跟随 GTCEu 更紧，但快照会变；建议锁 `3.2.0` 发布版。
- 若你不想加任何依赖，就选 AE2 方案；我会用它的贴图和控件重写，风格会明显"像 AE2 终端"。
