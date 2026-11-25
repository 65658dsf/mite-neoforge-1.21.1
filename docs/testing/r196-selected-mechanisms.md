# 本轮 14 项 R196 机制手动测试

开发署名：NingMeng123。本文给出待玩家执行的测试步骤，不把编译、自动测试或注册成功当作客户端手测结果。来源细节见 [近战与触及](../player-combat-reach.md)、[采集门槛](../source-mining-requirements.md)、[便携方块](../portable-blocks.md)、[移动食耗](../movement-hunger.md)、[世界物理](../world-physics.md)。

## 测试准备

请在开启指令的独立普通测试存档中执行。下方会清空测试背包、摆放方块、修改经验、召唤 TNT 和杀死玩家，不适合直接在正式生存基地执行。测试客户端可以继续加载 JEI、Jade、AppleSkin 等可选扩展，MITE 本身不依赖这些扩展。

建立一个平坦测试台，统一使用 20 TPS：

```mcfunction
/gamemode creative
/difficulty normal
/tick rate 20
/gamerule doMobSpawning false
/gamerule doDaylightCycle false
/gamerule doWeatherCycle false
/gamerule naturalRegeneration false
/gamerule keepInventory true
/gamerule doImmediateRespawn false
/time set 6000
/weather clear
/fill -8 100 -8 12 100 8 minecraft:stone
/tp @s 0.5 101 0.5
/clear @s
/experience set @s 0 levels
/mite food set saturation @s 0
/mite food set reserves @s 20 20
/effect clear @s
/gamemode survival
```

`reserves` 会受当前等级食物上限约束，低等级没有十格食物条是正常现象。F3+H 可打开物品耐久的高级提示。长时间项目可用 `/tick rate 200` 加速，测试实际秒数、攻击冷却和移动食耗时应保持 `/tick rate 20`；完成后用 `/tick unfreeze`、`/tick rate 20` 恢复正常运行。

## 1. 等级影响近战伤害

生成一个不主动攻击的高生命目标，手持无附魔铁剑：

```mcfunction
/summon minecraft:pig 1.5 101 0.5 {Tags:["mite_melee_test"],NoAI:1b,PersistenceRequired:1b}
/attribute @e[tag=mite_melee_test,limit=1] minecraft:generic.max_health base set 1000
/data merge entity @e[tag=mite_melee_test,limit=1] {Health:1000.0f}
/give @s minecraft:iron_sword
/experience set @s 0 levels
/data get entity @e[tag=mite_melee_test,limit=1] Health
```

站稳、不跳跃、等攻击冷却完全恢复后只砍一次，再读取目标 `Health`。记下这次伤害为 B。依次执行 `/experience set @s 100 levels` 和 `/experience set @s 250 levels`，每次重置目标生命为 1000，用同一把剑、同样姿势各砍一次。

预期：100 级为约 `1.5×B`，250 级为约 `2.25×B`，超过 200 级继续生效。可在 0 级时执行 `/experience add @s -10 levels` 测负等级，−10 级基础倍率为 0.8。以实际等级显示为准；比较前不要跳劈、疾跑或更换附魔。

来源顺序是暴击基础→等级→银制打亡灵→钝器打骷髅→附魔→悬浮液体减伤。新版攻击冷却、暴击事件与横扫保留，不能把连点未冷却的伤害差异算作等级错误。

完成后 `/kill @e[tag=mite_melee_test]`，再把经验重设为 0。

## 2. 完整触及距离

在测试台近处放置石头和泥土，准备铁镐、铁锹、木棍：

```mcfunction
/setblock 2 101 0 minecraft:stone
/setblock 2 101 2 minecraft:dirt
/give @s minecraft:iron_pickaxe
/give @s minecraft:iron_shovel
/give @s minecraft:stick
```

分别空手、拿木棍、拿铁镐面对石头，缓慢后退，观察准星选中与真实点击；再对泥土用铁锹与铁镐对照。不要只看准星高亮，实际开始采掘、持续采掘和右键操作都要试。

预期：生存方块基础触及为 2.75，只有对当前目标有效且满足采集等级的工具才增加触及；木棍不能为挖石头增加距离。换成错误工具或后退后不能继续完成原先的远距离采掘。目标中心与眼睛下方两个测试点参与判定，不能只按脚下 X/Z 距离量数。

