# 018：已有食品的 R196 数值目录与现代适配

维护者：NingMeng123。原作：Avernite。

## 本批边界

018阶段建立已存在于 Minecraft 1.21.1 的食品摄入目录，配合双饱食/营养状态底座。018目录共 49 个物品 ID：33 个保留 R196 来源数值、16 个适配（15 个现代食品及 1 个用户指定的小麦种子规则）。018未注册奶酪、面团、巧克力、洋葱、水碗或新增汤类，也不替换食品合成、熔炼配方或自然取得链。019扩展后的80项目录和取得加工链见 [019说明](food-content-019.md)。食品取得、加工、食用分属不同入口，目录覆盖不能算食品全类完成。

本文件保留018原食品数值和来源校验值。当前FOOD饱和度元数据及AppleSkin显示按 [020兼容规则](food-saturation-020.md)：普通食品用N绝对值作为预览，不新增真实饱和度储备；种子既有+1例外保留。018历史默认零saturation组件不是020当前行为。

源数值使用本地发布包的 [注册快照](../reference-data/runtime-registry.json)，对应本地压缩包 SHA-256 `fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。完整旧食品、容器与加工证据见 [R196 食品目录证据](r196-food-catalog-evidence.md)。运行时统一读取 [foods-018.json](../src/main/resources/mite/foods-018.json)，不在 Java 另存第二份食品数值表。

## R196 对应食品

S/N 为 satiation / nutrition。P/F/V 为蛋白 / 必需脂肪 / 植物营养素；“满值可用”是来源里的 `alwaysEdible` 标志，仍由服务端摄入资格限制。下表不把 N 当成现代 saturation。表中小麦种子行仅保留历史来源参考，已由下文用户适配覆盖；有效来源分类只有其余 33 个 ID。

| 现代 ID | R196 ID | S/N | P/F/V | 糖 | 堆叠 | 动作 | 满值可用 |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| `minecraft:brown_mushroom` | 39 | 1/1 | — | 0 | 32 | 食用 | 否 |
| `minecraft:red_mushroom` | 40 | 1/1 | — | 0 | 32 | 食用 | 否 |
| `minecraft:apple` | 260 | 2/1 | V | 1000 | 16 | 食用 | 否 |
| `minecraft:mushroom_stew` | 282 | 2/4 | — | 0 | 4 | 食用 | 否 |
| `minecraft:wheat_seeds`（历史原值，当前已覆盖） | 295 | 1/0（历史） | F（历史） | 0 | 64 | 食用 | 否 |
| `minecraft:bread` | 297 | 8/2 | — | 0 | 16 | 食用 | 否 |
| `minecraft:porkchop` | 319 | 4/4 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_porkchop` | 320 | 8/8 | P | 0 | 16 | 食用 | 否 |
| `minecraft:golden_apple` | 322:0 | 2/1 | V | 1000 | 16 | 食用 | 是 |
| `minecraft:milk_bucket` | 335 | 0/4 | P | 0 | 1 | 饮用 | 是 |
| `minecraft:egg` | 344 | 1/3 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cod` | 349 | 3/3 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_cod` | 350 | 6/6 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:sugar` | 353 | 1/0 | — | 1000 | 16 | 食用 | 否 |
| `minecraft:cookie` | 357 | 3/1 | — | 250 | 16 | 食用 | 否 |
| `minecraft:melon_slice` | 360 | 1/1 | V | 1000 | 16 | 食用 | 否 |
| `minecraft:pumpkin_seeds` | 361 | 1/2 | F | 0 | 64 | 食用 | 否 |
| `minecraft:melon_seeds` | 362 | 1/1 | F | 0 | 64 | 食用 | 否 |
| `minecraft:beef` | 363 | 5/5 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_beef` | 364 | 10/10 | P | 0 | 16 | 食用 | 否 |
| `minecraft:chicken` | 365 | 3/3 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_chicken` | 366 | 6/6 | P | 0 | 16 | 食用 | 否 |
| `minecraft:rotten_flesh` | 367 | 2/1 | P | 0 | 16 | 食用 | 否 |
| `minecraft:nether_wart` | 372 | 1/1 | — | 0 | 64 | 食用 | 否 |
| `minecraft:spider_eye` | 375 | 0/1 | P | 0 | 16 | 食用 | 否 |
| `minecraft:carrot` | 391 | 1/2 | V | 0 | 16 | 食用 | 否 |
| `minecraft:potato` | 392 | 3/1 | — | 0 | 16 | 食用 | 否 |
| `minecraft:baked_potato` | 393 | 6/2 | — | 0 | 16 | 食用 | 否 |
| `minecraft:poisonous_potato` | 394 | 2/0 | — | 0 | 16 | 食用 | 否 |
| `minecraft:golden_carrot` | 396 | 1/2 | V | 0 | 16 | 食用 | 否 |
| `minecraft:pumpkin_pie` | 400 | 10/6 | PV | 1000 | 8 | 食用 | 否 |
| `minecraft:mutton` | 1172 | 3/3 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_mutton` | 1173 | 6/6 | P | 0 | 16 | 食用 | 否 |
| `minecraft:enchanted_golden_apple` | 322:1 | 2/1 | V | 1000 | 16 | 食用 | 是 |

