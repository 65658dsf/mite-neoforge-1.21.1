# 第八次血月艾德曼与铜银同级验证

开发者：NingMeng123。日期：2026-10-16。自动验证对象为基于 `838a4fefcda68ef2269a4dc279341ca082b968db` 的血月调整工作树，下述校验值对应当时交付的测试包。用户随后授权提交当前修改。规则与手测见 [装备成长](../blood-moon-equipment.md)，独立数值依据见 [概率规格](../../spec/blood-moon-equipment.json)。

本批按用户要求调整现代扩展：第八次血月及以后以艾德曼为最高概率材质；铜和银共享一个等级并均分该等级概率，不把银作为单独升级。

## 结果

| 检查 | 实际结果 |
| --- | --- |
| JUnit | 1342 项全部通过；本批新增 2 项，包含第八次血月边界及铜银同级 |
| 独立服务器 GameTest | 全新存档 1390 项必需测试全部通过；本批新增 2 项，覆盖第八次艾德曼混搭与第五次铜银混搭 |
| 数值 | 九个兼容索引的 18 组分布、12 个普通日锚点与独立有理数一致；铜银始终同概率、各项非零、归一总和为 1 |
| 日历与成长 | 第八次实际血月为第 320 天；蓝月不计数，第 6/7/8 次依次使用铁/远古/艾德曼主档；普通日高阶累计概率不超过对应血月 |
| 保存与生成 | 原有槽位、主手和固定装备保留；四部位仍独立，可缺件和混搭；旧 v3 实体跨越第八次血月后保存重载仍不重抽 |
| JAR | 构建通过；920 个生产类与编译输出一致，2241 个 JSON 可解析，264 个 Mixin 类均存在且登记不重复 |
| 依赖与工作区 | 必需依赖仍只有 Minecraft 和 NeoForge，不捆绑测试或可选 API；44 个既有未提交文件字节未变 |

## 独立概率与适配

材质索引仍为 0～8，等级映射为 `[0,1,2,3,3,4,5,6,7]`；八个等级先按距离归一化，铜、银各分等级 3 的一半。普通日日期进度、穿甲率和空手补工具率公式保留，因此兼容索引 3～4 之间材质条件分布相同，但穿戴/持械概率继续按日期增长。物品品质、磨损、附魔和掉落规则未因本批额外提高。

第八次血月使用最高档，独立 `Fraction` 结果：

- 已成功穿戴的单件材质为艾德曼：`1280000000 / 1968787323`，约 **65.014640%**。
- 一个原本空护甲槽生成艾德曼：`409600000 / 656262441`，约 **62.414055%**。
- 四槽原本全空时至少一件艾德曼：约 **98.004269%**；四件均为艾德曼约 **15.175031%**。
- 每槽任意护甲为 96%，四件全空为 0.000256%，四件齐全（允许混材）为 84.934656%；空手补工具率 94%。

实际生成测试使用独立整数权重与随机序列，验证艾德曼头盔、秘银胸甲、空护腿和艾德曼靴，以及铜头盔、银胸甲、空护腿和铁靴的混搭，未以生产选材方法作为期望值来源。

## 运行与记录

Java 21、Minecraft 1.21.1、NeoForge 21.1.259、ModDevGradle 2.0.148。

```powershell
.\gradlew.bat test jar runGameTestServer -PmiteGameTestDirectory=build/verification/blood-eighth-final-run --console=plain
python build/verification/audit-blood-eighth-package.py
```

本机忽略目录的证据：`build/verification/blood-eighth-final-test.log` 包含 `All 1390 required tests passed` 与 `BUILD SUCCESSFUL`；JUnit XML 在 `build/test-results/test/`；包审计在 `build/verification/blood-eighth-package-audit.json`。

首轮 1390 项中，血月相关用例全部通过，仅旧 `swappingToolsBeforeStopCannotCompleteOrBypassDelayedMining` 的同刻掉落查询为 0。直接解析该轮存档实体区确认存在数量为 1 的原木，属于空间索引可见性问题。仅将 `LogHarvestGameTests.BlockFixture` 改为既有物品生成事件捕获与清理，保留真实 START/STOP/tick、原木数量和耐久断言，未改挖掘生产代码；最终全套复跑通过。首轮日志保存在 `build/verification/blood-eighth-first-test.log`。

测试包 `build/libs/mite-0.1.0-blood-eighth-test.jar` 与正常构建产物内容一致，大小 7,495,622 字节，SHA-256：

```text
d207ab5d98cae3dba2f04e8a015608e944eba90c188e8b7c959645083481a3b8
```

没有操作或重启用户游戏。2026-10-16 用户要求提交当前修改；未另行提供逐项客户端画面、真实多人或长期运行手测结果，不补记这些项目已通过。测试夹具修复单独采用 `fix` 提交，装备成长调整采用 `feat` 提交。
