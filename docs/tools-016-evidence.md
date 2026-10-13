# 016 R196 工具清单、数值与行为证据

开发者：NingMeng123。原作：Avernite。版本依据：本地 R196 发布包及 `reference-data/runtime-registry.json`，源码为 `.reference/r196/decompiled`。本文件不是游戏通过记录；本轮统一构建、GameTest 和用户实机验收由主线程完成后记录。

## 范围与计数

目录中默认包含且运行时确为 `xj` 工具的条目为 **105**；其中 **93** 项本轮新增，另有既有燧石短斧和 11 个复用原版 ID（铁金五族与木棒）。额外核对运行时旧铁剪刀 `359` 和木铲 `269`，也沿用现代 ID，共 **107** 个源工具定义。

包含八种鹤嘴锄、八种匕首、燧石/黑曜石小刀和木棍。用户排除的八种金属小刀不注册，不能把它们在原包存在误写成原版不存在。原包没有注册的燧石镐等不凭空增加。锈铁工具保留实际注册和能力，没有捏造合成配方；来源怪物/战利品仍属于后续迁移范围。

现代石、钻石及下界合金工具保留其 ID、速度、附魔、冷却、右键功能和工具目标；仅耐久接入明确的现代材料倍率 1/16/32。木镐、木斧维持先前用户要求的正常获取移除。

## 来源索引与共用公式

- `xj.java:20–40`：工具单栈、基准耐久 `int(4F * componentCount * material.durability * 100F)`；`EnumEquipmentMaterial.java:6–16` 为材料倍率和品质上限。
- `xj.java:43–107`：材料效率 wood 1、flint 1.25、obsidian 1.5、rusted_iron 1.25、copper/silver/gold 1.75、iron/ancient_metal 2、mithril 2.5、adamantium 3。`akc.java:351–388` 为材料伤害加成。
- `xj.java:125–138`：有效目标还受材料采集等级约束；石质工具等级为材料最低等级减 1，金属直接使用材料等级。`akc.java:623–633` 给出各材料最低等级。
- `xj.java:146–180`：按有效方块硬度和家族磨损计算；零硬度、无效目标、便携物、创造模式不扣采掘耐久。具体逐点耐久、品质、不毁最后一点、附魔算法由 `EquipmentDurability` 统一维护，工具类不复制另一套公式。
- `uf.java:1059–1065`：银工具对亡灵伤害乘 1.25；木棍类、战锤对骷髅乘 2；二者可叠加。现代攻击冷却和属性管线保留，绝不把这些乘数算进基础面板两次。
- `yb.java:tryTillSoil` 与 `yx.java:tryEntityInteraction`：成功耕地和剪羊各耗 50；`ItemMattock` 复用耕地。新版新工具通过真实物品交互、方块权限和 `IShearable` 路径实现。
- `RecipesTools` 对应运行时配方的精确顺序、形状、输入、difficulty、bench_material 全存于 `spec/r196-tools-016.json`；生产 `ToolRecipes` 每条注释记录 order 和 difficulty。

| 家族 | 组件数 | 基础效率（乘材料效率前） | 基础伤害加成（加材料伤害前） | 默认攻击磨损 |
| --- | ---: | ---: | ---: | ---: |
| 镐 | 3 | 4 | 2 | 100 |
| 斧 | 3 | 4 | 3 | 100 |
| 短斧 | 1 | 2 | 2 | 133 |
| 战斧 | 4 | 3 | 4 | 75 |
| 战锤 | 5 | 3 | 2 | 66 |
| 铲 | 1 | 4 | 1 | 100 |
| 鹤嘴锄 | 4 | 3 | 1 | 100 |
| 锄 | 2 | 2 | 1 | 200 |
| 剑 | 2 | 2 | 4 | 50 |
| 匕首 | 1 | 2 | 2 | 50 |
| 小刀 | 1 | 2 | 1 | 50 |
| 镰刀 | 2 | 4 | 1 | 400 |
| 剪刀 | 2 | 4 | 0 | 200 |
| 木棍 | 1 | 2 | 1 | 25 |
| 木棒（已有） | 2 | 2 | 2 | 25 |

