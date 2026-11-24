# Better Combat 持械怪物适配

作者：NingMeng123

模组设置中的「开启怪物更好战斗兼容」默认关闭。入口为「MITE: Modern → 配置 → 战斗与附魔 → 战斗设置」，也可在 `config/mite-common.toml` 的 `[combat]` 下设置 `better_combat_mobs = true`。联机时以服务端设置为准。

关闭时，即使安装 Better Combat，怪物也使用项目原有的 Source AI 或原版攻击方式，不使用本适配的攻击前摇、连招、冷却或怪物动画。攻击中关闭会取消待命中的挥击、移除攻击减速与兼容冷却，恢复原本攻击；再次开启不会恢复已取消的攻击。玩家的 Better Combat 兼容不受此开关影响。

安装 Better Combat 并开启该选项后，持有已登记近战武器或斧头的敌对生物会按该物品的攻击配置战斗。没有安装时，继续使用项目原有的 Source AI 或原版攻击规则。本适配不会使 Better Combat 成为 MITE 的必装前置。

开发对照版本为 Minecraft 1.21.1 / NeoForge 的 Better Combat `2.4.0+1.21.1`。自行安装 Better Combat 时，还需按其要求安装 Player Animator（`player-animation-lib`，本地版本 `2.0.4+1.21.1`）和 Cloth Config 的对应 NeoForge 版本；这些依赖仅随可选 Better Combat 的安装需要提供。

2026-10-23 开关验证：1722 项 JUnit、构建与打包通过；安装 Better Combat / Simply Swords 和未安装扩展的独立服务端各 71 项专项 GameTest 通过，覆盖默认关闭、原生立即命中、开启后的前摇、关闭清理、原生 Brain 新冷却保留、重新开启、真实网络编解码及配置导出。旧状态在实体 Pre 阶段清理，关闭时 Post 不再处理；若设置在 Pre 后改变，也会在原生攻击前清理，避免擦除原生 Brain 刚写入的冷却。

真实客户端在新建测试世界中通过动画开关、关键帧、五种怪物模型、护甲层与共享模型恢复审核，确认关闭拒绝新动画、取消中的动画不会在重新开启后复活。报告与日志保留在 `build/verification/bettercombat-toggle-*`；此项未进行真实多人对打验收。

## 战斗范围与行为

- 作用对象为主手持械的敌对生物，包括僵尸、骷髅、凋零骷髅、猪灵及卫道士等；现有 Source Goal、原版近战 Goal 与猪灵 Brain 共用适配。剑、匕首、棍棒、战斧和战锤按武器处理；工具中仅斧头、短柄斧参与此适配。MITE 和 Simply Swords 的对应武器在 Better Combat 能解析其武器配置时同样生效，包含 Simply Swords 的战斗镰刀。
- 攻击距离读取武器配置，并使用与玩家一致的近战基础距离规则：普通原版/MITE 武器沿用 R196 触及，简易刀剑保留其各自配置的长度（默认振金长柄刀 3.5、弯刀 2.75），不按耐久分类统一截成普通剑距离。继续保留高低差、物品距离属性及当前招式范围限制。到达攻击距离后先前摇，到配置的命中刻才结算，攻击间隔、连段、伤害倍率与攻击时减速取自武器配置。
- 实际命中重新检查距离、朝向锥角与方块碰撞，仅攻击 AI 当前选定的一个目标。玻璃也能阻挡攻击；走出范围或绕到攻击方向之外可以躲开这一击。
- 持械怪物获得武器近战距离加成的三分之一，按武器基础近战距离超过空手 1.5 格的部分计算。生存玩家等价距离减 0.2 格仍作为上限，并保留更短的招式和体型限制。寻路、起手和命中共用这些条件，前摇期间属性或目标位置改变也会重验。旧存档的 `mite:tool_reach` 不重复叠加。
- 攻击通过原生伤害流程结算，保留装备、附魔、盾牌及凋零等实体效果。换武器、丢失已追踪目标或举盾会取消待命中的攻击，并消耗已开始的恢复时间；临时空手再重新装备也不能重置该恢复时间。
- 镐、铲、锄、镢、剪刀、MITE 收割用镰刀与制作小刀不视为攻击武器，继续使用原有规则。原有弓弩射击、普通空手攻击与动物行为也保持原规则；换成这些装备或空手，不能绕过此前持械攻击已经消耗的恢复时间。

