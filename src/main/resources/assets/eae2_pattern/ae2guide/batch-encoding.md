---
navigation:
  parent: index.md
  title: 批量编码 / Batch Encoding
  icon: ae2:processing_pattern
  position: 20
---
# 批量编码

1. 在 JEI 中对工作方块按用途快捷键，打开对应配方类别。
2. 点击工作方块列表中的 EAE2 Pattern 卡片图标，收集该类别的配方。
3. 分别搜索输入与输出，配置输入消耗类型、输出策略和分组。搜索在停止输入 200ms 后执行。
4. 勾选需要编码的配方。选择框、材料槽、概率标记与详情可用于核对；超出的材料在列表中以数量提示，详情保留完整内容。
5. 单击切换原始配方与编码预览，检查数量、流体单位、配置电路、不消耗工具和概率提示。
6. 使用倍增、倍减及重置调整处理配方数量；连接 ME 网络后进行编码。

普通批量编码每张消耗一个 AE2 空白样板。通配符编码使用独立的空白通配符样板；两类库存不会互相替代。

JEI 搜索、书签及材料拖入搜索框仍可使用。标签使用 `$`，物品注册 ID 使用 `&`；例如 `&gtceu:silver_single_wire`。安装 Just Enough Characters 时，本地文本搜索支持其拼音匹配。

## English

Open a workstation's uses in JEI, then click the EAE2 Pattern card in its catalyst list. Filter inputs and outputs, select recipes, and compare original recipes with the encoded preview.

Search waits 200ms after the last change. Material overflow stays available in the detail panel. Check circuit settings, fluid units, non-consumed tools and chance outputs before encoding.

Ordinary batches consume AE2 blank patterns; wildcard batches consume blank wildcard patterns. JEI search, bookmarks and ingredient dragging remain available. Use `$` for tags and `&` for registry IDs. Optional Just Enough Characters integration supports pinyin in local text searches.