注意 `aqz.V` 是砂岩（`aqz.java:2284`），不是蜘蛛网。斧族对砂岩效率减半，基础采掘磨损为 1.875；短斧与战斧继续叠加各自倍率。小刀的较低磨损目标为旧布、植物、藤蔓；镰刀低磨损目标是旧草和小麦，不是所有藤蔓。镰刀攻击若有锋利变 200、吸血变 100；吸血附魔本身尚未注册，不能宣称对应流程已可游玩。

## 现代目标、配方与功能边界

新工具以现代 `mineable` 标签为主要兼容入口，再补源中额外目标（砂岩、布、叶、草、作物、玻璃、冰、南瓜等）。红砂岩系列对应旧砂岩；深板岩矿对应其同类矿物；哭泣黑曜石对应黑曜石；远古残骸/下界合金块对迁移工具采用等级 4。这些现代映射是设计选择，不冒充 R196 注册表里存在相同方块。

已确认源矿材门槛：铜/银/金/锈铁工具等级 2，铁/远古 3，秘银 4，艾德曼 5；黑曜石、绿宝石、铁块要求 3，钻石矿/块要求 4。工具无效时速度回到 1，也不给正确工具掉落资格。现代石/钻/下界合金本轮保留原有目标规则。

本轮配方为 93 条常规源形状，外加 2 条工作台拆刀；既有木棒/燧石短斧形状不重复生成。旧四种木材工作台拆刀合并到各新工作台 ID；玻璃碎片等 015 配方不被工具模块重写。原配方用筋处同时允许线，属于与既有现代开局保持一致的适配。金属材料门槛统一交给服务端工作台校验，绝不仅靠配方书隐藏。计时由主线程集成的制造服务处理，精确 difficulty 留在源数据而不是自行估算秒数。

新增工具继续使用现代冷却：斧/短斧/战斧/战锤 1 APS，镐/铲/鹤嘴锄 1.2 APS，锄 2 APS，其他 1.6 APS。源触及加成直接叠加现代交互基础距离，是明确现代适配。剪刀保留现代裁剪长植物、`IShearable` 兼容，并通过同一个真实采集路径右键剪叶/草/网/羊毛；其正常掉落对所有剪刀扩展标签，由共享资源模块实现。

不能写成已完成的依赖：

- R196 普遍工具右键格挡及其减伤公式尚未接入；没有替换成现代盾牌的完全格挡效果。
- 作物病害、肥沃/吸血附魔、源工作台合成品质选择、特殊修理制造流程仍需各自完整迁移。普通品质与品质耐久持久化接入不等于完整品质制造已完成。
- 新金属矿、锈铁怪物装备、熔炼炉温及其它特殊来源由后续内容模块接入。本次新增物品有已证实的能力和配方，不代表所有资源获取流程已完成。
- 铜材质及其他图像策略以用户最新要求和 `docs/art/textures-016.md` 为准，本工具逻辑模块不改图像。

## 验收范围（待统一运行）

`ToolEquipmentGameTests` 覆盖 93 个新 ID 计数与排除项、独立容量向量、真实玩家采石/砍木/存档耐久、有效目标及等级倍率、耕地剪羊 50、银/钝器实际攻击、12 种铜工具精确形状的消费。共享耐久模块另测品质/耐久附魔/终末保护和原版 ID 动作；工作台、自动化、制造计时由相应模块测。不以源码存在、自动测试定义或单纯编译替代用户实机通过。

## 完整工具表

下表耐久是未应用品质的源基础值。锈铁默认品质最高为 poor，实际栈最大耐久会按 0.75 缩放。

