# 怎么测这一个 jar

前置：这个 jar 只能在**真实客户端**里验证。本工作区的开发环境跑不起来（AE2 的 mixin refmap 是生产 SRG 名，ForgeGradle 的 `fg.deobf()` 不重映射 jar 内 refmap，AE2 在 mixin 阶段就崩），和本模组无关，详见 `README.md` 的「关于开发环境跑起来」。

## 〇、1.1.0 改了什么（最新，先看这里再测）

1. **GT / NBT 兼容：带 NBT 的配方材料不再被抹平**。GregTech(O) 的「配置电路」（`gtceu:programmed_circuit` + `Configuration=N`）以前在 JEI → `StackSpec` 这一步就被压成「任意电路」，编出来的样板能跑但做错东西。现在 NBT 全程带着：JEI 槽位 → 网络包 → `AEItemKey.of(ItemStack)` → 合成网格。
2. **预览页有搜索框了**，语法学 JEI：

   | 输入 | 含义 |
   | --- | --- |
   | `钢` | 名称包含「钢」 |
   | `@gtceu` | 只看 gtceu 的产物 |
   | `#forge:plates` | 只看带这个标签的产物 |
   | `*plate*` / `c?rcuit` | 通配符 |
   | `-青铜` | 排除 |

   多个词是「与」关系。「全选 / 全不选」现在只作用于**筛选出来的**条目，所以「先搜 `-gtceu`，再全不选，再清空搜索全选」能一步剔掉某个模组的全部配方。
3. **游戏内改配置，立刻生效不用重启**：

   ```
   /eae2_pattern config list
   /eae2_pattern config get limits.max_patterns_per_request
   /eae2_pattern config set limits.max_patterns_per_request 16384
   /eae2_pattern config add filters.ignored_stacks gtceu:programmed_circuit
   /eae2_pattern config reset filters.strip_container_items
   /eae2_pattern config reload
   ```

   写操作要 OP；值立刻进内存并落盘到 toml。
4. **单类别配方上限 2048 → 8192**（GT(O) 一台机器常常上千条），并且日志改成 `Collected X of Y …, skipped Z`，每条跳过在 debug 级写明原因。

重测四件事：

- **GT 电路**：在 GTO 里找一条要求特定配置电路的配方（组装机/装配线带电路那条），生成样板后拿去用 AE2 自动合成，看是否**强制**要对应配置的电路。也可以先生成两条只差电路配置的配方，比对两条样板是否不同（相同就是 NBT 还是丢了）。
- **搜索**：预览页输入 `钢`、`@gtceu`、`#forge:plates`、`-青铜`，确认结果条数变化合理；在搜索状态下点「全不选」再清空搜索，确认只有筛选过的那批被取消。
- **命令**：`/eae2_pattern config set limits.max_patterns_per_request 16384` → 立刻再点一次生成 → `logs/latest.log` 里 `config maxPatternsPerRequest=16384`；重启游戏后 `get` 仍是 16384（说明落盘成功）。
- **大机器**：卷板机 799 条照旧应该走通；若出现 `skipped N`，把 `Skipping recipe …` 那几行发我。

## 〇一、第三版改了什么（历史）

1. **修掉「生成失败」的真凶：单次请求上限原来是 512 条**。你那台卷板机 `gtceu:bender` 有 799 条配方，所以整个请求在服务端被直接拒了，一条都没写——不是样板不够。现在默认上限提到 **8192**，并且这种拒绝会走独立的结果类型，聊天栏直接告诉你「请求过大 + 该改哪个配置」。
   > 注意：旧版本已经在 `serverconfig/eae2_pattern-server.toml` 里写死了 `maxPatternsPerRequest = 512`。**改默认值不会覆盖已有文件**，请把那一行改成 `8192`，或者直接删掉这个 toml 让模组重新生成。
2. **编码不了的配方改成「跳过」而不是「整体中止」**。一台机器几百条配方里总会有几条 AE2 表达不了（没产物、纯流体、输入超过 81 槽）。以前第一条失败就整批停，现在只跳过那条，其余照常写入，并在聊天栏/日志里列出前几条失败原因。
3. **按钮挪到 JEI 左侧的「工作方块」竖条里**：就是 JEI 显示「哪些方块能做这个配方」的那一列，按钮是它最下方一个 16×16 的九宫格槽位，跟着 JEI 窗口走，不再占用标题栏。
4. **日志变详细**：客户端发送、服务端接收、编码跳过、最终结果每一步都有独立日志行，且带具体配方 id 与数字。

