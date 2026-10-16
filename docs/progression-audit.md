# 生存成长与通关路线审计

作者：NingMeng123

审计日期：2026-10-16

依据：当前生产代码、生成配方、炉温数据与本地 R196 证据。下列行号对应本次审计时的源码。

## 审计结论与边界

已确认一条从出生到艾德曼工具的材料依赖路线，必要配方之间没有发现确定的循环或缺件死锁。村庄是可选探索分支；艾德曼是材料成长目标，**不是进入末地或击杀原版末影龙的硬门槛**。取得末影珍珠与烈焰棒后，可以走原版末影之眼、要塞传送门和末影龙路线，不必先做完艾德曼装备。

这是静态依赖审计，不代表已经在新存档中从空手到杀龙实机全程通关。新增 9 项 GameTest 已通过，最终全套 1450 项 GameTest 与 1421 项 JUnit 均通过；测试中提供材料、人工搭建门框或创建测试出口，也不能代替自然获取、寻找天然要塞与完整龙战。

## 出生到铁器

### 1. 燧石、木材与第一张工作台

1. 空手挖砂砾，取得燧石碎片；背包 2×2 用四碎片合一燧石。掉回的砂砾可以放下再挖。
2. 从树叶、枯灌木等取得木棍；通过蜘蛛取得线，或将皮革拆为四筋腱，作为绑定材料。
3. 背包 2×2 制作燧石手斧：`FS / BS`，F=燧石、S=木棍、B=线或筋腱。用合格手斧采集原木。
4. 背包 2×2 制作燧石工作台：`FB / SL`，L=原木。它提供 3×3 加工网格，之后才能把原木加工成木板。

工作台需要的是原木，未要求先取得木板，因此“木板需要工作台”没有形成循环。燧石手斧耐久有限，路线允许重复制作；不应假定第一把能砍完初期所有木材。

