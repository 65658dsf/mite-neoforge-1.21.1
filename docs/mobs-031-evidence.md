# R196 生物注册、属性、掉落与原始渲染来源（031）

开发署名：NingMeng123。原作作者：Avernite。版本基准为本地 `MITE 1.6.4 R196.zip`，SHA-256：`fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。

完整证据数据见 [mobs-source-031.json](../spec/mobs-source-031.json)。本文件说明来源事实，不能以实体已注册或纹理已找到代替现代运行时机制完成。

## 实际注册与启用分类

直接核对 `nt.java` 的注册入口及已提交的 `reference-data/runtime-registry.json`，共 **85 个实体身份**：60 个继承 `EntityLivingBase`，其中 58 个可具体构造、2 个是抽象 `Mob`/`Monster` 注册名；其余 25 个是物品实体、投掷物、载具等。

新增生物为以下 **29 个**，实际 ID 连续为 512–540，全部在默认注册中具有刷怪蛋。注册代码没有以开发模式或职业开关屏蔽它们。刷怪蛋和注册身份并不表示所有条件下都能自然生成；各自的生物群系列表、深度、维度、替换与召唤逻辑需要保留。

| 源 ID | 注册名 / 源类 | 当前身份 |
| --- | --- | --- |
| 512 | Ghoul / EntityGhoul | mite:ghoul |
| 513 | Wight / EntityWight | mite:wight |
| 514 | InvisibleStalker / EntityInvisibleStalker | mite:invisible_stalker |
| 515 | DemonSpider / EntityDemonSpider | mite:demon_spider |
| 516 | Hellhound / EntityHellhound | mite:hellhound |
| 517 | DireWolf / EntityDireWolf | mite:dire_wolf |
| 518 | WoodSpider / EntityWoodSpider | mite:wood_spider |
| 519 | InfernalCreeper / EntityInfernalCreeper | mite:infernal_creeper |
| 520 | Shadow / EntityShadow | mite:shadow |
| 521 | FireElemental / EntityFireElemental | mite:fire_elemental |
| 522 | BlackWidowSpider / EntityBlackWidowSpider | mite:black_widow_spider |
| 523 | Revenant / EntityRevenant | mite:revenant |
| 524 | EarthElemental / EntityEarthElemental | mite:earth_elemental |
| 525 | Jelly / EntityJelly | mite:jelly |
| 526 | Blob / EntityBlob | mite:blob |
| 527 | Ooze / EntityOoze | mite:ooze |
| 528 | Pudding / EntityPudding | mite:pudding |
| 529 | VampireBat / EntityVampireBat | mite:vampire_bat |
| 530 | GiantVampireBat / EntityGiantVampireBat | mite:giant_vampire_bat |
| 531 | Longdead / EntityLongdead | mite:longdead |
| 532 | LongdeadGuardian / EntityLongdeadGuardian | mite:longdead_guardian |
| 533 | Nightwing / EntityNightwing | mite:nightwing |
| 534 | Netherspawn / EntityNetherspawn | mite:netherspawn |
| 535 | Copperspine / EntityCopperspine | mite:copperspine |
| 536 | HoarySilverfish / EntityHoarySilverfish | mite:hoary_silverfish |
| 537 | ClayGolem / EntityClayGolem | mite:clay_golem |
| 538 | BoneLord / EntityBoneLord | mite:bone_lord |
| 539 | AncientBoneLord / EntityAncientBoneLord | mite:ancient_bone_lord |
| 540 | PhaseSpider / EntityPhaseSpider | mite:phase_spider |

原版对应保留现代原版 ID。旧的骷髅种类、僵尸村民、已驯服豹猫和马种类在 1.21.1 中已拆为不同实体，单独记录在 `modern_split_identities`，不能把旧的一条注册记录当作只有一种现代生物。

Giant 保留旧注册身份，但没有来源自然生成表或刷怪蛋，分类为旧命令实体；`Mob`、`Monster` 是不能实例化的抽象身份。文件名带 `Entity` 的其余类另列为未进入主实体注册的实现类，包含 AI、共享父类、粒子与内部投掷物，不能一概称为已删除生物。

## 受保护的实际属性查询

[MobsRulesExport.java](../tools/reference/MobsRulesExport.java) 复用已提交的 `RegistryExport.ReadOnlyGuard`，在安全检查后完成注册并构造 **null-world 生物对象**。没有创建 World、Minecraft 客户端、独立服务器或玩家，也没有调用生成完成、tick、死亡、掉落或 OpenGL 渲染入口。七项防护自检全部通过，构造失败数为 0。

虚方法 `az()` 在实际原类构造链中执行。每个属性记录 `tp` 符号、真实属性名、基础值和当前有效值，并直接读取宽高、最大生命与经验 getter。不是按叶类里的某条赋值猜测父类结果。共查询 **78 个明确状态样本**：普通构造状态、方块魔像各材料状态，以及立方体请求尺寸 1、2、4。

以下字段可供实现直读：`entities[].base_attributes`、`experience`、`width`、`height`。其中攻击属性不包含手持武器、附魔、狂暴或目标特例加成；经验 getter 不等于不带条件的最终死亡经验。

需要特别保留的区别：

- DireWolf 配置成人宽度是 0.6，但刚执行完子类构造时观察到的宽度仍是父狼的 0.70000005。原 `nk.a(float,float)` 先存储成人基础形状，第二次调用不会立即重新缩放，年龄更新随后应用它。数据同时保存构造时宽高和配置成人宽高；实现直读字段采用成人形状。
- InfernalCreeper 的源 `getScale()` 实际继承 `of` 并返回 1；不能只凭名字给它任意放大碰撞箱。爆炸强度有独立倍数。
- Ooze 的 `setSize` 把请求尺寸 4 限制为真实尺寸 2。数据记录请求变体及 `actual_cubic_size`，不会把它误记为生命 16 的大灰浆怪。
- Jelly、Blob、Ooze、Pudding 的攻击和经验随尺寸与类型变化，不能用属性表里不存在的 `generic.attackDamage` 冒充实际攻击公式。尺寸 1/2/4 的基础生命分别为 1/4/16；Ooze 另有尺寸上限。
- 不在 null-world 中执行需要世界状态的自然防御 getter。ClayGolem 的普通/硬化防御 0/2 来自源方法条件；不可把 getter 查询失败当作固定 0。

## 掉落与父类调用链

`loot_source` 单独记录来源算法，公开 `source_methods` 只保存叶类与关键父类的方法签名、源码位置和正文校验值。完整反编译方法留在忽略的 `.reference/runtime-helper/mobs-031-methods-private.json`，不进入公开规格或模组。`primary_drop_getter_single_sample` 只是某一次 `s()` 调用返回值：这个 getter 可能自己掷随机数，且常常不是特殊掉落逻辑，不能用它直接生成掉落表。

- Wight 和 Revenant 的腐肉概率分别是玩家最近攻击时 0.5、其他情况 0.25。稀有金属粒需最近玩家攻击、没有大量摔落伤害；判断为 `nextInt(denominator) < 5 + looting*2`。Wight 分母 200，Revenant 分母 50，四种粒等概率选一。
- Longdead/Guardian 的箭来自远古金属箭，BoneLord/AncientBoneLord 的普通箭来自锈铁箭；掉落数量、非玩家折减和 Longdead 的二次三分之一筛选仍按 `tr.dropFewItems`。不能拿继承的 `s()` 锈铁箭返回值误改 Longdead。
- 蜘蛛掉线数量来自死亡时**剩余蛛网库存**，不是死亡时重新掷一次掉线数量。蛛眼需要最近玩家攻击，并保留屠宰修正；PhaseSpider 初始不发放蛛网库存。
- Hellhound 覆盖了狼的掉落方法为空；DireWolf 仍继承固定一张皮革。经验和材料掉落不能机械继承现代狼默认数据。
- 元素魔像固定掉一个对应方块，石头体掉圆石，泥体/硬化泥体分别掉黏土块/陶瓦。
- 最小立方怪的原胶球掉落仍沿用原数量、抢夺和非玩家折减，大立方怪先产生 2–4 个半尺寸子体。灰浆怪尺寸限制继续生效。
- “没有固有少量材料掉落”不取消装备、拾取并持有的物品或其他独立死亡返还逻辑。

受保护的同一注册查询还顺取全部 **667 个物品**的 `isHarmedByPepsin()`、`isHarmedByAcid()`、`isHarmedByFire()`、`isHarmedByLava()` 布尔结果，保存在 `items_corrosion`。这些是实际 Item getter，不是从材料名字推断。

## 原作纹理与模型

来源资源包 SHA-256：`4a9bb9f7ab5afee7c0e4e9f57f3b81ff185a0c619d29036b8c2f1ece7450e67a`。29 个新增生物共引用 **44 张去重 PNG**，逐张记录原路径、尺寸、长度和 SHA-256；纹理路径按实际 renderer 选择及动态子类型名核实。

- Ghoul、Wight、Shadow、FireElemental 是 64×32 人形纹理，使用 `ModelInvisibleStalker` 的旧 64×32 Biped 布局。InvisibleStalker 复用 Wight 纹理并使用 0.05 模型透明度。
- 蜘蛛使用 `ModelArachnid`，各有 64×32 身体与发光层。WoodSpider、BlackWidow、PhaseSpider 渲染缩放为 0.6。
- DireWolf 使用四张 64×32 狼纹理，渲染缩放 1.1；Hellhound 使用狼模型与独立身体纹理。
- Revenant 使用 64×64 僵尸纹理；Longdead、Guardian、BoneLord 使用 64×32 骷髅纹理。AncientBoneLord 明确复用 LongdeadGuardian 纹理。
- 两种吸血蝙蝠复用 64×64 vampire 纹理，Nightwing 使用 64×64 nightwing 纹理。渲染基础缩放是 `0.35 * getScaleFactor()`；巨型吸血蝙蝠的因子是 2。
- Jelly/Blob/Ooze/Pudding 使用 `ModelGelatinousCube` 的内外两层模型与独立 64×32 纹理。
- EarthElemental 使用石头、黑曜石、地狱岩、末地石的普通/熔岩八种身体；ClayGolem 使用泥体/硬化泥体两种身体，另有公共发光层，均为 64×32。

没有通过加载旧 renderer 或执行 OpenGL 得到这些结论。它们来自原 `bgl` 注册、renderer/model 源码与实际资源包像素布局。

## 怨魂、骷髅与蝙蝠运行时迁移边界

`R196Zombie`、`R196Skeleton`、`R196Bat` 承载 8 个来源身份，并使用现代 `Zombie`、`AbstractSkeleton`、`Bat` 的联机、声音与实体存储接口。自定义骷髅没有继承现代粉雪转流浪者行为；旧下界骷髅子型仍为同一来源身份的 `SkeletonType=1`，生成完成时使用差品质铁剑、攻击基础值 4、0.72×2.34 形状、火/熔岩免疫及 200 刻凋零攻击。

- 装备随机化直接对应 `abw.I`、`og.enchantEquipment`、`ye.randomizeForMob`。区域紧张度使用区块居住时间 `/3600000`、难度和月亮亮度；附魔概率是 `0.1*tension`，附魔等级为取整 `5+tension*nextInt(18)`。普通装备初始磨损为 70%–100%，已附魔装备为 50%–80%，始终保留至少一单位耐久。现代附魔候选保留 1.21.1 内容。
- 世界日数按来源 `(dayTime+6000)/24000+1`；普通怨魂/骨君的战斧和战锤分别从第 10、20 天进入武器池，剑权重 2，另两种权重各 1。古代骨君默认生存模式无这两个日期限制；未开启旧锦标赛模式。
- 守卫完整保存备用远古金属短剑堆，包括品质、磨损与附魔；可见目标距离严格 `<5` 切近战、`>6` 切回弓，边界之间维持当前武器。普通远古骷髅与守卫的初始护甲掉落机会均乘 0.25。
- 死亡先计算少量材料，再返还装备。`of.a` 的大量摔落标志在摔落伤害 `>=maxHealth*0.5` 时设置，并将整个少量材料入口压到 10%；稀有粒与凋零头骨仍彻底禁止。装备有独立的摔落和差品质磨损惩罚；源手持武器与护甲使用不同掉落条件，且死亡不会重新掷装备磨损。现代副手、身体装备沿用护甲条件，现代消失诅咒继续生效。
- 骨君每 20 刻检验目标、治疗附近可见骷髅 1 点，并刷新 20 刻狂暴；狂暴额外伤害是基础攻击的 50%，不会叠加多次。原版骷髅也接收该状态。最多记录 6 个召唤名额，记录字节保存；部队死亡不自动返还名额，符合来源算法。召出部队保留 `-9600` 消失计数保护，玩家多点视线可见时刷新保护，回收需满足原加权距离条件。
- 小吸血蝙蝠只按目标实际失去的生命恢复自身；满生命后暂停动物/村民觅食 1200 刻，仍会攻击生存玩家。巨型吸血蝙蝠没有这次暂停。攻击接触箱横向缩半，每次尝试冷却 20 刻；未造成真实生命损失不会吸血。暗翼只接受银、魔法或阳光伤害，命中玩家提交 1.25 的视觉模糊增量，并在低亮度时按来源周期恢复。
- 吊挂位置新增同步天花板高度，保存原初次吊挂标志；巨蝙蝠要求下方两格空气，小蝙蝠要求一格，玻璃不可吊挂。旧 `Y>=1` 飞行边界适配现代世界最低高度，允许地底负高度正常活动；不把现代 `-64` 高度错当成旧版本空域外。

独立验收代码为 `UndeadBatMobGameTests`：使用实际原生攻击、死亡事件、装备堆、箭实体与保存恢复路径，并从源公式独立构造随机结果。规格常量不从生产 helper 回读作为期望。测试是否通过及客户端确认另由本批验收记录说明。

## 复跑与证据边界

```powershell
python tools/reference/extract_mobs_031.py --run-oracle --java "D:/Coding/huanjing/jdk/bin/java.exe"
```

无 JVM 重跑时，直接运行脚本可从已保存的 `.reference/runtime-helper/mobs-031-oracle.stdout.log` 重新形成规格。参考运行库、反编译源码和原压缩包仍留在忽略目录或用户工作区，不能放进模组 JAR。

本批提取器不依赖旧的未提交 `r196-entity-declarations.json`、`r196-combat-rules.json` 或现代内容研究目录。自然生成表中的数字是其对应源码调用位置的事实，仍需结合构造器和世界生成调用条件。完整现代机制、保存同步、战斗、掉落与用户实机验证由实现及验收记录另行确认。

## 原生蛛形父规则与普通蜘蛛骑乘

`EntityArachnid` 的库存、投网与跃击应用于原生 Spider / CaveSpider；原生死亡少量掉落与投网读写同一保存库存，不另外生成一份线数量。普通蜘蛛投网间隔 500 刻、洞穴蜘蛛 200 刻，时钟使用 `tickCount+entityId*47`。投网先检查眼部路径，阻塞后检查目标四分之一身高；液体与方块形状阻塞，最近实体必须是实际目标。跃击范围严格 `2<distance<6`，每刻一次 `nextInt(10)==0`，然后检查在地面；垂直 0.4、水平 0.4 加原水平速度的 0.2。

`te` 明确覆盖 `peacefulDuringDay=false`，并在目标未死亡且实际损血后施加 **480 刻**毒，与现代难度毒时长不同。普通蜘蛛需要严格亮度大于 0.5 与来源局部户外开放路线才进入日间中立，玩家反击目标继续保留。来源玩家获取不要求视线，鸡猎物要求可见；原生现代蜘蛛的仅按室内亮度中立和主动寻找铁傀儡不替代这些条件。

`tt.a(oi)` 是普通蜘蛛的独有生成完成回调，不能让所有继承现代 `Spider` 的新生物都触发它：

- 基础 `og.a(oi)` 使用**实体随机** `nextGaussian()*0.05` 添加寻敌范围乘基数修正。
- 普通蜘蛛的 1% 骑乘使用**世界随机** `q.s.nextInt(100)`。地下世界构造 `EntityLongdead`，其他来源维度构造 `tr`；每次完整完成生成后，丢弃非 `SkeletonType=0` 的候选，接受持弓个体。
- 只有 `tt` 具有困难模式群组增强回调。来源 `tu.a(Random)` 实际 `nextInt(4)`：0/1 为速度、2 为力量、3 为再生；其后面的隐身分支实际不可达。来源效果时长是 `Integer.MAX_VALUE`。洞穴蜘蛛和新增四种来源蜘蛛只执行 `og` 的基础回调，不能继承现代普通蜘蛛骑乘/药水。
- 生成原因保存为 NeoForge 元数据；候选骑手的 `FinalizeSpawnEvent` 或实体加入被取消时立即停止，不通过重抽绕过取消。

**明确的现代安全适配：** 在来源规则下，用命令将普通蜘蛛放进下界时，`tr` 每次固定抽到 type1，旧骑乘筛选循环永远不能找到 type0。本批最多进行 64 次筛选，然后让最后一个已获准完成生成的普通骷髅使用来源 type0 弓装备，避免服务端卡死；该行为不是原包能终止返回的结果。地下世界 64 次均未抽到持弓远古骷髅时跳过骑乘，避免其他模组干预导致无限循环。正常可达来源维度仍执行完整拒绝抽样。

`NativeArachnidGameTests` 覆盖实际原生回调、实体时钟事件、投射物、独立世界/实体随机种子、共享库存与保存、取消路径，以及新四种蜘蛛没有错误继承普通蜘蛛回调。验收结果由根任务实际运行日志记录，不以源码存在声称通过。