重测三件事：

- 卷板机（799 条）→ 点按钮 → 预览显示 799 条、已选 799 → 生成 → 聊天栏应该报「生成完成：写入 N 个样板…」，而不是失败。若仍失败，把聊天栏那几行 + `logs/latest.log` 里 `Generation` 开头的行发我，里面会直接写明原因。
- 按钮位置：打开任意 JEI 配方页，按钮应在**左侧工作方块图标那一列的最下面**，是一个灰色小方块里嵌九宫格。
- 若出现「另有 N 个配方无法编码」：说明这 N 条被跳过了，把那几条细节发我，我判断是适配问题还是应当过滤掉。

## 〇二、第二版改了什么（历史）

1. **采集范围收紧到「当前这个 JEI 类别」**。之前会把 JEI 隐藏的配方、以及类别自己 `isHandled` 拒绝的配方一起收进来，多个类别共用同一个 `RecipeType` 时就会串味。现在只收 JEI 在**这个工作方块页面上真正会画出来**的配方。另外如果 JEI 正停在「全部类别」索引页（例如按 U 后弹出类别列表），点按钮会先弹**类别选择页**，不会盲取某一个类别。
2. **预览改成翻页**：标题栏右侧有 `<` `>` 按钮和页码，鼠标滚轮、方向键左右、PageUp / PageDown 也能翻页；勾选状态**跨页保留**。
3. **「快速样板」按钮嵌进 JEI 标题栏**：位置在 JEI 自己那对翻页箭头的**左边、同一行**，13×13 的九宫格图标，鼠标悬停有提示。不再是屏幕右上角那个悬空的长条按钮。

重测三件事：

- 打开熔炉（或任意机器）的 JEI 配方页 → 左侧工作方块竖条最下方出现九宫格槽位（第二版时它在标题栏，第三版已挪走）→ 点它 → 预览里的条目应该**只有这台机器的配方**，类别标签不该出现别的东西。
- 打开一个条目很多的类别（合成台）→ 验证翻页：在第 1 页取消几条、翻到第 2 页再勾，翻回来时第 1 页的改动还在。
- `logs/latest.log` 里现在会多一行 `Collected X of Y recipes from category '…'`，把这个 X/Y 和类别名发我，就能确认范围对不对。

需要的东西已经下载到 `dist\`：

| 文件 | 用途 |
| --- | --- |
| `eae2-pattern-<版本>.jar` | 本模组（版本形如 `1.1.0`，以 `build/libs/` 里实际产出的为准） |
| `appliedenergistics2-forge-15.2.16.jar` | AE2（必须，版本 `[15.2.0,16.0.0)`） |
| `jei-1.20.1-forge-15.20.0.106.jar` | JEI（必须，版本 `[15.20.0,16.0.0)`） |
| `forge-1.20.1-47.4.16-installer.jar` | Forge 安装器 |

你机器上可用的 Java 17：`C:\Users\jiang\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\java.exe`（17.0.15）。

## 一、装客户端（一次性）

1. 先用启动器**正常下载一次 1.20.1 原版**，让 `.minecraft\versions\1.20.1` 出现。Forge 安装器依赖原版文件。
2. 跑 Forge 安装器（GUI，选 **Install client**）：

```powershell
& "C:\Users\jiang\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\java.exe" `
  -jar "D:\dsh workspace\Quickly Pattern\dist\forge-1.20.1-47.4.16-installer.jar"
```

3. 把三个 jar 复制进 mods 目录：

```powershell
$mods = "$env:APPDATA\.minecraft\mods"
New-Item -ItemType Directory -Force -Path $mods | Out-Null
Copy-Item 'D:\dsh workspace\Quickly Pattern\build\libs\eae2-pattern-*.jar',
          'D:\dsh workspace\Quickly Pattern\dist\appliedenergistics2-forge-15.2.16.jar',
          'D:\dsh workspace\Quickly Pattern\dist\jei-1.20.1-forge-15.20.0.106.jar' $mods -Force
```

4. 启动器里选 `forge-1.20.1-47.4.16` 进游戏。主界面能看到 JEI 的合成表就说明三个模组都加载了。

> 不想动现有 `.minecraft` 的话：用 Prism Launcher / MultiMC 建一个 1.20.1 实例，在里面装 Forge 47.4.16，jar 丢进该实例的 `mods`。

## 二、建测试世界

创造模式 + 开启作弊，然后搭一套最小 AE2 网络：

