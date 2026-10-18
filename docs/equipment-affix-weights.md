# 成长副词条权重与上限

开发者：NingMeng123。装备0级基础奖励概率为25%，每提升一级增加3个百分点，按刚升到的等级L计算 `min(100%, 25% + 3% × L)`；升到1级28%、5级40%、10级55%、15级70%、20级85%、25级100%。每次升级按该等级概率独立判定是否获得一阶副词条；未获得奖励也正常升级并扣除经验，没有补发或保底。成功时才对当前候选执行权重随机：`P(词条 | 本级有奖励) = 词条权重 / 当前有效候选权重总和`。

候选必须符合装备的现有附魔适用规则、效果已接入、不属于诅咒或经验修补，并且副词条等级低于该项上限。普通附魔不占用副词条上限，已有普通附魔和副词条不限制互斥候选。一次经验奖励连续升级时，每一级按其新等级更新概率并独立判定，选中项满级后立即退出；空池只停止副词条奖励，不停止装备升级。旧存档超过新上限的副词条保留等级和效果，不再抽取。

## 参考依据

本地参考文件：`.reference/[MITE不可能这么简单]MITE-ITE-2.0.28.jar`。

SHA-256：`b891dc495379e0d503207d1a44eb01a9661db42dba31831d77a32dd5162ff798`。

核对了 `ToolModifierTypes`、`ArmorModifierTypes`、`ModifierUtils.getModifierWithWeight`、两种 `getAllCanBeApplied*Modifiers`，以及 `ItemTrans.addExpForTool` 与工具/护甲的 `onItemLevelUp`。该JAR先排除不适用、满级项目，再按有效权重累计区间抽取；工具耐久为40/5，护甲耐久20/5，急速和锋利20/10，三种专项保护7/5，普通保护5/5，稀有单级项权重为4或2。

参考版本本身在有候选时每级直接抽取，没有独立的失败判定。25%基础值加每级3个百分点的奖励判定是本项目新增的平衡规则。这里延续已有附魔型副词条，直接对应项沿用参考权重和上限，其余按参考的稀有度档位扩展映射；不新增另一套ITE修饰效果，也不移植其保护修饰互斥规则。

## 当前映射

| 副词条ID | 权重 | 自身等级上限 |
| --- | ---: | ---: |
| `minecraft:unbreaking`，工具、武器、盾牌 | 40 | 5 |
| `minecraft:unbreaking`，护甲 | 20 | 5 |
| `minecraft:efficiency`、`sharpness`、`smite`、`bane_of_arthropods`、`power`、`impaling`、`density`、`breach`；`mite:true_flight` | 20 | 10 |
| `minecraft:protection` | 5 | 5 |
| `minecraft:fire_protection`、`blast_protection`、`projectile_protection`、`feather_falling` | 7 | 5 |
| `minecraft:knockback`、`punch`、`respiration`、`loyalty`、`piercing`、`quick_charge`、`lure`；`mite:stun`、`arrow_recovery`、`fertility`、`tree_felling`、`harvesting`、`butchering` | 10 | 5 |
| `minecraft:fortune`、`looting`、`fire_aspect`；`mite:fishing_fortune`、`poison`、`piercing` | 6 | 5 |
| `minecraft:depth_strider`、`soul_speed`；`mite:speed`、`regeneration` | 8 | 5 |
| `minecraft:frost_walker`、`riptide`、`wind_burst`；`mite:vampiric` | 4 | 5 |
| `minecraft:thorns`；`mite:disarming` | 5 | 5 |
| `mite:endurance` | 5 | 4 |
| `mite:free_action` | 3 | 4 |
| `mite:quickness`；`minecraft:sweeping_edge` | 10 | 10 |
| `minecraft:aqua_affinity` | 10 | 1 |
| `minecraft:flame`、`multishot` | 4 | 1 |
| `minecraft:silk_touch`、`infinity`、`channeling` | 2 | 1 |

表中同组省略的命名空间沿用组首的命名空间。规则只改变随机权重和成长上限，不扩大附魔的装备适用范围。例如头盔类和靴子类效果仍受原有部位限制。

已接入效果但未在映射表中的附魔，使用其注册权重；普通最高等级为1的项目成长上限为1，其余为普通最高等级的2倍、最多255。非正权重不进入候选。未来自定义效果仍须先接入效果桥。

例如仅剩效率20和耐久40时，奖励成功后的概率分别为1/3与2/3；若耐久达到5级而退出，下一次奖励成功必选效率。总奖励机会只由装备新等级决定，不会因候选减少而提高；例如升到5级时仍是40%。即使升到25级达到100%，候选耗尽时也没有副词条可奖励，但装备照常升级。

实际规则集中在 `GearAffixRules`，普通附魔定义和效果计算保持原有规则。
