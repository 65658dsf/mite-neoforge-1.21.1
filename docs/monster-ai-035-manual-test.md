# 035：怪物基础 AI 手动测试

开发者：NingMeng123。原作：Avernite。

本批补充的是来源怪物的基础寻敌、追踪、同类协作、近战、木门/活板门/栅栏门敲击、挖掘、智能僵尸、血月狂暴，以及来源远程任务、苦力怕引爆边界和仙人掌陷阱反应。主要接入普通/村民僵尸、亡魂等四类新增亡灵、各类骷髅、土/火元素、苦力怕与女巫；雪傀儡接入来源远程节拍。

这不是“全部怪物及全部特殊机制完成”的声明。猪人、蜘蛛、蠹虫等原本使用另一套旧 AI，未统一替换为这套目标任务；现代独有生物也不批量改成同一 AI。

## 1. 测试准备

保存后自行重启测试客户端，使用开启作弊的测试存档。每组测试选择一块不同的空地，不要在自己的建筑中测试拆墙和爆炸。以下命令没有 `NoAI`，生物需要正常执行 AI。附加 NBT 是为了构造明确的测试条件，不代表默认自然生成装备。

```mcfunction
/gamemode creative
/difficulty hard
/gamerule mobGriefing true
/gamerule doMobSpawning false
/gamerule doDaylightCycle false
/time set 13000
/tick rate 20
```

造好场地后，切换 `/gamemode survival` 才会被主动追踪。可先使用 `/effect give @s minecraft:resistance infinite 4 true`，防止初始生命过少影响观察；此效果不等于创造模式的目标免疫。每组结束先切回创造，再清理自己这批实体：

```mcfunction
/gamemode creative
/kill @e[tag=mite035]
```

所有下文命令生成的测试实体都带有 `mite035` 标签；清理只针对这些实体。需要长时间等待时用 `/tick rate 200`，但移动、攻击和爆炸观感保持 20 TPS。测试完恢复 `/tick rate 20`。`/time set` 只用于切换日历与月相，不能代替真实攻击、敲门或挖掘进度。

## 2. 追踪、障碍与近战

```mcfunction
/summon mite:wight ~8 ~ ~ {Tags:["mite035","mite035_wight"],PersistenceRequired:1b}
/summon mite:ghoul ~8 ~ ~5 {Tags:["mite035","mite035_ghoul"],PersistenceRequired:1b,HandItems:[{},{}]}
```

切换生存，先确认两者能看见并接近你。在亡魂与自己之间临时放完整石墙：普通追踪有约 60 个正常游戏刻的丢失视线宽限；尸鬼能够挖掘时，不能因为隔了一面墙就直接忘掉目标。完整石块不能被近战隔空打穿。

再把一处观察窗换成普通玻璃：怪物可以透过玻璃发现目标，但近战仍应被玻璃实体阻挡。换成遮光玻璃时应保留新版遮挡。树叶视觉按每块 50 阻抗累计；一层树叶与两层树叶不能被误当作随机一半概率。

靠近普通空手僵尸时，基础打击范围是到玩家脚底的三维距离 1.5 格；持工具的怪物仅取得该工具来源触及加成的 60%。攻击间隔约 20 刻，也就是 20 TPS 下约 1 秒。上下高度差也参与距离计算，不能仅因为身体框在竖直方向重叠就打到过远目标。

## 3. 智能僵尸与挖掘

推荐搭一个两格高、两侧不能绕行的通道，在中间放泥土墙。玩家与僵尸保持在墙的相对两边，距离尽量在 4 格以内。

```mcfunction
/summon minecraft:zombie ~6 ~ ~ {Tags:["mite035","mite035_dig"],PersistenceRequired:1b,HandItems:[{},{}],NeoForgeData:{mite_zombie_smart_035:1b}}
```

预期：智能僵尸能在墙体上产生分阶段裂纹，最终破坏允许挖的方块。若存在正常通路，应优先走路；脚下支撑柱、视线挡墙与普通追击不是同一个判断顺序。不要用一块可直接绕过去的墙来判断挖掘有没有启用。