近战基础 1.5、实体交互基础 2.5；木棍/骨头近战加 0.5，但不增加实体右键交互。用无 AI 的猪近距离对照攻击和右键，能右键的位置可能还不能攻击。双方脚下高差也参与判定；创造模式三类基础触及固定 5。新版三叉戟/重锤加成、第三方范围属性属于明确现代适配。

## 3. 空手反伤、虚弱攻击

先恢复食物并准备近身、无 AI 的烈焰人：

```mcfunction
/mite food set saturation @s 0
/mite food set reserves @s 20 20
/effect give @s minecraft:instant_health 1 10 true
/summon minecraft:blaze 1.5 101 0.5 {Tags:["mite_hand_test"],NoAI:1b,PersistenceRequired:1b}
```

空手打一次应产生手部损伤；拿木棍或合格工具再试，不应发生该手伤。拿弓、泥土或仅副手拿木棍不保护主手。烈焰人与火元素是确定性的 100% 手伤对照；普通怪物仅在追击攻击者且非弱击时有 12.5% 概率，不能以一两次没反伤认定异常。

清理目标：`/kill @e[tag=mite_hand_test]`。再生成第 1 项的高生命猪，空手后执行：

```mcfunction
/mite food set saturation @s 0
/mite food set reserves @s 0 0
```

立刻空手砍一下并读取猪的生命：预期只有较弱击退，没有生命伤害；换主手木棍后允许正常造成伤害。测试结束立即恢复 `reserves 20 20`，避免饿伤干扰。生命严格小于 2 或基础攻击属性严格小于 1 也是无保护主手弱击条件；不要求故意把玩家打到濒死才能完成本项。

## 4. 必需工具的破坏门槛

```mcfunction
/setblock 1 101 0 minecraft:stone
/setblock 1 101 2 minecraft:cobblestone
/setblock 1 101 4 minecraft:netherrack
/give @s minecraft:diamond_shovel
/give @s minecraft:diamond_pickaxe
/give @s mite:flint_hatchet
```

空手、木棍、钻石锹分别长按石头/圆石/下界岩：预期方块本体没有破坏进度，而非“可以挖掉但没掉落”。换合格镐应能正常采集。设为 100 级或加急迫再试，错误工具仍不能绕过资格。

补测整块玻璃与玻璃板：

```mcfunction
/setblock 2 101 0 minecraft:glass
/setblock 2 101 2 minecraft:glass_pane
/setblock 2 101 4 minecraft:purple_stained_glass
/setblock 2 101 6 minecraft:tinted_glass
```

这些玻璃都可空手或持镐破坏。遮光玻璃空手就会掉落自身，其他三种需用精准采集工具才会掉落自身。再空手测试潜影盒、泥坯、滴水石锥、按钮、铁轨、活塞、萤石和煤炭块，确认都有破坏进度。

## 5. 便携方块规则

在测试台放床、箱子、漏斗、梯子、炉子或工作台，分别用空手与无附魔下界合金镐拆卸：

```mcfunction
/give @s minecraft:chest
/give @s minecraft:hopper
/give @s minecraft:ladder
/give @s minecraft:furnace
/give @s minecraft:red_bed
/give @s minecraft:netherite_pickaxe
/give @s mite:flint_crafting_table
```

预期：0 级空手即可拆这些来源便携方块；换高级工具不额外加速，工具也不扣耐久。50 级正常能量/站地时拆卸比 0 级快一倍；水中、空中或低能量仍有最低搬运速度，不会慢到无法拆卸。箱内物品按正常容器散落，不会自动打包进箱子。

自己放置的保险箱给主人便携资格；其他玩家的保险箱仍要求原有材料等级。刷怪笼、试炼刷怪笼、宝库、木桶、潜影盒不是“有方块实体就便携”，不要据它们的外观推断豁免。

## 6. 移动消耗食物

保持 20 TPS、关闭饥饿药水，清空原版饱和缓冲但填满食物储备。用 AppleSkin 或指令观察：

```mcfunction
/effect clear @s
/mite food set saturation @s 0
/mite food set reserves @s 20 20
/mite food status @s
/data get entity @s foodExhaustionLevel
```

分别走相同距离、潜行、疾跑；在水中水平游动与按住上浮；原地站立跳跃；主动前划、倒划与松键漂船。每轮先记录起始数据，做同样时长/距离，并用静止同样时长的记录对照基础代谢。

