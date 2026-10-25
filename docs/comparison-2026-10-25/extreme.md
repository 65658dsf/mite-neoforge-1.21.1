# Extreme-lh 0.6.6 全机制审计与当前实现差异

作者：NingMeng123。日期：2026-10-25。仅静态审计，不启动附件代码、不连接附件内远程服务、不更改游戏实现。

## 证据范围与可信度

- 原附件 SHA-256：`594c152bb7cfe655f9b0744aea2ed45d71696779cd842c71be2501189c540d98`；844 个 ZIP 条目，391 个 class；374 个顶层 Java 反编译文件。
- 用现有 CFR 0.152 对原 JAR 重新完整反编译到 `extreme-fresh/`。剥离 CFR 文件头的缺依赖说明后，374/374 与 `.reference/extreme/decompiled/` 内容完全一致；不是以旧目录名字猜测版本。
- JAR `fml.mod.json`：MC `1.6.4-mite`，FishModLoader `>=3.4.1`，ITE `>=2.0.25`，Rusted Iron Core `>=1.5.0`。Extreme 是 ITE 附属，不能把 R196、ITE、Extreme 当作三套完全独立同时生效的覆盖规则。
- `extreme-lh.mixins.json` 注册 135 项；`extreme.itf.mixins.json` 注册 59 项，后者由 `compat/EXITFMixinPlugin.java:22` 检查 `mite-itf-reborn` 后才应用。主入口 `ExtremeInit.java:32`；FML/RIC 注册链 `events/EXEventListeners.java:12`、`ExtremeRICEvents.java:25`。
- `mixin/block/BlockFlowerPotMixin.java`、`mixin/render/RenderBlocksMixin.java` 虽存在 class 和源文件，但两个 mixin 清单均未列入，应标为未启用，不能拿来宣称花盆/方块渲染改动生效。
- 全量 374 文件、方法、注入对象及条件表达式记录于 `extreme-source-index.md/json`。这是全文件追溯索引，不等于对每条继承链进行过游戏内验证。常数替换、RIC处理顺序和旧版缺依赖调用有相应边界说明。
- 下文 `E/` = `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/`；`C/` = `D:/Coding/LemonDev/minecraft/MITE Modern ITE/src/main/java/io/github/ningmeng123/modernite/`。行号属于现有已验证反编译文件，而非原始源码的调试行号。

## 世界、矿石、群系与结构

| 机制 | Extreme 0.6.6 实际代码行为 | 证据 |
|---|---|---|
| 全群系绿宝石 | 每次 decorate 做 `6+nextInt(3)` 次单点尝试，X/Z 区块内随机，Y=`4+nextInt(28)` 即4～31，只有目标是石头才放；6～8是尝试数，不保证成功矿数 | `E/mixin/world/BiomeGenBaseMixin.java:121` |
| 地底红钻 | RIC UNDERWORLD 注册矿脉尺寸3，最低130最高225、标准随机高度，频率默认1 | `E/events/listener/EXOreGeneration.java:17` |
| 红钻获取和献祭 | 最低采掘等级4；掉落由 `BlockFancyRed` 判断爆炸/精准/时运；献祭默认500经验 | `E/register/EXBlocksRegistryInit.java:69`、`E/block/BlockFancyRed.java:27`、`E/mixin/item/ItemRockMixin.java:18` |
| 地底艾德曼高度 | `world.isUnderworld && underworld_y_offset!=0` 时起点覆盖130～249。世界字段仍120，因此该条件有效；Extreme把地幔配置75改0仅影响Chunk地幔，不会清零世界字段。父ITE只拒绝低于130，225仅参与尺寸缩放 | `E/mixin/world/WorldGenMinableMixin.java:21`、`E/mixin/compat/ITEConfigMixin.java:36`；旧ITE`ChunkTrans.java:15`、R196`abw.java:176,249` |
| 艾德曼频率 | `genMinable(IL...;Z)` 中匹配常数2改1；下界ITE默认矿物次数3→4、矿脉数2→3。前者最终单位必须结合R196原目标方法解释，不能仅凭方法名写成所有艾德曼翻倍/减半 | `E/mixin/world/BiomeDecoratorMixin.java:13`、`E/mixin/compat/ITEConfigMixin.java:19` |
| 大洞穴 | 洞穴 `recursiveGenerate` 第9个随机整数调用强制返回0，改变大洞触发分支 | `E/mixin/world/MapGenCavesMixin.java:17` |
| 村庄 | 村庄原日期60常数接配置，默认仍60 | `E/mixin/world/MapGenVillageMixin.java:14` |
| 树木 | 基础和森林树种随机加入枫/樱；有大树、小树、树苗两阶段成长、树叶腐烂、玩家放叶标记及掉落 | `E/mixin/world/BiomeGenBaseMixin.java:104`、`BiomeForestMixin.java:24`、`E/block/BlockEXSapling.java:39`、`BlockEXLeaves.java:87` |
| 出生点光源 | 主世界初始化在出生点首个非空气上方放Gotcha方块 | `E/mixin/world/WorldServerMixin.java:41` |
| 下界定位眼 | 只在下界使用，查找最近Fortress并生成相应眼实体，眼落地返还对应物品；通过 `ChunkProviderHell.findClosestStructure` 接入要塞查找 | `E/item/ItemNeatherEnderEye.java:27`、`E/mixin/entity/EntityEnderEyeMixin.java:35`、`E/mixin/world/ChunkProviderHellMixin.java:19` |
| 环境频率 | `tickBlocksAndAmbiance` 第一个随机调用的分母缩小10倍；不是把全部随机刻统一乘10 | `E/mixin/world/WorldServerMixin.java:49` |