挖掘初次等待 40 刻，随后阶段间隔以 `300×方块来源硬度` 为基准；进度从 -1 开始，到 10 才破坏，共 11 次阶段推进。普通空手智能僵尸挖硬度 0.5 的单块泥土，目标选定后约需 1540 刻，即 77 秒；实际还会叠加选点、导航、停顿与目标变化时间。尸鬼后续阶段间隔减半，土元素和有效工具也有不同加速，不要把所有生物计成同一个固定秒数。

下面两条分别用于对照持剑禁止挖掘、持镐启用对应有效材料：

```mcfunction
/item replace entity @e[tag=mite035_dig,limit=1] weapon.mainhand with minecraft:iron_sword
/item replace entity @e[tag=mite035_dig,limit=1] weapon.mainhand with minecraft:iron_pickaxe
```

剑、木棒/木棍与镰刀会阻止这条挖掘机制；向下挖需要锹。基岩、地幔、液体、超过来源触及范围或工具等级不足的材料不能被绕过。可以在负 Y 洞穴复测，不能重新出现“旧世界高度以下不工作”。

普通僵尸初始只有 1/8 是智能僵尸。以下命令明确生成非智能空手对照个体，然后由玩家造成一次实际伤害：

```mcfunction
/summon minecraft:zombie ~6 ~ ~ {Tags:["mite035","mite035_plain"],PersistenceRequired:1b,HandItems:[{},{}],NeoForgeData:{mite_zombie_smart_035:0b}}
/data get entity @e[tag=mite035_plain,limit=1] NeoForgeData.mite_zombie_smart_035
/damage @e[tag=mite035_plain,limit=1] 1 minecraft:player_attack by @s
/data get entity @e[tag=mite035_plain,limit=1] NeoForgeData.mite_zombie_smart_035
```

标记应由 false 变 true，保存重进后不重新抽取。最后把 `/gamerule mobGriefing false` 再测一次：不应继续产生实际地形破坏；恢复 true 后再测。

## 4. 木门、活板门与栅栏门

建一个封闭两格高通道，把玩家和尸鬼隔在关闭木门两边。用持木棒的尸鬼隔离普通挖墙任务，只观察敲门：

```mcfunction
/summon mite:ghoul ~6 ~ ~ {Tags:["mite035","mite035_door"],PersistenceRequired:1b,HandItems:[{id:"minecraft:wooden_sword",count:1},{}]}
```

确保它确实正在沿通道接近你，而不是在通道外绕行。普通空手或无加速工具的木门阈值是 1920 刻，20 TPS 下约 96 秒；木活板门、木栅栏门阈值是 480 刻，约 24 秒。血月狂暴将阈值减半，工具效率和土元素材质还会改变实际推进速度。可用 200 TPS 等待，实际墙钟时间大约缩短到十分之一，随后恢复 20 TPS。

预期：Hard 下阈值达到后破坏木门；Normal 下可以敲打，但这条来源敲门任务不应真正拆门。木栅栏门要求攻击目标是玩家，不能用村民作为等价诱饵。把木门换成铁门或 MITE 金属门后，不应仍以 1920/480 的木门任务直接拆除；金属材料的普通挖掘门槛另行判断。

声音复测：敲击只应听见僵尸敲木门及方块碎屑声，完成破门时听见破门声，不应再触发唱片音乐。旧版 `1010` 直接沿用曾误触发新版唱片事件，当前已改用新版明确的声音常量。

亡魂与隐形潜行者具有规划经过关闭木门的来源导航标记，但没有因此获得主动开门或破门能力；暗影不应被误设成同样的闭木门规划能力。

## 5. 血月狂暴与同类协作

用空手、无额外效果的同一只测试僵尸对比普通夜与血月夜：

