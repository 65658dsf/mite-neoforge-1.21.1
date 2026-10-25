# 当前主模组：现代新增、适配与机制覆盖矩阵

分析署名：NingMeng123。日期：2026-10-25。当前仓库`MITE: Modern 0.2.10`。路径前缀`src/main/java/io/github/ningmeng123/mite/`。本表补充`r196.md`的来源审计，区分当前生效路径、现代新增与可选集成，不以类计数替代玩法数量。

## 一、主模组现代内容与现有R196机制的关系

| 家族 | 当前实际机制 | 对 R196 的关系及生产证据 |
|---|---|---|
| 装备成长 |装备独立UUID、经验、等级上限、升级抽词条；本体所有已知材料上限统一25；武器升级XP`150+100L`、工具`100+50L`、护甲`(20+10L)*部位系数2/4/3/1`；所有经验奖励再减半；候选池非空每级100%给词条；玩家放置块防刷；数据随维修升级保留 |R196品质不等于装备等级。`progression/GearProgressionRules.java:9–15,28–40,58–59`、`GearProgressionEvents`、`GearModifierRules`、`GearAffixRules.java:5–6,52–61`及`GearProgression`；部分思想承继ITE/Extreme，不能只称原R196 |
| 词条概率与附魔配置 |权重、副词条数值、自然获取和玩家附魔上限、怪物装备附魔与词条整合；配置界面预览、保存验证与同步 |`config/EnchantmentCatalog`、`enchantment/ConfiguredEnchantments`、`progression/EnchantmentEffectBridge`；来源原38目录以外需看扩展实际注册 |
| 材质升级模板 |4铜/银锭+2铁+2金+青金石，产1模板，无复制配方；铁台能力、400难度、计时；锻造消耗整件制造成本向上取整锭 |R196没有1.21锻造模板槽模型。`registry/MiteUpgradeTemplates`、`data/UpgradeTemplateAssetsProvider`、`data/MaterialUpgradeRecipes`、`crafting/MaterialEquipmentUpgrade`及`TimedSmithingController` |
| 铜升铁工作台 |铜工具/武器/板甲在铁配方第一木棍位置换铜原件，仍付全部金属；无木棍配方空位放旧件；铜盾/竿/锁甲/马铠保留模板路线 |现代独立制造路线。`data/CopperEquipmentUpgradeRecipes`及升级Ingredient；不能把它描述为所有材质均直接工作台升级 |
| 数据继承 |名称、附魔、品质、成长UUID/XP/级别/词条、磨损比例、染色纹饰、盾旗、维修惩罚和个人组件保留；目标默认攻击/采掘属性优先 |`MaterialEquipmentUpgrade`/`MaterialUpgradeRequirements`；换材质不立即重抽词条或补满耐久；旧原材质显式组件不能覆盖目标默认材料 |
| 升级路线 |低金属→铁→远古→秘银→艾德曼→振金；同类型逐阶，不跨缺失目录；强化弓等只生成实际存在相邻材料路线 |`MaterialUpgradeRecipes`/`MaterialUpgradeCostRules`；马铠无普通制造配方故成本例外不是“按实际配方计算” |
| 振金套装与工具 |艾德曼以上的材质、工作台/铁砧/装备/粒锭/武器、品质维修、成长上限和后期怪物装备；本体有基础注册，ITE可再增强 |R196没有振金。`registry/MiteTools`、`MiteArmorContent`、`MiteMaterials`、`rules/VibraniumEquipmentRules`、`crafting/VibraniumCrafting`/`VibraniumUpgradeRecipe`；附魔48书架/158能量见`R196EnchantmentTable:133–139` |
| 盾牌格挡 |按实际挡下的伤害和次数双阈值；基础3次/25伤害，铁远古5次、秘银7、艾德曼9、振金11；阈值伤害同比；破防100tick共享冷却，换盾不绕过 |R196没有原版1.9后盾机制。`rules/ShieldGuardRules.java:5–9,22–43`、`combat/ShieldGuard.java:88–162`；不是简单无限右键格挡 |
| 怪物持盾 |新生持剑人形15%抽盾，方向性阻挡、举盾Goal、斧破防、显示姿势；出生决定保存 |`entity/MobShields.java:33,85,98–99`、`ShieldBlockGoal`及Living/MobShieldMixin；旧生物原武器/特殊形态按保护条件，非任意实体全部持盾 |
| 幻灵刃 |主背包36格携带即激活最高阶一件，10格可见合法敌人，自动逐把发射，不借主手附魔词条；碰墙/友方拦截；弹匣/冷却跨材质重登死亡维度持久，不即时回收 |当前用户定制，不套旧扩展同名数值。`rules/PhantomBladeRules.java:6–17`、`phantom/PhantomBladeSystem`、`PhantomBladeState`、`PhantomBladeProjectile`、`PhantomBladeRecipe` |
| 幻灵刃数值 |铜银4把、.5伤害/60tick间隔/600tick恢复；铁5把1/50/540；秘银6把1.5/40/460；艾德曼7把2/30/380；振金8把3/20/300；锈铁/金/远古另有档 |`PhantomBladeRules.java:10–16`；不是各材质统一8把；半径、发射和换材质防补弹由真实系统管控 |
| 武器平衡 |铜银等近战基础伤害/攻速重调、现代攻击冷却、触及与高差、怪物持械距离读取、命中反馈 |`rules/NativeWeaponBalanceRules`、`SourcePlayerCombatRules`、`combat/SourcePlayerReach`等；源材质效率/伤害与当前平衡表需分开 |
| Simply Swords |可选加载15武器族×7新材质=105把，金复用；品质/耐久/制作/升级/成长；90可制锈铁只生成；独特命名武器禁获取/使用/清理；保留注册防旧档缺项 |`compat/simplyswords/SimplySwordsCompat`、`rules/SimplySwordsBalanceRules`、`SimplySwordsMobRules`、相应registry/data/mixin。未安装不注册兼容装备，不消耗对应额外随机抽签 |
| SS双手代价 |巨剑/巨斧/巨锤主手移速-20%/-22%/-25%，其余双手通常-15%；副手/背包/怪物不负重；攻速采用分族表 |当前15家族独立平衡，`SimplySwordsBalanceRules`与实际动作属性；不能用映射耐久类别决定武器长度/双手属性 |
| Better Combat |可选动作/距离解析及玩家/怪物攻击触及，实际武器资料优先；无BC回退；动画/姿势与空值适配 |`compat`、相关`BetterCombat*`类与Mixin；依赖不是主模组必需。不能说R196有同一套现代动作/距离 |
| 血月入侵/装备 |记录最高天数、阶段材质、日常/血月怪物装备、出生只抽一次、早期规则/稀有保留、装备经验/附魔；ITE策略可替换日历/计数 |`worldgen/BloodMoonInvasion`/`BloodMoonInvasionData`、`rules/BloodMoonEquipmentRules`、`entity/MobEquipmentUpgrades`；参考刷怪模式与自定义入侵不是同一开关 |
| 怪物成长公式 |可编辑四公式：周期血量/攻击与血月结算；默认血每天参考*.005、攻*.0025；源本体基础/骷髅参考20；血月后值做下一周期base/reference |`rules/MonsterGrowthRules.java:7–10,164–218`，公式验证安全边界。**仍硬编码32边界并跳128**，`completedBloodMoons=elapsed/32-elapsed/128`；ITE若16日血月而只改日历不会令此算法每16天结算，需最终调用链比较 |
| 自然刷怪编辑 |reference_rules开关，人数面积预算，深浅尝试/密度、频率、倍率/权重/种族列表；扩展可贡献自己的控件和策略 |`rules/MonsterSpawningSettings`、`worldgen/ConfiguredMonsterSpawns`、`api/MiteExtensions.Policy:35–65`；纯本体默认reference忽略保存100/200/5，自定义才启用。配置数字存在不等于最终有效 |
| 矿物编辑 |stone/deepslate分别候选倍率、高度范围、候选次数/几率；地底煤/绿宝石；默认普遍提升资源 |`R196OreFeature`/`OreGenerationRates`、`MiteConfig`、`gameplay-defaults.toml`。当前全群系绿宝石/粗矿/现代洞穴已在R196笔记详述 |
| 天然尖啸体矿奖励 |世界生成/远古城市自然可召唤尖啸体，球半径5内额外总计1–3块四选一矿（钻石/秘银/青金/绿宝石），石头/深板岩匹配；记录来源不重复 |现代1.19内容。`worldgen/NaturalShriekerOres.java:36,104–152`及三生成Mixin；玩家摆/指令/运行期催发蔓延不触发，独立随机流不套普通矿倍率/高度 |
| 地底试炼密室 |复用现代试炼模板、独立地底结构集合；普通/不祥试炼怪物映射到MITE身份，装备/权重/奖励/计数保留、排领主 |`worldgen/UnderworldDungeonSpawns`、`UnderworldSpawnerMobs`、`rules/UnderworldSpawnerRules`、`UnderworldDungeonMobRules`。R196没有试炼笼/旋风人体系 |
| 地底宝库 |独立普通/不祥奖励表，高阶金属粒、远古装备及保留附魔、材料箭权重、宝石/附魔金苹果；已领奖/队列/种子状态保留 |`worldgen/UnderworldVaultLoot`、`data/UnderworldVaultLootProvider`、`mixin/UnderworldVaultLootMixin`；不是将全部维度原版宝库同改 |
| 天然试炼方块 |真实结构生成记录区块来源，挖/炸/支撑连锁不掉方块/经验，铜变化/活塞/落块保留标记；罐内部loot保留；玩家后放及拆后重放正常 |`worldgen/NaturalTrialBlocks.java:32–45,100–145`、`TrialBlockDrops`、`TrialBlockMovement`、`NaturalTrialFallingBlock`；旧无标记密室不扫描追溯 |
| 现代主世界战利品 |试炼战利品/现代装备移除、村庄模板/铁匠奖励、地底旧地牢奖励、宝库各有不同路径 |`loot/OverworldTrialLootBalance`、`ReducedTrialLoot`、`UnderworldDungeonLoot`、`VillageBlacksmithLoot`；不能由某张loot表存在认定所有结构已平衡 |
| 营火燃料 |新放熄灭0燃料，最多3点，每点900tick或熟2肉先到耗尽；同时2肉每块300tick；打火石补满/火把+1并点燃/木材+1不点燃 |现代独立生存设计。`campfire/CampfireRules.java:5–11`、`CampfireInteraction`/`CampfireController`，普通与灵魂均适用，红石火把不适用 |
| 营火环境 |雨/水/铲灭中断返原生肉不刷配额；光10、3格解除冻结、1格可燃物每秒1/60点火、16格闲置怪调查、蜂烟保留、精准不掉完整营火 |`CampfireEnvironment`、`InvestigateCampfireGoal`、CampfireMixin；只吸引已有怪，不因火另刷怪；不新增温度条 |
| 世界物理 |坍塌扩现代土壤但排草、全部落块伤害、试炼天然来源、源整格液体/爆炸预处理/物品碎裂与源XP物理 |R196原机制+现代扩张混合，不能整体标“现代独有”或“原版照搬”；见`CollapsePhysics`、`FallingBlockImpact`、`SourceFluidFall`、`SourceExplosionItems/Blocks`、`ExperienceOrbPhysics` |
| 液体桶 |临时普通放液、Ctrl+100XP永源、实际成源扣费/失败返款、已有源不降级、熔岩/水反应、容器采集/石桶冷却 |Ctrl/经验源有旧R196来源；现代服务端事务/网络/配置/水浸容器属适配，`BucketPlacementController:32–140`/`TemporaryFluidChanges`/`StoneBucketCooling` |
| 宝石献祭 |青金25、石英50、绿宝石250、钻石500XP；扣完整1物品，10tick四种共享冷却，玻璃声/5粒子；副手、创造和事件取消检查 |不是现代新增，源`ItemRock:19–44`已存在；当前`item/GemSacrifice.java:23–61`；ITE可覆石英XP/等级门槛 |
| 经验/食物适配 |200以上经验曲线、天然属性200封顶、B→N→S缓冲/双营养、睡眠恢复8倍、严格服务端饥饿/进度/使用间隔 |`ExperienceRules`/`ProgressionRules`/`FoodDataMixin`/`NutritionRules`；R196基础部分与现代差异在`r196.md` |
| 生物路线与逃跑 |现代路线规划比较绕路和拆障；家畜受伤逃跑与恢复、寻食饮水/草恢复/健康贴图；独立日期怪物装备 |`entity/ZombieRoutePlanner`、`rules/ZombieRouteRules`、`rules/AnimalEscapeRules`、`animal/Livestock032`/`LivestockHealth033`；源家畜三需求不是未实现 |
| 护甲/装备掉落 |现代减伤公式与源磨损、材料/品质/来源装备掉落、怪物成长/装备词条/盾、稀有和专属装备保护 |`rules/ArmorBalanceRules`、`SourceArmorDefenseRules`、`entity/SourceVanillaMobLoot`、`NativeMobEquipment`及LootMixin；历史文档中的部分复制状态不能代源码 |
| 配置与同步 |游戏设置包括矿物、怪物成长公式、刷怪、附魔目录、装备/触及等；服务端校验、保存、发布、客户端只读同步；导出TOML |`config/MiteConfig`、`GameplayConfigExport`、`GameplayConfigSync.java:13–27`、`network/GameplayConfigPayload`、`client/config`；本地TOML/内置defaults/ITE策略/存档状态优先顺序必须按入口分开 |
| 管理命令 |`/mite clearblocks`需权限2、玩家中心范围、分tick队列、公平轮转、取消、离线/停服清任务；其他查状态/诅咒/调试命令 |`command/ClearBlocksCommand.java:44–46,72–74,111–139`；是管理员工具，不属于玩家生存获得的能力 |
| 客户端反馈 |品质/耐久/铁砧/炉热、目标眼睛、命中、月相/天空/黑暗覆盖、Tab状态、AHM/AUM绿色提示、最小调试HUD、双条食物 |源功能和现代显示混合，`client`/`network`/client Mixins；渲染和网络跨人同步需实机，不从server数值测试推定视觉通过 |
| 可选UI/动画兼容 |JEI配方/升级/炉温页、Jade农牧/营火/砧/方块/怪物提示、AppleSkin双营养、Mod Menu设置、Fresh Animations模型/眼睛/盾姿 |无需安装第三方即可运行本体；可选项目分别在`integration/jei`、`integration/jade`、`client`与兼容Mixin；不等同R196原UI |
| API与分包 |主模组策略涵盖日历、刷怪上下限/组数/权重/距离、生命食物、成长、炉热、附魔、经验、门、村民交易、装备；外部注册工具护甲附魔并统一属性上限 |`api/MiteExtensions.java:27–100`；当前ITE通过正式hook叠加，不能把源于ITE思想却现位于本体的成长/振金/锻造与独立ITE类混算两次 |