当前 ITE 已有红钻、全域绿宝石、可配置下界矿、枫樱木装饰、下界眼等对应内容；其有效值由实际资源JSON、`ExtremeWorldState`、`ConfigurableNetherOre`与主模组矿物调度共同决定。现代负Y/扩展地底高度不是旧Y坐标的等比例数量承诺。最终矿表与打包工厂默认值见主报告及配置附件。

低版矿量按当前生成倍率的候选、有效起点和块预算换算见[矿量换算](ore-equivalence.md)。Extreme地底艾德曼候选为64次，对当前80次是0.8；普通金属还承接父ITE最低140的拒绝，不能把候选比例直接当最终矿量比例。

## 刷怪、天数、维度与数值体系

1. `E/mixin/world/SpawnerAnimalsMixin.java:58` 完整覆写敌对调度。基础上限：`int(int(baseMax*eligibleChunks / ITE.BLOOD_MOON_MAX_HOSTILE_FRACTION) + perDayIncrease*max(day,100))`；血月再×3，deep_only再×2。Y60上下分别计数和限额。注意外层整数截断，且这些参数取ITE实际配置，不全在Extreme文件里。
2. 通常3轮、每轮4次；血月6轮、8次。下界普通3/4轮随机、血月7/8轮随机，并增加1次尝试上限。血月单群上限可翻倍。主世界相位蜘蛛出现特殊64/byte(-128)反编译分支，不能把负byte直接写成128次成功生成。
3. 通常避玩家24格、血月32格；主世界/地底双格全黑时允许更近（普通最小8、血月12格）；出生点仍排24格。白天室外亡灵不准，室外月相跳过机会分别蓝月54、血月1、满月3、新月6、其它4；蓝月夜露天另受天光条件。
4. `E/mixin/entity/EntityMonsterMixin.java:29` 将随机光照阈值抽样范围额外+3。配置文字说亮度>=12阻怪，但实际随机边界与R196原函数有关；不能把它误说成Minecraft1.21的“任意非零方块光”规则。
5. 基础群系池扩充36行、地底池另扩充；转移骷髅在基础池同时添加权重2和5，合计可按7理解，但子群系若清空基础池会改变最终结果。所有自然池条目还需过实体的 `getCanSpawnHere`：例转移>=64、矿工/恶魂尸/精英尸>32、混乱苦力怕>=64、闪电骷髅>=96、女巫领主>=配置默认96、不朽尸皇>=128、亡灵恶魂仅地底>=128且1/4判定。见各实体以及两Biome mixin，完整条目见JSON索引。
6. 全局维度出生强化由 `E/mixin/entity/EntityLivingMixin.java:143` 调 `E/util/EntitySpawnEnhancementUtil.java:20`：敌对EntityMob和恶魂在下界血/攻各×1.5，地底血×1.25攻×1.5；地底敌对持武器/护甲随机0～5锋利/保护，0代表不加，低于现有等级才覆盖。不能把这个数再套所有被动生物。
7. Extreme继承ITE按天数、强化上限和阶梯伤害。它把阶梯伤害默认基值1改0.5、最大4改2、周期24改50（`E/mixin/compat/ITEConfigMixin.java:49`）。当前主模组+ITE采用拆分基础属性、物种挑战曲线、统一日历/成长配置；当前 `C/entity/MobAbilities.java:84` 明确移除很多继承线性day加成。不是“全部旧数值一字不改移植”。
8. 普通僵尸>32天20%幼年，幼年攻击`(8+day/24)/2`，速度0.35；客户端/掉落/日光判断另统一修正。普通骷髅20%骑蝙蝠，非地狱苦力怕20%骑吸血蝙蝠；蝙蝠载客不倒挂，空手右键能骑。猪人自然替换1/5为ITE猪人领主；猪人主世界受血月日限制且愤怒索敌距离×2。
9. 旧普通苦力怕与蜘蛛死亡均3%生成4只后代（`E/mixin/entity/EntityCreeperMixin.java:48`、`EntitySpiderMixin.java:21`）；当前新增明确分裂子代、繁殖递归保护和成长缩放，不是只照搬旧回调。

