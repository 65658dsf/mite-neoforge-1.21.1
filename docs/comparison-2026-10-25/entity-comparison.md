# 当前45种生物逐项对照旧Extreme/ITE

作者：NingMeng123。日期：2026-10-25。每行是代码执行链静态对照，不意味着所有数值和UI已逐种游戏内跑测。基础属性还叠主模组日历/维度/装备/分裂规则；这里只列辨识机制与确认差异。

当前证据根：`D:/Coding/LemonDev/minecraft/MITE Modern ITE/src/main/java/io/github/ningmeng123/modernite/`。`ExtremeMobs.java:44`遍历全部45个ExtremeKind注册；`ExtremeMobEvents.java:69`逐tick调用MobAbilities，`:137`成功伤害分派hit；`ExtremeMobs.java:153`、`:183`、`:229`执行对应近战后处理。

| 当前身份 | 旧版机制 | 当前行为/差异 | 证据 |
|---|---|---|---|
| `zombie_door` | 持门、高血/攻击曲线、力量速度，10%一次转移召援、每约200tick最多10僵尸 | 保留召援；固定铁门并增加可视门盾、防御意图和抬门停止攻击，旧随机材质门没有这套防御状态 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieDoor.java:33`；`entity/MobAbilities.java:92,168,188,270,298` |
| `miner_zombie` | >32天、高阶镐、加速挖掘、day曲线，玩家击杀掉铁粒 | 保留矿工身份；接主模组R196DiggerGoal与现代工具方块判定，普通种日期/预算仍限制 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityMinerZombie.java:18`；`entity/MobAbilities.java:64,93,169` |
| `zombie_chaos` | 空甲/空手近战给200tick混乱 | 保留效果来源，但旧输入四向反向改为90°移动轴旋转并镜头摇动 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieChaos.java:16`；`entity/MobAbilities.java:182,554` |
| `zombie_fly_away` | 基础攻击3、速度II；竖直追加min(1+floor(day/32)*0.4,4)+0.1，水平约2 | 改有效命中后纵速固定1.6929821475，约15格上升，水平0.25，不再按day把人越抛越高 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieFlyAway.java:17`；`entity/MobAbilities.java:94,188,572,574` |
| `zombie_ghast` | >32天、预测火球；下界一次五发，其它一次一发，另概率恶魂泪 | 保留预判火球/下界五发；当前60tick冷却、64格/视线检查，受现代弹道/爆炸实现 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieGhast.java:22`；`entity/MobAbilities.java:297` |
| `zombie_jump` | 近战给120tick倒置；杀死玩家后额外生成普通僵尸 | 保留倒置；当前hit路径未见该物种杀死玩家后额外召僵尸的分支，属于缺少旧细节 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieJump.java:19`；`entity/MobAbilities.java:95,188,555` |
| `zombie_knockback` | 速度I、基础速度0.3；水平加3.2、竖直加0.16、自减速0.75 | 当前保留水平3.2而保持Y原速度，未加旧0.16；成功伤害后结算 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieKnockback.java:24`；`entity/MobAbilities.java:111,188,589` |
| `zombie_invisible_look_up` | 空甲空手近战给200tick仰头 | 保留仰头效果并同步/相机脉冲；名字含Invisible但不应仅凭名字宣称本体全时隐身 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieInvisibleLookUp.java:18`；`entity/MobAbilities.java:182,556` |
| `zombie_pumpkin` | 生命60+day/8、攻击8+day/24、南瓜/防火，近战给600tick遮蔽 | 基础生命改60、攻击8后走统一成长；600tick遮蔽保留 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombiePumpkin.java:17`；`entity/MobAbilities.java:96,181,557` |
| `zombie_iron_bucket` | 铁桶盔，生命60+day/8，没有给玩家套桶的攻击方法 | 基础60后统一成长；新增命中玩家给200tick桶头，视觉不替换真实头盔 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieIronBucket.java:16`；`entity/MobAbilities.java:97,170,585` |
| `zombie_siege` | 出生骑一幼年僵尸；投掷条件同时要求目标是玩家且null，代码不可达 | 当前真实检测乘客并按60tick抛幼尸，方向/乘客关系修正，属于修复 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieSiege.java:18`；`entity/MobAbilities.java:272,276,317` |
| `dragon_zombie` | 每20tick对16×8×16范围其他僵尸给40tick力量/速度，不燃烧 | 光环保留；新增6格有视线每100tick龙息，不是旧类原有能力 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityDragonZombie.java:14`；`entity/MobAbilities.java:294,295` |
| `random_zombie` | 始终随机四件护甲+武器并随机耐久；onDeath只清头盔未调super | 当前每件护甲50%、武器70%独立决定有无，正常死亡回调、装备/绿宝石掉落，显著改写 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityRandomZombie.java:18`；`entity/MobAbilities.java:178` |
| `fishing_rod_zombie` | 15格视线远程给100tick大头，减速/头部视觉配套；免火 | 保留大头施法并清主手鱼竿；当前每20tick检测，不应理解成必须鱼钩拉人 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/FishingRodEntity.java:27`；`entity/MobAbilities.java:112,177,196,296` |
| `mirror_zombie` | >32天，每20tick复制玩家非空装备/药效；首次可提高血上限，不掉装备 | 当前复制组件而不共享ItemStack，连空槽也同步；复制药效保存放大级，避免旧构造丢amplifier；不掉克隆装备 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityMirrorZombie.java:21`；`entity/MobAbilities.java:104` |
| `mimic_zombie` | 每次玩家死亡生成，80血10攻，不自然刷、不捡物/掉装备、不消失 | 改死亡10%生成、10基础血，捕获/返还死亡背包；生成成功才清原掉落，非旧行为等价移植 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityMimicZombie.java:15`；`entity/MobAbilities.java:55,98,148,182,187` |
| `zombie_caller` | 被玩家攻击或地底锁敌触发4僵尸；200tick再3；400tick爆炸并生7～9蠹虫 | 保留分阶段召唤/自爆；当前还有召援准入与密度预算，计时存档方式变为MobMemory | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieCaller.java:47`；`entity/MobAbilities.java:99,118,312` |
| `zombie_boss` | 地底刷怪笼50%召出；近战伤害/5、清玩家力量，远离2.4格雷击/脱战回血；按伤害发奖 | 保留挑战主线；当前雷击还要求水平每轴<=5，贡献以UUID记录；书级受现代附魔上限、奖励取整/远程伤害检查不同 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieBoss.java:52`；`entity/MobAbilities.java:55,100,119,159,165` |
| `zombie_elite_lord` | >32天、振金甲锤/药效、day曲线；每51tick左右召矿工/食尸鬼/尸妖/亡魂，最多24 | 保留编队和总次数；攻击基值12去掉旧day/24，物种生命曲线保留再走统一成长/预算 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityZombieEliteLord.java:28`；`entity/MobAbilities.java:101,159,165,302` |
| `undying_zombie_emperor` | >=128天，尸领主双倍血，振金保护V、锋利V，出生2尸领主+2流浪女巫；材料掉落 | 保留护甲和编队；当前附属召援受统一日期/预算，不能承诺每次四只必定落地 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityUndyingZombieEmperor.java:26`；`entity/MobAbilities.java:64,102,159,164,179` |
| `exchanger` | >=64天、32格锁定、约3秒后交换位置；固定附魔阻止；基础群系重复2+5权重 | 保留交换与固定克制；当前使用安全准入/按开关配置，数值曲线和统一出生门槛需合算 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityExchanger.java:28`；`entity/MobAbilities.java:91,172,287` |
| `mirror_skeleton` | 每20tick复制目标非空装备/药效、首次提高血上限、禁克隆装备掉落 | 同镜像僵尸，当前组件copy与空槽/药效幅度同步，武器变化重配AI | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityMirrorSkeleton.java:21`；`entity/MobAbilities.java:104` |
| `skeleton_fast_arrow` | 使用20～30tick区间、15格弓AI | 当前SourceRangedAttackGoal用30tick参数，仍是速射但不是旧区间逐值相同 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonFastArrow.java:17`；`entity/MobAbilities.java:243` |
| `skeleton_knockback` | 只非主世界，木棒近战，竖直至少1.25、水平0.45、自减速0.65 | 当前基础装备木剑占位并接格挡/近战；有效命中竖直固定1.6929821475，水平路径另算，抛高数值已改 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonKnockback.java:26`；`entity/MobAbilities.java:61,171,564,572` |
| `skeleton_lightning` | >=96天、弓，免火；箭对玩家固定6伤，闪电仅表现 | 当前保留固定6且分裂子代按倍率缩放，现代visualOnly闪电；阶段/拥挤仍限制 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonLightning.java:18`；`entity/MobAbilities.java:61` |
| `skeleton_floating` | 可下界或地底，弓箭给200tick漂浮 | 当前customNaturalAllowed只允许地底（不是旧下界+地底）；攻击漂浮保留 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonFloating.java:15`；`entity/MobAbilities.java:60,562` |
| `skeleton_blaze` | >32天、烈焰附魔弓、防火、概率烈焰粉 | 保留火焰弓/防火/粉掉落；受新物种装备阶段、出生预算 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonBlaze.java:16`；`entity/MobAbilities.java:174` |
| `wither_skeleton_arrow` | 凋灵召援远程兵、远古弓、免火/熔岩，伤害给300tick凋零、煤和凋灵头 | 保留身份与凋零；自然只下界，额外来自凋灵阶段军队；名字Arrow但本体是骷髅，不是箭实体 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityWitherSkeletonArrow.java:26`；`entity/MobAbilities.java:62,175,561` |
| `skeleton_elite_lord` | 振金装备、速度力量、day属性、玩家掉钻石，onUpdate只有烟雾 | 当前新增每100tick召普通骷髅最多6，旧类没有此召唤；基础攻击去day线性项 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonEliteLord.java:22`；`entity/MobAbilities.java:103,159,173,307` |
| `skeleton_zombie` | 按距离切弓/匕首、备用武器、分段甲，腐肉掉落 | 保留近远切换与备用武器状态；现代AI重配、早期装备锁会清隐藏备用武器 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntitySkeletonZombie.java:23`；`entity/MobAbilities.java:176,318` |
| `run_creeper` | 速度0.5 | 保留疾跑0.5，基础生命/成长/引信与主模组共同决定 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityRunCreeper.java:12`；`entity/MobAbilities.java:113` |
| `phase_creeper` | 2～4闪避次数，每100tick回复一次，受击闪避；沿路径跃进传送 | 保留2～4/100tick；追击传送改朝目标方向固定3格，不再按旧路径点随机跳进 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityPhaseCreeper.java:27`；`entity/MobAbilities.java:281` |
| `chaos_creeper` | >=64天，爆炸伤害玩家给200tick混乱 | 保留来源/时长，混乱移动变轴旋转；新实现还可能从伤害事件统一分派 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityChaosCreeper.java:13`；`entity/MobAbilities.java:554` |
| `invisible_creeper` | 不可见表现来自专用渲染器而非类新增服务器AI | 当前常驻隐身状态并在死亡/爆炸清掉，避免生成隐身残留云；属于现代适配 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityInvisibleCreeper.java:11`；`entity/MobAbilities.java:189` |
| `invisible_reversed_creeper` | 隐形渲染，爆炸给120tick倒置 | 隐形与倒置保留；当前状态清理、防隐身药水云及移动规则有现代细节 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityInvisibleReversedCreeper.java:11`；`entity/MobAbilities.java:189,555` |
| `black_infernal_creeper` | 地狱苦力怕基类爆炸半径×6.25，生命(30+day/8)×3+100，掉复活阵 | 当前用R196ExplosionCalculator显式爆炸块/生物强度68.75/75，保留生命曲线和复活阵；不参与普通分裂 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityBlackInfernalCreeper.java:17`；`entity/MobAbilities.java:63,114` |
| `flash_creeper` | 爆炸伤害给100tick闪光 | 保留；当前爆炸detonate另检查玩家、遮挡和距离，包含爆炸实际关联实体路径 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityFlashCreeper.java:11`；`entity/MobAbilities.java:559` |
| `wither_creeper` | 爆炸伤害给160tick凋零 | 保留；当前爆炸detonate补效果路径，判定边界与旧checkForAfterDamage不完全同 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityWitherCreeper.java:11`；`entity/MobAbilities.java:560` |
| `witch_lord` | >=96天、370血、10～25tick远程、最多召2流浪女巫、掉2附魔金苹果 | 保留370血、2女巫与2苹果；当前远程参数25、召援另受预算/数量/日期 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityWitchLord.java:28`；`entity/MobAbilities.java:64,108,248,308` |
| `red_ghoul` | 肉食/猎动物AI，命中玩家给Potion28 120tick放大4灵敏度效果 | 当前明确fast_sensitivity 120tick/4，基础20血5攻，名字/父类从旧EntityAnimalWatcher改ZombieVariant而重配猎物 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityRedGhoul.java:37`；`entity/MobAbilities.java:110,118,260,558` |
| `dark_iron_golem` | 生命(45+day/6)×4、攻击30+day/20，水/雪球/爆炸/有效破石工具能伤，向上追加15 | 当前基础攻击再×2，受伤工具条件收窄到镐；命中纵速设15而非追加15，水平固定小偏移 | `.reference/extreme/decompiled/cn/wensc/mitemod/extreme/entity/EntityDarkIronGolem.java:36`；`entity/MobAbilities.java:61,75,109,118,188` |
| `zombie_lord` | 旧ITE尸领主为前置，Extreme另有定点兼容修改 | 当前独立身份，振金装备/曲线和AI；旧ITE机制及Extreme补丁详ite.md，不把其算为Extreme新注册44之一 | `.reference/ite/decompiled/net/xiaoyu233/mitemod/miteite/entity/EntityZombieLord.java:23`；`entity/MobAbilities.java:101,159,207,275` |
| `wandering_witch` | 旧ITE流浪女巫为前置，Extreme改drops/行为并用于女巫领主召援 | 当前保留独立身份和ITE参考召狼/蝙蝠/药水状态路径，65天门槛；详ite.md | `.reference/ite/decompiled/net/xiaoyu233/mitemod/miteite/entity/EntityWanderingWitch.java:43`；`entity/MobAbilities.java:55,59,64,107,275` |
| `zombie_pigman_lord` | 旧ITE猪人领主，Extreme普通猪人1/5替换及兼容补丁 | 当前独立猪人身份、下界自然池；IteReferenceSpawns恒true时旧替换fallback不执行，不能再额外宣称一直1/5替换 | `.reference/ite/decompiled/net/xiaoyu233/mitemod/miteite/entity/EntityZombiePigmanLord.java:27`；`entity/MobAbilities.java:65,106,159` |
| `annihilation_skeleton` | 旧ITE湮灭骷髅，Extreme定点护甲/免疫/属性修改 | 当前要求玩家直接近战且高差<3，短暂阶段后倒计时消失，详ite.md；统一日期/显式召唤路径有别 | `.reference/ite/decompiled/net/xiaoyu233/mitemod/miteite/entity/EntityAnnihilationSkeleton.java:34`；`entity/MobAbilities.java:57,77,79,105,118` |