生/熟小鱼明确映射 `cod` / `cooked_cod`，是现代命名适配。大鱼保留后续独立食品范围，不把鲑鱼宣称为 R196 大鱼。历史 R196 小麦种子为 S1/N0、F=true，另补必需脂肪 2000；这些值保存在 JSON 的 `explicit_original_source_values` 中，当前用户适配不执行该脂肪特例。

蛋糕不在手持目录。`CAKE_SLICE` 为每片 2/2、蛋白、糖 166（整数 `1000/6`）；手持蛋糕仅改堆叠 8，不添加 FOOD。原作分片与完整蛋糕掉落属于方块交互，由独立入口处理。空木碗堆叠 16，蘑菇煲堆叠 4。

## 小麦种子用户适配与原版饱和度

用户明确要求小麦种子只补原版 `saturation`，不补饥饿条 S、营养条 N 或微量营养。有效配置是：

| 物品 | 有效 S/N | 原版饱和度增量 | P/F/V | 糖 | 来源分类 |
| --- | --- | ---: | --- | ---: | --- |
| `minecraft:wheat_seeds` | 0/0 | 1 | 均不补 | 0 | `user_adaptation` |

`native_saturation` 是小麦种子用户例外的绝对缓冲增量，不是饥饿值乘倍率，因此 FOOD.nutrition=0 仍可携带 saturation=1。020中只有该种子把此值实际加入原生缓冲；其余目录食品的FOOD.saturation改为食品N的绝对预览值，但真实摄入仍只加既有N一次，不额外加原生缓冲。省略 `native_saturation` 仍表示没有独立缓冲覆盖，不再意味着FOOD.saturation显示为0。018阶段 `sourceProfiles()` 有33项、`modernProfiles()`有16项；019后来源项64、适配项仍16，后者包含种子，不能宣称80项均是未改动的R196原值。

种子原生缓冲上限为当前S。S=4、N=3、该缓冲=0时吃一份小麦种子，得到S4/N3/缓冲1；P/F/V不增加。S=0时种子缓冲上限也是0，吃种子无法补出缓冲，仍不增加S/N。其他食品不会无故清空已有种子缓冲，但当S下降时仍按新上限钳位。020的AppleSkin显示取N＋此缓冲，按等级上限显示；N本身不受S限制，奶S0/N4也能正确展示。显示值不是第三个可再次消耗的储备。

正常代谢累积exhaustion。exhaustion≥4时每tick只处理一次；若存在种子例外缓冲，先扣1缓冲，耗尽后按R196双资源原逻辑扣S或N。普通食品的FOOD.saturation=N只是020预览元数据，不进入该缓冲消费阶段。种子例外不启用现代快速回血：N=0的慢饥饿伤害、以N为依据的恢复仍独立生效。

`/mite food status @s` 同时显示S、N、原生缓冲、P/F/V与糖抵抗；020将原生字段名称改为“种子饱和缓冲”，它不是兼容叠层显示的N。管理员可用 `/mite food set saturation @s <0..20浮点值>` 设置缓冲，实际结果按当前S钳位。