当前的额外全局开始日期、实体拥挤预算、预约/失败回滚、召唤怪是否豁免、最高经历天数等由主调度+`C/entity/SpawnPopulation.java`、`ExtremeSpawnProgression.java`、`IteReferenceSpawns.java`共同控制。当前打包极难开始日期为3；不是IteConfig代码备用65。旧Extreme没有这一套统一空间预算。

## 玩家生存、时间、死亡与药效

- 玩家等级最大300；血上限先算 `max(min(6+floor(level/5)*2, maxLevelLimit/5),6)`，再加护甲每件同类最高生命宝石值，最后日期封顶：<96天20，96～127天30，128～169天40，>=170解除日期封顶。即默认数值基础封顶60，并非“到300级一定126血”。RIC最终回调再夹一次避免其它监听器超限。`E/events/listener/EXPlayerAttribute.java:13`、`E/mixin/compat/PlayerAttributeHandlerHealthLimitMixin.java:13`。
- 白天延长在ITE开关开启时，前96天每天18点把totalWorldTime回拨该天6点，每天仅一次；默认界限96。此操作会改变日历流逝，不是修改TPS。`E/mixin/compat/TickListenerMixin.java:25`。
- 地底累计54000tick（45分钟）触发缓慢I，停留持续刷新220tick；离开地底累计24000tick（20分钟）后才清空。死亡克隆/NBT保留，未激活前离开也不会自动清累计。`E/mixin/compat/EntityPlayerUnderworldPenaltyMixin.java:38`。
- 复活阵绑定一个玩家一个位置/维度；致死时先检查可用急救附魔，否则复活石救命：20生命、再生1200tick II、抗性600tick II、抗火1200tick、虚弱18000tick II并反复补回、消耗复活阵、经验归零、可跨维度传送。绑定保存到玩家NBT。`E/block/BlockSpawn.java:61`、`E/mixin/entity/EntityPlayerMixin.java:304`。
- **旧每次服务器玩家死亡直接生成拟态僵尸**，80血10攻击，不自然生成，不捡物不掉装备，不自然消失；并没有搬运玩家背包逻辑。当前改10%概率、10基础血、捕获并持久化真实死亡掉落、杀死返还；生成失败保留原掉落，是明确玩法差异。旧`E/mixin/entity/ServerPlayerMixin.java:91`、`E/entity/EntityMimicZombie.java:19`；新`C/entity/ExtremeMobEvents.java:214`、`MobAbilities.java:42`、`PlayerMimicInventory.java`。
- 8个Extreme负面药效：混乱、闪光、遮蔽、倒置、灵敏度、仰头、漂浮、大头。漂浮自实现每tick向上加速并清坠落距离；其它依输入、相机、HUD、模型各侧注入。当前还加入桶头、狂犬病、矮人诅咒等，不是这8种全来自旧Extreme。
- **旧混乱=前后左右各反向**；当前`ExtremeVisuals.java:31`为90°轴旋转并叠镜头摇动，是操作行为差异。旧`E/mixin/MovementInputFromOptionsMixin.java:28`。倒置旧含相机/手部180°翻转；仰头每60tick脉冲、闪光白屏与遮蔽另在HUD；不能把只注册药效视为完成全部客户端行为。
- 大头旧兼有头部模型与蛛网减速0.45×(1-麻痹抗性)，来源为“大头僵尸”15格内视线远程施加100tick；其名称保留钓鱼僵尸但实际不必是钓钩攻击。`E/entity/FishingRodEntity.java:58`、`E/mixin/entity/EntityLivingBaseMixin.java:60`。
- 骑乘/被骑乘忽略雨淋；家畜拥挤常数2→15；牛鸡/末影人等补丁中的空方法与继承要按索引追踪，不能只看名字猜新AI。