## 二、本轮确认主模组可达来源缺口

1. **砖和地狱砖投掷**：旧`ItemBrick:16–28`+`EntityBrick:24–42`可右键、消耗砖、2伤害和.07重力；当前`src/main`只有source数据提到EntityBrick/ItemBrick，没有Java生成/使用投射链。黏液球投掷不能替代。
2. **旧末影龙整场战斗与旧末地设施逐点一致**：现代有意保留1.21.1龙战/龙蛋/门户设计，应列明确适配而不是“已完全源式复刻”。
3. **全原生现代生物、结构经济和战利品闭环**：守望者/旋风人/猪灵/袭击/现代贸易等没有R196逐一对应，已有定向改动不能认定所有新增内容都符合MITE进度。应列现代保留与待专项平衡，不因为没旧对应而叫漏移植。

## 三、仍未完成本轮逐条证明的范围

完整902注册配方和动态算法配方、221来源方块所有metadata环境行为、85实体（特别非Living投射物/车辆/天气）全部生命周期、所有未覆盖原版父类、职业/赛事显式启用分支、未记录旧存档NBT迁移、所有第三方组合和客户端/多人视觉、长期自然分布。清单范围很广，但没有给这些逐条打“通过”。

## 四、跨系统差异及适用条件

- **ITE新血月日期与本体成长结算不共用计数**：`MonsterGrowthRules:166–218`独立32/128；当前ITE若128日后每16日血月但只覆盖`LunarCalendar`和`bloodMoonCount`，通用HP/攻击不会按每个新血月结算。不能将装备阶段计数替换误述为所有成长共用。
- **reference刷怪保存项与实际入口**：纯本体reference=1忽略100/200/5；ITE可独立保reference生境而开放自定义频率。正确报告应该写“策略解析结果”，不要由TOML字段直接给默认刷怪密度。
- **旧文档过时**：031仅初值畜牧、早期无炉温、七差距补齐前毒/火球/自动使用/交易缺失、world-physics无Ctrl永久源均已不代表当前源码；不能把这些当确认缺口。
- **本轮分析未修改实现、未跑完整游戏回归**。已有测试/构建日志只按原版本与原范围引用，不能给五方完整机制等价保证。