来源距离费用：地面走/潜行为每格约 0.01，疾跑 0.05，水中 0.015；攀爬只算上升量，每上升一格 0.1；划船每刻为 `abs(前进输入)×0.01`，乘客与无输入漂流不收该项；水/熔岩中主动上浮额外每刻 0.01。纯空中位移和普通跳跃没有现代跳跃附加费用。

预期：疾跑距离费用为行走的五倍；水中按住上浮有额外消耗；松开上浮、重登、换维度后不会因旧输入一直扣费。基础代谢、战斗、恢复仍会消耗，不要求整轮的总数恰好等于纯移动公式。

## 7. 普通睡眠

用石头密封安全房，留室内空间，再给自己床并在室内实际放置：

```mcfunction
/gamemode creative
/fill -3 100 -3 3 104 3 minecraft:stone
/fill -2 101 -2 2 103 2 minecraft:air
/tp @s 1.5 101 0.5
/give @s minecraft:red_bed
/experience set @s 100 levels
/mite food set reserves @s 20 20
/gamerule naturalRegeneration true
/time set 15000
/gamemode survival
```

床需要两格地面且顶部留空。安全房内右键床，预期约 100 刻逐渐失去意识、画面变黑，随后按实际经过的睡眠时间结算代谢并推进到天亮；醒来渐渐恢复视野。完整推进至少 6000 刻可获得“休息充足”。不要只在露天放床验证正常睡眠，露天拒绝是本项规则之一。

依次做负例：白天 `/time set 6000` 卧床不应直接跳到第二天；露天床应提示需要庇护；营养储备为零、入睡前中毒、怪物有路径接近床、附近僵尸正在挖掘均应阻止或中断睡眠。可在床外开门并召唤一只正常 AI 僵尸检查威胁，不能用 `NoAI` 验证寻路威胁。

普通夜晚让两名存活且非旁观玩家留在主世界。执行 `/gamerule playersSleepingPercentage 50` 后让一人上床，预期该玩家深睡后跳夜。改为 51 或 100 后重复测试，预期两人都深睡才跳夜。醒着的玩家保持原有营养和状态效果。测试结束后恢复原来的游戏规则。

血月拒绝推进睡眠。第 32 天可用 `/time set 759000` 检查，结束后用 `/time set 15000` 回到普通测试时间。地底与下界拒绝入睡，床保持完整。

## 8. 死亡倒计时与自动重生

普通非极限模式、保持 20 TPS：

```mcfunction
/tick rate 20
/gamerule doImmediateRespawn false
/kill @s
```

第一轮不点击重生，观察死亡界面的自动重生倒计时从 120 秒下降，结束后自动走正常重生流程。此处 120 是按游戏刻推进的秒，TPS 加速后真实等待会缩短；不能拿 200 TPS 下约 12 秒的结果认定倒计时过快。

第二轮再次死亡，在原版重生按钮可点后立即点击：预期可以提前手动重生，不需要等满 120 秒。倒计时是自动重生计时器，不是手动重生锁。可补测死亡中退出重登后继续剩余计数、床出生点、已有死亡经验债务及延迟经验球；极限模式不应被自动重生绕过。多人其余有效玩家都深睡时计时暂停，需另开联机对照。

## 9. MITE 成就体系

先按 E 解锁首项，再按 L 打开现代进度界面；共注册 62 个来源条目，较深的未解锁分支按原版进度界面的可见性规则逐步显示。显示父子关系不代替真实触发条件。下表列出真实资源路径和 `/miteachievements` 接受的查询键，查询键有大小写之分。