## 方块破坏、农业、物理与交互

- 基础挖掘规则大部分由R196/ITE继承。Extreme自己的主要增量是宝石效率、矿工挖掘冷却、连锁矿、扩展木材伐木和特殊区块保护，并非另一整套挖掘公式。
- 连锁附魔每级27预算，在26邻域广度队列找任意BlockOre/红石矿，再逐个调用原tryHarvestBlock；不要求矿种相同。原 `hint.contains(int[])` 是Java数组引用相等，不是坐标值去重，不过成功破坏后方块变空气；不可把它宣称为完善去重算法。`E/mixin/util/ItemInWorldManagerMixin.java:67`。
- 矿工僵尸持高阶镐，`EntityAnimalWatcher.getCooloffForBlock`对它加速；当前`C/mixin/entity/MinerDiggingMixin.java`接主模组`R196DiggerGoal`并按工具/方块判定，而不是复制旧AI对象。
- 成熟作物右键采收、镰/鹤嘴锄/采摘收割附魔的范围与重新种植由 `E/util/CropHarvestingHelper.java`实际处理；各工具Mixin只负责接入。作物生长倍率默认0.5；农作物概率出种植凭证；树叶/草地合法性与附着藤蔓补上扩展树种。
- 沙砾开关默认开，RIC覆盖沙砾0.5、完整燧石0.5、燧石碎片0。无需从“提高概率”注释推定成20%或只改碎片。
- 刷怪笼地底破坏50%触发Boss+女巫，旧钩子是`dropBlockAsEntityItem`返回后，没有只限玩家/无爆炸条件；当前钩子限定玩家非创造成功破坏、延迟确认方块真的被移除、经过全局生成预算。旧`E/mixin/block/BlockMobSpawnerMixin.java:36`；新`C/entity/ExtremeMobEvents.java:227`。
- 私人箱子破坏记录可配置，记录归属/破坏者；活塞运动包围盒修正、防卡方块pushOutOfBlocks改写；这些行为分别有服务端/实体条件，不应概括成“所有方块更难挖”。`E/mixin/block/BlockStrongboxMixin.java:30`、`BlockPistonMovingTrans.java:26`、`E/mixin/entity/EntityMixin.java:75`。
- 蠹虫方块按维度/材质生出地狱虫、铜刺虫、地底白蚁等；主世界另20%地狱虫分支。`E/mixin/block/BlockSilverFishMixin.java:25`。
- 扩展木材/树叶作燃料、腐殖值；16色床的放置、占用、出床、复活点与相机；单/双半砖掉落/挖掘硬度、灯笼碰撞形状和宝石台便携性均在block/item/mixin索引中单独覆盖。

## 装备、宝石、附魔、锻造与制造