未安装 Better Combat 时，Source AI 把三分之一的武器加成计入脚底距离，原生近战则水平扩展攻击框的允许范围。两者都受生存玩家等价距离减 0.2 格的上限约束。直接近战伤害仍校验这一上限。

## 动画边界

客户端读取实际安装的 Better Combat 动画资源键，并由 Player Animator 的关键帧播放器驱动人形与灾厄村民模型的躯干、四肢和持物变换。保留怪物本身的模型比例；猪灵衣物层随对应肢体更新。

原版怪物使用刚性肢体，当前没有玩家模型的肘膝弯曲，也没有副手交替攻击或范围横扫伤害。非人形模型仍可使用服务端攻击规则，其自定义模型动画不在本次适配范围内。

## Fresh Animations 与极难版

从 MITE 0.2.4 起，可同时使用 FreshAnimations `1.10.4`、FA+Player `1.1`、EMF `3.3.12`、ETF `7.2.6`，以及上述 Better Combat / Player Animator 版本。原资源包和第三方模组无需修改。只需更新 MITE 主模组，极难版 `MITE: Modern ITE 0.2.1` 的持械人形怪共用此兼容处理。

冲突来自 EMF 在模型绘制阶段重新写入 Fresh Animations 骨骼，覆盖此前已经计算好的战斗姿态。兼容使用 EMF 官方动画回调：资源包正常计算变量、面部与其它部件，仅在真实攻击动画有效时保留躯干、头部、四肢及皮肤外层的战斗位置、旋转和缩放。玩家取消攻击的淡出及攻击恢复阶段也包含在内；动画结束后恢复资源包的正常动作。灾厄村民的持械双臂在攻击中保持展开。

此处理包含本地与远端玩家，以及极难版使用人形模型的僵尸、骷髅、猪人等。自定义肩轴、头部缩放、恶魂头与龙头层仍沿用各自模型。动物、非人形怪、没有正在播放持械攻击的实体继续使用自身动画；没有将 Better Combat 的攻击强加给爬行者、恶魂等模型。单独的 Better Combat 持武器待机姿态仍由资源包正常覆盖。

0.2.4 的审核只覆盖了自定义骷髅的战斗姿态，没有要求其待机和走路实际执行 FA；因此遗漏了 `mite:source_skeleton` 无对应资源文件的问题。0.2.6 将普通骷髅及四种主模组骷髅改用网格相同的标准骨架入口，并补充真实走路断言。扩展骷髅僵尸保留原 64×64 粗四肢几何，仅借用标准骷髅的 FA 姿态。女巫也以姿态驱动保留旧皮肤与红眼。主、扩展需要一起更新到 0.2.6。

其它模型接入和保留独立动作的完整名单见 [模型覆盖审计](fresh-animations-model-coverage.md)。此适配不复制第三方资源包内容，也不改变怪物攻击伤害、距离和命中时刻。

客户端专项验证与复现方法见 [Fresh Animations 战斗兼容验证](testing/fresh-animations-combat.md)。EMF、ETF 和 Player Animator 均为编译期可选依赖，不会打进发布 JAR；无这些模组的实例继续使用原有流程。

## 手动检查

先保持默认关闭，确认持械怪物仍以原本方式立即攻击；随后开启「开启怪物更好战斗兼容」进行以下前摇、命中与动画检查，也应在前摇中关闭，确认立即恢复原攻击方式。在有权限的测试世界中切换生存模式，在夜间平地逐个召唤并观察；猪灵测试时不要穿金质护甲。每次检查后可清理带测试标签的生物。以下命令覆盖剑、斧头及不同 AI 路径：

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

