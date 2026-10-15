# 地底宝库奖励自动验证

开发署名：NingMeng123。日期：2026-10-15。工作树基于 `09ead4d3eb740c2ddb6127831e4dc5f76ddf78ff`，包含地底地牢扩展。2026-10-15 用户反馈“没有问题了提交吧”，确认当前交付正常并授权提交；逐项画面及真实多人结果未另行记录。本轮未操作或重启用户游戏。

## 结果

| 验证 | 结果 |
|---|---|
| 数据生成 | 成功；八份奖励表、两份材料箭子表 |
| JUnit | 1221 项全部通过，零失败、错误、跳过 |
| 全量 GameTest | `All 1076 required tests passed`，`BUILD SUCCESSFUL`；本轮新增 11 项 |
| 独立概率与资源审计 | 306 项全部通过，使用有理数计算完整开启至少一次的概率 |
| 发布包及已有 Python 回归 | 781 个生产类与编译输出逐字节匹配、2133 个 JSON、199 个 Mixin；11 项 Python 回归通过 |
| 无关工作保留 | 原有 42 个排除文件及本轮开始时两份外部语言修改保持 SHA-256 不变 |
| 客户端与真实多人 | 用户确认本批正常；逐项画面及真实多人未另行记录 |

构建产物 `build/libs/mite-0.1.0.jar`，7,174,935 字节，SHA-256：

```text
e67308239186b50ef58027ebfd6de7dc1e379dc662e33fae0a0cb108ebcb23b5
```

## 新增验证范围

七项路由 GameTest 检查普通/不祥真实宝库配置、八种预览表、其他维度隔离、自定义钥匙及组件/范围/选择器/检测器保留、保存先于首次 tick 的旧配置迁移、二次重载后的已领奖玩家限制，以及真实 `VaultBlock.useItemOn` 开锁、消耗一个钥匙、预览、原生延迟及待发奖励保存。

四项战利品 GameTest 使用 Minecraft 实际战利品引擎，对普通、不祥根表各进行 8192 次固定种子开启；对两份箭表各抽取 8192 次，检查全部材料可达、数量、禁用条目排除、远古装备附魔和宽容差概率。另检查原版四份稀有/独特子表仍能产出原钻石装备及装饰奖励，确认没有全局覆盖。随机样本仅作为运行检查，精确掉率以独立计算为准。

`tools/testing/verify_underworld_vault_loot.py` 独立读取锁定的原版资源与生成后的战利品图，不调用生产 provider，检查根表的 80%/20% 选择、额外 1～3 抽、25%/75% 独特触发、物品和数量、禁止项、附魔函数保留及所有十种箭权重。两份箭池权重均为 `480/稀有度系数`，总和 1921。

## 概率结论

- 普通：绿宝石 38.441636730% → 40.953170%，钻石 8.5264896% → 9.172447%。
- 不祥：绿宝石 56.213991770% → 58.277194%，钻石 26.391967078% → 27.587026%；绿宝石块 13.793103448% → 14.864865%，钻石块 2.758620690% → 2.972973%。
- 不祥附魔金苹果 22.5% → 25%，沉重核心保持 7.5%。
- 远古金属粒/秘银粒/艾德曼粒：普通 8.333333% / 4.166667% / 2.083333%，不祥 25% / 12.5% / 5%。

完整含义、箭种和手测见[地底宝库奖励](../underworld-vault-loot.md)。这些是按用户要求设定的现代宝库平衡，不标记为 R196 原生掉率。

## 复现与日志

```powershell
.\gradlew.bat runData -PmiteDataDirectory=build/verification/underworld-vault-data-run
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/underworld-vault-gametest-release
python tools/testing/verify_underworld_vault_loot.py
python tools/testing/verify_stacking_030.py --report build/verification/underworld-vault-package-audit.json
```

使用 Java 21。复跑使用未用过的隔离 GameTest 存档目录，真实测试客户端仍使用 `run/mods/`，本轮没有搬动用户模组。

- `build/verification/underworld-vault-datagen.log`：生成成功，十份新 JSON。
- `build/verification/underworld-vault-gametest-release.log`：最终完整回归通过。
- `build/verification/underworld-vault-loot-audit.json`：306 项独立检查和原版/地底概率。
- `build/verification/underworld-vault-package-audit.json`：构建类、资源、Mixin 和已有 Python 回归。
- `build/verification/underworld-vault-commit-scope.json`：本轮文件范围与原有工作哈希保留记录。

首轮日志 `underworld-vault-gametest.log` 保留一项旧投射物测试失败。失败是随机测试网格区块尚未进入实体查询索引，原代码实际已成功生成投射物；测试改为捕获真实实体加入事件，并将原有燃烧断言加强为精确 200 刻，原来只消耗一份蛛网的断言保留。未为该测试更改生产代码；修正后的完整回归全部通过。