```mcfunction
/summon minecraft:zombie ~8 ~ ~ {Tags:["mite035","mite035_frenzy"],PersistenceRequired:1b,HandItems:[{},{}],NeoForgeData:{mite_zombie_smart_035:1b}}
/time set 13000
/attribute @e[tag=mite035_frenzy,limit=1] minecraft:generic.attack_damage get
/attribute @e[tag=mite035_frenzy,limit=1] minecraft:generic.movement_speed get
/time set 757000
/attribute @e[tag=mite035_frenzy,limit=1] minecraft:generic.attack_damage get
/attribute @e[tag=mite035_frenzy,limit=1] minecraft:generic.movement_speed get
```

757000 是本实现日历第 32 日的 13000 刻。狂暴攻击增加“基础攻击值的一半”，不是把装备和附魔总伤害再整体乘 1.5；移动速度乘 1.2，挖掘阶段和破木门阈值减半。反复切换时间不能叠加出越来越多的加成。切回普通夜后应回落；第 128 日蓝月 `/time set 3061000` 也不能当成血月。末影人保留来源的血月狂暴例外。

召两只尸鬼和一只亡魂，测试受击者的同类协作：

```mcfunction
/summon mite:ghoul ~4 ~ ~ {Tags:["mite035","mite035_peer_a"],PersistenceRequired:1b}
/summon mite:ghoul ~6 ~ ~ {Tags:["mite035","mite035_peer_b"],PersistenceRequired:1b}
/summon mite:wight ~5 ~ ~3 {Tags:["mite035","mite035_other"],PersistenceRequired:1b}
```

建议让第二个玩家观察，或在玩家与未受击同伴之间留石墙，避免“大家本来就看见玩家”掩盖协作差异。对第一只尸鬼造成伤害后，空闲同类可接手攻击者；亡魂不能仅因为与尸鬼共享现代 Java 实现就被错误招募，已有目标的同伴不能被强制改成新目标。骷髅、女巫、苦力怕没有因此一律获得召集同类的能力。

## 6. 骷髅、女巫与雪傀儡远程

```mcfunction
/summon minecraft:skeleton ~16 ~ ~ {Tags:["mite035","mite035_archer"],PersistenceRequired:1b,HandItems:[{id:"minecraft:bow",count:1},{}]}
/summon mite:longdead ~16 ~ ~6 {Tags:["mite035"],PersistenceRequired:1b,HandItems:[{id:"mite:ancient_metal_bow",count:1},{}]}
```

切到生存，让骷髅先锁定你。在开阔地分别站在约 15、25、30 格处观察：来源 nominalRange=15 的远程任务实际使用 30 格距离上限；距离需要按三维计算。连续可射线约 20 刻后可停步射击，普通射击间隔固定约 60 刻（3 秒），血月/骨领主狂暴约 40 刻（2 秒）。超过射程会继续尝试接近，不能在任意距离无条件命中。

用两格高实墙挡住玩家上身，再改变下身观察口；上身射线被挡时会补查目标约四分之一身高，不应只因眼睛看不到就完全放弃所有射击机会。实体面前的实墙仍会阻挡发射。金属栏杆与普通玻璃板需要分别测：栏杆可供来源探线通过，玻璃板不能被当作金属栏杆忽略。树叶探射线不应消耗随机数来产生莫名抖动；实际箭撞到方块是另一条物理路径。

女巫 nominalRange=10、来源任务上限 20 格，普通间隔 60 刻；投出的药水种类、喝药等既有机制仍会影响实际观察。雪傀儡 nominalRange=10、上限 20 格，间隔 20 刻。这些与玩家隔墙测试分组进行，避免不同生物相互攻击干扰。

高柱测试：持弓骷髅的普通目标扫描没有找到玩家时，仍可找到头顶 6 至 20 层内、水平范围内且可见的玩家；超过 20 层不适用这条额外回退。能挖掘的户外观察者则是头顶 6 至 14 层、水平距离小于 4 格的另一条回退。

## 7. 苦力怕引爆边界与陷阱反应

```mcfunction
/summon minecraft:creeper ~8 ~ ~ {Tags:["mite035","mite035_creeper"],PersistenceRequired:1b}
/summon mite:infernal_creeper ~8 ~ ~6 {Tags:["mite035","mite035_infernal"],PersistenceRequired:1b}
```