| 现代 ID | 原 ID | 家族 | 基础耐久 | 有效速度 | 攻击属性加成 | 采集等级 | 源配方 order |
| --- | ---: | --- | ---: | ---: | ---: | ---: | --- |
| `mite:flint_hatchet` | 978 | HATCHET | 400 | 2.5 | 3.0 | 1 | 300 |
| `mite:obsidian_hatchet` | 1204 | HATCHET | 800 | 3.0 | 4.0 | 2 | 301 |
| `mite:copper_hatchet` | 1175 | HATCHET | 1600 | 3.5 | 5.0 | 2 | 286 |
| `mite:silver_hatchet` | 1176 | HATCHET | 1600 | 3.5 | 5.0 | 2 | 288 |
| `mite:golden_hatchet` | 1177 | HATCHET | 1600 | 3.5 | 4.0 | 2 | 296 |
| `mite:rusted_iron_hatchet` | 1181 | HATCHET | 1600 | 2.5 | 4.0 | 2 | 无 |
| `mite:iron_hatchet` | 1178 | HATCHET | 3200 | 4 | 6.0 | 3 | 290 |
| `mite:ancient_metal_hatchet` | 1262 | HATCHET | 6400 | 4 | 6.0 | 3 | 298 |
| `mite:mithril_hatchet` | 1179 | HATCHET | 25600 | 5.0 | 7.0 | 4 | 292 |
| `mite:adamantium_hatchet` | 1180 | HATCHET | 102400 | 6 | 8.0 | 5 | 294 |
| `minecraft:golden_axe` | 286 | AXE | 4800 | 7.0 | 5.0 | 2 | 209 |
| `minecraft:iron_axe` | 258 | AXE | 9600 | 8 | 7.0 | 3 | 203 |
| `mite:flint_axe` | 979 | AXE | 1200 | 5.0 | 4.0 | 1 | 214 |
| `mite:obsidian_axe` | 1205 | AXE | 2400 | 6.0 | 5.0 | 2 | 216 |
| `mite:copper_axe` | 980 | AXE | 4800 | 7.0 | 6.0 | 2 | 199 |
| `mite:silver_axe` | 981 | AXE | 4800 | 7.0 | 6.0 | 2 | 201 |
| `mite:rusted_iron_axe` | 984 | AXE | 4800 | 5.0 | 5.0 | 2 | 无 |
| `mite:ancient_metal_axe` | 1241 | AXE | 19200 | 8 | 7.0 | 3 | 211 |
| `mite:mithril_axe` | 982 | AXE | 76800 | 10.0 | 8.0 | 4 | 205 |
| `mite:adamantium_axe` | 983 | AXE | 307200 | 12 | 9.0 | 5 | 207 |
| `minecraft:golden_pickaxe` | 285 | PICKAXE | 4800 | 7.0 | 4.0 | 2 | 27 |
| `minecraft:iron_pickaxe` | 257 | PICKAXE | 9600 | 8 | 6.0 | 3 | 12 |
| `mite:copper_pickaxe` | 967 | PICKAXE | 4800 | 7.0 | 5.0 | 2 | 2 |
| `mite:silver_pickaxe` | 968 | PICKAXE | 4800 | 7.0 | 5.0 | 2 | 7 |
| `mite:rusted_iron_pickaxe` | 971 | PICKAXE | 4800 | 5.0 | 4.0 | 2 | 无 |
| `mite:ancient_metal_pickaxe` | 1239 | PICKAXE | 19200 | 8 | 6.0 | 3 | 32 |
| `mite:mithril_pickaxe` | 969 | PICKAXE | 76800 | 10.0 | 7.0 | 4 | 17 |
| `mite:adamantium_pickaxe` | 970 | PICKAXE | 307200 | 12 | 8.0 | 5 | 22 |
| `minecraft:golden_shovel` | 284 | SHOVEL | 1600 | 7.0 | 3.0 | 2 | 368 |
| `minecraft:iron_shovel` | 256 | SHOVEL | 3200 | 8 | 5.0 | 3 | 365 |
| `mite:flint_shovel` | 972 | SHOVEL | 400 | 5.0 | 2.0 | 1 | 213 |
| `mite:obsidian_shovel` | 1202 | SHOVEL | 800 | 6.0 | 3.0 | 2 | 215 |
| `mite:copper_shovel` | 973 | SHOVEL | 1600 | 7.0 | 4.0 | 2 | 363 |
| `mite:silver_shovel` | 974 | SHOVEL | 1600 | 7.0 | 4.0 | 2 | 364 |
| `mite:rusted_iron_shovel` | 977 | SHOVEL | 1600 | 5.0 | 3.0 | 2 | 无 |
| `mite:ancient_metal_shovel` | 1240 | SHOVEL | 6400 | 8 | 5.0 | 3 | 369 |
| `mite:mithril_shovel` | 975 | SHOVEL | 25600 | 10.0 | 6.0 | 4 | 366 |
| `mite:adamantium_shovel` | 976 | SHOVEL | 102400 | 12 | 7.0 | 5 | 367 |
| `minecraft:golden_hoe` | 294 | HOE | 3200 | 3.5 | 3.0 | 2 | 210 |
| `minecraft:iron_hoe` | 292 | HOE | 6400 | 4 | 5.0 | 3 | 204 |
| `mite:copper_hoe` | 985 | HOE | 3200 | 3.5 | 4.0 | 2 | 200 |
| `mite:silver_hoe` | 986 | HOE | 3200 | 3.5 | 4.0 | 2 | 202 |
| `mite:rusted_iron_hoe` | 989 | HOE | 3200 | 2.5 | 3.0 | 2 | 无 |
| `mite:ancient_metal_hoe` | 1242 | HOE | 12800 | 4 | 5.0 | 3 | 212 |
| `mite:mithril_hoe` | 987 | HOE | 51200 | 5.0 | 6.0 | 4 | 206 |
| `mite:adamantium_hoe` | 988 | HOE | 204800 | 6 | 7.0 | 5 | 208 |
| `mite:copper_mattock` | 997 | MATTOCK | 6400 | 5.25 | 4.0 | 2 | 3 |
| `mite:silver_mattock` | 998 | MATTOCK | 6400 | 5.25 | 4.0 | 2 | 8 |
| `mite:golden_mattock` | 999 | MATTOCK | 6400 | 5.25 | 3.0 | 2 | 28 |
| `mite:rusted_iron_mattock` | 1003 | MATTOCK | 6400 | 3.75 | 3.0 | 2 | 无 |
| `mite:iron_mattock` | 1000 | MATTOCK | 12800 | 6 | 5.0 | 3 | 13 |
| `mite:ancient_metal_mattock` | 1244 | MATTOCK | 25600 | 6 | 5.0 | 3 | 33 |
| `mite:mithril_mattock` | 1001 | MATTOCK | 102400 | 7.5 | 6.0 | 4 | 18 |
| `mite:adamantium_mattock` | 1002 | MATTOCK | 409600 | 9 | 7.0 | 5 | 23 |
| `mite:copper_scythe` | 1011 | SCYTHE | 3200 | 7.0 | 4.0 | 2 | 6 |
| `mite:silver_scythe` | 1012 | SCYTHE | 3200 | 7.0 | 4.0 | 2 | 11 |
| `mite:golden_scythe` | 1013 | SCYTHE | 3200 | 7.0 | 3.0 | 2 | 31 |
| `mite:rusted_iron_scythe` | 1017 | SCYTHE | 3200 | 5.0 | 3.0 | 2 | 无 |
| `mite:iron_scythe` | 1014 | SCYTHE | 6400 | 8 | 5.0 | 3 | 16 |
| `mite:ancient_metal_scythe` | 1246 | SCYTHE | 12800 | 8 | 5.0 | 3 | 36 |
| `mite:mithril_scythe` | 1015 | SCYTHE | 51200 | 10.0 | 6.0 | 4 | 21 |
| `mite:adamantium_scythe` | 1016 | SCYTHE | 204800 | 12 | 7.0 | 5 | 26 |
| `mite:copper_shears` | 1018 | SHEARS | 3200 | 7.0 | 3.0 | 2 | 287 |
| `mite:silver_shears` | 1019 | SHEARS | 3200 | 7.0 | 3.0 | 2 | 289 |
| `mite:golden_shears` | 1020 | SHEARS | 3200 | 7.0 | 2.0 | 2 | 297 |
| `mite:rusted_iron_shears` | 1182 | SHEARS | 3200 | 5.0 | 2.0 | 2 | 无 |
| `mite:ancient_metal_shears` | 1247 | SHEARS | 12800 | 8 | 4.0 | 3 | 299 |
| `mite:mithril_shears` | 1021 | SHEARS | 51200 | 10.0 | 5.0 | 4 | 293 |
| `mite:adamantium_shears` | 1022 | SHEARS | 204800 | 12 | 6.0 | 5 | 295 |
| `mite:wooden_cudgel` | 1024 | CUDGEL | 200 | 2 | 1.0 | 0 | 417 |
| `minecraft:wooden_sword` | 1025 | CLUB | 400 | 2 | 2.0 | 0 | 370 |
| `mite:obsidian_knife` | 1203 | KNIFE | 800 | 3.0 | 3.0 | 2 | 303, 496, 497, 498, 499 |
| `mite:flint_knife` | 1023 | KNIFE | 400 | 2.5 | 2.0 | 1 | 302, 492, 493, 494, 495 |
| `mite:copper_dagger` | 1033 | DAGGER | 1600 | 3.5 | 5.0 | 2 | 403 |
| `mite:silver_dagger` | 1034 | DAGGER | 1600 | 3.5 | 5.0 | 2 | 405 |
| `mite:golden_dagger` | 1035 | DAGGER | 1600 | 3.5 | 4.0 | 2 | 413 |
| `mite:rusted_iron_dagger` | 1039 | DAGGER | 1600 | 2.5 | 4.0 | 2 | 无 |
| `mite:iron_dagger` | 1036 | DAGGER | 3200 | 4 | 6.0 | 3 | 407 |
| `mite:ancient_metal_dagger` | 1249 | DAGGER | 6400 | 4 | 6.0 | 3 | 415 |
| `mite:mithril_dagger` | 1037 | DAGGER | 25600 | 5.0 | 7.0 | 4 | 409 |
| `mite:adamantium_dagger` | 1038 | DAGGER | 102400 | 6 | 8.0 | 5 | 411 |
| `mite:copper_sword` | 1028 | SWORD | 3200 | 3.5 | 7.0 | 2 | 371 |
| `minecraft:golden_sword` | 283 | SWORD | 3200 | 3.5 | 6.0 | 2 | 376 |
| `minecraft:iron_sword` | 267 | SWORD | 6400 | 4 | 8.0 | 3 | 373 |
| `mite:silver_sword` | 1029 | SWORD | 3200 | 3.5 | 7.0 | 2 | 372 |
| `mite:rusted_iron_sword` | 1032 | SWORD | 3200 | 2.5 | 6.0 | 2 | 无 |
| `mite:ancient_metal_sword` | 1248 | SWORD | 12800 | 4 | 8.0 | 3 | 377 |
| `mite:mithril_sword` | 1030 | SWORD | 51200 | 5.0 | 9.0 | 4 | 374 |
| `mite:adamantium_sword` | 1031 | SWORD | 204800 | 6 | 10.0 | 5 | 375 |
| `mite:copper_battle_axe` | 1004 | BATTLE_AXE | 6400 | 5.25 | 7.0 | 2 | 4 |
| `mite:silver_battle_axe` | 1005 | BATTLE_AXE | 6400 | 5.25 | 7.0 | 2 | 9 |
| `mite:golden_battle_axe` | 1006 | BATTLE_AXE | 6400 | 5.25 | 6.0 | 2 | 29 |
| `mite:rusted_iron_battle_axe` | 1010 | BATTLE_AXE | 6400 | 3.75 | 6.0 | 2 | 无 |
| `mite:iron_battle_axe` | 1007 | BATTLE_AXE | 12800 | 6 | 8.0 | 3 | 14 |
| `mite:ancient_metal_battle_axe` | 1245 | BATTLE_AXE | 25600 | 6 | 8.0 | 3 | 34 |
| `mite:mithril_battle_axe` | 1008 | BATTLE_AXE | 102400 | 7.5 | 9.0 | 4 | 19 |
| `mite:adamantium_battle_axe` | 1009 | BATTLE_AXE | 409600 | 9 | 10.0 | 5 | 24 |
| `mite:copper_war_hammer` | 990 | WAR_HAMMER | 8000 | 5.25 | 5.0 | 2 | 5 |
| `mite:silver_war_hammer` | 991 | WAR_HAMMER | 8000 | 5.25 | 5.0 | 2 | 10 |
| `mite:golden_war_hammer` | 992 | WAR_HAMMER | 8000 | 5.25 | 4.0 | 2 | 30 |
| `mite:rusted_iron_war_hammer` | 996 | WAR_HAMMER | 8000 | 3.75 | 4.0 | 2 | 无 |
| `mite:iron_war_hammer` | 993 | WAR_HAMMER | 16000 | 6 | 6.0 | 3 | 15 |
| `mite:ancient_metal_war_hammer` | 1243 | WAR_HAMMER | 32000 | 6 | 6.0 | 3 | 35 |
| `mite:mithril_war_hammer` | 994 | WAR_HAMMER | 128000 | 7.5 | 7.0 | 4 | 20 |
| `mite:adamantium_war_hammer` | 995 | WAR_HAMMER | 512000 | 9 | 8.0 | 5 | 25 |
| `minecraft:shears` | 359 | SHEARS | 6400 | 8 | 4.0 | 3 | 291 |
| `minecraft:wooden_shovel` | 269 | SHOVEL | 200 | 4 | 1.0 | 0 | 362 |