- 10类宝石×6阶：攻击、生命、防护、恢复、效率、耐久、速度、抗火、抗爆、营养。每装备8孔，同类只取最高级，四件防具之间可相加；防护每级0.25、恢复0.125，其余类型倍率1。攻击加主手宝石值；效率加效率附魔级；耐久额外`等级×0.05`；鞋子速度额外`等级×0.05`；胸甲抗火、护腿抗爆在原保护分数中加`等级/6`；恢复`base×(1+恢复总值)`，营养上限直接加营养总值。`E/item/GemModifierTypes.java:7`、`E/mixin/item/ItemStackMixin.java:72`、`E/mixin/item/ItemArmorMixin.java:30`、`E/mixin/item/enchantment/EnchantmentProtectionMixin.java:18`、`E/mixin/entity/PlayerCapabilitiesMixin.java:18`、`E/mixin/util/FoodStatsMixin.java:22`。
- 宝石台9槽=装备1+宝石8，关闭时还回装备、临时宝石库存清空；工具/护甲才能入主槽，辅助槽仅宝石，每槽1件，实时写装备NBT；没有自动抽出接口。合成升级2～6阶需对应碎片×2+前阶同类宝石；三颗同样宝石能合宝石盒，使用只随机出一级宝石。`E/inventory/container/{ContainerGemSetting,SlotGem}.java`、`E/tileentity/TileEntityGemSetting.java`、`E/register/EXItemsRegistryInit.java:141`、`E/item/ItemEnhanceGemBox.java:26`。
- 保护典籍配方、与护甲合成/替换冲突防护词条；副属性提升石最多额外3次，消耗放下的提升石；装备`enhance_times`NBT读写兼容。额外上限通过`CompatUtilMixin.getMaxToolLevel`生效：振金在原上限另+3+enhance_times，其他+enhance_times；剑/棍升级经验需求再ceil(原值×1.4)。当前磨具不再提供该+3路线，数值另见当前ITE分册。`E/item/recipe/RecipeProtectionTomeArmor.java:27`、`E/block/BlockEnhanceToolLevelUp.java:51`。
- 新武器经验词条每级1，上限3，击杀额外掉`int(1+词条值)`经验；护甲荆棘0.5/级上限5，与防护类别互斥，近战反伤=词条值×2，并产生额外护甲耐久消耗。效率副属性最大5。`E/mixin/compat/ToolModifierTypesMixin.java:20`、`E/register/EXArmorModifierTypes.java:14`、`E/util/ItemUtil.java:16`。
- 5新增附魔：固定阻止交换位置，连锁矿，缓慢箭，采摘，收割。精准采集与连锁互斥；延伸等级改2；伤害类对棍/战锤适用性重写；高耐久材质吸血禁用逻辑不能凭方法名onlyVibranium猜反：代码实际`durability>=vibranium/2`直接false，普通棍末尾又有true补丁，需要按注入返回优先级理解。`E/register/EXEnchantmentRegistryInit.java:23`、`E/mixin/item/enchantment/*.java`、`E/mixin/compat/EnchantmentExtendMixin.java:13`。
- 禁普通书/水瓶直接附魔；已附魔装备可用书追加，非OP需经验>=8000才放行；取出时固定扣8000经验；另有附魔总级数统计（单级附魔按5权重）用于其它显示/逻辑，不能混作本次扣费公式。振金允许超原附魔等级，与独占/冲突判定共同运行。`E/mixin/item/ItemStackMixin.java:37`、`E/mixin/container/ContainerRepairMixin.java:73`、`ContainerRepairINNER2Mixin.java:41`、`E/mixin/client/gui/GuiRepairMixin.java:46`。
- 锻造沿用ITE复杂材料/概率方案，Extreme专门把失败降级反馈改0，不等于无失败或免费锻造。振金工作台无BEX时合成周期最多25tick。金属棍可格挡，基础攻击额外+2；振金锭新增合成路线及骷髅头转化。各配方源见两register类，完整配方方法均入索引。
- 振金炉5%概率烧炼额外+1，受输出容量；金属块温度门槛：振金/艾德曼5、秘银/远古4、铜银金铁块与红钻矿3；动态核心秘银等级3/艾德曼等级5，每11tick扣2耐久、等级控制客户端夜视/照明，不是永久免费夜视。`E/events/listener/EXFurnaceUpdate.java:12`、`E/mixin/block/tileentity/TileEntityFurnaceMixin.java:20`、`E/entity/manager/JewelryManager.java:31`。
- 弩有装填箭NBT、发射消耗、急速附魔缩短装填、力量/冲击/火焰读取、材质箭回退及声音/模型，装填时间`max(5,25-5*quickCharge)`；新下界掠夺者使用它。当前使用现代弩系统并给原生掠夺者升级武器，因此不等于旧掠夺者AI/数值完整保留。