| 成就 | 完整资源路径 | 查询键 |
|---|---|---|
| 打开物品栏 | `mite:r196/open_inventory` | `openInventory` |
| 拾枝者 | `mite:r196/stick_picker` | `stickPicker` |
| 锋利的开端 | `mite:r196/cutting_edge` | `cuttingEdge` |
| 取得原木 | `mite:r196/mine_wood` | `mineWood` |
| 搭建工作台 | `mite:r196/build_work_bench` | `buildWorkBench` |
| 制作铲子 | `mite:r196/build_shovel` | `buildShovel` |
| 金属颗粒 | `mite:r196/nuggets` | `nuggets` |
| 更好的工具 | `mite:r196/better_tools` | `betterTools` |
| 第一把金属镐 | `mite:r196/build_pickaxe` | `buildPickaxe` |
| 石质熔炉 | `mite:r196/build_furnace` | `buildFurnace` |
| 取得铁锭 | `mite:r196/acquire_iron` | `acquireIron` |
| 耕作的开始 | `mite:r196/build_hoe` | `buildHoe` |
| 研磨面粉 | `mite:r196/flour` | `flour` |
| 烤制面包 | `mite:r196/make_bread` | `makeBread` |
| 制作蛋糕 | `mite:r196/bake_cake` | `bakeCake` |
| 开采黑曜石 | `mite:r196/build_better_pickaxe` | `buildBetterPickaxe` |
| 在铁轨上 | `mite:r196/on_a_rail` | `onARail` |
| 制作木棒 | `mite:r196/build_club` | `buildClub` |
| 击败怪物 | `mite:r196/kill_enemy` | `killEnemy` |
| 取得皮革 | `mite:r196/kill_cow` | `killCow` |
| 飞天猪 | `mite:r196/fly_pig` | `flyPig` |
| 狙击骷髅 | `mite:r196/snipe_skeleton` | `snipeSkeleton` |
| 黑曜石熔炉 | `mite:r196/obsidian_furnace` | `obsidianFurnace` |
| 秘银时代 | `mite:r196/mithril_ingot` | `mithrilIngot` |
| 钻石！ | `mite:r196/diamonds` | `diamonds` |
| 进入地底世界 | `mite:r196/portal` | `portal` |
| 深入下界 | `mite:r196/portal_to_nether` | `portalToNether` |
| 以其人之道还治其人之身 | `mite:r196/ghast` | `ghast` |
| 烈焰棒 | `mite:r196/blaze_rod` | `blazeRod` |
| 本地酿造厂 | `mite:r196/potion` | `potion` |
| 进入末地 | `mite:r196/the_end` | `theEnd` |
| 结束了？ | `mite:r196/the_end2` | `theEnd2` |
| 制作附魔台 | `mite:r196/enchantments` | `enchantments` |
| 强力一击 | `mite:r196/overkill` | `overkill` |
| 知识的书架 | `mite:r196/bookcase` | `bookcase` |
| 觅食者 | `mite:r196/seeds` | `seeds` |
| 品尝生鸡蛋 | `mite:r196/eggs` | `eggs` |
| 制作斧子 | `mite:r196/build_axe` | `buildAxe` |
| 制作镰刀 | `mite:r196/build_scythe` | `buildScythe` |
| 皮革防护 | `mite:r196/wear_leather` | `wearLeather` |
| 制作锁链甲 | `mite:r196/build_chain_mail` | `buildChainMail` |
| 全套金属防具 | `mite:r196/wear_all_plate_armor` | `wearAllPlateArmor` |
| 艾德曼全甲 | `mite:r196/wear_all_adamantium_plate_armor` | `wearAllAdamantiumPlateArmor` |
| 黏土烤炉 | `mite:r196/build_oven` | `buildOven` |
| 找到燧石 | `mite:r196/flint_finder` | `flintFinder` |
| 点亮黑暗 | `mite:r196/build_torches` | `buildTorches` |
| 施肥 | `mite:r196/soil_enrichment` | `soilEnrichment` |
| 休息充足 | `mite:r196/well_rested` | `wellRested` |
| 航海者 | `mite:r196/seaworthy` | `seaworthy` |
| 远行者 | `mite:r196/explorer` | `explorer` |
| 启迪 | `mite:r196/enlightenment` | `enlightenment` |
| 末影人的羡慕 | `mite:r196/runegate` | `runegate` |
| 制作钓鱼竿 | `mite:r196/fishing_rod` | `fishingRod` |
| 烤鱼 | `mite:r196/cook_fish` | `cookFish` |
| 植物医生 | `mite:r196/plant_doctor` | `plantDoctor` |
| 培育菌丝 | `mite:r196/make_mycelium` | `makeMycelium` |
| 巨型蘑菇 | `mite:r196/supersize_me` | `supersizeMe` |
| 绿宝石！ | `mite:r196/emeralds` | `emeralds` |
| 地狱岩熔炉 | `mite:r196/netherrack_furnace` | `netherrackFurnace` |
| 艾德曼时代 | `mite:r196/adamantium_ingot` | `adamantiumIngot` |
| 精致饮食 | `mite:r196/fine_dining` | `fineDining` |
| 晶体破坏者 | `mite:r196/crystal_breaker` | `crystalBreaker` |