原版生命包只在生命、饱食或饱和度零/非零状态改变时发送，不能保证连续种子的 `1→2→3` 饱和度及时显示。本模组营养同步包单独携带饱和度 float，变化即同步、客户端写入原生 FoodData，设置命令也强制同步；面板仍不显示这个字段。同步协议版本为 2，存档版本不变。

## 现代食品适配

以下数值为显式现代适配，不能称 R196 原有数据。多数保留 1.21.1 食品的原 nutrition，作为 S/N 两个资源；鲑鱼与小鱼映射故意各自单列。食物分类决定微量营养素，不能从原 saturation 倍率猜测。蜂蜜、浆果、紫颂果糖含量是本模组明确选定的适配；蜂蜜保留满值饮用/解毒、紫颂果保留满值食用/传送、谜之炖菜保留其储存在物品组件的药效。

| 现代 ID | S/N | P/F/V | 糖 | 堆叠 | 动作 | 满值可用 |
| --- | --- | --- | ---: | ---: | --- | --- |
| `minecraft:rabbit` | 3/3 | P | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_rabbit` | 5/5 | P | 0 | 16 | 食用 | 否 |
| `minecraft:rabbit_stew` | 10/10 | PV | 0 | 4 | 食用 | 否 |
| `minecraft:salmon` | 2/2 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:cooked_salmon` | 6/6 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:tropical_fish` | 1/1 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:pufferfish` | 1/1 | PF | 0 | 16 | 食用 | 否 |
| `minecraft:beetroot` | 1/1 | V | 0 | 16 | 食用 | 否 |
| `minecraft:beetroot_soup` | 6/6 | V | 0 | 4 | 饮用 | 否 |
| `minecraft:sweet_berries` | 2/2 | V | 1000 | 16 | 食用 | 否 |
| `minecraft:glow_berries` | 2/2 | V | 1000 | 16 | 食用 | 否 |
| `minecraft:dried_kelp` | 1/1 | V | 0 | 16 | 食用 | 否 |
| `minecraft:honey_bottle` | 6/6 | — | 2000 | 16 | 饮用 | 是 |
| `minecraft:chorus_fruit` | 4/4 | V | 1000 | 16 | 食用 | 是 |
| `minecraft:suspicious_stew` | 2/4 | — | 0 | 4 | 食用 | 是 |

海带的现代快速食用改为本批目录统一 32 tick。现代甜菜汤归饮料，但原版 `Item` 动画需在共用入口按 profile 匹配，组件本身不决定动画。河豚保留 1.21.1 的中毒 II 1200 tick、饥饿 III 300 tick、恶心 I 300 tick；这不是替换 R196 毒效的数据。

其它模组/自定义数据组件食品使用明确兼容规则：含 FOOD 时，原 FOOD.nutrition 分别补 S 和 N；P/F/V=false、糖=0，drink 根据物品 UseAnim.DRINK，alwaysEdible 按该 FOOD.canAlwaysEat。现代 saturation 不转换为 N，也不参与微量营养；小麦种子的显式原版饱和度缓冲按上一节运行。包含 FOOD 的自定义饮料同样进入双状态，不允许通过饮料入口绕过营养。没有 FOOD 的普通药水/水瓶不返回食品 profile。

`Items.OMINOUS_BOTTLE` 在 1.21.1 带 FOOD(1) 元数据，但 `OminousBottleItem.finishUsingItem` 不调用 `Player.eat`，不会真实补食物。本批移除其无效 FOOD，保留原灾厄饮用与效果，避免将药剂误识别为 1/1 食品。

## 默认组件与副作用

默认FOOD组件的nutrition使用S，时长1.6秒（32tick）；020起普通目录食品的saturation使用N绝对值作为兼容预览，小麦种子仍显式为1。真实食用走FoodProfile，只加S/N一次，除种子例外外不把该元数据加到原生缓冲。canAlwaysEat统一为true，用于让原版交互入口进入服务端双资源检查，**不代表所有食品允许满值食用**；真正的满值标志保留在FoodProfile.alwaysEdible，服务端同时检查S/N、微量营养、恶心和呼吸条件。其它模组食品的组件保持原样，profile明确兼容。

