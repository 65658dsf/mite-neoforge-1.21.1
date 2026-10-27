# 护甲防御与盔甲韧性的现代适配

开发者：NingMeng123。

玩家和怪物穿戴的人形盔甲使用 Minecraft 1.21.1 的护甲、盔甲韧性与附魔减伤。2026-10-28 按用户要求取消耐久降低穿戴防御：护甲和韧性始终使用完整材质属性，包括只剩 1 点耐久的盔甲。物品身份、配方、品质、材料耐久、维修、银甲毒抗继续使用各自规则。本文件列出的是现代适配数值，不把新增韧性或现代百分比减伤称为 R196 原值。

## 原因与结算入口

此前普通装备属性使用 R196 的平减防御表示，铁整套只有 8 点，且主动剥除了钻石和下界合金的韧性。受管怪物又按装备的来源 profile 重新计算平减，真实 `ARMOR`、未知 profile 的盔甲和额外属性未能一致参与。生成时已有严重磨损的盔甲还可能被按玩家磨损规则削弱，造成怪物穿甲却防不住攻击的表现。

现在每件装备提供现代 `ARMOR` 与 `ARMOR_TOUGHNESS`。普通玩家和人形怪物的实际减伤读取其真实属性；混搭、原版海龟壳、命令或第三方设置的属性组件也保留。没有自定义属性组件覆盖的既有装备会使用新的默认属性，无需重新合成。命令及第三方明确指定的属性组件不被强行替换。

结算依据本地固定版本的 `LivingEntity`、`CombatRules` 与 NeoForge 属性事件实现。材质数值由 `ArmorBalanceRules` 统一定义，包括对保留的原版装备所作的平衡调整；当前分档见[装备平衡](equipment-balance-2026-10-24.md)。

设普通护甲处理前的伤害为 `D`，护甲为 `A`，韧性为 `T`，普通护甲减伤为：

```text
effective = clamp(A - D / (2 + T / 4), A × 0.2, 20)
damage = D × (1 - effective / 25)
```

实际运行调用原版 `CombatRules`，包括其整数护甲读取、原生属性上限、最大 80% 护甲减伤及破甲数据函数。该表达式用于解释独立测试期待，不另写一套服务端减伤替代实现。

保护、弹射物保护、摔落保护等使用原版附魔减伤层，EPF 上限为 20；抗性提升 I 在护甲后减免 20%；抗火效果仍免疫普通火焰，但岩浆只额外减伤 50%，不再提供岩浆免疫。岩浆减半发生在护甲、抗性及保护附魔结算后、吸收生命结算前，对玩家和生物统一生效，效果等级不再提高这一比例；移除抗火后恢复原有岩浆伤害，生物天生的岩浆免疫仍保留。绕过状态效果的伤害不享受这项减伤。保护附魔不再额外增加 `ARMOR`。破甲使用原版 `ARMOR_EFFECTIVENESS`，MITE 穿甲每级只扣 1 点现代护甲、最低为 0，不增加无甲目标受到的原始伤害。

新增生物和保留生物的天然防御仍作为单独的物种机制，先从原伤害中扣除，再处理现代装备。天然防御的原有最低输入规则保留；没有天然防御的普通人形怪物不再被额外强制扣至少 1 点生命，原生吸收生命可以完整吸收一次命中。马的 BODY 槽、来源马铠保护值及马专用受伤规则维持原机制。

## 基础属性

护甲一栏顺序为头盔、胸甲、护腿、靴子；韧性一栏是每件值，不是整套值。

| 板甲材质 | 各部位护甲 | 整套护甲 | 每件韧性 | 整套韧性 |
|---|---|---:|---:|---:|
| 皮革 | 1 / 3 / 2 / 1 | 7 | 0.25 | 1 |
| 锈铁 | 1 / 4 / 3 / 1 | 9 | 0.375 | 1.5 |
| 金 | 2 / 4 / 3 / 1 | 10 | 0.5 | 2 |
| 铜、银 | 2 / 5 / 3 / 2 | 12 | 0.75 | 3 |
| 铁 | 2 / 5 / 5 / 2 | 14 | 1 | 4 |
| 远古金属 | 2 / 6 / 5 / 3 | 16 | 1.25 | 5 |
| 钻石、秘银 | 3 / 7 / 5 / 3 | 18 | 2 | 8 |
| 下界合金、艾德曼 | 3 / 8 / 6 / 3 | 20 | 2.5 | 10 |
| 振金 | 4 / 8 / 6 / 4 | 22 | 3 | 12 |