## 经济、战利品、成就、客户端与命令

- 村民禁旧图书管理员/铁匠职业，注册替代职业。铜/银7～11、金8～12、铁5～8等金属买卖；4绿宝石↔3钻石、3红钻↔5绿宝石、8红钻→恶魂泪；种植/村民凭证交易；农民新增洋葱/胡萝卜/土豆12～15换绿宝石。普通书价约等级×5+随机，特殊书低概率0.05；时运特殊书最多III、延伸最多I。`E/events/listener/EXTrading.java:37`。
- 钓鱼表新增15行：凭证权重36、食物各16、铜银金铁粒各8、秘银/远古/艾德曼/振金粒各4；是权重而非这些百分比。成熟作物/村民交易分别概率出相应凭证，三个凭证可合棍核心。`E/events/listener/EXLootTable.java:15`、`E/mixin/block/BlockCropMixin.java:27`、`E/mixin/entity/EntityVillagerMixin.java:42`。
- 怪物战利品包括复活阵、宝石/材料、金属粒、钻石/绿宝石、凋灵头、药材等；本报告实体附表逐项列关键特殊掉落，常规继承掉落保留来源边界。Boss按伤害贡献发奖，单个玩家floor(四舍五入伤害/20)钻石、伤害/50书级，排行榜。新实现用UUID、float直接除后取整且按现代附魔maxLevel夹，精度及高阶书上限不同。
- FML成就/物品方块获得/制作、击杀、钓鱼、种植凭证触发；EMI宝石特殊配方，新商店扩展，Baubles可选饰品槽，keep-inventory兼容，BEX/GA部分内容条件跳过。现代替换为JEI/NeoForge接口，不能按“文件名不一样”算缺失。
- 客户端：宝石星级与颜色、物品tooltip/GUI、宝石台、附魔修复经验、彩床、双半砖/材质、血月红色光照、动态核心夜视、实体贴图模型/发光/烟雾、倒置/遮蔽/大头、声音、主菜单文字、创造页及玩家背包扩展。35个render/model/gui文件及各客户端/渲染类mixin逐文件在索引；未注册RenderBlocks例外单列。
- 命令实际来自 `E/events/ExtremeFishEvents.java:129`：开发模式recall、layer keep ore、potion；普通分支firework切换烟花、reconfig重新读配置并广播。**gm会请求远程wensc.cn/mite.txt，比对口令MD5，成功设自定义OP/创造/飞行，失败撤回相应状态**；CommandHandler/ServerPlayer/EnumGameType等修改让此OP影响权限。这里只记录行为，未执行请求。当前代码未检出此远程gm鉴权链，属于保留差异，不应移植时默认补上。
- 现实GMT+8每年1月1日00:00～00:05或firework开关会创建烟花；玩家tick中实际逻辑有这一日期条件。配置有`isRecipeRingKiller`字段但全源码仅声明和注册未见使用，不能说它控制魔灵刃配方已实现。

## 可选 ITF 兼容全部机制族

59个条件mixin完整名单/注入目标见索引。其作用不是Extreme默认开渴水季节，反而大量禁用或还原：

