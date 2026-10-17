# R196 复刻遗漏核查

开发署名：NingMeng123。核查日期：2026-10-17。

本文件保留实施前的核查证据：当时仍有可达玩法未复刻，部分已注册内容的行为也与 R196 不同。新增生物、附魔和物品目录已比较完整，主要缺口位于输入辅助、物品环境行为、药水规则、生物射击和多方块材料条件。

用户随后选择了 AHM/AUM、物品机制、生物 AI、多方块、Buff、药水免疫、交易与钓鱼七类进行复刻。这七类已有实现；最终自动验证记录与手测步骤见 [本轮交付验证](testing/r196-parity-implementation.md)。砖与地狱砖投掷未在本次选中范围内，仍保留为后续项目。下文表格的“未实现”和“差距”均描述实施前状态，不覆盖后续实现。

核查对象是当前工作区，包括已有未提交变更；基准 HEAD 为 `34c19393d2df7d97a3814699866fec530f905a94`。参考包为本地 `MITE 1.6.4 R196.zip`，本轮重新计算 SHA-256 得到 `fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。下列旧版位置均相对于 `.reference/r196/decompiled/`，行号为当前反编译文件行号。

## AHM 和 AUM

| 对照项 | 实施前状态 | 证据与边界 |
| --- | --- | --- |
| AHM 自动采集 | 已实现主要采集流程 | [AutoHarvestInput.java](../src/main/java/io/github/ningmeng123/mite/client/AutoHarvestInput.java#L35)、[AutoHarvestState.java](../src/main/java/io/github/ningmeng123/mite/rules/AutoHarvestState.java#L24) 已实现先攻击再组合使用键、同类方块继续采集、现实 5 秒超时和取消。界面、换栏、失焦立即清理属于已有明确适配，见 [自动采集说明](auto-harvest-input.md)。 |
| AUM 自动使用 | 未实现 | 旧 `bdc.java:414–440` 有独立状态；`atv.java:1565–1591,1611–1639` 将其接入持续使用、组合按键和取消。当前输入状态与 Minecraft 注入只扩展攻击键；[AutoHarvestMinecraftMixin.java:20](../src/main/java/io/github/ningmeng123/mite/mixin/client/AutoHarvestMinecraftMixin.java#L20) 没有对应使用键状态机。 |
| AUM 绿色槽位提示 | 未实现 | 旧 `avj.java:133–138` 在 AUM 时给选中快捷栏框着色。当前无该状态或快捷栏着色入口。 |
| 用户说明中的 AHM 绿色边框与背景 | 尚未加入；与本包原始行为需区分 | 本地 R196 的快捷栏着色条件只读取 `auto_use_mode_item`，不读取 AHM。若两种模式都显示绿色，应明确记为用户要求的界面补充。 |

R196 的 AUM **只允许吃喝类动作**：`bdc.java:420–422` 调用 `EnumItemInUseAction.isIngestion()`，该枚举在 `EnumItemInUseAction.java:12–13` 只接受 EAT 和 DRINK。它不是通用的自动放置、拉弓或武器格挡模式。上述资格判断和快捷栏着色条件已用发布包 `javap -p -c` 复核，分别为 `bdc.isItemStackEligibleForAUM` 偏移 8–20、`avj` 渲染方法偏移 786–800 和 846–856。

## 实施前确认的其他机制缺口

| 机制 | R196 行为和来源 | 实施前差距 |
| --- | --- | --- |
| 物品耗尽后的自动换持与补货 | `ud.java:61–145` 搜索同种或相似物品，切换快捷栏或把背包物品移入空出的槽位；`ud.java:702–708` 在转换最后一件手持物时通知客户端；`jv.java:1446–1452`、`bcw.java:1156–1168`、`atv.java:1596–1600` 构成可达调用链。`bdc.java:258–263,347–350` 管理现实时间窗口。 | 生产代码没有等价的耗尽触发、相似物品选择及自动补货流程。现有整批搬运逻辑处理容器操作，不能代替此机制。这里指耗尽后的换持，不是自动为准星方块挑选最佳工具。 |
| 吃喝完成后的间隔 | `bdi.java:312–320` 在一次 EAT/DRINK 完成时设置延迟；`bdc.java:339–344` 限制现实 1 秒内再次摄入；`uf.java:2291–2307` 在下一次摄入前检查。 | [FoodConsumption.java:24](../src/main/java/io/github/ningmeng123/mite/survival/FoodConsumption.java#L24)、[CompleteFoodUseMixin.java:15](../src/main/java/io/github/ningmeng123/mite/mixin/CompleteFoodUseMixin.java#L15) 只有摄入资格校验，没有完成后的共享 1 秒间隔。单件食用所需的 32 tick 与此不同。 |
| 冰雪物品受热融化 | `jv.java:559–566` 检查玩家主库存；`ss.java:110–113` 检查地面掉落物。`yc.java:1543–1544` 的每刻概率为 `(群系温度−1)×0.0001`，仅正概率时生效；着火玩家按温度 20 计算。`ye.java:1036–1045` 对栈内物品逐个抽取。 | 当前冰雪相关逻辑没有玩家库存或掉落物的温度消耗入口。不能用方块融化、作物降雪、堆叠限制或熔岩桶规则代替。 |
| 砖和地狱砖投掷 | `yc.java:81,150` 实际注册两种 `ItemBrick`；`ItemBrick.java:16–28` 右键生成 `EntityBrick`、消耗一砖并收取耐力修正后的饥饿费用。`EntityBrick.java:24–42` 包含重力、2 点实体伤害及方块命中逻辑。 | 当前没有砖投掷入口或砖投射实体。[GreenSphereItemMixin.java:17](../src/main/java/io/github/ningmeng123/mite/mixin/GreenSphereItemMixin.java#L17) 只处理黏液球。[附魔说明](enchantments-r196.md) 中历史记录的砖投射物缺口仍然存在。 |
| 信标的四种金属基座 | `arw.java:56–70`，特别是 `:61`，明确允许铜、银、秘银、艾德曼块作为金字塔材料。 | [MiteMetalBlocks.java:22](../src/main/java/io/github/ningmeng123/mite/registry/MiteMetalBlocks.java#L22) 已有方块身份，但 [MiteBlockTagsProvider.java:44](../src/main/java/io/github/ningmeng123/mite/data/MiteBlockTagsProvider.java#L44) 只加入采掘标签，没有扩展 `minecraft:beacon_base_blocks`，也没有信标替换逻辑。这四种材料目前不能激活信标。远古金属不在旧白名单内。 |

## 实施前已有内容的行为差异

| 类别 | R196 与实施前实现的差异 | 证据 |
| --- | --- | --- |
| 中毒 | R196 的基础间隔是 `100 >> amplifier` tick，下限 1；执行毒伤时没有现代的“生命大于 1”限制。当前原生毒为 `25 >> amplifier` tick，且毒伤不会直接扣到死亡。剧毒附魔虽然存在，最终毒伤仍受此差异影响。 | 旧 `ni.java:85–86,126–140`、`of.java:659–665`；当前 [RangedEnchantments.java:32](../src/main/java/io/github/ningmeng123/mite/enchantment/RangedEnchantments.java#L32)、[R196Spider.java:63](../src/main/java/io/github/ningmeng123/mite/entity/R196Spider.java#L63) 使用原生 POISON，没有替换毒效果更新。 |
| 力量和虚弱 | R196 使用 operation 1，每级增加或减少攻击属性计算基数的 40%，该基数已包含 operation 0 的武器等固定加值；当前仍是现代每级固定加 3、减 4。这里的级数为 `amplifier+1`，不是直接使用放大器数值。 | 旧 `ni.java:18,31`；`nf.java` 没有覆盖父类算法。原属性实例 `ox.g()` 字节码偏移 0–47 先累加 operation 0，49–92 再算百分比；`xj.java:220` 的武器加值为 operation 0。当前没有对应效果属性覆盖。 |
| 普通缓慢 | 单独施加缓慢、没有速度与自由行动等修正时，R196 每级贡献 −20% 速度修正，当前普通缓慢为现代 −15%。旧玩家的速度与缓慢先相加，自由行动折扣净负修正，最终再限幅。已有自由行动附魔仅在相关装备生效时局部使用来源算法，不能代表普通缓慢已统一。 | 旧 `ni.java:15,219`、`of.java:1741–1758`；当前 [SourceEnchantmentCombat.java:150](../src/main/java/io/github/ningmeng123/mite/enchantment/SourceEnchantmentCombat.java#L150) 无自由行动时直接返回，`:161–163` 恢复的是原生效果模板。 |
| 非生物活性实体的药水免疫 | R196 对这类目标统一拒绝再生、中毒、凋零。当前亡灵标签只拒绝毒和再生，凋零未统一排除；地狱犬、火元素、土元素和黏土魔像也未把生物活性标记接入通用效果资格判断。 | 旧 `of.java:504–506`、`EntityHellhound.java:99`、`EntityFireElemental.java:146`、`EntityEarthElemental.java:317`；当前 [R196Mob.java:9](../src/main/java/io/github/ningmeng123/mite/entity/R196Mob.java#L9)、[MobAssetsProvider.java:43](../src/main/java/io/github/ningmeng123/mite/data/MobAssetsProvider.java#L43)。`biologicallyAlive()` 的附魔判断不能代替通用药水免疫。 |
| 烈焰人和恶魂远程 AI | 两者在 R196 都预测移动目标的位置；烈焰人首轮准备和轮末停顿各 20 tick。当前仍为现代直接瞄准位置，烈焰人相应间隔为 60/100 tick。基础属性、生成和掉落接入不代表这段射击逻辑已迁移。 | 旧 `td.java:83–107`、`tj.java:102–109`；当前 [R196MonsterAi.java:73](../src/main/java/io/github/ningmeng123/mite/entity/R196MonsterAi.java#L73) 的管理名单不覆盖两者，全生产源码没有对应预判替换。未发现类似末影龙的明确现代射击保留说明。 |
| 村民来源货单 | R196 黑匠可出售铜镐、铜匕首、铜剑、铜甲和铜链甲。当前保留现代职业体系，但未加入对应来源货单，属于交易内容部分复刻。 | 旧 `ub.java:361–379`，相关价格 `:579–597`；当前 [MerchantBatchTransfers.java:16](../src/main/java/io/github/ningmeng123/mite/crafting/MerchantBatchTransfers.java#L16) 处理既有报价搬运，[RemovedDiamondTradesMixin.java:13](../src/main/java/io/github/ningmeng123/mite/mixin/RemovedDiamondTradesMixin.java#L13) 过滤钻石装备。不能将旧存档报价转换中的祛魔瓶误算为新鲜货单。 |
| 骑马甩竿豁免 | 旧版明确豁免船和马，当前误映射成船和矿车，漏掉马的豁免。这是静态条件不一致，不能据此推断所有骑马状态下都绝对无法甩竿。 | 旧 `uf.java:2608–2621`；[类索引](../reference-data/class-index.json) 确认 `rs=EntityHorse`、`sq=EntityBoat`；当前 [RangedFishingRodMixin.java:35](../src/main/java/io/github/ningmeng123/mite/mixin/RangedFishingRodMixin.java#L35) 使用 `AbstractMinecart`。现有 [远程证据文档](ranged-027-evidence.md) 也沿用了该误读，后续修复应同时更正。 |

上述现代原生效果及射击数值直接核对项目固定依赖 `build/moddev/artifacts/neoforge-21.1.259-sources.jar`：`PoisonMobEffect.java:11–27`、`MobEffects.java:27,51,76`、`LivingEntity.java:980–988`、`Blaze.java:213–237`、`Ghast.java:272–280`。没有以其他 Minecraft 版本的规则代替当前依赖。

## 已覆盖目录及不应误报的项目

- **物品目录**：`spec/item-catalog-015.json` 的 347 项默认目录当前为 313 项模组注册、34 项原版复用；本轮没有发现该目录中的确定漏注册。目录身份齐全不代表所有物品行为和全部来源配方一致。
- **新增生物**：29 种具体新增生物通过 [MobKind.java](../src/main/java/io/github/ningmeng123/mite/entity/MobKind.java) 和 [MiteMobs.java:28](../src/main/java/io/github/ningmeng123/mite/registry/MiteMobs.java#L28) 接入真实家族实现，不能把家族复用当作缺少实体。
- **附魔和诅咒**：[R196EnchantmentCatalog.java:34](../src/main/java/io/github/ningmeng123/mite/enchantment/R196EnchantmentCatalog.java#L34) 覆盖 38 种来源附魔，即 21 种旧原版对应项与 17 种 MITE 附魔；16 种女巫诅咒及延迟、生效、发现、保存和解除生命周期已有实现。没有发现新的缺失名称；药水规则仍会影响部分附魔的最终效果。
- **制造与多方块条件**：九种材料工作台、来源炉型/热级/炉口通风与浸水条件、普通与绿宝石附魔台的最多 24 书架、符文门四角条件已有实现。没有来源证据表明“大黏土炉”必须另搭一个大型炉壳。
- **世界结构**：村庄/神庙/矿井/要塞的分布及门槛已有实现；现代建筑模板是明确适配。地底试炼密室、现代怪物房次数和身份映射见 [地底地牢说明](underworld-dungeons.md)，不能把用户要求的现代设计当成漏复刻。
- **明确适配与关闭分支**：现代龙战、龙蛋及传送设施，玩家与人形装备的现代护甲/抗性结算、攻击冷却，现代时间推进方式，青金石费用，用户要求的 B→N→S 食耗顺序，以及原包默认关闭的职业技能均需保留各自设计边界，不列为本轮新遗漏。马铠仍有独立的来源防御路径。

## 核查范围与验证状态

本轮按输入、物品方块、制造、世界结构、生物、附魔、23 种旧原生状态效果和 16 种诅咒分类核对，并追踪上表指定调用链。来源运行时库存包含 667 个物品、221 个方块、85 条实体注册、38 个附魔、902 条合成注册及 28 条熔炼注册；这些计数不是现代玩法一致性的通过数量。

最初核查完成了源码审阅、局部发布包字节码复核和固定依赖源码对照，未逐条证明全部 902 条配方、全部原版继承方块及 85 条实体记录的所有行为一致，也未进行游戏实测。因此该核查不能证明清单以外已无遗漏。核查阶段仅新增本文档；后续已实施用户选中的七类功能，当前验证和交付状态以上方链接为准。
