# MITE: Modern 0.3.8

November 21, 2026. Developed by NingMeng123.

## Changes

- Added per-material workbenches and grouped their recipes in JEI. Crafting time now updates with the player's current level, and the level-based melee reach bonus uses the adjusted progression interval.
- Expanded underworld terrain with mantle patches on the three lower levels, mantle and a Nether entrance at Y=0, and deepslate in negative strata. Added configurable Nether ore generation.
- Improved Nether and overworld hostile spawning, strengthened piglin brutes, prevented overworld piglin brutes from zombifying, and adjusted monster equipment tiers by world age.
- Fixed underworld home portals when the spawn chunk is not loaded, quality-equipment durability bars, and durability loss when a tool breaks an incompatible block.
- Improved compatibility for higher-tier smithing tables and added timed netherite-ingot crafting with a mithril-workbench requirement.
- Fixed timed crafting and smithing progress during inventory sorting and result collection. Completed smithing now consumes ingots only from the material slot; when the result cannot be delivered, progress stays complete until space is available. Reduced ordinary and panic animal-flee speed.
- Rebalanced Simply Swords' built-in weapon traits, including narrower proc ranges for chakrams, longswords, twinblades, rapiers, spears, glaives, greataxes, scythes and halberds. Greathammer armor sunder now stacks by 1% per hit up to 10%; legacy saved values are clamped consistently in tooltips and combat.
- Hostile monster health and attack now grow with world age by default, including previously spawned monsters. Added separate configurable formulas and previews for health and attack growth; Withers and Ender Dragons remain excluded.

Build file `MITE-Modern-0.3.8.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the mod on both client and server for multiplayer.

## 中文更新日志

- 新增按材质区分的工作台，并在 JEI 中分组展示对应配方。制作耗时会按玩家当前等级实时更新，等级带来的近战距离加成也调整了成长间隔。
- 扩展地底地形：下方三层生成地幔斑块，Y=0 层新增地幔与下界入口，负数层改用深板岩；新增可配置的下界矿石生成设置。
- 改进下界与主世界敌对生物自然刷新，强化猪灵蛮兵并阻止其在主世界僵尸化；怪物装备档位现在随世界天数调整。
- 修复出生区块未加载时主世界回家门失效、品质装备耐久条显示异常，以及工具破坏不匹配方块时错误消耗耐久的问题。
- 改进高阶锻造台兼容性，并新增带制作计时和秘银工作台门槛的下界合金锭配方。
- 修复整理背包和领取成品时制造/锻造进度异常的问题。装备锻造仅从材料槽扣除锭；成品暂时无法交付时，进度保持完成状态，腾出空间后即可领取。降低动物普通逃跑与恐慌逃跑速度。
- 调整简易刀剑的内置特性数值范围，涉及飞轮、长剑、双刃剑、刺剑、长矛、长柄刀、巨斧、镰刀和戟。巨锤每次命中使目标护甲降低 1%，最多叠加至 10%；旧存档中的特性值在提示与战斗结算时统一按新范围处理。
- 敌对怪物生命和攻击力默认随世界天数成长，已生成的怪物也会更新。新增可分别配置和试算生命、攻击成长公式；凋灵和末影龙不参与。

构建文件为 `MITE-Modern-0.3.8.jar`。

运行环境为 Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。多人游戏的客户端与服务端均须安装本模组。

开发者 NingMeng123。