| 类别 | 条件兼容行为 |
|---|---|
| 水/营养/腹泻 | 禁水HUD与ITF水扣除/生食水逻辑；恢复MITE蛋白/植物营养不良和湿身饥饿；禁腹泻更新；恢复营养上限/初值；部分步速×3还原 |
| 季节/天气/生态 | 世界季节固定春季，冻结/雪回群系规则；暴雨/永久雨不再按季节；移除风蚀高原初始/丘陵/要塞候选 |
| 矿物/物品内容 | 禁ITF矿注册、蓝晶/硫磺/石锥矿、挖矿额外hook；禁魔杖/图腾/矿袋使用；剪除禁用内容的配方、掉落、烧炼、奖品表，避免只隐藏物品但仍能获得 |
| 附魔/药水 | 禁ITF附魔、无限、附魔预测、附魔台额外增强、伸展药水配方和女巫掉落 |
| 敌人/掉落 | 禁侍从僵尸自然池与亡灵召侍从；骷髅无限箭恢复；移除ITF装备/跨维入侵装备掉落；巫妖数值、存档、召唤/掉落改写 |
| 地牢/城堡保护 | 地牢与地底城堡宝箱过滤禁用物品；巫妖城堡在受保护状态拒绝点挖/收获，私人箱NBT存标记、城堡死亡解锁；城堡召怪与掉落转换 |
| 农业/交互 | 作物/瓜茎/灌木恢复MITE成长，允许重新点燃熄灭火把，石头右键献祭回原行为，金苹果ITF效果兼容、定型配方工作台硬度修正 |

这些只在额外安装ITF时有意义，不能把ITF不存在时的目标类当作Extreme“加载失败”。当前项目无ITF旧加载器生态，报告应归为兼容平台差异而非默认生存内容缺失。

## 已确认的死代码、边界及较明显差异

1. 配置类30项声明（完整表见 `extreme-config.csv`），只有29加入loadConfigs映射，`zombieBossSpawnPercent=0.5`未加入，普通配置文件不能改它。`ConfigItem`保存min/max但读取setConfigValue并未执行范围夹取。配置注释与运行逻辑需分开。
2. `isRecipeRingKiller`默认true但无机制使用；未注册花盆/RenderBlocks注入不可算生效；`JewelryManager.tickServer`为空，不能凭ring名字写成周围杀怪光环。
3. 旧攻城尸抛幼尸条件同时要求target是玩家且target==null，永不成立；当前实现真实投掷，是修复。旧随机尸`onDeath`未super，传统死亡/掉落链有偏差，不能声称旧声明的掉绿宝石必然执行。
4. 当前保留的41种Extreme身份+4种ITE身份=45；旧注册44的三个额外身份亡灵恶魂、掠夺者、掠夺者队长当前没有enum/独立注册。原生掠夺者33天后换Extreme弩是一部分替代；保留Family.GHAST/PILLAGER和内部类不代表这些身份能实际生成。
5. 当前给龙头尸新增龙息、给铁桶尸新增桶头攻击、给骷髅暴君新增召唤6骷髅；混乱输入、拟态概率/血量/背包、大力士/击飞纵向速度、暗铁傀儡攻击×2等均有明确差异，参见实体附表。
6. 旧很多怪物线性day基础属性/维度加成由ITE父类叠加，当前统一成长体系改写。任何单列“实际总血量”必须说明出生维度、经历天数、配置、分裂子代、装备与附魔，而不能只取一个applyEntityAttributes常数。
7. 旧Boss玩家受击缓冲字段也有不一致：`EntityPlayerMixin.getIsAttackByBossCounter()`始终return0，setter写自身字段，但独立BossManager又管理另一组字段，不能把原意30tick免雷击当作已证实生效；当前使用持久化时间戳修正。旧ItemStack空NBT修复分支在判断stackTagCompound==null后却对同一空字段setInteger，有潜在NPE，是否触发受前置ITE修复顺序影响，未动态断言必崩。
8. 本次不重跑Minecraft完整联机/GUI；静态执行链和源文一致性已核实，依赖冲突、随机分布实测、所有UI像素与资源观感均不能冒充动态通过。

完整逐种比较见 [entity-comparison.md](entity-comparison.md)，完整源文件索引见 [extreme-source-index.md](extreme-source-index.md)。
