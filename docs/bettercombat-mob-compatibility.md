# Better Combat 持械怪物适配

作者：NingMeng123

安装 Better Combat 后，持有已登记近战武器或斧头的敌对生物会按该物品的攻击配置战斗。没有安装时，继续使用项目原有的 Source AI 或原版攻击规则。本适配不会使 Better Combat 成为 MITE 的必装前置。

开发对照版本为 Minecraft 1.21.1 / NeoForge 的 Better Combat `2.4.0+1.21.1`。自行安装 Better Combat 时，还需按其要求安装 Player Animator（`player-animation-lib`，本地版本 `2.0.4+1.21.1`）和 Cloth Config 的对应 NeoForge 版本；这些依赖仅随可选 Better Combat 的安装需要提供。

## 战斗范围与行为

- 作用对象为主手持械的敌对生物，包括僵尸、骷髅、凋零骷髅、猪灵及卫道士等；现有 Source Goal、原版近战 Goal 与猪灵 Brain 共用适配。剑、匕首、棍棒、战斧和战锤按武器处理；工具中仅斧头、短柄斧参与此适配。MITE 和 Simply Swords 的对应武器在 Better Combat 能解析其武器配置时同样生效，包含 Simply Swords 的战斗镰刀。
- 攻击距离读取武器配置，并以玩家现有 MITE 近战距离规则为上限，保留高低差和物品距离属性。到达攻击距离后先前摇，到配置的命中刻才结算，攻击间隔、连段、伤害倍率与攻击时减速取自武器配置。
- 实际命中重新检查距离、朝向锥角与方块碰撞，仅攻击 AI 当前选定的一个目标。玻璃也能阻挡攻击；走出范围或绕到攻击方向之外可以躲开这一击。
- 攻击通过原生伤害流程结算，保留装备、附魔、盾牌及凋零等实体效果。换武器、丢失已追踪目标或举盾会取消待命中的攻击，并消耗已开始的恢复时间；临时空手再重新装备也不能重置该恢复时间。
- 镐、铲、锄、镢、剪刀、MITE 收割用镰刀与制作小刀不视为攻击武器，继续使用原有规则。原有弓弩射击、普通空手攻击与动物行为也保持原规则；换成这些装备或空手，不能绕过此前持械攻击已经消耗的恢复时间。

## 动画边界

客户端读取实际安装的 Better Combat 动画资源键，并由 Player Animator 的关键帧播放器驱动人形与灾厄村民模型的躯干、四肢和持物变换。保留怪物本身的模型比例；猪灵衣物层随对应肢体更新。

原版怪物使用刚性肢体，当前没有玩家模型的肘膝弯曲，也没有副手交替攻击或范围横扫伤害。非人形模型仍可使用服务端攻击规则，其自定义模型动画不在本次适配范围内。

## 手动检查

在有权限的测试世界中切换生存模式，在夜间平地逐个召唤并观察；猪灵测试时不要穿金质护甲。每次检查后可清理带测试标签的生物。以下命令覆盖剑、斧头及不同 AI 路径：

```mcfunction
/summon minecraft:zombie ~3 ~ ~ {Tags:["mite_bc_test"],PersistenceRequired:1b,CanPickUpLoot:0b,HandItems:[{id:"minecraft:iron_sword",count:1},{}]}
/summon minecraft:skeleton ~3 ~ ~ {Tags:["mite_bc_test"],PersistenceRequired:1b,CanPickUpLoot:0b,HandItems:[{id:"minecraft:iron_axe",count:1},{}]}
/summon minecraft:piglin ~3 ~ ~ {Tags:["mite_bc_test"],PersistenceRequired:1b,CanPickUpLoot:0b,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_sword",count:1},{}]}
/summon minecraft:vindicator ~3 ~ ~ {Tags:["mite_bc_test"],PersistenceRequired:1b,CanPickUpLoot:0b,HandItems:[{id:"minecraft:iron_axe",count:1},{}]}
```

确认挥击有可见前摇，伤害发生在命中时刻；连续攻击遵循武器间隔。前摇中后退、绕背或隔上玻璃，确认这次攻击落空。对照玩家使用同一武器时的 MITE 距离；换武器或短暂空手不能立刻补一次伤害。

将测试骷髅改持弓，确认恢复弓箭行为；也可把最近测试怪物改为空手检查原攻击方式：

```mcfunction
/item replace entity @e[type=minecraft:skeleton,tag=mite_bc_test,limit=1,sort=nearest] weapon.mainhand with minecraft:bow
/data modify entity @e[tag=mite_bc_test,limit=1,sort=nearest] HandItems[0] set value {}
/kill @e[tag=mite_bc_test]
```

另以不含 Better Combat 的独立实例启动 MITE，重复持械、空手和骷髅射箭检查，确认没有缺类错误，且继续使用项目原有规则。

自动测试入口为 `BetterCombatMobGameTests`，命名空间 `mite_bettercombat`，使用该命名空间下的 `empty` 测试模板。可通过 `-PmiteGameTestNamespaces=mite_bettercombat` 选择；有模组、无模组测试均应使用各自独立的 `-PmiteGameTestDirectory`。

## 2026-10-17 自动验证

- 安装 Better Combat、Player Animator、Cloth Config 及 Simply Swords 的独立服务端：20 项专项 GameTest 通过。覆盖 54 个 MITE 武器/斧类物品、53 个排除工具、原版剑斧与排除工具、45 个 Simply Swords 物品，以及实际前摇命中、距离上限、冷却和取消。
- 未安装扩展的独立服务端：1903 项完整回归通过，包括原生近战回退；无 Better Combat、无 Player Animator 的独立客户端正常加载并自动退出，没有可选类链接错误。
- 实际客户端创建独立测试世界，使用真实模型与动画资源绘制僵尸、骷髅、猪灵、卫道士和远古骷髅的原生、前摇、命中、恢复四阶段。检查真实护甲层跟随、渲染后模型恢复及下一只未动画怪物的姿态，并保存两帧后截图。此项通过；实际追击操作与联机观感仍待人工手测。
- 11 份共享手斧/短木棒配置通过 `python tools/assets/generate_bettercombat_tool_attributes.py --check`，没有为镐、铲、锄等工具新增攻击配置。
- 最终无扩展专项 20 项、1528 项 JUnit 与构建通过。发布包已核对 Better Combat 为 optional、没有捆绑扩展类库或 gametest 审核器，且没有镐、铲、锄等攻击配置。

日志、客户端 JSON 和动画对照截图位于被忽略的 `build/verification/bettercombat-*`。客户端审核可通过 `-PmiteBetterCombatMobAudit=true` 与独立的 `-PmiteClientDirectory` 运行；审核自动创建新测试世界并退出，代码仅在 gametest 源集中，发布包不包含审核器。

本批测试包：`build/releases/mite-0.1.0-bettercombat-mobs.jar`。安装到其他实例时替换已有 MITE 包，避免同时放入两份 MITE。

2026-10-17 用户已授权提交并推送本次适配；未另提供人工追击或联机手测记录。