来源：[`MiteRecipeProvider.java:44`](../src/main/java/io/github/ningmeng123/mite/data/MiteRecipeProvider.java#L44) 的工作台及 `:67–89` 的碎片、筋腱、手斧配方；[`CraftingAccessRules.java:11`](../src/main/java/io/github/ningmeng123/mite/crafting/CraftingAccessRules.java#L11) 的木板网格限制；[`NaturalFoodLootModifier.java:60`](../src/main/java/io/github/ningmeng123/mite/loot/NaturalFoodLootModifier.java#L60) 的旧版树叶掉落分支；[`LogHarvestRules.java`](../src/main/java/io/github/ningmeng123/mite/mining/LogHarvestRules.java) 的原木工具校验。

### 2. 铜、银与第一把镐

初期不应先尝试挖铜矿：石头、煤矿、铜银铁金矿要求采集等级 2，燧石手斧不能代替金属镐。先从砂砾随机取得金属粒，再升级工具。

无时运时，单次砂砾破坏的有关结果如下；概率分母按实际随机调用链计算，并非每消耗一个全新砂砾物品的概率。

| 结果 | 单次概率 |
| --- | ---: |
| 原砂砾 | 3/4 = 75% |
| 燧石碎片 | 5/32 = 15.625% |
| 完整燧石 | 1/96 ≈ 1.041667% |
| 铜粒 | 1/18 ≈ 5.555556% |
| 银粒 | 1/54 ≈ 1.851852% |
| 金粒 | 1/162 ≈ 0.617284% |
| 黑曜石碎片 | 1/486 ≈ 0.205761% |
| 秘银粒 | 1/13122 ≈ 0.007621% |
| 艾德曼粒 | 1/26244 ≈ 0.003810% |

表中省略绿宝石、钻石碎片结果，因此列出的概率不合计为 100%。高阶粒的极低概率不是常规成长路线的必要前提。

九粒在 3×3 合一锭。以纯铜路线为例，积攒 **36 铜粒→4 铜锭**：一锭加皮革、木棍、木板制作铜工作台，另三锭加两木棍制作铜镐。铜、银、金台的制造容量同为 4，可以混合利用已经获得的这些材料，不必每种台都制作。

来源：[`FlintGravelRules.java:19`](../src/main/java/io/github/ningmeng123/mite/rules/FlintGravelRules.java#L19)；[`MaterialRecipes.java:49`](../src/main/java/io/github/ningmeng123/mite/data/MaterialRecipes.java#L49)；[`WorkbenchRecipes.java:37`](../src/main/java/io/github/ningmeng123/mite/data/WorkbenchRecipes.java#L37)；[`ToolRecipes.java:61`](../src/main/java/io/github/ningmeng123/mite/data/ToolRecipes.java#L61)；[`WorkbenchRules.java:19`](../src/main/java/io/github/ningmeng123/mite/crafting/WorkbenchRules.java#L19)；[`OreBlockRules.java:16`](../src/main/java/io/github/ningmeng123/mite/rules/OreBlockRules.java#L16)。

### 3. 普通熔炉、铁台与铁镐

铜或银镐的采集等级为 2，能够采集石头、煤、铁矿及更多铜银矿。用八圆石制作普通熔炉，以煤烧炼铁矿。铁工作台用一铁锭，铁镐用三铁锭；台的其他材料仍是皮革、木棍、木板。铜级工具快损坏时，可以先采更多铜银矿熔炼备用工具，避免将所有剩余耐久用于无目的掘进。

**煤是 2 热，木炭只有 1 热。** 木炭不能替代煤烧炼这些金属矿。普通熔炉最高 2 热，足够铜、银、铁、金。铁镐采集等级 3，解锁黑曜石与秘银矿。

来源：[`MiteTools.java:60`](../src/main/java/io/github/ningmeng123/mite/registry/MiteTools.java#L60)；[`FurnaceRecipes.java:20`](../src/main/java/io/github/ningmeng123/mite/data/FurnaceRecipes.java#L20)；[`furnaces-024.json:112`](../src/main/resources/mite/furnaces-024.json#L112) 的木炭与 `:142` 的煤，`:2379` 的石炉档位，`:2415` 起的材料所需热级。

## 铁器到地底、秘银与艾德曼

### 4. 点火、黑曜石与地底入口

打火石由一铁粒与一燧石斜放合成；虽然形状只占 2×2，制造校验仍要求铁工作台。铁锭可以拆为九铁粒。熔炉在满足输入和燃料条件时自动点燃，当前路线不依赖火柴。

铁镐可以采集天然或浇铸的黑曜石，无需先做钻石镐。可从遗迹、天然黑曜石或水与熔岩取得；碎片压制也是概率较低的备用途径。若用桶取得熔岩，铁桶每次装填有 8% 熔毁率，铜银桶为 16%，需要准备补充桶，但这不是无法取得材料的死锁。

普通门的内部必须为 2×3，十块黑曜石构成必要边框，四角可省略。主世界门框底部须在该维度最低高度之上的八格范围内，并使边框下方至少一处接触基岩，才通往地底世界。主世界最小 Y 为 −64，例如基岩在 −64 时，可令其上方门框底边位于 −63，内部最低格位于 −62；无需破坏基岩。地表普通门不直接通往下界。

来源：[`WorldRecipes.java:16`](../src/main/java/io/github/ningmeng123/mite/data/WorldRecipes.java#L16)；[`WorkbenchRules.java:56`](../src/main/java/io/github/ningmeng123/mite/crafting/WorkbenchRules.java#L56)；[`FurnaceThermalController.java:115`](../src/main/java/io/github/ningmeng123/mite/furnace/FurnaceThermalController.java#L115)；[`SourceMiningRequirements.java:30`](../src/main/java/io/github/ningmeng123/mite/mining/SourceMiningRequirements.java#L30)；[`LavaBucketRules.java:7`](../src/main/java/io/github/ningmeng123/mite/rules/LavaBucketRules.java#L7)；[`MitePortalFrame.java:40`](../src/main/java/io/github/ningmeng123/mite/portal/MitePortalFrame.java#L40)、`:74–80`；[`R196PortalRules.java:15`](../src/main/java/io/github/ningmeng123/mite/rules/R196PortalRules.java#L15)。

### 5. 地底远古金属与秘银加工

远古金属不是需要补找的矿石。可行供应包括：

- 正常玩家击杀持弓的远古骷髅，其无抢夺箭矢掉落分支为 `nextInt(2)`，抽到正数后再以 `nextInt(3)==0` 保留一箭，即每只约 **1/6** 获得一支远古金属箭；它可直接拆成一远古金属粒。巨量跌落击杀有额外抑制，不适用这一概率。
- 地底试炼地牢宝库有远古金属粒奖励，普通独特奖励子表权重为 4/12、数量 2–5；凶兆独特奖励子表权重为 100/300、数量 4–9。这是**选到该子表后的条件概率**，不是每次开宝库直接获得远古金属的总概率。
- 地底普通地牢战利品中的远古金属硬币，可以一币拆一粒；远古金属链也有一链拆四粒配方，若从其他有效来源取得链可利用。

积攒九粒成一锭，在铁台制作远古金属工作台。**不需要先制作远古金属镐或整套装备。** 铁镐已经能采秘银矿，主世界候选高度为 Y−64 至 0，地底也有分布。

用八黑曜石制作黑曜石熔炉，使用熔岩桶提供 3 热烧秘银。秘银台需要远古金属台的制造容量 16；在秘银台制造秘银镐。台一锭、镐三锭，至少四秘银锭即可完成这一跳。秘银用熔岩加工，**不需要先取得下界烈焰棒**。

来源：[`R196Skeleton.java:322`](../src/main/java/io/github/ningmeng123/mite/entity/R196Skeleton.java#L322)；[`ancient_metal_arrow_dismantle.json`](../src/generated/resources/data/mite/recipe/ancient_metal_arrow_dismantle.json)；[`reward_unique.json`](../src/generated/resources/data/mite/loot_table/chests/underworld_trial_chambers/reward_unique.json)、[`reward_ominous_unique.json`](../src/generated/resources/data/mite/loot_table/chests/underworld_trial_chambers/reward_ominous_unique.json)；[`chest-content-028.json:14`](../src/main/resources/mite/chest-content-028.json#L14)；[`utility_028_881.json`](../src/generated/resources/data/mite/recipe/utility_028_881.json)；[`WorkbenchRules.java:63`](../src/main/java/io/github/ningmeng123/mite/crafting/WorkbenchRules.java#L63)；[`R196OreRules.java:86`](../src/main/java/io/github/ningmeng123/mite/rules/R196OreRules.java#L86)、`:183`；[`furnaces-024.json:427`](../src/main/resources/mite/furnaces-024.json#L427)、`:2384`、`:2474`。

### 6. 下界与艾德曼

在地底底部以同样门框规则建门，边框下面接触地幔，可前往下界。该入口本身不要求秘银或艾德曼符文；符文属于另一种传送功能。下界返回普通路由指向地底。

铁镐已能采下界地狱岩。八地狱岩制作地狱岩熔炉，最大 4 热；烈焰棒提供 4 热。秘银镐采集等级 4，可采地底艾德曼矿；其当前初始候选高度为 Y−64 至 139，实际矿脉还会连通扩展。用地狱岩熔炉加烈焰棒烧炼艾德曼，在秘银台制造艾德曼台，再用艾德曼台制造艾德曼镐；台一锭、镐三锭。

下界探索可在铁器阶段先进行，不必等秘银；上文按材料升级顺序排列，不表示额外加入维度锁。烈焰棒既可以用于这条最高材料路线，也可直接加工为烈焰粉走末地路线。

来源：[`MitePortalFrame.java:74`](../src/main/java/io/github/ningmeng123/mite/portal/MitePortalFrame.java#L74)；[`R196PortalRules.java:15`](../src/main/java/io/github/ningmeng123/mite/rules/R196PortalRules.java#L15)；[`SourceMiningRequirements.java:39`](../src/main/java/io/github/ningmeng123/mite/mining/SourceMiningRequirements.java#L39)；[`FurnaceRecipes.java:23`](../src/main/java/io/github/ningmeng123/mite/data/FurnaceRecipes.java#L23)；[`furnaces-024.json:47`](../src/main/resources/mite/furnaces-024.json#L47)、`:2389`、`:2472`；[`MiteTools.java:65`](../src/main/java/io/github/ningmeng123/mite/registry/MiteTools.java#L65)；[`ToolRecipes.java:71`](../src/main/java/io/github/ningmeng123/mite/data/ToolRecipes.java#L71)；[`R196OreRules.java:114`](../src/main/java/io/github/ningmeng123/mite/rules/R196OreRules.java#L114)、`:183`。

## 炉温路线速查

| 设备 | 上限 | 这条路线中的用途与燃料 |
| --- | ---: | --- |
| 小黏土炉 | 1 热 | 小型食物、黏土球等；不能塞原木、沙、黏土块等大物品 |
| 大黏土炉、砂岩炉 | 1 热 | 原木烧木炭、黏土块烧陶瓦等大物品加工 |
| 普通熔炉 | 2 热 | 煤烧铜、银、铁、金；木炭不能替代这一热级 |
| 黑曜石熔炉 | 3 热 | 熔岩桶烧秘银，无需下界燃料 |
| 地狱岩熔炉 | 4 热 | 烈焰棒烧艾德曼 |

小黏土炉不是必须经过的金属进度环节；可直接在铜镐阶段取得圆石制造普通熔炉。若走完整黏土炉升级支线，砂岩能由四沙直接合成，砂岩炉能以 1 热烧黏土块取得陶瓦，再制作大黏土炉，没有要求“小黏土炉必须先烧出其不能放入的黏土块”。熔炉正面被实心方块或水堵住会影响运行，应保留炉口空间。

来源：[`FurnaceRecipes.java:17`](../src/main/java/io/github/ningmeng123/mite/data/FurnaceRecipes.java#L17)；[`furnaces-024.json:2364`](../src/main/resources/mite/furnaces-024.json#L2364)；[`FurnaceThermalController.java:56`](../src/main/java/io/github/ningmeng123/mite/furnace/FurnaceThermalController.java#L56)、`:89`。

## 可选村庄与末地通关

### 村庄资格

主世界村庄要求两项同时满足：显示日期达到第 60 天，以及世界中有玩家**实际完成制造**一件能采黑曜石的合格工具。制作铁镐可以满足；仅 `/give` 一把铁镐、背包持有、查看预览或机器自动合成不会替代实际玩家制造。世界条件共享保存，并能从旧玩家的真实制作统计补回。

日期使用 `floor((max(dayTime,0)+6000)/24000)+1` 的等价防溢出实现，因此显示第 60 天从 `/time set 1410000` 开始。村庄解锁后在之后生成的新区块寻找；未解锁时已生成的旧区块不会凭空补出村庄。村庄不是采矿、下界或末地的必经条件。

来源：[`R196StructureGeneration.java:59`](../src/main/java/io/github/ningmeng123/mite/worldgen/R196StructureGeneration.java#L59)、`:100–109`、`:138`；[`StructureProgressData.java:19`](../src/main/java/io/github/ningmeng123/mite/worldgen/StructureProgressData.java#L19)；[`StructureProgressGateMixin.java:36`](../src/main/java/io/github/ningmeng123/mite/mixin/StructureProgressGateMixin.java#L36)。

### 末地路线与保留行为

1. 自然取得末影珍珠和下界烈焰棒。一烈焰棒在背包 2×2 合两烈焰粉；一末影珍珠加一烈焰粉合一末影之眼，不要求金属工作台。
2. 寻找天然要塞末地传送门房间。村庄套用日期与工具锁；2026-10-16 新增废弃传送门第 30 天与地底访问锁、前哨站第 45 天与弓及秘银装备制作锁，详见 [结构生成规则](structure-generation.md)。**要塞及末地房间没有日期、村庄、艾德曼装备或成就门槛**；仍需实际找得到、能到达房间。
3. 原版十二个向内门框全部有眼后，原版形状检测生成中间九格末地传送门；天然门框已有的眼可以保留，寻找时消耗的眼另行准备。
4. 原版进入末地、龙战、首杀出口与龙蛋、经出口返回的流程保留；未发现以秘银、艾德曼或水晶成就阻断原版龙战的附加校验。现有龙经验 Mixin 修改的是死亡经验数量，不替换这些龙战与出口行为。

来源：原版 1.21.1 `blaze_powder`、`ender_eye` 配方与 `EnderEyeItem#useOn`、`EndPortalFrameBlock`、`EndDragonFight`；本项目门槛见 [`R196StructureGeneration.java:68`](../src/main/java/io/github/ningmeng123/mite/worldgen/R196StructureGeneration.java#L68)，龙改动见 [`R196VanillaDragonExperienceMixin.java:11`](../src/main/java/io/github/ningmeng123/mite/mixin/R196VanillaDragonExperienceMixin.java#L11)。新增实际菜单、十二门框与传送路径用例见 [`ProgressionRouteGameTests.java:45`](../src/gametest/java/io/github/ningmeng123/mite/gametest/ProgressionRouteGameTests.java#L45)、`:87`、`:129`；本次三项实际路径测试均已通过；原版龙战与首杀出口保留是源码审计结论，并未在测试中执行完整杀龙。

**非阻塞适配差异：**烈焰粉、末影之眼目前走现代原版瞬时合成，尚未完整迁移 R196 对应制造耗时。这两种配方能完成，不是缺件或无法通关，但不能据此宣称制造机制已经逐项完全还原。基础矿物粒和远古金属来源存在随机性；没有发现所需物品概率恒为零的必经环节。

## 本轮自动验证范围

新增九项 GameTest：三项走实际背包合成、末地门激活、原生末地往返；六项检查聊天包、日期和实际制作门槛、关闭结构生成时静默、去重、真实玩家文件重登、死亡重生与跨维度。末地往返使用人工测试出口，不包含自然寻找要塞或末影龙首杀。

首次全套运行中，新九项均通过，唯一失败为旧工具挖矿用例即时查实体索引数不到掉落；失败世界的实体区域文件中实际保存着一枚圆石。已将该用例改为捕获未被取消的实际实体加入事件，仍严格验证一圆石与精确耐久，没有修改掉落生产逻辑。最终 `gradlew build runGameTestServer` 成功，1450 项 GameTest 与 1421 项 JUnit 全部通过（失败、错误、跳过均为 0）。

## 解锁聊天提示手测

提示说明真实结构资格，不会额外锁住要塞。开启结构生成的普通测试存档中，末地要塞资格从开局存在；村庄、废弃传送门及前哨站在各自全部条件满足后提示。当前通过服务端检查发送，按**每名玩家的保存数据**去重，而不是每次登录或每天重复播报。青色为末地要塞提示，绿色为村庄提示，金色为废弃传送门提示，红色为前哨站提示。

1. 新建开启结构生成的测试存档。首次登录应收到末地要塞资格提示；普通开局不应收到村庄提示。退出重进，已发送的提示不应重复。
2. 在尚未实际做过合格工具的该新存档执行 `/gamerule doDaylightCycle false`、`/time set 1410000`。仅到第 60 天不应提示村庄。可用 `/give @s minecraft:iron_pickaxe` 验证单纯持有不解锁。
3. 测试材料可用 `/give @s mite:iron_crafting_table`、`/give @s minecraft:iron_ingot 3`、`/give @s minecraft:stick 2` 准备；打开铁工作台，**实际完成**铁镐配方。在服务端下一次检查（约一秒）后应收到一次绿色村庄提示。也可先在 `/time set 1409999` 完成配方，确认仍无提示，再将时间设为 `1410000`，验证日期边界。
4. 保存退出、重新登录，再死亡并正常重生，两个已通知提示都不应重复。若换一名没有通知记录的玩家进入这个已解锁的世界，应各收到一次当前可用资格提示。完成后按需要恢复 `/gamerule doDaylightCycle true`。
5. 废弃传送门需要第 30 天（`/time set 690000`）和地底访问记录，前哨站需要第 45 天（`/time set 1050000`）且实际完成弓与秘银装备制作；任一条件缺失时不应提示。满足后约一秒内分别收到金色或红色聊天消息。保存重进、死亡和跨维度后不重复，已有村庄/末地提示的旧玩家仍能各收到一次新提示。

以上命令用于隔离提示条件，不能作为自然获取材料的通关证据。若要补实机结论，应另外在未提供物品的新存档执行材料路线，并实际寻找天然要塞、激活传送门、完成龙战，记录版本、种子和结果。

来源：[`StructureUnlockAnnouncements.java:29`](../src/main/java/io/github/ningmeng123/mite/worldgen/StructureUnlockAnnouncements.java#L29)、`:51–71`、`:74–90`。新增 GameTest 结果：**九项全部通过**；完整新存档实机通关：**未在本次静态审计中执行**。

## 本轮测试包与验证记录

- 测试副本：`build/libs/mite-0.1.0-progression-test.jar`；常规输出：`build/libs/mite-0.1.0.jar`，两者字节一致。
- SHA-256：`b0eccadf19d94b39e1533ece7dcd4e6dfb30f81eab338f3be03cd843fa537260`，均附同名 `.sha256`。
- 构建与服务端日志：`build/verification/progression-final-build-test.log`；包内容及已有工作保护检查：`build/verification/progression-package-audit.json`。
- 已有 44 条路径均保留，仅在两份语言文件追加本功能各两个键，原有提示删除仍保留；测试类、JEI、Jade 未打入模组，必需依赖仍仅为 Minecraft 与 NeoForge。
- 本次未启动、重启或控制用户客户端，也未执行自然资源获取到杀龙的实机全流程。玩家已确认本轮验收并授权提交 GitHub。

手测时只保留一份 MITE 模组 JAR，用此测试副本替换旧版；不要同时把常规输出和测试副本放进同一个 `mods` 目录。
