# R196 战斗与生物规则

维护者：NingMeng123。下表为静态已核对的核心行为；现代功能尚未实现。完整实体声明索引位于 `spec/r196-entity-declarations.json`，它不代表最终出生属性。

| 规则 | 行为 | 来源 |
| --- | --- | --- |
| damage_absolute | 非正伤害返回0；绝对伤害直接返回原值，不进入防御计算。 | `Damage.java#applyTargetDefenseModifiers` |
| damage_blocking | 玩家格挡且伤害不绕过普通护甲时，伤害减半并至少为1；持有工具时另按格挡后伤害及工具战斗耗损系数损伤工具。 | `Damage.java#applyTargetDefenseModifiers` |
| damage_defense | 总防御减去穿透附魔等级比例×5，最低0。通常生命伤害=max(格挡后伤害-有效防御,1)；护甲耐久损耗在生命伤害计算前处理。 | `Damage.java#applyTargetDefenseModifiers` |
| damage_full_block | 目标为玩家且有效防御>=伤害时，进行 floor(有效防御-伤害)+1 次独立20%判定，任何一次成功则伤害为0。不能只用固定20%或原版百分比减伤替代。 | `Damage.java#applyTargetDefenseModifiers` |
| armor_durability_defense | 非玩家装备拥有者固定取0.5防御倍率；玩家护甲仅剩最后耐久时为0；其余取min(2-2*damage/maxDamage,1)，使用该物品堆的品质相关maxDamage。 | `wh.java#getDamageFactor` |
| armor_quality | 护甲实际支持品质；材料品质上限与耐久倍数见r196-equipment.json。 | `wh.java#hasQuality` |
| mob_frenzy | 狂暴额外增加基础攻击伤害属性的50%；之后再处理武器对生物的附魔加成、击退及火焰。 | `tm.java#attackEntityAsMob` |
| enchantment_stun | 成功负面影响 EntityLivingBase 后（此分支无死亡排除），stunLevel > Math.random()*10 时施加50*等级 tick、放大等级5*等级的麻痹/减速效果；需按原效果语义映射现代效果。 | `tm.java#attackEntityAsMob` |
| enchantment_vampiric | 仅正的实际掉血且目标生物学存活时计算；Math.random()*10 > 附魔等级则无转移，否则 max(floor(inflictedDamage*0.5*random),1)。 | `aaw.java#getVampiricTransfer` |
| enchantment_speed | 已穿装备中最高速度附魔等级使速度倍率为1+level*0.05。 | `aaw.java#getSpeedModifier` |
| ghoul_attributes | 显式跟随范围40、移动速度0.28、攻击5；40不是生命值。其生命值需沿父类确定。 | `EntityGhoul.java#az` |
| ghoul_attack | 目标存活且实际掉血并属于EntityLivingBase时施加50 tick、等级5的麻痹/减速效果。 | `EntityGhoul.java#attackEntityAsMob` |
| wight_immunity | 只有带火焰属性、熔岩、银属性或魔法属性的伤害可以通过该特定免疫判定。 | `EntityWight.java#isImmuneTo` |
| wight_drain | 玩家实际掉血且仍存活时40%概率吸取经验；原始吸取量max((玩家等级+1)*10,20)，再经玩家抗性函数减免。 | `EntityWight.java#attackEntityAsMob` |
| wood_spider | 显式生命6、移动速度0.8、攻击1。白天也不和平。 | `EntityWoodSpider.java#az` |
| wood_spider_poison | 实际掉血且存活时，精确木蜘蛛类施加240 tick中毒；黑寡妇变体施加960 tick，放大等级均0。 | `EntityWoodSpider.java#attackEntityAsMob` |
| hellhound | 显式生命20、移动速度0.4、攻击4；不受火和岩浆伤害。 | `EntityHellhound.java#az` |
| hellhound_fire | 目标仍存活且受到负面影响时40%概率点燃1+nextInt(8)秒。 | `EntityHellhound.java#attackEntityAsMob` |
| dire_wolf | 未驯服生命16，已驯服24，攻击5。 | `EntityDireWolf.java#az` |
| dire_wolf_aggro | 服务端成年、未驯服、非求偶、非蓝月夜时，每次更新0.004概率攻击4格内最近可伤害玩家。 | `EntityDireWolf.java#l_` |
| shadow_immunity | 只有银、魔法、阳光属性的伤害通过特定免疫；不受普通火/熔岩伤害。 | `EntityShadow.java#isImmuneTo` |
| shadow_regeneration | 服务端阳光下造成1000点阳光伤害；否则每40 tick，在floor((0.4-brightness)*10)>0时治疗该值，并执行破坏附近光源逻辑。 | `EntityShadow.java#c` |
| infernal_creeper | 继承苦力怕后爆炸强度字段bs乘2；普通防御在父类基础加2，绕过普通护甲时不加；免疫火和岩浆，经验为父类3倍。 | `EntityInfernalCreeper.java#<init>` |

## 边界

- 只审计部分共用伤害路径与新增实体；全部投射物、AI、掉落、生成和其他实体仍需逐条补全。
- 属性声明目录不能作为最终运行时属性表。
- 效果ID由原版注册语义映射，不直接复用1.6.4数字ID。

