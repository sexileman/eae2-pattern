---
navigation:
  parent: index.md
  title: 通配符样板 / Wildcard Pattern
  icon: eae2_pattern:machine_wildcard_pattern
  position: 10
categories:
- tools
item_ids:
- eae2_pattern:machine_wildcard_pattern
---
# 通配符样板

<ItemImage id="eae2_pattern:machine_wildcard_pattern" scale="4" />

将同一机器类型的多条处理配方提供给 AE2。样板绑定机器类型，不能让不兼容的机器执行其他机器的配方。

## 获取与编码

<RecipeFor id="eae2_pattern:machine_wildcard_pattern" />

1. 将一张 AE2 空白样板无序合成为一张通配符样板。无 NBT 的样板最多堆叠 64 张。
2. 手持样板右键，选择机器类型；也可以从批量编码页面打开通配符配置。
3. 使用形态、材料或输入输出规则筛选配方，在预览页核对输入、输出和预置工具。
4. 写入手持样板会使用手中的载体，无需连接 ME 网络。一叠空白样板编码时，背包需有一个空位。
5. 编码至网络时，ME 网络必须存有足够的空白通配符样板。普通 AE2 空白样板和已编码样板不计入此库存。

## 规则与预览

形态筛选可直接选择“锭 → 板”等组合，不必手工输入材料标签。材料筛选可限制同一材料系列。

输入与输出分别支持包含、排除规则；排除优先。高级规则可使用 `&gtceu:silver_single_wire` 匹配注册 ID、`$forge:ingots` 匹配标签、`@gtceu` 匹配模组。
动态规则使用可在服务端解析的 ID、标签与模组条件；本地化名称和拼音不作为持久动态规则。

选择“原始配方 / 编码预览”可核对原配方及最终编码数量。倍率按钮调整处理配方数量，不消耗材料不会随倍率增加。
配置电路、模具等被排除时，仍须在机器中预置。概率材料按显示数量编码，不代表每次必定产出。

## 容量与更新

每张最多 2048 条配方。动态模式超过上限时需缩小范围；固定收录模式可自动拆分，较大的 NBT 可能使其提前拆分。
服务端会在写入前核对实际所需载体，不足时不开始扣除。

动态规则在重新解析时匹配当前配方，提示中的已匹配数量是编码时记录的数量。固定模式保存所选配方，源配方变化后可能需要重新编码。

## 供应器

将已编码样板放入连接对应机器的 AE2 样板供应器。安装相应模组时，也支持 ExtendedAE 的 ME 扩展样板供应器和受支持 GTLCore 版本的通配符样板总成。

样板绑定的是机器类型，而不是某个方块坐标。检查机器配方、电压、配置电路、输入输出端口及回收网络，确保实际机器能够执行所收录的配方。

## 清除数据

将一张通配符样板单独放入工作台、背包合成栏或 AE 合成终端，取出结果即可清除编码、规则及自定义名称，返回空白通配符样板。

## English

Craft one AE2 blank pattern into one wildcard pattern. Right-click the held item, select a machine type, filter its recipes, then encode. Writing a held item needs no ME network; encoding a stack needs a free inventory slot.

Network encoding consumes blank wildcard patterns, not regular AE2 blanks or encoded carriers. The server checks the exact number of carriers before writing.

Use form and material selectors for rules such as ingot to plate. Advanced rules support `&namespace:item`, `$namespace:tag` and `@modid`; exclusions take priority. Dynamic saved rules require server-resolvable identifiers.

Each carrier supports up to 2048 recipes. Narrow oversized dynamic rules; fixed collections can split earlier for large data. Place the encoded item in a provider connected to a compatible machine. ExtendedAE providers and supported GTLCore wildcard assemblies are supported when installed.

Keep excluded circuits and molds in the machine. Chance outputs are encoded at their displayed amounts and are not guaranteed drops. To reset, craft a single wildcard pattern by itself; all NBT, including custom names, is removed.