分开测试，不能让第一只的爆炸提前伤到第二只。在有正常路径、满血时，普通苦力怕起爆距离比较的平方阈值是 4.5，约 2.12 格；受伤且仍有路径时阈值是 9，约 3 格；导航已经结束/无路时阈值是 16，约 4 格。烈焰苦力怕对应“起爆距离平方阈值”乘 2，不能误写为实际距离乘 2。判断用严格小于边界；精确小数边界由自动测试覆盖，手动重点看不同场景是否明显混成一个固定距离。

进入膨胀后，能维持引爆的不一定是最初缓存的那位玩家。普通苦力怕的维持距离平方为 `16 / clamp(剩余生命比例,0.4,1)`，烈焰苦力怕基数为 36。受伤后可维持更远的引爆；用完整石墙挡住所有符合条件的玩家后应退回收缩状态。特别注意透明窗口、身体高低观察口与墙角。

仙人掌部分作为补测：在沙子上搭仙人掌陷阱，实际接触致死累计到至少两次后，让苦力怕再接触。它有来源概率记住危险约 120 刻，附近仍有仙人掌时可强制进入膨胀。该状态有随机触发，不要以一次接触没触发判断失败。陷阱计数保存在沙子支撑位置；拆掉支撑/底部仙人掌后重建不能无条件继承旧记录，顶端仙人掌随机更新也可能逐渐降低计数。

## 8. 着火趋树、扩火与冰雪

保持 `mobGriefing=true`、`doFireTick=true`。搭一根地面原木柱，站在较低原木上方 3 至 8 格内的平台，让一只正常 AI 僵尸在附近着火。可用 `/data merge entity @e[tag=mite035_plain,limit=1] {Fire:1200s}` 给上述对照僵尸持续着火。它抽中趋树检查且能找到路径时，会朝树干移动并得到 100 刻扩火强化；范围内的树干按来源螺旋及每列从低到高取前 8 个，不能期待总选几何最近的一根。

普通着火扩火概率低，需多次观察；强化期间概率提高。分别关闭 `doFireTick`、关闭 `mobGriefing` 后，不能继续实际放火或融冰。火元素和熔岩土元素也有各自的来源扩火概率。

让着火僵尸直接站在冰或雪上：成功热脉冲可把冰变水，雪块变七层雪，雪层逐层减少。实体必须落地、没有骑乘且脚底真实接触支撑面；悬空、隔有细缝或骑在另一只实体上，不应仅因接近冰就把它融化。涉及火焰和树木的测试请使用专门场地，结束后恢复自己的游戏规则。

## 9. 保存、联机和反馈

保留一只已经变聪明的普通僵尸、一只正在挖掘的尸鬼和一个仍有存活目标的怪物，记录标签与下列数据，保存退出重进再比对：

```mcfunction
/data get entity @e[tag=mite035_plain,limit=1] NeoForgeData.mite_zombie_smart_035
/data get entity @e[tag=mite035_ghoul,limit=1] NeoForgeData.mite_digging_031
/data get entity @e[tag=mite035_wight,limit=1] mite_monster_target_035
```

不存在对应数据时命令报“未找到”是正常的，例如当时未在挖掘、没有有效目标，或已清理该标签实体。当前目标 UUID 只尝试恢复附近已加载的实体；不能隔着极远距离或跨维度强行找回。挖掘裂纹和进度不能因第二个客户端看见、保存重进或取消后重启就凭空完成、复制掉落。旁观玩家应能看到一致的手持装备、敲门/挖掘动画和苦力怕膨胀结果。

结束后清理测试实体、恢复 TPS 与自己需要的世界设置，并用 `/effect clear @s minecraft:resistance` 清除测试抗性。

可以分组反馈：**实体 ID / 维度及 Y / 难度 / mobGriefing / 普通夜或血月 / TPS / 手持装备 / 场地和距离 / 实际结果**。无需一次测完所有组；精确 tick、概率与源码边界由本批自动验证配合，手动重点确认寻路、动画、保护规则和组合模组下的真实体验。