建议先在新存档用真实动作测试：按 E、拾起地上的木棍、真正制作一把燧石短斧/小刀、真正烤出并拿走面包/鱼、穿皮甲、进入地底世界。制造类仅 `/give` 或看合成预览不应解锁，必须完成制作/加工事务。刷怪/装备/结构先前的进度门槛仍保留。

```mcfunction
/miteachievements stickPicker
/miteachievements buildClub
/miteachievements portal
/mitebook 1
```

`/miteachievements` 查询世界首位达成者及日期；未达成时显示尚无人完成。个人撤销进度不会抹掉世界首次记录，保存重进和另一位玩家查询应保持同一记录。命令人工授予仅用于界面/路径核对，不能作为真实动作触发已通过的证据。

管理员 `/mitebook 1` 到 `/mitebook 9` 提供九本真实来源参考书。每本首次打开应增加 100 经验，重复阅读同一本不重复加经验；读齐九种应触发 `mite:r196/enlightenment`。保存重进、死亡后阅读记录保持。九本是同一种现代成书的不同身份，不是九个新注册物品。普通获取路径是满足日期/书架世界进度后新生成结构的奖励，不能仅凭给书命令证明结构奖励已测。

## 10. 掉落物抗爆与碎裂

切创造，站在远处，在独立爆破区召唤物品和固定位置 TNT：

```mcfunction
/gamemode creative
/fill 35 100 -6 48 100 6 minecraft:stone
/summon minecraft:item 41.5 103 0.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:iron_ingot",count:3}}
/summon minecraft:item 40.5 103 1.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:redstone",count:3}}
/summon minecraft:item 39.5 103 0.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:diamond",count:2}}
/summon minecraft:item 40.5 103 -0.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:emerald",count:3}}
/summon minecraft:item 41.5 103 1.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:flint",count:4}}
/summon minecraft:item 45.5 103 0.5 {Tags:["mite_blast_test"],PickupDelay:32767,Item:{id:"minecraft:iron_sword",count:1}}
/tp @s 55.5 104 0.5
/summon minecraft:tnt 40.5 103 0.5 {Fuse:40s,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}
```

预期：铁锭和红石保留并被抛出；每个钻石/绿宝石/燧石分别变成一个对应碎片，总数为 2/3/4。碎片没有原物品的测试标签，观察时应看全体掉落物。铁剑不享受“全金属零件”免疫，在这类较远位置应扣耐久；更近位置可直接损坏。不要据工具靠近炸没了判断未实现磨损。

```mcfunction
/execute positioned 40.5 103 0.5 as @e[type=minecraft:item,distance=..20] run data get entity @s Item
```

本项保留现代耐久事件、耐久附魔和对应方块掉落；未映射的第三方物品仍用自身处理。整个测试不要求低抗性/土壤完全符合爆炸前的旧原版形状。

## 11. 地底泉水、熔岩泉

必须检查尚未生成的地底区块：

```mcfunction
/gamemode spectator
/execute in mite:underworld run tp @s 24000 190 24000
```

如果该区域已经探索过，改成另一个未探索坐标。观察洞穴石壁：应能出现符合石壁洞口的水泉/熔岩泉；天然菌丝所在及周边区块回避泉候选。玩家后来摆放菌丝不会把整片区域标为天然生成来源。

每区块是 70 次尝试，不是 70 个成功泉眼；要求上下石头、水平三石一空、目标为空气或石头，很多候选会失败。源局部高度使用嵌套抽样，已接入新版地底高度映射；不是在所有 Y 均匀撒泉。独立现代随机流不承诺旧版同种子落点一致，已有区块不会回填；保留新版主世界地形是既定适配，不属于本项异常。

## 12. 整格液体自由下落

回到主世界后建立孤立液体测试区，玩家停在 16 格以内：

```mcfunction
/gamemode creative
/execute in minecraft:overworld run tp @s 80.5 101 3.5
/fill 77 100 -2 87 100 4 minecraft:stone
/tick freeze
/setblock 80 104 0 minecraft:water
/tick step 5
```