下界合金另保留每件 0.1、整套 0.4 的原版击退抗性。铜和银同级；当前所有板甲材质都有正韧性。

| 链甲材质 | 各部位护甲 | 整套护甲 | 每件韧性 | 整套韧性 |
|---|---|---:|---:|---:|
| 锈铁 | 1 / 3 / 2 / 1 | 7 | 0.28125 | 1.125 |
| 金 | 1 / 3 / 2 / 1 | 7 | 0.375 | 1.5 |
| 铜、银 | 1 / 4 / 3 / 1 | 9 | 0.5625 | 2.25 |
| 铁 | 2 / 4 / 4 / 1 | 11 | 0.75 | 3 |
| 远古金属 | 2 / 5 / 4 / 2 | 13 | 0.9375 | 3.75 |
| 秘银 | 2 / 6 / 5 / 2 | 15 | 1.5 | 6 |
| 艾德曼 | 3 / 6 / 5 / 3 | 17 | 1.875 | 7.5 |

## 品质、磨损与刷新

品质只改变最大耐久，不直接乘护甲或韧性。玩家与 NPC 的穿戴防御均不随耐久衰减：全新、75%、99% 损伤和只剩 1 点耐久时，同一件盔甲提供相同护甲与韧性。通常受击保留最后一点及维修机制沿用原规则。

物品提示与实际穿戴属性不再按剩余耐久缩小。已存在的受损盔甲通过原生装备更新读取完整属性，无需重新制作；自定义属性、附魔、名称和品质保留。装备、维修、品质变化、存档读取与网络组件往返均沿用原生刷新流程。

玩家受击耐久仍使用独立的 R196 随机分摊预算；旧材料防御与保护附魔在此只决定耐久成本，不决定现代生命减伤。`ArmorHurtEvent` 的取消和修改保持有效。普通 NPC 受伤不额外引入新的盔甲耐久消耗。绕过普通护甲的原版伤害按原生路径处理，不为摔落补造一次普通磨损。

## 自动验证范围

`ModernArmorDefenseGameTests` 覆盖玩家和僵尸的全部板甲阶段、链甲属性、严重磨损怪物装备、实体 NBT 重载、原版骷髅与新增守卫、天然防御组合、大伤害下韧性差异、保护 EPF 上限、抗性、抗火的普通火焰免疫与岩浆减半、破甲、额外属性与未知 profile 混搭、可取消磨损、摔落保护、小伤害和吸收生命。实际健康值期待使用独立写出的常量，不把生产平衡函数当作测试答案。

同件胸甲跨持有者用例验证 NPC → 玩家 → NPC 的实际属性：75% 损伤和最后一点耐久的秘银胸甲始终为 7/2（护甲/韧性）。原持有者摘下后为 0/0。普通骷髅真实箭矢命中验证全新、75%、99% 损伤和最后一点耐久的铜套都提供 12 护甲、3 韧性，相同原始箭伤对应相同实际掉血。

`EquipmentDurabilityGameTests` 覆盖玩家的实时磨损、最后一点保护、维修、品质组件变化、物品 NBT 和原生网络编解码后护甲及韧性刷新。`RangedAndUtilityEnchantmentGameTests` 验证 MITE 穿甲扣现代护甲一次，并保留无甲目标原伤害。原有纯 R196 防御算术单元测试继续作为来源证据，不能再解读为本次适配后的运行时减伤公式。

服务端测试不能代替客户端的物品提示、HUD、Jade 与实际操作检查；最终通过数量及构建产物以本次验证日志和交付说明为准。

## 手动测试

1. 替换旧版模组 JAR 后进入有作弊权限的测试存档。查看远古金属、秘银、艾德曼、钻石、下界合金胸甲：应分别有 1.25、2、2.5、2、2.5 点盔甲韧性；铁、铜、银、金分别为 1、0.75、0.75、0.5。
2. 给玩家穿戴整套新制铁甲、钻石甲和秘银甲，确认防御属性随更换而变化。下界合金仍有击退抗性；铜和银同级，链甲低于对应板甲。
3. 在遮阳处生成一只测试僵尸，只使用这一个带标签的目标，给它穿全套铁甲。执行一次 10 点仙人掌伤害，比较前后生命：没有其他效果或额外属性时应减少约 5.7333 点，而无甲目标减少 10 点。每次伤害比较间隔至少一秒。

