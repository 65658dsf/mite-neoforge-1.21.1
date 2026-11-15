# MITE: Modern 0.3.4

November 14, 2026. Developed by NingMeng123.

## Changes

- Restored R196-based damage values for core weapons and retuned weapon and tool attack speeds and melee reach. Updated Simply Swords damage, attack speed and reach balance.
- Added level-based melee reach. Every 5 current experience levels grant 0.1 blocks of extra reach, up to 1 block at level 50. The bonus tracks the player's current level and applies to standalone combat and Better Combat. Item tooltips show the effective melee reach.
- Rebalanced armor points and toughness across material tiers and chainmail, using R196 protection as the calibration reference for modern combat.
- Updated multi-enchantment selection to use the remaining enchanting energy, with up to 3 enchantments. Fixed anvil handling of material-based and configured enchantment caps, and added support for ITE's material-specific compatibility rules.
- Unified Vibranium workbench crafting at a 25-tick manufacturing period, equivalent to 1.25 seconds at 20 TPS. This covers recipes without a material requirement, quality crafting and compatible automated crafting beside the workbench.
- Unified the first-period health growth reference across monsters, using 20 health by default. Each monster's native starting health forms the starting point of its growth calculation.
- Food exhaustion consumes the displayed saturation layer first, including the seed buffer and nutrition, then the hunger bar. Starvation begins after both food reserves are empty.
- Aligned Dynamic Crosshair 9.11 entity attack indicators with actual melee reach, aiming and obstruction checks. The cached indicator refreshes as attack eligibility changes.

Build file `MITE-Modern-0.3.4.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the mod on both client and server for multiplayer.

## 中文更新日志

- 恢复本体武器基于 R196 的伤害数值，调整武器与工具的攻击速度和近战距离，并重新平衡 Simply Swords 的伤害、攻速与距离。
- 新增经验等级近战距离加成。当前等级每满 5 级增加 0.1 格，50 级及以上最多增加 1 格。加成随当前等级实时变化，适用于独立战斗与 Better Combat，物品提示同步显示有效近战距离。
- 参考 R196 的防护表现，重新平衡各材质护甲与锁链甲的护甲值和韧性。
- 调整多词条附魔选取，按剩余附魔能量最多生成 3 项附魔，修正铁砧对材质附魔上限和自定义上限的处理，并支持 ITE 的材质兼容规则。
- 振金工作台的制造周期统一为 25 tick，20 TPS 下为 1.25 秒，覆盖无材质门槛配方、品质制造及邻接工作台的兼容自动制造。
- 统一所有怪物的首周期生命成长参考值，默认为 20 点生命。各怪物以自身原生出生血量作为成长起点。
- 优先消耗金色饱食层中的种子缓冲与营养，再消耗鸡腿饥饿条；两层都耗尽后开始积累饥饿伤害。
- 修复 Dynamic Crosshair 9.11 的实体攻击提示，按实际近战距离、瞄准点和遮挡判断，跨越可攻击边界时刷新缓存。

构建文件为 `MITE-Modern-0.3.4.jar`。

运行环境为 Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。多人游戏的客户端与服务端均须安装本模组。

开发者 NingMeng123。