等本轮步进完成后，再执行数次 `/tick step 5` 观察。预期：孤立整格水逐格往下移动，原位置不保留永久源；到达地面后按暂存计划退成流动水。水的退流延迟为 8 刻，主世界熔岩 48 刻，下界超热环境熔岩 16 刻。计划刻和多次下落会交叠，不要求一条命令恰好只改变一层。

再放水平相邻两格水源，或在测试格正上方连接同类液体；预期不走该整格搬移分支，仍可按正常流体规则向下流。水浸载体不整块搬走。玩家远于水平 16 格、没有满足条件的液体计划刻时，不应凭空执行本分支。

可在下移后保存退出再重进，确认待执行退流没有丢失；测试结束：

```mcfunction
/tick unfreeze
/tick rate 20
```

桶仍保留既有现代取放操作；这次没有增加 Ctrl+100 经验永久水源操作。测试地幔正上方水时仍应服从已有蒸发保护。

## 13. 低抗性爆炸预处理

在另一处场地建立远处软物与菌丝对照，固定 TNT 避免落地改变爆心：

```mcfunction
/gamemode creative
/fill 127 99 -1 130 99 1 minecraft:stone
/fill 127 100 -1 130 100 1 minecraft:mycelium
/fill 127 101 -1 130 101 1 minecraft:short_grass
/tp @s 140.5 105 0.5
/summon minecraft:tnt 120.5 103 0.5 {Fuse:40s,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}
```

预期：部分主爆炸边缘外的低抗性草被预处理清除，向下命中的菌丝转为泥土；有石头支撑的泥土保留，撤掉支撑后走已有坍塌。射线有随机初始强度，不要求整个长方形同时变色。近爆心的土壤可能随后被现代主爆炸炸掉，所以不能仅检查爆心坑底是否还剩泥土。

继续回归真实苦力怕/TNT：主破坏范围没有缩小；关闭 `mobGriefing` 的怪物爆炸不应借预处理拆方块。到主世界和地底的新试炼密室复查天然方块，爆炸仍不能获得其方块掉落；正常宝库、箱子内容及饰纹陶罐内部奖励保留既有约定。玩家后放方块应继续正常掉落。不要用密室区域外手工复制的一块铜冒充天然来源方块。

## 14. 经验球拾取与物理细节

使用薄玻璃板，使经验球与玩家本来处于可近身拾取的距离，再换成铁栏杆做正对照：

```mcfunction
/gamemode creative
/fill 158 100 -2 165 100 2 minecraft:stone
/setblock 161 101 0 minecraft:glass_pane
/setblock 161 102 0 minecraft:glass_pane
/setblock 161 103 0 minecraft:glass_pane
/tp @s 160.9 101 0.5 -90 0
/experience set @s 0 levels
/gamemode survival
/summon minecraft:experience_orb 161.9 101.9 0.5 {Value:7s,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}
```

预期：隔玻璃板不能收取该球。把三块玻璃板替换成 `minecraft:iron_bars`，相同距离应可拾取；普通实心墙、玻璃、冰和树叶均不能套用金属栏杆豁免。原生球会吸引和碰撞移动，若被挤远，先清掉测试区球再在同一点重新召唤，避免把“离得太远”当作射线失败。

其他对照：在可接触水池中召唤普通经验球，球应下沉；满格熔岩中球不随机上蹦，火/熔岩不销毁经验；清空主手修补装备时正常获得经验，改拿有损伤的经验修补工具时应先修补。可准备工具：

```mcfunction
/give @s minecraft:iron_pickaxe[minecraft:enchantments={levels:{"minecraft:mending":1}},minecraft:damage=100]
```

现有死亡经验球仍只允许原主人收取，延迟发出、保存重载、普通 6000 刻/死亡 24000 刻寿命均保留。现代经验修补、拾取事件、公共球合并保留。拾取先检查归属，再检查 8 格安全范围与三条身高射线；未知区块按屏障处理，不应因经验球检查加载远区块。不能用远程人工修改/直接方法调用结果代替真实近身拾取。

## 结束与反馈

```mcfunction
/tick unfreeze
/tick rate 20
/effect clear @s
/gamerule naturalRegeneration true
```

请按“项目号—通过/异常—复现步骤”反馈即可。异常最好记录手持物、等级、实际距离、维度、当日时间、是否带扩展模组，以及截图或日志。先完成手测，再按用户约定处理提交；本文不预填“全部正常”的结论。