```mcfunction
/summon minecraft:zombie ~ ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:["mite_armor_test"]}
/attribute @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] minecraft:generic.max_health base set 100
/data merge entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] {Health:100.0f}
/item replace entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] armor.feet with minecraft:iron_boots
/item replace entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] armor.legs with minecraft:iron_leggings
/item replace entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] armor.chest with minecraft:iron_chestplate
/item replace entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] armor.head with minecraft:iron_helmet
/attribute @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] minecraft:generic.armor get
/data get entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] Health
/damage @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] 10 minecraft:cactus
/data get entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] Health
```

4. 将僵尸和玩家的装备换为高阶甲，再把物品损伤提高到接近损坏；两者装备防御均应与全新时相同。保存退出再进入，确认防御没有消失；维修后数值不变。
5. 穿甲时使用抗火效果接触普通火焰，应免疫伤害；接触岩浆仍会掉血，实际伤害为同装备、同其他效果且没有抗火时的一半。无甲、提高抗火等级、移除抗火后分别对照；用附魔保护、抗性提升或 MITE 穿甲作对照，确认不会出现加倍计算或无甲目标额外受伤。

### 只剩一点耐久的怪物护甲

在上述僵尸仍穿其他铁甲时执行以下命令，换入普通品质艾德曼胸甲（基础耐久 4096、损伤 4095）。等待一刻后，僵尸护甲应为 17（其余铁甲 9 + 胸甲 8），韧性为 5.5；把同一胸甲损伤改成 0 后，两项数值仍相同。玩家单独穿这件只剩一点耐久的胸甲时，应为 8 护甲、2.5 韧性。

```mcfunction
/item replace entity @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] armor.chest with mite:adamantium_chestplate[minecraft:damage=4095]
/attribute @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] minecraft:generic.armor get
/attribute @e[type=minecraft:zombie,tag=mite_armor_test,limit=1] minecraft:generic.armor_toughness get
```

## 2026-10-28 取消耐久防御衰减验证

本体构建、全部 GameTest 源码编译和 1,770 项 JUnit 通过；38 项护甲、属性与耐久专项 GameTest 全部通过。另加载 ITE 正式包复跑真实铜甲箭伤用例通过：全新、75%、99% 损伤及只剩一点耐久的铜套均为 12 护甲、3 韧性，普通骷髅 2.5 点原始箭伤的实际掉血均约为 1.390909。穿脱、同物品跨玩家/NPC 转移、品质变化、维修、存档及网络往返均保留完整护甲和韧性。

日志为 `build/verification/full-armor-durability-build.log`、`full-armor-durability/base.log` 和 `full-armor-durability/ite.log`。发布包仍为 `build/libs/mite-0.2.16.jar`，核对 1,532 个生产类与编译输出一致，不含 GameTest。需要服务端和客户端更新本体包；此次未操作游戏界面或在线服务器。

## 此前交付与结果

- `gradlew build runGameTestServer` 成功：1455 项 JUnit、1505 项 GameTest 全部通过，失败、错误、跳过均为 0。
- 最终日志：`build/verification/monster-periodic-final-build-test.log`；包内容及原有改动保护记录：`build/verification/monster-progression-package-audit.json`。
- 测试包：`build/libs/mite-0.1.0.jar`。
- SHA-256：`39624b434db9a82af487a3de13b89daf55935ff744e43d26a29b96aa5ac615e5`，附带 `.sha256`。同一实例只保留一份 MITE JAR，使用本包替换旧版。
- 各材质情形及同件磨损胸甲的 NPC/玩家转交、最后一点耐久与作用域清理均已验证。
- 本次未启动或操作用户游戏窗口；本轮按玩家确认后的授权提交 GitHub。

回归中另更新了与现代抗火、吸收生命一致的旧断言，保留无健康损失不触发洞穴蜘蛛毒的来源规则；两处旧同步掉落用例改为捕获真实成功生成的实体。失败存档中确有原掉落物，修复保留精确数量和物品身份断言，未修改掉落生产逻辑。