注册组件完整替换 food.effects，避免旧效果叠加：

| 食品 | 本批 food.effects |
| --- | --- |
| `minecraft:red_mushroom` | poison 1，200 tick，100%；nausea 5，1200 tick，100% |
| `minecraft:golden_apple` | regeneration 1，1200 tick，100% |
| `minecraft:chicken` | poison 1，400 tick，30% |
| `minecraft:rotten_flesh` | poison 1，400 tick，80% |
| `minecraft:spider_eye` | poison 1，100 tick，100% |
| `minecraft:poisonous_potato` | poison 1，100 tick，60% |
| `minecraft:enchanted_golden_apple` | regeneration 2，1200 tick，100%；resistance 1，1200 tick，100%；fire_resistance 1，1200 tick，100% |
| `minecraft:pufferfish` | poison 2，1200 tick，100%；hunger 3，300 tick，100%；nausea 1，300 tick，100% |

R196 `ni.u` 是 poison；生鸡肉和腐肉不保留现代 hunger。普通金苹果只有再生 I 1200 tick，附魔金苹果只有再生 II / 抗性 I / 抗火 I 各 1200 tick，均不添加现代伤害吸收。金苹果治疗僵尸村民的专用行为是独立入口，本批目录不改现代转换时间。

## 必须由共用入口处理的特殊 Item

- `MilkBucketItem.finishUsingItem` 不走 Player.eat，需要独立补 S/N；奶桶保留原生退桶，不能为了补营养再调用 Player.eat 造成双余物。本批 FOOD.usingConvertsTo=BUCKET 只是统一预览元数据，真实容器路径仍仅执行一次。
- `EggItem.use` 无视 FOOD，原生直接投掷；新增食用行为必须通过独立 use 拦截并明确客户端 modifier 同步。发射器的蛋投掷仍是 ProjectileItem 的独立入口。
- `HoneyBottleItem.use` 直接开始饮用，`getUseDuration` 固定 40；需要共用资格和时长拦截。其完成调用 super.eat 后原生解毒/退瓶，不能重复退瓶。
- 原版 `SuspiciousStewItem.finishUsingItem` 先施自定义药效再调用 super.eat。本批改为先验证、完成原生摄入/扣物品/退碗，再在服务端施已捕获的自定义效果；自带恶心不应令同一份食物变成免费药效，已有恶心则在下一份开始前拒绝。
- `ChorusFruitItem.finishUsingItem` 调用 super.eat 后仍执行传送；同样需完成前资格验证。
- 种子、蘑菇、下界疣等 BlockItem 的 useOn 优先放置/种植，空中 use 才食用；本批 FOOD 不改这条原版放置优先路径。

## 验证入口

[FoodCatalogGameTests](../src/gametest/java/io/github/ningmeng123/mite/gametest/FoodCatalogGameTests.java)在018阶段提供6组游戏内测试，覆盖目录数量、R196独立数值、FOOD组件、32tick、当时除用户种子之外零saturation、原副作用不重复、金苹果去除现代吸收、可食用材料、源堆叠区别、奶/碗/蛋糕区别、现代/用户适配与外部FOOD fallback。020将组件saturation断言更新为N兼容预览及种子1例外；旧零值断言不代表当前规则。概率和时长依据独立源固定值，不能读取实现数据再计算期望代替验收；当前020验证结果另见其记录，不改018历史通过数量/哈希。

背包配方书按钮右侧的饱和效果图标打开只读营养面板，只显示 N、P、F、V、糖抵抗五行及精确数值/整数百分比。Esc、背包键（默认 E）或“返回”恢复原背包与拖拽物品；品质条移至背包相对坐标 (136,49)，窄屏展开配方书时隐藏相关按钮，JEI 共存仍需人工核对。

这些测试不等于客户端图标/提示和完整食用流程的人工验收。保存、联机、重生、状态加减和重复完成防护由双食物状态及摄入测试负责；容器满背包、Ctrl 蛋投掷、种植优先和现代特殊完成行为需要人工测试或对应集成案例。具体本轮构建与验收结果以项目 README / 018 测试文档为准。