自动测试入口为 `BetterCombatMobGameTests` 和 `BetterCombatMobConfigGameTests`，命名空间 `mite_bettercombat`，使用该命名空间下的 `empty` 测试模板。原兼容行为测试会临时开启并恢复选项；新增回归覆盖默认关闭、原生立即伤害、前摇中关闭、减速与猪灵脑冷却清理、重新开启、联机配置同步、旧快照与导出。可通过 `-PmiteGameTestNamespaces=mite_bettercombat` 选择；有模组、无模组测试均应使用各自独立的 `-PmiteGameTestDirectory`。

### 2026-10-22 持械距离回归

新增 `MobWeaponReachGameTests`（`mite_mob_reach`）覆盖玩家等价配置、高低差、幼年/蜘蛛体型、原生攻击预判后目标退开、直接玩家伤害兜底、规划落脚点、旧属性过滤、零距离和空手不变。Better Combat 专项另外覆盖短招式先乘后减、保留本次招式的命中复查，以及前摇途中缩小体型后攻击落空；同样验证范围内仍能实际扣血。

安装 Better Combat 2.4.0、Simply Swords 1.70.2 及其依赖，运行 `mite_mob_reach,mite_bettercombat,mite_simplyswords`，66 项 GameTest 全部通过。未进行本次客户端手动对打或联机观感验收。

未安装扩展的独立服务端通过 2332 项完整 GameTest，1719 项 JUnit 也全部通过。原有凋零骷髅近战测试曾把牛留在世界原点，现改为攻击者前方 1 格并显式验证合法距离，继续断言实际扣血及凋零效果；没有放宽生产距离来兼容超距测试。

旧 `mite_targeting` 的混合扩展运行另有旧距离断言和 Simply Swords 向测试模拟连接发送未注册 payload 的夹具问题，不纳入上述通过记录；没有为通过测试而修改第三方网络行为或旧测试的攻击规则。

## 2026-10-17 自动验证

- 安装 Better Combat、Player Animator、Cloth Config 及 Simply Swords 的独立服务端：20 项专项 GameTest 通过。覆盖 54 个 MITE 武器/斧类物品、53 个排除工具、原版剑斧与排除工具、45 个 Simply Swords 物品，以及实际前摇命中、距离上限、冷却和取消。
- 未安装扩展的独立服务端：1903 项完整回归通过，包括原生近战回退；无 Better Combat、无 Player Animator 的独立客户端正常加载并自动退出，没有可选类链接错误。
- 实际客户端创建独立测试世界，使用真实模型与动画资源绘制僵尸、骷髅、猪灵、卫道士和远古骷髅的原生、前摇、命中、恢复四阶段。检查真实护甲层跟随、渲染后模型恢复及下一只未动画怪物的姿态，并保存两帧后截图。此项通过；实际追击操作与联机观感仍待人工手测。
- 11 份共享手斧/短木棒配置通过 `python tools/assets/generate_bettercombat_tool_attributes.py --check`，没有为镐、铲、锄等工具新增攻击配置。
- 最终无扩展专项 20 项、1528 项 JUnit 与构建通过。发布包已核对 Better Combat 为 optional、没有捆绑扩展类库或 gametest 审核器，且没有镐、铲、锄等攻击配置。

日志、客户端 JSON 和动画对照截图位于被忽略的 `build/verification/bettercombat-*`。客户端审核可通过 `-PmiteBetterCombatMobAudit=true` 与独立的 `-PmiteClientDirectory` 运行；审核自动创建新测试世界并退出，代码仅在 gametest 源集中，发布包不包含审核器。

本批测试包：`build/releases/mite-0.1.0-bettercombat-mobs.jar`。安装到其他实例时替换已有 MITE 包，避免同时放入两份 MITE。

2026-10-17 用户已授权提交并推送本次适配；未另提供人工追击或联机手测记录。