- ME 控制器 1 个（供电）
- ME 驱动器 1 个 + 1k 存储元件 1 个
- ME 终端（或样板编码终端）1 个，接在同一网络上
- 空白样板 64 个（创造模式搜 "Blank Pattern"）

再准备四类机器物品：工作台、熔炉、切石机、锻造台（锻造台测试需要模板 + 可升级装备）。

## 三、用例表

按顺序做，每条都能对应到需求编号。**每做完一条都回网络里看一眼已编码样板数对不对**。

| # | 操作 | 期望 |
| --- | --- | --- |
| 1（需求 2） | 打开 JEI 的工作台配方页，点左侧工作方块竖条最下方的九宫格按钮 | 出现「读取配方」进度页（几千条配方也不会卡死）→ 进预览，行数约等于 JEI 该类别配方数 |
| 2 | 预览里点「生成」 | 聊天栏报告「已生成 N 个样板，消耗 N 个空白样板」；ME 终端里出现 N 个已编码样板，空白样板少 N 个 |
| 3（需求 5） | 打开熔炉的烧炼类别，同样生成 | 生成的是**处理样板**，图标与合成样板不同 |
| 4 | 切石机类别生成 | 一次生成几十条切石样板，不报错 |
| 5 | 锻造台类别生成 | 生成锻造样板；若配方含模板，图案里 template/base/addition 位置正确 |
| 6（需求 6） | 预览里取消勾选 3 条，再生成 | 只生成剩下的，被取消的 3 条**不在**网络里 |
| 7（需求 3） | 只留 2 个空白样板，勾 10 条 | 预览顶部数字变红提示不足；点生成后聊天栏提示「空白样板不足」，且网络里空白样板**一个不少** |
| 8（需求 4） | 把驱动器塞满（多个 1k 元件装满石头），再生成较多样板 | 报告「存储空间不足，已在第 x/N 个样板处停止」，刚扣的空白样板被归还 |
| 9（需求 7） | 退出存档，编辑 `<存档>/serverconfig/eae2_pattern-server.toml`：`ignoredStacks = ["minecraft:bucket"]`，重启游戏再生成 | 新生成的样板里不再出现桶；原样板不受影响 |
| 10（需求 8） | 在 JEI 里对某个物品按 `U` 看用途列表 | 左侧工作方块竖条最下方的九宫格按钮仍在，点它仍能工作 |
| 11 | 悬停机器物品按 `G` 键 | 单类别直接进预览；多类别先弹类别选择页 |
| 12 | 无线终端链接到无线接入点，走远 500 格再生成 | 报「没有可用的 AE2 网络」，一条都不写 |

## 四、验证样板本身对不对（最重要的一步）

生成数量对不代表样板对。挑 3 条做真实合成：

1. 把已编码样板放进 AE2 样板供应器，接上 ME 合成 CPU；
2. 请求产物，看机器是否真的做出来、消耗是否正确；
3. **合成形状配方**专门挑一条 `"ABA"` / `" C "` 这种带空位的（例如火把、楼梯、栅栏），在 AE2 样板终端里打开它的图案，确认空位和相对位置与 JEI 一致。

如果形状对了但数量多了/少了，多半是该配方在 JEI 里画的输入槽本来就缺信息，属于整合包适配问题，见 `README.md` 的「已知限制」。

## 五、出问题看哪里

- 日志：`%APPDATA%\.minecraft\logs\latest.log`，搜 `eae2_pattern`。
- 配置（单人）：`%APPDATA%\.minecraft\saves\<存档>\serverconfig\eae2_pattern-server.toml`。改完要重进存档。
- 启动就崩、提示缺依赖：确认 `mods` 里三个 jar 都在，且 JEI 是 15.20+（低版本会缺我用到的 API）。
- 配方页左侧竖条里没有按钮：确认你打开的是 JEI 自己的配方界面（不是别的模组仿制的界面），并且 JEI 版本 ≥ 15.20。按钮靠反射读取 JEI 的 `RecipesGui.logic`，JEI 改了字段名时按钮会消失，日志里会有 `warn`。
- 生成 0 条：该类别里 JEI 建不出布局，日志里会有 `Skipping recipe` 之类的 debug 行。

## 六、测完请告诉我

把这几类结果贴回来，我据此改下一版：

1. 哪一类配方生成失败 / 数量不对（附类别名）；
2. 哪条样板在 AE2 里图案错了（附配方 id，样板终端里能看到）；
3. 报告文案是否够清楚（特别是停止原因）；
4. 进度页在大类别下大概读多久。
