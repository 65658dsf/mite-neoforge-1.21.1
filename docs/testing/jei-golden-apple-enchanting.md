# 金苹果附魔与 JEI 展示

开发者：NingMeng123。2026-10-26。

金苹果转换通过附魔菜单直接实现，没有 `RecipeManager` 配方，因此 JEI 无法自动发现。现在通过可选 JEI 插件注册“附魔”分类，明确展示 **1 个金苹果 → 1 个附魔金苹果，消耗 200 点经验，无需青金石、无需书架**。普通附魔台和绿宝石附魔台都登记为加工设备；它们不会作为原料消耗。

JEI 页面的经验价格复用 `R196EnchantmentTable.energy` 和 `experienceCost`。此处的 200 是经验点数，不是经验等级。配方登记独立于世界的普通合成配方扫描，不生成工作台配方或自动填料。

## 手动检查

1. 更新模组并重启客户端，在 JEI 对附魔金苹果按 R，应出现“附魔”分类及金苹果原料。
2. 对金苹果按 U，应能查到转换页面；对普通、绿宝石附魔台分别按 U，也应查到此页。
3. 确认页面显示 200 点经验、无需青金石、无需书架，没有青金石原料格。
4. 在无书架的两种附魔台放入一个金苹果，青金石槽留空，支付 200 点经验应得到一个附魔金苹果；不足 200 点时拒绝转换。
5. 普通装备附魔仍遵守自身的青金石要求。

## 自动检查

`EnchantingJeiLayoutAudit` 随带 JEI 的客户端数据生成运行，使用真实 JEI API 检查类别、输入和输出的检索角色、200 点经验价格、无青金石输入和两个附魔台设备登记。该检查不创建游戏窗口，不替代实际画面和键盘查询验收。

2026-10-26 验证通过：

- `build` 成功，1,769 项 JUnit 无失败；构建包含新的 JEI 类与中英文翻译，不包含 GameTest 类。
- 隔离编译 `GoldenAppleEnchantingGameTests`、`R196EnchantmentTableGameTests`、`VanillaEnchantingGameTests`，31 项真实菜单 GameTest 全部通过。覆盖两种台、三个选项、0／4 层书架、青金石为空／不足／充足时苹果均不消耗青金石、199／200 点边界、支付回调、客户端菜单包同步及普通附魔继续扣除青金石。日志：`build/verification/golden-apple-regression-20261026.log`。
- 使用 JEI 19.57.0.451 与 MezzConfig 0.6.8 执行无窗口客户端数据生成，`MITE enchanting JEI layout audit` 通过；转换后的附魔界面及原生语言解析审计也通过。数据生成写入独立验证目录。日志：`build/verification/golden-apple-jei-audit-20261026.log`，报告：`build/verification/golden-apple-enchantment-client-audit-20261026.json`。

构建包：`build/libs/mite-0.2.15.jar`。本轮未执行客户端画面与实际 R／U 按键验收。
