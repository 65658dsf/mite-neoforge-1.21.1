# 015 物品目录与 R196 映射

开发者：NingMeng123。目录基于用户指定的 [MC百科物品/方块列表](https://www.mcmod.cn/item/list/226-1.html)，版本身份以本地 R196 发布包及 `reference-data/runtime-registry.json` 核对。机器可读完整清单见 [item-catalog-015.json](../spec/item-catalog-015.json)。这里只提取目录名称、分类、链接和必要身份事实，没有复制百科长正文。

## 数量与覆盖

- 百科显示 377：303 个主列表链接，加 74 个合并子项；其中 18 个主链接只是套装或分组父页。
- 真正展开的叶条目为 **359**，不是 377 个待注册物品。18 个父页均已实际展开核对，父子链接保存在 JSON。
- 按已确定范围，排除“已删除/未添加”12 项，保留 **347 个默认候选**；这 347 项全部定位到 R196 实际注册和必要子类型。
- 015 批次之前的状态快照：**307 项待新增、35 项已有原版 ID 待适配、5 项已存在且部分机制已做**。这不是 347 项复刻已完成；仅有注册、名称或材质也不能标为机制完成。
- 015 历史快照中的 5 项部分实现为燧石碎片、筋、燧石短斧，以及沿用原版 ID 的木棒和燧石工作台。原版 ID 共建议复用 37 个，其中后两项包含在部分实现里。
- 全目录 355 项有 R196 注册证据（含 8 项被排除的冲突内容），剩余 4 项仅在废弃分类，未匹配本地注册。主要映射锚点为 294 个物品、53 个方块注册引用；门另关联其放置方块，数量不等于新增 Java 类数。

## 当前维度方块注册核对

021已将两种符文石、地幔和地核共4条目录叶项标为“已添加”。两种符文包含各16个符号；地幔/地核是不可携带方块，没有注册可得物品。制造、门路由和边界行为已接入，用户于2026-10-13确认022修复后的当前交付正常，客户端验收见 [021手测](testing/021-underworld-portals.md) 和 [022修复](underworld-depth-022.md)。此确认不代表后续矿物/生物阶段已完成。

## 当前矿石注册核对

023已接入本目录列出的铜矿石、下界金矿石，并新增银矿石、秘银矿石、艾德曼矿石共5条目录叶项。铜、铁、金及煤、青金石、红石、钻石、绿宝石、下界石英继续沿用原版 ID，普通金属矿按 R196 掉矿石本体，新增银/秘银/艾德曼各有一个深板岩变体。另新增 R196 下界虫石对应的 `mite:infested_netherrack`，使用现代银鱼作为暂时的生物适配；这些额外世界方块不伪造百科叶页，不改变359项目录总数。

“已添加”表示身份、采集门槛、源掉落及相关资源已接入；用户于2026-10-13确认023当前交付正常并授权提交，生成与客户端验收见 [023手测](testing/023-ores.md)。矿物熔炼确认了1矿块→1锭和200刻加工；完整炉温、燃料与炉种层级仍待后续制造阶段。新增深板岩变体暂用同种资源包矿石贴图，方块身份和底材生成范围分开。机器数据的 `ore_registration_update` 保存本次定向范围，历史 `summary` 保持不变。

## 当前工具注册核对

2026-10-13 按 [MiteTools.define](../src/main/java/io/github/ningmeng123/mite/registry/MiteTools.java) 的 `legacyId → actual modern ID` 逐项核对，已将 **105 个目录工具叶条目**标为“已添加”。实际工具定义共 **107 项**，另外两项是目录未列出的原版铁剪刀 `minecraft:shears`（旧 359）和木锹 `minecraft:wooden_shovel`（旧 269）；不为它们伪造百科叶页或改变原 359 项目录总数。

| 工具族 | 本目录已添加叶条目 |
| --- | ---: |
| 手斧 | 10 |
| 斧 | 10 |
| 镐 | 8 |
| 锹 | 10 |
| 锄 | 8 |
| 鹤嘴锄 | 8 |
| 镰刀 | 8 |
| 剪刀 | 7 |
| 短木棒 | 1 |
| 木棒 | 1 |
| 小刀 | 2 |
| 短剑 | 8 |
| 剑 | 8 |
| 战斧 | 8 |
| 战锤 | 8 |

仅工具定向更新完成时的目录标签为：已添加 105、待新增 214、原版待适配 25、原有部分实现 3，另保留排除冲突 8 与废弃/未匹配 4。当时另有 242 个非工具候选未重新核对；下节继续更新其中的食品条目。这些限定范围的标签数量不代表全项目当前注册缺口，历史 307 项待新增也不是实时统计。JSON `summary` 原样保留历史，工具核对及当时数量仍保存在 `tool_registration_update`。

“已添加”表示实际注册或已沿用原版 ID 接入工具定义，不把注册自动升级为完整机制完成。各条 `mechanism_status` 保留原历史字段，并附实际注册来源与 ID。016 的工具/耐久/工作台和 017 的品质系统已由用户手测确认并提交，依据分别见 [016 验收](testing/016-tools-workbenches-durability.md)（功能提交 `298dc2c`、点击修正 `54a7e30`）和 [017 验收](testing/017-equipment-quality.md)（`99a3a20`）；本次只是目录维护，没有启动游戏或新增实测记录。完整格挡、高级铁砧与锈铁来源等仍按对应系统文档界定。

**小刀仅燧石与黑曜石两种已添加。** 铜、银、金、锈铁、铁、远古金属、秘银、艾德曼八种金属小刀虽然旧 R196 注册过，但按用户已确认的废弃项排除范围，现 `MiteTools` 明确不注册；下文继续标排除，不将“所有工具已加”扩写为这八种也已加入。

## 当前食品与植物注册核对

2026-10-13 按 [MiteFoods](../src/main/java/io/github/ningmeng123/mite/registry/MiteFoods.java)、[MiteFoodPlants](../src/main/java/io/github/ningmeng123/mite/registry/MiteFoodPlants.java) 和 [食品运行数据](../src/main/resources/mite/foods-018.json) 核对，新增标记 **50 个目录叶条目为“已添加”**，并填写实际 ID。原有 105 个工具条目的注册和机制字段保持不动。

| 本次核对范围 | 目录叶条目 | 当前实现 |
| --- | ---: | --- |
| MiteFoods 食品及饮料 | 31 | 新增食品 ID 和来源摄入 profile，包含 S/N 为零的水碗 |
| MiteFoods 材料及空/水容器 | 14 | 面粉、粪便，及六种金属各自的空桶/水桶；不带 FOOD |
| 复用原版食品 | 4 | 生/熟羊肉、红/棕蘑菇，已接入 018 食品数据与食用规则 |
| 蓝莓丛 | 1 | `mite:blueberry_bush` 方块及其 BlockItem |
| 合计 | **50** | 46 个新增 MITE 物品 ID，4 个原版 ID 接入 |

其中食物分类的 **34 个叶条目全部已有实际映射**，另外更新杂项 12、材料 3、资源 1。洋葱 `mite:onion` 同时用于食用和种植，关联已注册作物方块 `mite:onion_crop`（旧 234）；目录没有独立洋葱作物叶页，故只记录关联方块，不改变原目录总数。蓝莓食品与蓝莓丛物品分开注册。

食品运行目录目前有 **80 个摄入 profile：64 个 R196 来源、15 个现代适配、1 个小麦种子用户适配**。其中 35 个对应上述叶条目，另外 45 个为此百科增量目录未单列的原版/现代食品，不伪造百科页加入 359 个叶项。小麦种子仍按用户规则仅补原版饱和度 1，S/N 和 P/F/V 均不补；蛋糕走已放置方块的六片交互，不作为手持 FOOD。完整数据与这些非叶项 ID 保存在 JSON `food_registration_update`。

“已添加”只说明当前注册或原版食品接入。各条历史 `mechanism_status` 不因本次目录维护自动改为完成，新增 `food_runtime_status` 记录 018/019 的代码范围。2026-10-13 用户在020显示修复后确认当前交付正常，记录为本批用户确认，并非把来源旧版或全部分组场景逐条标为实测。食品合成、熔炼、自然取得、容器守恒与农业的最终自动验证及手测结果由对应批次文档记录；目录维护本身没有启动客户端或运行 Gradle；019/020 的自动验证与用户确认见对应批次记录。

工具与食品定向核对后的目录标签为：**已添加 155、待新增 170、原版待适配 19、原有部分实现 3**，另保留排除冲突 8 与废弃/未匹配 4。仍有 **192 个非工具/非食品默认候选未重新核对**，待办标签属于历史记录，不能当作全项目实时未注册数量。金属岩浆桶、石头桶、黏土桶及钓鱼竿不在本次空/水/奶桶范围内，仍保留各自待办标签；12 个废弃/冲突条目继续排除。

## 身份与现代适配边界

尚未更新行的 `proposed_modern_id` 是后续注册建议，采用小写下划线；已添加工具/食品行另有 `actual_modern_id`，并将 `modern_id_is_proposal` 设为 false。食品行原 015 建议保存在 `registration_baseline_015`。已有 Minecraft 1.21.1 等价内容优先复用 ID。复用不表示耐久、营养、制造成本或其他 MITE 行为已经实现。第三方数据包、现代配方与存档兼容仍需各功能单独验证。

需要特别保留的差异：

- 生鱼/熟鱼两个百科页正文指“大鱼”；对应 `yc.fishLargeRaw/fishLargeCooked`，当前实际注册为 `mite:raw_large_fish` / `mite:cooked_large_fish`。015 曾建议的现代鲑鱼映射已被替代；现代鲑鱼保留 018 适配值，与来源大鱼分开。
- 奶油蘑菇汤/奶油蔬菜汤的实际 ID 为 `mite:cream_of_mushroom_soup` / `mite:cream_of_vegetable_soup`，生虫为 `mite:raw_worm`。六种金属桶统一用 `<metal>_bucket`、`<metal>_water_bucket`、`<metal>_milk_bucket`，金属名为 `gold`；旧 `cream_mushroom_soup`、`worm`、`golden_bucket_*` 等候选名称没有注册，不作为别名。
- 四个重名唱片分别为 Descent、Legends、Underworld、Wanderer；由各页身份说明与 `yc.java:463–466` 对齐。未复制音频或歌词，后续声音资源仍需单独处理。
- 燧石、铜、银工具台是旧 ID 58 的不同 metadata；已做燧石台合并四木材外观，高级台建议独立 ID。
- 两种符文石各有 16 个符文子类型；JSON 明列 0–15。仅注册一个默认状态不能算完整变体实现。
- 地幔/地核同属旧 ID 95，metadata 为 0/1，`BlockMantleOrCore.canBeCarried` 为 false；它们是方块内容，不能自动宣称有正常可得物品。
- 花朵身份以 `BlockFlowerMulti` 常量核对；对郁金香和兰花仅只读检查原纹理颜色，建议复用现代橙色郁金香、兰花，不复制旧贴图。
- 砂砾下界变体为旧 ID 13:2；下界金矿为旧 ID 14:2，不能把不同方块的 metadata 含义混用。
- 熔炉明列未点燃/点燃旧方块配对，门列出物品与放置方块 ID。JSON 的 `item_metadata` 不宣称已穷举方块朝向、生长和损伤状态。

## 废弃分类冲突

用户已选择排除废弃内容，因此下面 12 项默认不进入实现清单。8 种金属小刀虽然被百科放在“已删除/未添加”，R196 却真实注册；7 种有实际配方，锈铁没有输出配方。`ItemKnife.java:9` 设置工具创造栏，继承 `yc.java:855–856` 的物品枚举路径。此处是静态注册/调用链证据，没有启动旧客户端观察；保持 `excluded_conflict`，不把百科分类改写为“R196 不存在”。

| 条目 | R196 身份 | 实际配方 order | 状态 |
| --- | --- | --- | --- |
| [铜小刀](https://www.mcmod.cn/item/35725.html) | yc.knifeCopper / 1050 | 404 | 排除：目录/R196冲突 |
| [铁币](https://www.mcmod.cn/item/823724.html) | 未匹配本地注册 | 无已确认配方 | 排除：废弃/未添加 |
| [Mushroom Luminous](https://www.mcmod.cn/item/823813.html) | 未匹配本地注册 | 无已确认配方 | 排除：废弃/未添加 |
| [石短剑](https://www.mcmod.cn/item/823870.html) | 未匹配本地注册 | 无已确认配方 | 排除：废弃/未添加 |
| [银小刀](https://www.mcmod.cn/item/35800.html) | yc.knifeSilver / 1051 | 406 | 排除：目录/R196冲突 |
| [金小刀](https://www.mcmod.cn/item/36118.html) | yc.knifeGold / 1052 | 414 | 排除：目录/R196冲突 |
| [锈铁小刀](https://www.mcmod.cn/item/36346.html) | yc.knifeRustedIron / 1056 | 无已确认配方 | 排除：目录/R196冲突 |
| [铁小刀](https://www.mcmod.cn/item/37528.html) | yc.knifeIron / 1053 | 408 | 排除：目录/R196冲突 |
| [远古金属小刀](https://www.mcmod.cn/item/439704.html) | yc.knifeAncientMetal / 1057 | 416 | 排除：目录/R196冲突 |
| [秘银小刀](https://www.mcmod.cn/item/37936.html) | yc.knifeMithril / 1054 | 410 | 排除：目录/R196冲突 |
| [艾德曼小刀](https://www.mcmod.cn/item/37948.html) | yc.knifeAdamantium / 1055 | 412 | 排除：目录/R196冲突 |
| [柠檬](https://www.mcmod.cn/item/97550.html) | 未匹配本地注册 | 无已确认配方 | 排除：废弃/未添加 |

## 015 历史计划范围

015 初始计划先处理 5 种粒、4 种锭、5 种碎片，共 14 个新材料物品，另加下界砂砾方块/物品。它们只占完整候选的一部分。计划范围保存在 JSON 的 `planned_batch_015`；该计划记录保留为历史，不替代后续批次的实际注册与验收；本目录后续定向维护已核对上列工具与食品状态。铜锭、铁粒等已有原版 ID 不重复新增。

## 完整叶条目清单

下面每一行对应一个百科叶条目；方括号内 metadata 为旧物品子类型，`0–15` 表示全部十六个符文。分类取自目录；“已添加”工具/食品行给出已核对的实际 ID，其余行的 ID 仍为历史建议，均不是新旧存档迁移承诺。

| 分类 | 百科条目 | R196 符号 / ID:meta | 现代 ID（已添加为实际，其余为建议） | 状态 |
| --- | --- | --- | --- | --- |
| 食物 | [沙拉](https://www.mcmod.cn/item/29626.html) | yc.bowlSalad / 1209:[0] | mite:salad | 已添加 |
| 食物 | [蓝莓](https://www.mcmod.cn/item/35534.html) | yc.blueberries / 1225:[0] | mite:blueberries | 已添加 |
| 食物 | [蓝莓粥](https://www.mcmod.cn/item/35536.html) | yc.bowlPorridge / 1226:[0] | mite:porridge | 已添加 |
| 食物 | [牛奶碗](https://www.mcmod.cn/item/35537.html) | yc.bowlMilk / 1166:[0] | mite:milk_bowl | 已添加 |
| 食物 | [麦片粥](https://www.mcmod.cn/item/35538.html) | yc.bowlCereal / 1227:[0] | mite:cereal | 已添加 |
| 食物 | [巧克力](https://www.mcmod.cn/item/35542.html) | yc.chocolate / 1186:[0] | mite:chocolate | 已添加 |
| 食物 | [南瓜汤](https://www.mcmod.cn/item/35544.html) | yc.bowlPumpkinSoup / 1217:[0] | mite:pumpkin_soup | 已添加 |
| 食物 | [奶油蘑菇汤](https://www.mcmod.cn/item/35545.html) | yc.bowlCreamOfMushroomSoup / 1215:[0] | mite:cream_of_mushroom_soup | 已添加 |
| 食物 | [洋葱](https://www.mcmod.cn/item/35546.html) | yc.onion / 1187:[0] | mite:onion | 已添加 |
| 食物 | [蔬菜汤](https://www.mcmod.cn/item/35547.html) | yc.bowlVegetableSoup / 1190:[0] | mite:vegetable_soup | 已添加 |
| 食物 | [奶油蔬菜汤](https://www.mcmod.cn/item/35548.html) | yc.bowlCreamOfVegetableSoup / 1216:[0] | mite:cream_of_vegetable_soup | 已添加 |
| 食物 | [鸡汤](https://www.mcmod.cn/item/35549.html) | yc.bowlChickenSoup / 1189:[0] | mite:chicken_soup | 已添加 |
| 食物 | [牛肉羹](https://www.mcmod.cn/item/35553.html) | yc.bowlBeefStew / 1188:[0] | mite:beef_stew | 已添加 |
| 食物 | [橘子](https://www.mcmod.cn/item/35554.html) | yc.orange / 1218:[0] | mite:orange | 已添加 |
| 食物 | [果汁雪糕](https://www.mcmod.cn/item/35555.html) | yc.bowlSorbet / 1224:[0] | mite:sorbet | 已添加 |
| 食物 | [奶酪](https://www.mcmod.cn/item/35556.html) | yc.cheese / 1183:[0] | mite:cheese | 已添加 |
| 食物 | [土豆泥](https://www.mcmod.cn/item/35557.html) | yc.bowlMashedPotato / 1223:[0] | mite:mashed_potato | 已添加 |
| 食物 | [冰淇淋](https://www.mcmod.cn/item/35558.html) | yc.bowlIceCream / 1206:[0] | mite:ice_cream | 已添加 |
| 食物 | [面团](https://www.mcmod.cn/item/35560.html) | yc.dough / 1185:[0] | mite:dough | 已添加 |
| 食物 | [生羊排](https://www.mcmod.cn/item/35561.html) | yc.lambchopRaw / 1172:[0] | minecraft:mutton | 已添加 |
| 食物 | [熟羊排](https://www.mcmod.cn/item/35562.html) | yc.lambchopCooked / 1173:[0] | minecraft:cooked_mutton | 已添加 |
| 食物 | [香蕉](https://www.mcmod.cn/item/35647.html) | yc.banana / 1219:[0] | mite:banana | 已添加 |
| 食物 | [装满牛奶的铜桶](https://www.mcmod.cn/item/35746.html) | yc.bucketCopperMilk / 1160:[0] | mite:copper_milk_bucket | 已添加 |
| 食物 | [装满牛奶的银桶](https://www.mcmod.cn/item/35786.html) | yc.bucketSilverMilk / 1161:[0] | mite:silver_milk_bucket | 已添加 |
| 食物 | [装满牛奶的金桶](https://www.mcmod.cn/item/36107.html) | yc.bucketGoldMilk / 1162:[0] | mite:gold_milk_bucket | 已添加 |
| 食物 | [装满牛奶的远古金属桶](https://www.mcmod.cn/item/105518.html) | yc.bucketAncientMetalMilk / 1165:[0] | mite:ancient_metal_milk_bucket | 已添加 |
| 食物 | [装满牛奶的秘银桶](https://www.mcmod.cn/item/37914.html) | yc.bucketMithrilMilk / 1163:[0] | mite:mithril_milk_bucket | 已添加 |
| 食物 | [装满牛奶的艾德曼桶](https://www.mcmod.cn/item/37918.html) | yc.bucketAdamantiumMilk / 1164:[0] | mite:adamantium_milk_bucket | 已添加 |
| 食物 | [生鱼](https://www.mcmod.cn/item/36277.html) | yc.fishLargeRaw / 1229:[0] | mite:raw_large_fish | 已添加 |
| 食物 | [熟鱼](https://www.mcmod.cn/item/36278.html) | yc.fishLargeCooked / 1230:[0] | mite:cooked_large_fish | 已添加 |
| 食物 | [虫子](https://www.mcmod.cn/item/105538.html) | yc.wormRaw / 1280:[0] | mite:raw_worm | 已添加 |
| 食物 | [熟虫子](https://www.mcmod.cn/item/105548.html) | yc.wormCooked / 1281:[0] | mite:cooked_worm | 已添加 |
| 食物 | [红蘑菇](https://www.mcmod.cn/item/289335.html) | aqz.al / 40:[0] | minecraft:red_mushroom | 已添加 |
| 食物 | [棕褐色蘑菇](https://www.mcmod.cn/item/289336.html) | aqz.ak / 39:[0] | minecraft:brown_mushroom | 已添加 |
| 红石 | [铜门](https://www.mcmod.cn/item/35750.html) | yc.doorCopper / 1111:[0] | minecraft:copper_door | 原版已有，待适配 |
| 红石 | [银门](https://www.mcmod.cn/item/35780.html) | yc.doorSilver / 1112:[0] | mite:silver_door | 待新增 |
| 红石 | [金门](https://www.mcmod.cn/item/35781.html) | yc.doorGold / 1113:[0] | mite:golden_door | 待新增 |
| 红石 | [远古金属门](https://www.mcmod.cn/item/105514.html) | yc.doorAncientMetal / 1260:[0] | mite:ancient_metal_door | 待新增 |
| 红石 | [秘银门](https://www.mcmod.cn/item/36576.html) | yc.doorMithril / 1114:[0] | mite:mithril_door | 待新增 |
| 红石 | [艾德曼门](https://www.mcmod.cn/item/36577.html) | yc.doorAdamantium / 1115:[0] | mite:adamantium_door | 待新增 |
| 杂项 | [祛魔之瓶](https://www.mcmod.cn/item/35674.html) | yc.bottleOfDisenchanting / 1214:[0] | mite:bottle_of_disenchanting | 待新增 |
| 杂项 | [音乐唱片](https://www.mcmod.cn/item/824163.html) | yc.recordDescent / 2277:[0] | mite:music_disc_descent | 待新增 |
| 杂项 | [音乐唱片](https://www.mcmod.cn/item/824164.html) | yc.recordLegends / 2279:[0] | mite:music_disc_legends | 待新增 |
| 杂项 | [音乐唱片](https://www.mcmod.cn/item/824165.html) | yc.recordUnderworld / 2276:[0] | mite:music_disc_underworld | 待新增 |
| 杂项 | [音乐唱片](https://www.mcmod.cn/item/824166.html) | yc.recordWanderer / 2278:[0] | mite:music_disc_wanderer | 待新增 |
| 杂项 | [铜桶](https://www.mcmod.cn/item/35747.html) | yc.bucketCopperEmpty / 1142:[0] | mite:copper_bucket | 已添加 |
| 杂项 | [银桶](https://www.mcmod.cn/item/35785.html) | yc.bucketSilverEmpty / 1143:[0] | mite:silver_bucket | 已添加 |
| 杂项 | [金桶](https://www.mcmod.cn/item/36125.html) | yc.bucketGoldEmpty / 1144:[0] | mite:gold_bucket | 已添加 |
| 杂项 | [远古金属桶](https://www.mcmod.cn/item/105069.html) | yc.bucketAncientMetalEmpty / 1147:[0] | mite:ancient_metal_bucket | 已添加 |
| 杂项 | [秘银桶](https://www.mcmod.cn/item/37911.html) | yc.bucketMithrilEmpty / 1145:[0] | mite:mithril_bucket | 已添加 |
| 杂项 | [艾德曼桶](https://www.mcmod.cn/item/37915.html) | yc.bucketAdamantiumEmpty / 1146:[0] | mite:adamantium_bucket | 已添加 |
| 杂项 | [装满水的铜桶](https://www.mcmod.cn/item/35748.html) | yc.bucketCopperWater / 1148:[0] | mite:copper_water_bucket | 已添加 |
| 杂项 | [装满水的银桶](https://www.mcmod.cn/item/35787.html) | yc.bucketSilverWater / 1149:[0] | mite:silver_water_bucket | 已添加 |
| 杂项 | [装满水的金桶](https://www.mcmod.cn/item/36108.html) | yc.bucketGoldWater / 1150:[0] | mite:gold_water_bucket | 已添加 |
| 杂项 | [装满水的远古金属桶](https://www.mcmod.cn/item/105517.html) | yc.bucketAncientMetalWater / 1153:[0] | mite:ancient_metal_water_bucket | 已添加 |
| 杂项 | [装满水的秘银桶](https://www.mcmod.cn/item/37913.html) | yc.bucketMithrilWater / 1151:[0] | mite:mithril_water_bucket | 已添加 |
| 杂项 | [装满水的艾德曼桶](https://www.mcmod.cn/item/37916.html) | yc.bucketAdamantiumWater / 1152:[0] | mite:adamantium_water_bucket | 已添加 |
| 杂项 | [装满岩浆的铜桶](https://www.mcmod.cn/item/35749.html) | yc.bucketCopperLava / 1154:[0] | mite:copper_bucket_lava | 待新增 |
| 杂项 | [装满岩浆的银桶](https://www.mcmod.cn/item/35788.html) | yc.bucketSilverLava / 1155:[0] | mite:silver_bucket_lava | 待新增 |
| 杂项 | [装满岩浆的金桶](https://www.mcmod.cn/item/36126.html) | yc.bucketGoldLava / 1156:[0] | mite:golden_bucket_lava | 待新增 |
| 杂项 | [装满岩浆的远古金属桶](https://www.mcmod.cn/item/105070.html) | yc.bucketAncientMetalLava / 1159:[0] | mite:ancient_metal_bucket_lava | 待新增 |
| 杂项 | [装满岩浆的秘银桶](https://www.mcmod.cn/item/37912.html) | yc.bucketMithrilLava / 1157:[0] | mite:mithril_bucket_lava | 待新增 |
| 杂项 | [装满岩浆的艾德曼桶](https://www.mcmod.cn/item/37917.html) | yc.bucketAdamantiumLava / 1158:[0] | mite:adamantium_bucket_lava | 待新增 |
| 杂项 | [装满石头的铜桶](https://www.mcmod.cn/item/39434.html) | yc.bucketCopperStone / 1231:[0] | mite:copper_bucket_stone | 待新增 |
| 杂项 | [装满石头的银桶](https://www.mcmod.cn/item/39435.html) | yc.bucketSilverStone / 1232:[0] | mite:silver_bucket_stone | 待新增 |
| 杂项 | [装满石头的金桶](https://www.mcmod.cn/item/39436.html) | yc.bucketGoldStone / 1233:[0] | mite:golden_bucket_stone | 待新增 |
| 杂项 | [装满石头的铁桶](https://www.mcmod.cn/item/39437.html) | yc.bucketIronStone / 1234:[0] | mite:iron_bucket_stone | 待新增 |
| 杂项 | [装满石头的远古金属桶](https://www.mcmod.cn/item/105516.html) | yc.bucketAncientMetalStone / 1237:[0] | mite:ancient_metal_bucket_stone | 待新增 |
| 杂项 | [装满石头的秘银桶](https://www.mcmod.cn/item/39438.html) | yc.bucketMithrilStone / 1235:[0] | mite:mithril_bucket_stone | 待新增 |
| 杂项 | [装满石头的艾德曼桶](https://www.mcmod.cn/item/39439.html) | yc.bucketAdamantiumStone / 1236:[0] | mite:adamantium_bucket_stone | 待新增 |
| 资源 | [蓝莓丛](https://www.mcmod.cn/item/36572.html) | aqz.bush / 249:[0] | mite:blueberry_bush | 已添加 |
| 资源 | [金矿石](https://www.mcmod.cn/item/823806.html) | aqz.L / 14:[2] | minecraft:nether_gold_ore | 已添加 |
| 资源 | [铜矿石](https://www.mcmod.cn/item/35744.html) | aqz.oreCopper / 200:[0] | minecraft:copper_ore | 已添加 |
| 资源 | [银矿石](https://www.mcmod.cn/item/35784.html) | aqz.oreSilver / 201:[0] | mite:silver_ore | 已添加 |
| 资源 | [秘银矿石](https://www.mcmod.cn/item/36571.html) | aqz.oreMithril / 202:[0] | mite:mithril_ore | 已添加 |
| 资源 | [艾德曼矿石](https://www.mcmod.cn/item/36575.html) | aqz.oreAdamantium / 203:[0] | mite:adamantium_ore | 已添加 |
| 材料 | [粪便](https://www.mcmod.cn/item/29627.html) | yc.manure / 1191:[0] | mite:manure | 已添加 |
| 材料 | [皮革线](https://www.mcmod.cn/item/29678.html) | yc.sinew / 1174:[0] | mite:sinew | 已存在，部分机制已做 |
| 材料 | [水碗](https://www.mcmod.cn/item/35535.html) | yc.bowlWater / 1167:[0] | mite:water_bowl | 已添加 |
| 材料 | [面粉](https://www.mcmod.cn/item/35559.html) | yc.flour / 1184:[0] | mite:flour | 已添加 |
| 材料 | [燧石碎片](https://www.mcmod.cn/item/29621.html) | yc.chipFlint / 1121:[0] | mite:flint_chip | 已存在，部分机制已做 |
| 材料 | [黑曜石碎片](https://www.mcmod.cn/item/35568.html) | yc.shardObsidian / 1122:[0] | mite:obsidian_shard | 待新增 |
| 材料 | [绿宝石碎片](https://www.mcmod.cn/item/35569.html) | yc.shardEmerald / 1117:[0] | mite:emerald_shard | 待新增 |
| 材料 | [钻石碎片](https://www.mcmod.cn/item/35570.html) | yc.shardDiamond / 1118:[0] | mite:diamond_shard | 待新增 |
| 材料 | [下界石英碎片](https://www.mcmod.cn/item/37884.html) | yc.shardNetherQuartz / 1119:[0] | mite:quartz_shard | 待新增 |
| 材料 | [玻璃碎片](https://www.mcmod.cn/item/37886.html) | yc.shardGlass / 1120:[0] | mite:glass_shard | 待新增 |
| 材料 | [铜粒](https://www.mcmod.cn/item/29624.html) | yc.copperNugget / 956:[0] | mite:copper_nugget | 待新增 |
| 材料 | [银粒](https://www.mcmod.cn/item/35566.html) | yc.silverNugget / 957:[0] | mite:silver_nugget | 待新增 |
| 材料 | [铁粒](https://www.mcmod.cn/item/36246.html) | yc.ironNugget / 958:[0] | minecraft:iron_nugget | 原版已有，待适配 |
| 材料 | [远古金属粒](https://www.mcmod.cn/item/105066.html) | yc.ancientMetalNugget / 961:[0] | mite:ancient_metal_nugget | 待新增 |
| 材料 | [秘银粒](https://www.mcmod.cn/item/35571.html) | yc.mithrilNugget / 959:[0] | mite:mithril_nugget | 待新增 |
| 材料 | [艾德曼粒](https://www.mcmod.cn/item/35572.html) | yc.adamantiumNugget / 960:[0] | mite:adamantium_nugget | 待新增 |
| 材料 | [铜锭](https://www.mcmod.cn/item/29662.html) | yc.ingotCopper / 962:[0] | minecraft:copper_ingot | 原版已有，待适配 |
| 材料 | [银锭](https://www.mcmod.cn/item/35783.html) | yc.ingotSilver / 963:[0] | mite:silver_ingot | 待新增 |
| 材料 | [远古金属锭](https://www.mcmod.cn/item/52208.html) | yc.ingotAncientMetal / 966:[0] | mite:ancient_metal_ingot | 待新增 |
| 材料 | [秘银锭](https://www.mcmod.cn/item/36282.html) | yc.ingotMithril / 964:[0] | mite:mithril_ingot | 待新增 |
| 材料 | [艾德曼锭](https://www.mcmod.cn/item/36281.html) | yc.ingotAdamantium / 965:[0] | mite:adamantium_ingot | 待新增 |
| 材料 | [铜锁链](https://www.mcmod.cn/item/35708.html) | yc.chainCopper / 1123:[0] | mite:copper_chain | 待新增 |
| 材料 | [银锁链](https://www.mcmod.cn/item/35782.html) | yc.chainSilver / 1124:[0] | mite:silver_chain | 待新增 |
| 材料 | [金锁链](https://www.mcmod.cn/item/36109.html) | yc.chainGold / 1125:[0] | mite:golden_chain | 待新增 |
| 材料 | [锈铁锁链](https://www.mcmod.cn/item/36248.html) | yc.chainRustedIron / 1129:[0] | mite:rusted_iron_chain | 待新增 |
| 材料 | [铁锁链](https://www.mcmod.cn/item/36247.html) | yc.chainIron / 1126:[0] | minecraft:chain | 原版已有，待适配 |
| 材料 | [远古金属锁链](https://www.mcmod.cn/item/105067.html) | yc.chainAncientMetal / 1261:[0] | mite:ancient_metal_chain | 待新增 |
| 材料 | [秘银锁链](https://www.mcmod.cn/item/36245.html) | yc.chainMithril / 1127:[0] | mite:mithril_chain | 待新增 |
| 材料 | [艾德曼锁链](https://www.mcmod.cn/item/36243.html) | yc.chainAdamantium / 1128:[0] | mite:adamantium_chain | 待新增 |
| 功能性方块 | [圆石熔炉](https://www.mcmod.cn/item/35616.html) | aqz.aG / 61:[0] | minecraft:furnace | 原版已有，待适配 |
| 功能性方块 | [粘土熔炉](https://www.mcmod.cn/item/29625.html) | aqz.furnaceClayIdle / 218:[0] | mite:clay_oven | 待新增 |
| 功能性方块 | [大粘土烤炉](https://www.mcmod.cn/item/33378.html) | aqz.furnaceHardenedClayIdle / 250:[0] | mite:large_clay_oven | 待新增 |
| 功能性方块 | [沙石熔炉](https://www.mcmod.cn/item/35543.html) | aqz.furnaceSandstoneIdle / 220:[0] | mite:sandstone_furnace | 待新增 |
| 功能性方块 | [黑曜石熔炉](https://www.mcmod.cn/item/35621.html) | aqz.furnaceObsidianIdle / 222:[0] | mite:obsidian_furnace | 待新增 |
| 功能性方块 | [地狱岩熔炉](https://www.mcmod.cn/item/35623.html) | aqz.furnaceNetherrackIdle / 224:[0] | mite:netherrack_furnace | 待新增 |
| 功能性方块 | [铁砧](https://www.mcmod.cn/item/826855.html) | aqz.cm / 145:[0] | minecraft:anvil | 原版已有，待适配 |
| 功能性方块 | [秘银符文石](https://www.mcmod.cn/item/823804.html) | aqz.runestoneMithril / 247:[0–15] | mite:mithril_runestone | 已添加 |
| 功能性方块 | [艾德曼符文石](https://www.mcmod.cn/item/823805.html) | aqz.runestoneAdamantium / 198:[0–15] | mite:adamantium_runestone | 已添加 |
| 功能性方块 | [绿宝石附魔台](https://www.mcmod.cn/item/35675.html) | aqz.enchantmentTableEmerald / 245:[0] | mite:emerald_enchanting_table | 待新增 |
| 功能性方块 | [铜块](https://www.mcmod.cn/item/35745.html) | aqz.blockCopper / 204:[0] | minecraft:copper_block | 原版已有，待适配 |
| 功能性方块 | [银块](https://www.mcmod.cn/item/35789.html) | aqz.blockSilver / 205:[0] | mite:silver_block | 待新增 |
| 功能性方块 | [铜砧](https://www.mcmod.cn/item/36171.html) | aqz.anvilCopper / 229:[0] | mite:copper_anvil | 待新增 |
| 功能性方块 | [银砧](https://www.mcmod.cn/item/36172.html) | aqz.anvilSilver / 230:[0] | mite:silver_anvil | 待新增 |
| 功能性方块 | [金砧](https://www.mcmod.cn/item/36173.html) | aqz.anvilGold / 231:[0] | mite:golden_anvil | 待新增 |
| 功能性方块 | [远古金属砧](https://www.mcmod.cn/item/678209.html) | aqz.anvilAncientMetal / 254:[0] | mite:ancient_metal_anvil | 待新增 |
| 功能性方块 | [秘银砧](https://www.mcmod.cn/item/36174.html) | aqz.anvilMithril / 232:[0] | mite:mithril_anvil | 待新增 |
| 功能性方块 | [艾德曼砧](https://www.mcmod.cn/item/37883.html) | aqz.anvilAdamantium / 233:[0] | mite:adamantium_anvil | 待新增 |
| 功能性方块 | [艾德曼块](https://www.mcmod.cn/item/36239.html) | aqz.blockAdamantium / 207:[0] | mite:adamantium_block | 待新增 |
| 功能性方块 | [秘银块](https://www.mcmod.cn/item/37882.html) | aqz.blockMithril / 206:[0] | mite:mithril_block | 待新增 |
| 功能性方块 | [铜箱子](https://www.mcmod.cn/item/37919.html) | aqz.chestCopper / 239:[0] | mite:copper_strongbox | 待新增 |
| 功能性方块 | [银箱子](https://www.mcmod.cn/item/37920.html) | aqz.chestSilver / 240:[0] | mite:silver_strongbox | 待新增 |
| 功能性方块 | [金箱子](https://www.mcmod.cn/item/37921.html) | aqz.chestGold / 241:[0] | mite:golden_strongbox | 待新增 |
| 功能性方块 | [铁箱子](https://www.mcmod.cn/item/37922.html) | aqz.chestIron / 242:[0] | mite:iron_strongbox | 待新增 |
| 功能性方块 | [远古金属箱子](https://www.mcmod.cn/item/105515.html) | aqz.chestAncientMetal / 255:[0] | mite:ancient_metal_strongbox | 待新增 |
| 功能性方块 | [秘银箱子](https://www.mcmod.cn/item/37923.html) | aqz.chestMithril / 243:[0] | mite:mithril_strongbox | 待新增 |
| 功能性方块 | [艾德曼箱子](https://www.mcmod.cn/item/37924.html) | aqz.chestAdamantium / 244:[0] | mite:adamantium_strongbox | 待新增 |
| 功能性方块 | [燧石工具台](https://www.mcmod.cn/item/68910.html) | aqz.aD / 58:[0,1,2,3] | minecraft:crafting_table | 已存在，部分机制已做 |
| 功能性方块 | [铜工具台](https://www.mcmod.cn/item/68911.html) | aqz.aD / 58:[4] | mite:copper_workbench | 待新增 |
| 功能性方块 | [银工具台](https://www.mcmod.cn/item/68912.html) | aqz.aD / 58:[5] | mite:silver_workbench | 待新增 |
| 功能性方块 | [地幔](https://www.mcmod.cn/item/105512.html) | aqz.mantleOrCore / 95:[0] | mite:mantle | 已添加 |
| 硬币 | [铜币](https://www.mcmod.cn/item/35563.html) | yc.coinCopper / 1220:[0] | mite:copper_coin | 待新增 |
| 硬币 | [金币](https://www.mcmod.cn/item/35564.html) | yc.coinGold / 1222:[0] | mite:gold_coin | 待新增 |
| 硬币 | [银币](https://www.mcmod.cn/item/35565.html) | yc.coinSilver / 1221:[0] | mite:silver_coin | 待新增 |
| 硬币 | [远古金属币](https://www.mcmod.cn/item/51526.html) | yc.coinAncientMetal / 1277:[0] | mite:ancient_metal_coin | 待新增 |
| 硬币 | [秘银币](https://www.mcmod.cn/item/35567.html) | yc.coinMithril / 1278:[0] | mite:mithril_coin | 待新增 |
| 硬币 | [艾德曼币](https://www.mcmod.cn/item/51527.html) | yc.coinAdamantium / 1279:[0] | mite:adamantium_coin | 待新增 |
| 手斧 | [燧石手斧](https://www.mcmod.cn/item/29622.html) | yc.hatchetFlint / 978:[0] | mite:flint_hatchet | 已添加 |
| 手斧 | [黑曜石手斧](https://www.mcmod.cn/item/36256.html) | yc.hatchetObsidian / 1204:[0] | mite:obsidian_hatchet | 已添加 |
| 手斧 | [铜手斧](https://www.mcmod.cn/item/35706.html) | yc.hatchetCopper / 1175:[0] | mite:copper_hatchet | 已添加 |
| 手斧 | [银手斧](https://www.mcmod.cn/item/35798.html) | yc.hatchetSilver / 1176:[0] | mite:silver_hatchet | 已添加 |
| 手斧 | [金手斧](https://www.mcmod.cn/item/36123.html) | yc.hatchetGold / 1177:[0] | mite:golden_hatchet | 已添加 |
| 手斧 | [锈铁手斧](https://www.mcmod.cn/item/36347.html) | yc.hatchetRustedIron / 1181:[0] | mite:rusted_iron_hatchet | 已添加 |
| 手斧 | [铁手斧](https://www.mcmod.cn/item/37529.html) | yc.hatchetIron / 1178:[0] | mite:iron_hatchet | 已添加 |
| 手斧 | [远古金属手斧](https://www.mcmod.cn/item/105578.html) | yc.hatchetAncientMetal / 1262:[0] | mite:ancient_metal_hatchet | 已添加 |
| 手斧 | [秘银手斧](https://www.mcmod.cn/item/37937.html) | yc.hatchetMithril / 1179:[0] | mite:mithril_hatchet | 已添加 |
| 手斧 | [艾德曼手斧](https://www.mcmod.cn/item/37949.html) | yc.hatchetAdamantium / 1180:[0] | mite:adamantium_hatchet | 已添加 |
| 斧 | [金斧](https://www.mcmod.cn/item/823874.html) | yc.L / 286:[0] | minecraft:golden_axe | 已添加 |
| 斧 | [铁斧](https://www.mcmod.cn/item/826856.html) | yc.j / 258:[0] | minecraft:iron_axe | 已添加 |
| 斧 | [燧石斧](https://www.mcmod.cn/item/29623.html) | yc.axeFlint / 979:[0] | mite:flint_axe | 已添加 |
| 斧 | [黑曜石斧](https://www.mcmod.cn/item/36257.html) | yc.axeObsidian / 1205:[0] | mite:obsidian_axe | 已添加 |
| 斧 | [铜斧](https://www.mcmod.cn/item/35705.html) | yc.axeCopper / 980:[0] | mite:copper_axe | 已添加 |
| 斧 | [银斧](https://www.mcmod.cn/item/35794.html) | yc.axeSilver / 981:[0] | mite:silver_axe | 已添加 |
| 斧 | [锈铁斧](https://www.mcmod.cn/item/36338.html) | yc.axeRustedIron / 984:[0] | mite:rusted_iron_axe | 已添加 |
| 斧 | [远古金属斧](https://www.mcmod.cn/item/105579.html) | yc.axeAncientMetal / 1241:[0] | mite:ancient_metal_axe | 已添加 |
| 斧 | [秘银斧](https://www.mcmod.cn/item/37926.html) | yc.axeMithril / 982:[0] | mite:mithril_axe | 已添加 |
| 斧 | [艾德曼斧](https://www.mcmod.cn/item/37939.html) | yc.axeAdamantium / 983:[0] | mite:adamantium_axe | 已添加 |
| 镐 | [金镐](https://www.mcmod.cn/item/823872.html) | yc.K / 285:[0] | minecraft:golden_pickaxe | 已添加 |
| 镐 | [铁镐](https://www.mcmod.cn/item/826857.html) | yc.i / 257:[0] | minecraft:iron_pickaxe | 已添加 |
| 镐 | [铜镐](https://www.mcmod.cn/item/29680.html) | yc.pickaxeCopper / 967:[0] | mite:copper_pickaxe | 已添加 |
| 镐 | [银镐](https://www.mcmod.cn/item/35792.html) | yc.pickaxeSilver / 968:[0] | mite:silver_pickaxe | 已添加 |
| 镐 | [锈铁镐](https://www.mcmod.cn/item/36337.html) | yc.pickaxeRustedIron / 971:[0] | mite:rusted_iron_pickaxe | 已添加 |
| 镐 | [远古金属镐](https://www.mcmod.cn/item/105598.html) | yc.pickaxeAncientMetal / 1239:[0] | mite:ancient_metal_pickaxe | 已添加 |
| 镐 | [秘银镐](https://www.mcmod.cn/item/36573.html) | yc.pickaxeMithril / 969:[0] | mite:mithril_pickaxe | 已添加 |
| 镐 | [艾德曼镐](https://www.mcmod.cn/item/36574.html) | yc.pickaxeAdamantium / 970:[0] | mite:adamantium_pickaxe | 已添加 |
| 锹 | [金锹](https://www.mcmod.cn/item/823875.html) | yc.J / 284:[0] | minecraft:golden_shovel | 已添加 |
| 锹 | [铁锹](https://www.mcmod.cn/item/826860.html) | yc.h / 256:[0] | minecraft:iron_shovel | 已添加 |
| 锹 | [燧石锹](https://www.mcmod.cn/item/35663.html) | yc.shovelFlint / 972:[0] | mite:flint_shovel | 已添加 |
| 锹 | [黑曜石锹](https://www.mcmod.cn/item/36255.html) | yc.shovelObsidian / 1202:[0] | mite:obsidian_shovel | 已添加 |
| 锹 | [铜锹](https://www.mcmod.cn/item/35701.html) | yc.shovelCopper / 973:[0] | mite:copper_shovel | 已添加 |
| 锹 | [银锹](https://www.mcmod.cn/item/35790.html) | yc.shovelSilver / 974:[0] | mite:silver_shovel | 已添加 |
| 锹 | [锈铁锹](https://www.mcmod.cn/item/36336.html) | yc.shovelRustedIron / 977:[0] | mite:rusted_iron_shovel | 已添加 |
| 锹 | [远古金属锹](https://www.mcmod.cn/item/105580.html) | yc.shovelAncientMetal / 1240:[0] | mite:ancient_metal_shovel | 已添加 |
| 锹 | [秘银锹](https://www.mcmod.cn/item/37925.html) | yc.shovelMithril / 975:[0] | mite:mithril_shovel | 已添加 |
| 锹 | [艾德曼锹](https://www.mcmod.cn/item/37938.html) | yc.shovelAdamantium / 976:[0] | mite:adamantium_shovel | 已添加 |
| 锄 | [金锄](https://www.mcmod.cn/item/823873.html) | yc.T / 294:[0] | minecraft:golden_hoe | 已添加 |
| 锄 | [铁锄](https://www.mcmod.cn/item/826859.html) | yc.R / 292:[0] | minecraft:iron_hoe | 已添加 |
| 锄 | [铜锄](https://www.mcmod.cn/item/35702.html) | yc.hoeCopper / 985:[0] | mite:copper_hoe | 已添加 |
| 锄 | [银锄](https://www.mcmod.cn/item/35793.html) | yc.hoeSilver / 986:[0] | mite:silver_hoe | 已添加 |
| 锄 | [锈铁锄](https://www.mcmod.cn/item/36339.html) | yc.hoeRustedIron / 989:[0] | mite:rusted_iron_hoe | 已添加 |
| 锄 | [远古金属锄](https://www.mcmod.cn/item/105597.html) | yc.hoeAncientMetal / 1242:[0] | mite:ancient_metal_hoe | 已添加 |
| 锄 | [秘银锄](https://www.mcmod.cn/item/37927.html) | yc.hoeMithril / 987:[0] | mite:mithril_hoe | 已添加 |
| 锄 | [艾德曼锄](https://www.mcmod.cn/item/37940.html) | yc.hoeAdamantium / 988:[0] | mite:adamantium_hoe | 已添加 |
| 鹤嘴锄 | [铜鹤嘴锄](https://www.mcmod.cn/item/35729.html) | yc.mattockCopper / 997:[0] | mite:copper_mattock | 已添加 |
| 鹤嘴锄 | [银鹤嘴锄](https://www.mcmod.cn/item/35796.html) | yc.mattockSilver / 998:[0] | mite:silver_mattock | 已添加 |
| 鹤嘴锄 | [金鹤嘴锄](https://www.mcmod.cn/item/36113.html) | yc.mattockGold / 999:[0] | mite:golden_mattock | 已添加 |
| 鹤嘴锄 | [锈铁鹤嘴锄](https://www.mcmod.cn/item/36341.html) | yc.mattockRustedIron / 1003:[0] | mite:rusted_iron_mattock | 已添加 |
| 鹤嘴锄 | [铁鹤嘴锄](https://www.mcmod.cn/item/37524.html) | yc.mattockIron / 1000:[0] | mite:iron_mattock | 已添加 |
| 鹤嘴锄 | [远古金属鹤嘴锄](https://www.mcmod.cn/item/105600.html) | yc.mattockAncientMetal / 1244:[0] | mite:ancient_metal_mattock | 已添加 |
| 鹤嘴锄 | [秘银鹤嘴锄](https://www.mcmod.cn/item/37929.html) | yc.mattockMithril / 1001:[0] | mite:mithril_mattock | 已添加 |
| 鹤嘴锄 | [艾德曼鹤嘴锄](https://www.mcmod.cn/item/37942.html) | yc.mattockAdamantium / 1002:[0] | mite:adamantium_mattock | 已添加 |
| 镰刀 | [铜镰刀](https://www.mcmod.cn/item/35728.html) | yc.scytheCopper / 1011:[0] | mite:copper_scythe | 已添加 |
| 镰刀 | [银镰刀](https://www.mcmod.cn/item/35802.html) | yc.scytheSilver / 1012:[0] | mite:silver_scythe | 已添加 |
| 镰刀 | [金镰刀](https://www.mcmod.cn/item/36115.html) | yc.scytheGold / 1013:[0] | mite:golden_scythe | 已添加 |
| 镰刀 | [锈铁镰刀](https://www.mcmod.cn/item/36343.html) | yc.scytheRustedIron / 1017:[0] | mite:rusted_iron_scythe | 已添加 |
| 镰刀 | [铁镰刀](https://www.mcmod.cn/item/37526.html) | yc.scytheIron / 1014:[0] | mite:iron_scythe | 已添加 |
| 镰刀 | [远古金属镰刀](https://www.mcmod.cn/item/105599.html) | yc.scytheAncientMetal / 1246:[0] | mite:ancient_metal_scythe | 已添加 |
| 镰刀 | [秘银镰刀](https://www.mcmod.cn/item/37931.html) | yc.scytheMithril / 1015:[0] | mite:mithril_scythe | 已添加 |
| 镰刀 | [艾德曼镰刀](https://www.mcmod.cn/item/37944.html) | yc.scytheAdamantium / 1016:[0] | mite:adamantium_scythe | 已添加 |
| 剪刀 | [铜剪刀](https://www.mcmod.cn/item/35703.html) | yc.shearsCopper / 1018:[0] | mite:copper_shears | 已添加 |
| 剪刀 | [银剪刀](https://www.mcmod.cn/item/35801.html) | yc.shearsSilver / 1019:[0] | mite:silver_shears | 已添加 |
| 剪刀 | [金剪刀](https://www.mcmod.cn/item/36116.html) | yc.shearsGold / 1020:[0] | mite:golden_shears | 已添加 |
| 剪刀 | [锈铁剪刀](https://www.mcmod.cn/item/36348.html) | yc.shearsRustedIron / 1182:[0] | mite:rusted_iron_shears | 已添加 |
| 剪刀 | [远古金属剪刀](https://www.mcmod.cn/item/105581.html) | yc.shearsAncientMetal / 1247:[0] | mite:ancient_metal_shears | 已添加 |
| 剪刀 | [秘银剪刀](https://www.mcmod.cn/item/37932.html) | yc.shearsMithril / 1021:[0] | mite:mithril_shears | 已添加 |
| 剪刀 | [艾德曼剪刀](https://www.mcmod.cn/item/37945.html) | yc.shearsAdamantium / 1022:[0] | mite:adamantium_shears | 已添加 |
| 钓鱼竿 | [钓鱼竿[燧石钓钩]](https://www.mcmod.cn/item/35664.html) | yc.fishingRodFlint / 1207:[0] | mite:fishing_rod_flint_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[黑曜石钓钩]](https://www.mcmod.cn/item/36249.html) | yc.fishingRodObsidian / 1212:[0] | mite:fishing_rod_obsidian_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[铜钓钩]](https://www.mcmod.cn/item/35700.html) | yc.fishingRodCopper / 1192:[0] | mite:fishing_rod_copper_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[银钓钩]](https://www.mcmod.cn/item/35803.html) | yc.fishingRodSilver / 1193:[0] | mite:fishing_rod_silver_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[金钓钩]](https://www.mcmod.cn/item/36124.html) | yc.fishingRodGold / 1194:[0] | mite:fishing_rod_gold_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[铁钓钩]](https://www.mcmod.cn/item/36251.html) | yc.fishingRodIron / 346:[0] | minecraft:fishing_rod | 原版已有，待适配 |
| 钓鱼竿 | [钓鱼竿[远古金属钓钩]](https://www.mcmod.cn/item/105065.html) | yc.fishingRodAncientMetal / 1263:[0] | mite:fishing_rod_ancient_metal_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[秘银钓钩]](https://www.mcmod.cn/item/36250.html) | yc.fishingRodMithril / 1195:[0] | mite:fishing_rod_mithril_hook | 待新增 |
| 钓鱼竿 | [钓鱼竿[艾德曼钓钩]](https://www.mcmod.cn/item/36242.html) | yc.fishingRodAdamantium / 1196:[0] | mite:fishing_rod_adamantium_hook | 待新增 |
| 武器 | [短木棒](https://www.mcmod.cn/item/35666.html) | yc.cudgelWood / 1024:[0] | mite:wooden_cudgel | 已添加 |
| 武器 | [木棒](https://www.mcmod.cn/item/35667.html) | yc.clubWood / 1025:[0] | minecraft:wooden_sword | 已添加 |
| 武器 | [黑曜石小刀](https://www.mcmod.cn/item/36254.html) | yc.knifeObsidian / 1203:[0] | mite:obsidian_knife | 已添加 |
| 武器 | [燧石小刀](https://www.mcmod.cn/item/35665.html) | yc.knifeFlint / 1023:[0] | mite:flint_knife | 已添加 |
| 武器 | [秘银弓](https://www.mcmod.cn/item/37935.html) | yc.bowMithril / 1048:[0] | mite:mithril_bow | 待新增 |
| 武器 | [远古金属弓](https://www.mcmod.cn/item/105519.html) | yc.bowAncientMetal / 1251:[0] | mite:ancient_metal_bow | 待新增 |
| 武器 | [褐色粘液球](https://www.mcmod.cn/item/52140.html) | yc.aO / 341:[1] | mite:ochre_jelly | 待新增 |
| 武器 | [红色史莱姆球](https://www.mcmod.cn/item/52141.html) | yc.aO / 341:[2] | mite:crimson_blob | 待新增 |
| 武器 | [黑色史莱姆球](https://www.mcmod.cn/item/52142.html) | yc.aO / 341:[4] | mite:black_pudding | 待新增 |
| 武器 | [灰色史莱姆球](https://www.mcmod.cn/item/110416.html) | yc.aO / 341:[3] | mite:grey_gelatinous_sphere | 待新增 |
| 短剑 | [铜短剑](https://www.mcmod.cn/item/35726.html) | yc.daggerCopper / 1033:[0] | mite:copper_dagger | 已添加 |
| 短剑 | [银短剑](https://www.mcmod.cn/item/35799.html) | yc.daggerSilver / 1034:[0] | mite:silver_dagger | 已添加 |
| 短剑 | [金短剑](https://www.mcmod.cn/item/36117.html) | yc.daggerGold / 1035:[0] | mite:golden_dagger | 已添加 |
| 短剑 | [锈铁短剑](https://www.mcmod.cn/item/36345.html) | yc.daggerRustedIron / 1039:[0] | mite:rusted_iron_dagger | 已添加 |
| 短剑 | [铁短剑](https://www.mcmod.cn/item/37527.html) | yc.daggerIron / 1036:[0] | mite:iron_dagger | 已添加 |
| 短剑 | [远古金属短剑](https://www.mcmod.cn/item/439705.html) | yc.daggerAncientMetal / 1249:[0] | mite:ancient_metal_dagger | 已添加 |
| 短剑 | [秘银短剑](https://www.mcmod.cn/item/37934.html) | yc.daggerMithril / 1037:[0] | mite:mithril_dagger | 已添加 |
| 短剑 | [艾德曼短剑](https://www.mcmod.cn/item/37947.html) | yc.daggerAdamantium / 1038:[0] | mite:adamantium_dagger | 已添加 |
| 剑 | [铜剑](https://www.mcmod.cn/item/35704.html) | yc.swordCopper / 1028:[0] | mite:copper_sword | 已添加 |
| 剑 | [金剑](https://www.mcmod.cn/item/823871.html) | yc.I / 283:[0] | minecraft:golden_sword | 已添加 |
| 剑 | [铁剑](https://www.mcmod.cn/item/826858.html) | yc.s / 267:[0] | minecraft:iron_sword | 已添加 |
| 剑 | [银剑](https://www.mcmod.cn/item/35791.html) | yc.swordSilver / 1029:[0] | mite:silver_sword | 已添加 |
| 剑 | [锈铁剑](https://www.mcmod.cn/item/36344.html) | yc.swordRustedIron / 1032:[0] | mite:rusted_iron_sword | 已添加 |
| 剑 | [远古金属剑](https://www.mcmod.cn/item/52207.html) | yc.swordAncientMetal / 1248:[0] | mite:ancient_metal_sword | 已添加 |
| 剑 | [秘银剑](https://www.mcmod.cn/item/37933.html) | yc.swordMithril / 1030:[0] | mite:mithril_sword | 已添加 |
| 剑 | [艾德曼剑](https://www.mcmod.cn/item/37946.html) | yc.swordAdamantium / 1031:[0] | mite:adamantium_sword | 已添加 |
| 战斧 | [铜战斧](https://www.mcmod.cn/item/35720.html) | yc.battleAxeCopper / 1004:[0] | mite:copper_battle_axe | 已添加 |
| 战斧 | [银战斧](https://www.mcmod.cn/item/35797.html) | yc.battleAxeSilver / 1005:[0] | mite:silver_battle_axe | 已添加 |
| 战斧 | [金战斧](https://www.mcmod.cn/item/36114.html) | yc.battleAxeGold / 1006:[0] | mite:golden_battle_axe | 已添加 |
| 战斧 | [锈铁战斧](https://www.mcmod.cn/item/36342.html) | yc.battleAxeRustedIron / 1010:[0] | mite:rusted_iron_battle_axe | 已添加 |
| 战斧 | [铁战斧](https://www.mcmod.cn/item/37525.html) | yc.battleAxeIron / 1007:[0] | mite:iron_battle_axe | 已添加 |
| 战斧 | [远古金属战斧](https://www.mcmod.cn/item/53112.html) | yc.battleAxeAncientMetal / 1245:[0] | mite:ancient_metal_battle_axe | 已添加 |
| 战斧 | [秘银战斧](https://www.mcmod.cn/item/37930.html) | yc.battleAxeMithril / 1008:[0] | mite:mithril_battle_axe | 已添加 |
| 战斧 | [艾德曼战斧](https://www.mcmod.cn/item/37943.html) | yc.battleAxeAdamantium / 1009:[0] | mite:adamantium_battle_axe | 已添加 |
| 战锤 | [铜战锤](https://www.mcmod.cn/item/35727.html) | yc.warHammerCopper / 990:[0] | mite:copper_war_hammer | 已添加 |
| 战锤 | [银战锤](https://www.mcmod.cn/item/35795.html) | yc.warHammerSilver / 991:[0] | mite:silver_war_hammer | 已添加 |
| 战锤 | [金战锤](https://www.mcmod.cn/item/36112.html) | yc.warHammerGold / 992:[0] | mite:golden_war_hammer | 已添加 |
| 战锤 | [锈铁战锤](https://www.mcmod.cn/item/36340.html) | yc.warHammerRustedIron / 996:[0] | mite:rusted_iron_war_hammer | 已添加 |
| 战锤 | [铁战锤](https://www.mcmod.cn/item/37523.html) | yc.warHammerIron / 993:[0] | mite:iron_war_hammer | 已添加 |
| 战锤 | [远古金属战锤](https://www.mcmod.cn/item/53113.html) | yc.warHammerAncientMetal / 1243:[0] | mite:ancient_metal_war_hammer | 已添加 |
| 战锤 | [秘银战锤](https://www.mcmod.cn/item/37928.html) | yc.warHammerMithril / 994:[0] | mite:mithril_war_hammer | 已添加 |
| 战锤 | [艾德曼战锤](https://www.mcmod.cn/item/37941.html) | yc.warHammerAdamantium / 995:[0] | mite:adamantium_war_hammer | 已添加 |
| 箭 | [燧石箭](https://www.mcmod.cn/item/35676.html) | yc.arrowFlint / 1040:[0] | minecraft:arrow | 原版已有，待适配 |
| 箭 | [黑曜石箭](https://www.mcmod.cn/item/36258.html) | yc.arrowObsidian / 1049:[0] | mite:obsidian_arrow | 待新增 |
| 箭 | [铜箭](https://www.mcmod.cn/item/35699.html) | yc.arrowCopper / 1041:[0] | mite:copper_arrow | 待新增 |
| 箭 | [银箭](https://www.mcmod.cn/item/35804.html) | yc.arrowSilver / 1042:[0] | mite:silver_arrow | 待新增 |
| 箭 | [金箭](https://www.mcmod.cn/item/36111.html) | yc.arrowGold / 1043:[0] | mite:golden_arrow | 待新增 |
| 箭 | [锈铁箭](https://www.mcmod.cn/item/36585.html) | yc.arrowRustedIron / 1047:[0] | mite:rusted_iron_arrow | 待新增 |
| 箭 | [铁箭](https://www.mcmod.cn/item/37607.html) | yc.arrowIron / 1044:[0] | mite:iron_arrow | 待新增 |
| 箭 | [远古金属箭](https://www.mcmod.cn/item/61594.html) | yc.arrowAncientMetal / 1250:[0] | mite:ancient_metal_arrow | 待新增 |
| 箭 | [秘银箭](https://www.mcmod.cn/item/37909.html) | yc.arrowMithril / 1045:[0] | mite:mithril_arrow | 待新增 |
| 箭 | [艾德曼箭](https://www.mcmod.cn/item/37910.html) | yc.arrowAdamantium / 1046:[0] | mite:adamantium_arrow | 待新增 |
| 装备 | [铜马铠](https://www.mcmod.cn/item/30998.html) | yc.horseArmorCopper / 1130:[0] | mite:copper_horse_armor | 待新增 |
| 装备 | [银马铠](https://www.mcmod.cn/item/31000.html) | yc.horseArmorSilver / 1131:[0] | mite:silver_horse_armor | 待新增 |
| 装备 | [远古金属马铠](https://www.mcmod.cn/item/105511.html) | yc.horseArmorAncientMetal / 1134:[0] | mite:ancient_metal_horse_armor | 待新增 |
| 装备 | [秘银马铠](https://www.mcmod.cn/item/30999.html) | yc.horseArmorMithril / 1132:[0] | mite:mithril_horse_armor | 待新增 |
| 装备 | [艾德曼马铠](https://www.mcmod.cn/item/31001.html) | yc.horseArmorAdamantium / 1133:[0] | mite:adamantium_horse_armor | 待新增 |
| 装备 | [铜头盔](https://www.mcmod.cn/item/35715.html) | yc.helmetCopper / 1067:[0] | mite:copper_helmet | 待新增 |
| 装备 | [铜胸甲](https://www.mcmod.cn/item/35710.html) | yc.plateCopper / 1078:[0] | mite:copper_chestplate | 待新增 |
| 装备 | [铜护腿](https://www.mcmod.cn/item/35709.html) | yc.legsCopper / 1089:[0] | mite:copper_leggings | 待新增 |
| 装备 | [铜靴子](https://www.mcmod.cn/item/35707.html) | yc.bootsCopper / 1100:[0] | mite:copper_boots | 待新增 |
| 装备 | [铜锁链头盔](https://www.mcmod.cn/item/35712.html) | yc.helmetChainCopper / 1071:[0] | mite:copper_chain_helmet | 待新增 |
| 装备 | [铜锁链胸甲](https://www.mcmod.cn/item/35714.html) | yc.plateChainCopper / 1082:[0] | mite:copper_chain_chestplate | 待新增 |
| 装备 | [铜锁链护腿](https://www.mcmod.cn/item/35713.html) | yc.legsChainCopper / 1093:[0] | mite:copper_chain_leggings | 待新增 |
| 装备 | [铜锁链靴子](https://www.mcmod.cn/item/35711.html) | yc.bootsChainCopper / 1104:[0] | mite:copper_chain_boots | 待新增 |
| 装备 | [银头盔](https://www.mcmod.cn/item/31002.html) | yc.helmetSilver / 1068:[0] | mite:silver_helmet | 待新增 |
| 装备 | [银胸甲](https://www.mcmod.cn/item/31003.html) | yc.plateSilver / 1079:[0] | mite:silver_chestplate | 待新增 |
| 装备 | [银护腿](https://www.mcmod.cn/item/31004.html) | yc.legsSilver / 1090:[0] | mite:silver_leggings | 待新增 |
| 装备 | [银靴子](https://www.mcmod.cn/item/31005.html) | yc.bootsSilver / 1101:[0] | mite:silver_boots | 待新增 |
| 装备 | [银锁链头盔](https://www.mcmod.cn/item/35813.html) | yc.helmetChainSilver / 1072:[0] | mite:silver_chain_helmet | 待新增 |
| 装备 | [银锁链胸甲](https://www.mcmod.cn/item/35809.html) | yc.plateChainSilver / 1083:[0] | mite:silver_chain_chestplate | 待新增 |
| 装备 | [银锁链护腿](https://www.mcmod.cn/item/35810.html) | yc.legsChainSilver / 1094:[0] | mite:silver_chain_leggings | 待新增 |
| 装备 | [银锁链靴子](https://www.mcmod.cn/item/35814.html) | yc.bootsChainSilver / 1105:[0] | mite:silver_chain_boots | 待新增 |
| 装备 | [金头盔](https://www.mcmod.cn/item/823814.html) | yc.an / 314:[0] | minecraft:golden_helmet | 原版已有，待适配 |
| 装备 | [金胸甲](https://www.mcmod.cn/item/823815.html) | yc.ao / 315:[0] | minecraft:golden_chestplate | 原版已有，待适配 |
| 装备 | [金护腿](https://www.mcmod.cn/item/823816.html) | yc.ap / 316:[0] | minecraft:golden_leggings | 原版已有，待适配 |
| 装备 | [金靴子](https://www.mcmod.cn/item/823817.html) | yc.aq / 317:[0] | minecraft:golden_boots | 原版已有，待适配 |
| 装备 | [金锁链头盔](https://www.mcmod.cn/item/36119.html) | yc.helmetChainGold / 1073:[0] | mite:golden_chain_helmet | 待新增 |
| 装备 | [金锁链胸甲](https://www.mcmod.cn/item/36120.html) | yc.plateChainGold / 1084:[0] | mite:golden_chain_chestplate | 待新增 |
| 装备 | [金锁链护腿](https://www.mcmod.cn/item/36121.html) | yc.legsChainGold / 1095:[0] | mite:golden_chain_leggings | 待新增 |
| 装备 | [金锁链靴子](https://www.mcmod.cn/item/36122.html) | yc.bootsChainGold / 1106:[0] | mite:golden_chain_boots | 待新增 |
| 装备 | [锈铁头盔](https://www.mcmod.cn/item/37908.html) | yc.helmetRustedIron / 1076:[0] | mite:rusted_iron_helmet | 待新增 |
| 装备 | [锈铁胸甲](https://www.mcmod.cn/item/37906.html) | yc.plateRustedIron / 1087:[0] | mite:rusted_iron_chestplate | 待新增 |
| 装备 | [锈铁护腿](https://www.mcmod.cn/item/37904.html) | yc.legsRustedIron / 1098:[0] | mite:rusted_iron_leggings | 待新增 |
| 装备 | [锈铁靴子](https://www.mcmod.cn/item/37902.html) | yc.bootsRustedIron / 1109:[0] | mite:rusted_iron_boots | 待新增 |
| 装备 | [锈铁锁链头盔](https://www.mcmod.cn/item/37907.html) | yc.helmetChainRustedIron / 1077:[0] | mite:rusted_iron_chain_helmet | 待新增 |
| 装备 | [锈铁锁链胸甲](https://www.mcmod.cn/item/37905.html) | yc.plateChainRustedIron / 1088:[0] | mite:rusted_iron_chain_chestplate | 待新增 |
| 装备 | [锈铁锁链护腿](https://www.mcmod.cn/item/37903.html) | yc.legsChainRustedIron / 1099:[0] | mite:rusted_iron_chain_leggings | 待新增 |
| 装备 | [锈铁锁链靴子](https://www.mcmod.cn/item/36586.html) | yc.bootsChainRustedIron / 1110:[0] | mite:rusted_iron_chain_boots | 待新增 |
| 装备 | [远古金属头盔](https://www.mcmod.cn/item/439682.html) | yc.helmetAncientMetal / 1252:[0] | mite:ancient_metal_helmet | 待新增 |
| 装备 | [远古金属胸甲](https://www.mcmod.cn/item/439683.html) | yc.plateAncientMetal / 1254:[0] | mite:ancient_metal_chestplate | 待新增 |
| 装备 | [远古金属护腿](https://www.mcmod.cn/item/439684.html) | yc.legsAncientMetal / 1256:[0] | mite:ancient_metal_leggings | 待新增 |
| 装备 | [远古金属靴子](https://www.mcmod.cn/item/439685.html) | yc.bootsAncientMetal / 1258:[0] | mite:ancient_metal_boots | 待新增 |
| 装备 | [远古金属锁链头盔](https://www.mcmod.cn/item/61595.html) | yc.helmetChainAncientMetal / 1253:[0] | mite:ancient_metal_chainmail_helmet | 待新增 |
| 装备 | [远古金属锁链胸甲](https://www.mcmod.cn/item/61596.html) | yc.plateChainAncientMetal / 1255:[0] | mite:ancient_metal_chainmail_chestplate | 待新增 |
| 装备 | [远古金属锁链护腿](https://www.mcmod.cn/item/61597.html) | yc.legsChainAncientMetal / 1257:[0] | mite:ancient_metal_chainmail_leggings | 待新增 |
| 装备 | [远古金属锁链靴子](https://www.mcmod.cn/item/105068.html) | yc.bootsChainAncientMetal / 1259:[0] | mite:ancient_metal_chainmail_boots | 待新增 |
| 装备 | [秘银头盔](https://www.mcmod.cn/item/37954.html) | yc.helmetMithril / 1069:[0] | mite:mithril_helmet | 待新增 |
| 装备 | [秘银胸甲](https://www.mcmod.cn/item/37957.html) | yc.plateMithril / 1080:[0] | mite:mithril_chestplate | 待新增 |
| 装备 | [秘银护腿](https://www.mcmod.cn/item/37955.html) | yc.legsMithril / 1091:[0] | mite:mithril_leggings | 待新增 |
| 装备 | [秘银靴子](https://www.mcmod.cn/item/37956.html) | yc.bootsMithril / 1102:[0] | mite:mithril_boots | 待新增 |
| 装备 | [秘银锁链头盔](https://www.mcmod.cn/item/37959.html) | yc.helmetChainMithril / 1074:[0] | mite:mithril_chain_helmet | 待新增 |
| 装备 | [秘银锁链胸甲](https://www.mcmod.cn/item/37961.html) | yc.plateChainMithril / 1085:[0] | mite:mithril_chain_chestplate | 待新增 |
| 装备 | [秘银锁链护腿](https://www.mcmod.cn/item/37960.html) | yc.legsChainMithril / 1096:[0] | mite:mithril_chain_leggings | 待新增 |
| 装备 | [秘银锁链靴子](https://www.mcmod.cn/item/37958.html) | yc.bootsChainMithril / 1107:[0] | mite:mithril_chain_boots | 待新增 |
| 装备 | [艾德曼头盔](https://www.mcmod.cn/item/37950.html) | yc.helmetAdamantium / 1070:[0] | mite:adamantium_helmet | 待新增 |
| 装备 | [艾德曼胸甲](https://www.mcmod.cn/item/37953.html) | yc.plateAdamantium / 1081:[0] | mite:adamantium_chestplate | 待新增 |
| 装备 | [艾德曼护腿](https://www.mcmod.cn/item/37952.html) | yc.legsAdamantium / 1092:[0] | mite:adamantium_leggings | 待新增 |
| 装备 | [艾德曼靴子](https://www.mcmod.cn/item/37951.html) | yc.bootsAdamantium / 1103:[0] | mite:adamantium_boots | 待新增 |
| 装备 | [艾德曼锁链头盔](https://www.mcmod.cn/item/37963.html) | yc.helmetChainAdamantium / 1075:[0] | mite:adamantium_chain_helmet | 待新增 |
| 装备 | [艾德曼锁链胸甲](https://www.mcmod.cn/item/37965.html) | yc.plateChainAdamantium / 1086:[0] | mite:adamantium_chain_chestplate | 待新增 |
| 装备 | [艾德曼锁链护腿](https://www.mcmod.cn/item/37964.html) | yc.legsChainAdamantium / 1097:[0] | mite:adamantium_chain_leggings | 待新增 |
| 装备 | [艾德曼锁链靴子](https://www.mcmod.cn/item/37962.html) | yc.bootsChainAdamantium / 1108:[0] | mite:adamantium_chain_boots | 待新增 |
| 装饰品 | [铜栅栏](https://www.mcmod.cn/item/823809.html) | aqz.fenceCopper / 213:[0] | mite:copper_bars | 待新增 |
| 装饰品 | [金栅栏](https://www.mcmod.cn/item/823808.html) | aqz.fenceGold / 215:[0] | mite:golden_bars | 待新增 |
| 装饰品 | [远古金属栅栏](https://www.mcmod.cn/item/823810.html) | aqz.fenceAncientMetal / 199:[0] | mite:ancient_metal_bars | 待新增 |
| 装饰品 | [秘银栅栏](https://www.mcmod.cn/item/823807.html) | aqz.fenceMithril / 216:[0] | mite:mithril_bars | 待新增 |
| 装饰品 | [艾德曼栅栏](https://www.mcmod.cn/item/823811.html) | aqz.fenceAdamantium / 217:[0] | mite:adamantium_bars | 待新增 |
| 装饰品 | [大丽花](https://www.mcmod.cn/item/35648.html) | aqz.aj / 38:[7] | mite:dahlia | 待新增 |
| 装饰品 | [郁金香](https://www.mcmod.cn/item/35649.html) | aqz.aj / 38:[5] | minecraft:orange_tulip | 原版已有，待适配 |
| 装饰品 | [绒球葱](https://www.mcmod.cn/item/35650.html) | aqz.aj / 38:[2] | minecraft:allium | 原版已有，待适配 |
| 装饰品 | [滨菊](https://www.mcmod.cn/item/35651.html) | aqz.aj / 38:[8] | minecraft:oxeye_daisy | 原版已有，待适配 |
| 装饰品 | [兰花](https://www.mcmod.cn/item/35652.html) | aqz.aj / 38:[1] | minecraft:blue_orchid | 原版已有，待适配 |
| 装饰品 | [凋零灌木](https://www.mcmod.cn/item/37966.html) | aqz.ad / 32:[1] | mite:witherwood_bush | 待新增 |
| 装饰品 | [地狱沙砾](https://www.mcmod.cn/item/37885.html) | aqz.K / 13:[2] | mite:nether_gravel | 待新增 |
| 装饰品 | [远古金属块](https://www.mcmod.cn/item/53114.html) | aqz.blockAncientMetal / 252:[0] | mite:ancient_metal_block | 待新增 |
| 装饰品 | [地核](https://www.mcmod.cn/item/105513.html) | aqz.mantleOrCore / 95:[1] | mite:core | 已添加 |
| 已删除/未添加 | [铜小刀](https://www.mcmod.cn/item/35725.html) | yc.knifeCopper / 1050:[0] | mite:copper_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [铁币](https://www.mcmod.cn/item/823724.html) | 未匹配 | 暂不建议 | 排除：废弃/未添加 |
| 已删除/未添加 | [Mushroom Luminous](https://www.mcmod.cn/item/823813.html) | 未匹配 | 暂不建议 | 排除：废弃/未添加 |
| 已删除/未添加 | [石短剑](https://www.mcmod.cn/item/823870.html) | 未匹配 | 暂不建议 | 排除：废弃/未添加 |
| 已删除/未添加 | [银小刀](https://www.mcmod.cn/item/35800.html) | yc.knifeSilver / 1051:[0] | mite:silver_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [金小刀](https://www.mcmod.cn/item/36118.html) | yc.knifeGold / 1052:[0] | mite:golden_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [锈铁小刀](https://www.mcmod.cn/item/36346.html) | yc.knifeRustedIron / 1056:[0] | mite:rusted_iron_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [铁小刀](https://www.mcmod.cn/item/37528.html) | yc.knifeIron / 1053:[0] | mite:iron_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [远古金属小刀](https://www.mcmod.cn/item/439704.html) | yc.knifeAncientMetal / 1057:[0] | mite:ancient_metal_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [秘银小刀](https://www.mcmod.cn/item/37936.html) | yc.knifeMithril / 1054:[0] | mite:mithril_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [艾德曼小刀](https://www.mcmod.cn/item/37948.html) | yc.knifeAdamantium / 1055:[0] | mite:adamantium_knife | 排除：目录/R196冲突 |
| 已删除/未添加 | [柠檬](https://www.mcmod.cn/item/97550.html) | 未匹配 | 暂不建议 | 排除：废弃/未添加 |

## 校验与限制

已静态校验主列表 303 个 URL、18 个父页的 74 个子项、359 个叶项 URL 唯一性，默认候选映射无空值；已核对所有引用的 R196 注册、源类文件与声明行、现代复用 ID，以及本批 14+1 计划项均在目录中。网页缓存位于 `.reference/mcmod-015/`，JSON 记录缓存 SHA-256；部分非必要叶详情页因连接错误未缓存，其目录身份仍来自已缓存主列表，重名项和所有分组已另行确认。

本目录未构建模组、未启动游戏，也不证明全部配方、机制、获取路线或客户端显示已经通过验证。后续应分别维护注册、配方/可得、行为、联机与实测状态，不能用目录覆盖率代替玩法完成率。
