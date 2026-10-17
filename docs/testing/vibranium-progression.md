# 振金怪物装备成长验证

开发者：NingMeng123。日期：2026-10-18。

将振金追加为保存索引 9、材质等级 8。原有保存索引 0～8、普通日第 1152 天进度 8、第八次血月艾德曼档位保持；第 1280 天普通日进度 9、第九次血月振金档位是本轮扩展。

## 最终结果

2026-10-18 已完成最终编译与自动验证：

| 验证 | 最终结果 | 证据 |
| --- | --- | --- |
| JUnit 全套 | 1541 项通过，0 失败、0 错误 | `build/test-results/test/TEST-*.xml` |
| 装备成长概率单元测试 | 15 项通过，0 跳过、0 失败、0 错误 | `build/test-results/test/TEST-io.github.ningmeng123.mite.rules.BloodMoonEquipmentRulesTest.xml` |
| 全量 GameTest | 1955 项全部通过 | `build/verification/vibranium-acceptance-20261018.log` |
| 已安装 Simply Swords 的集成 GameTest | 47 项全部通过 | `build/verification/vibranium-present-verified.log` |

最终全量日志在北京时间 02:19:17 明确记录 `All 1955 required tests passed`，随后为 `BUILD SUCCESSFUL in 5m 51s`。Simply Swords 集成日志在 02:07:08 记录 `All 47 required tests passed`。这些结果来自重新编译后的最终测试运行。

覆盖二十组归一化分布、全部十种材质、独立精确有理数分数、十七个日历字面量锚点、每天所有高阶尾部、振金实际护甲概率严格增长、铜银等分、全部累计抽取边界、无效输入、Long 极值、第八／第九次血月与第 1152／1280 天边界。

独立夹具生成命令：

```powershell
python tools/specs/generate_blood_equipment_fixture.py
```

夹具不导入生产 Java 公式。它在九个材质等级上用 `fractions.Fraction` 归一化，再将铜银同级的权重等分，输出静态精确分数。

## 实际装备与保存验证

`BloodMoonEquipmentGameTests` 的独立血月权重表已扩展为十列，包含旧阶段中新增的振金尾部；实际出生日期字面量明确保持第 320 天为档位 8，第 352 天开始为档位 9。新增 `ninthBloodAndOrdinaryCapGenerateActualVibraniumAndPreserveOldIndices` 检查第九次血月混搭振金和艾德曼、空槽、普通日第 1280 天振金装备，以及 NBT 保存不重抽。手持匹配测试新增振金的四种工具，`MobShieldGameTests` 的实际盾牌匹配遍历新增振金。

上述运行时测试已纳入最终 1955 项全量通过结果；已安装 Simply Swords 时的新增武器、材质分布和可选兼容路径也通过了 47 项集成测试。

完整服务器验证曾在 `SpecialMobEquipment` 发现实际生产问题：固定来源装备路径的平行材质表仍有九项，抽中振金索引 9 时越界。已补齐振金映射与反向材质索引；原生振金弓参与最高档，缺少振金锁链甲的家族保持已注册的艾德曼锁链甲上限。相关晚期世界断言、最终磨损物品断言、守卫切换武器断言和下界剑集合一并扩展；新增确定性最高尾部测试同时覆盖普通日第 1280 天及第九次血月的七种来源装备家族。该修复及其回归测试已通过最终全量验证。