## 旧注册但当前没有独立身份的3种

| 旧实体 | 旧行为 | 当前对照 |
|---|---|---|
| EntityRevenantGhast | 仅地底>=128天并1/4，通过预测火球、地狱苦力怕爆炸半径弹；独立渲染与刷怪权重 | 没有对应ExtremeKind，不能以内部GhastVariant类存在认定保留。普通恶魂火球增强不等于其身份/生成条件完整替代。 |
| EntityPillager | >32天，弩兵，40+day/16血、8+day/24攻击、不自然消失 | 当前保留原版Pillager AI，33天后标记并升级弩；没有旧独立ID。`ExtremeMobEvents.java:74`、`:40`。 |
| EntityPillagerLeader | 320血、16+day/12攻击，出生带掠夺者队伍，掉绿宝石 | 当前没有此独立ID和首领编队入口；Family.PILLAGER内部类从未被枚举实例选择。 |

## 当前额外独立疯狗

`entity/RabidDogContent.java`和`RabidDog.java`独立注册疯狗，属于45枚举之外第46种。旧Extreme44实体没有对应类；当前禁止驯服/繁殖/牵引，近战附6秒狂犬病并驱动自动挥砍，独立生成日期与群系资格，详当前ITE分册。

## 额外重要对照边界

- `skeleton_elite_lord`旧onUpdate仅烟雾；当前召6骨骷髅是新技能。`dragon_zombie`旧无龙息、`zombie_iron_bucket`旧无给玩家套桶技能。
- 旧`EntityZombieSiege`投掷条件逻辑矛盾，因此报告应写“修复旧不可达分支”，不能把旧宣称意图当实际行为。
- 旧`EntityRandomZombie.onDeath`没有调用super，会影响普通死亡与掉落链；当前修复后的普通流程不是无差别照抄。
- 四个ITE前置身份由旧ITE审计分册负责完整算法；表中列为“前置”防止重复计作Extreme原创。
