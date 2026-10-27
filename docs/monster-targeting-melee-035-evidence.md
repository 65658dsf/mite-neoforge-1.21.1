# R196 怪物寻敌、目标恢复与近战依据（035）

开发署名：NingMeng123。原作与原始行为来源：Avernite 的 Minecraft Is Too Easy R196。

以本地 `MITE 1.6.4 R196.zip` 为基准；SHA-256 为
`fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。
本文件记录方法与数值，不转载反编译方法正文。

## 来源与运行入口

| 来源 | 已核对的方法与行为 | 现代实现 |
| --- | --- | --- |
| `og` | `getMaxTargettingRange`：默认 16，读取跟随范围属性；被玩家或驯狼命中的 `aT > 0` 时乘 2 | `SourceMonsterTargeting.effectiveRange`，读取现代 `lastHurtByPlayerTime`，不误用受伤动画 `hurtTime` |
| `re` | 初选排除不适合的目标；可挖的 `EntityAnimalWatcher` 跳过视线检查；维持目标真距离超过范围时结束；正常视线丢失计数超过 60 才结束 | `SourceTarget`；独立 GameTest 验证第 60/61 次更新边界 |
| `qy` | 初选 AABB 水平为跟随范围、垂直 6；玩家查找概率参数为 0；每次 `nextInt(40)==0` 可重选可见的最近目标 | `Nearest`；原版构造器的默认随机 10 与垂直 4 不作为替代 |
| `qy` | 僵尸查动物时水平与垂直范围减半；排除地狱犬，僵尸拒绝距离大于 8 的狼；攻击骑在可伤害生物上的目标时改为攻击其坐骑 | `Nearest` 的族别条件与坐骑转换 |
| `qy` | 普通列表为空后，户外且能挖掘的观察者可找垂直 6 至 14、水平距离平方小于 16 的玩家；持弓骷髅可找垂直 6 至 20、水平范围内且可见的玩家 | `Nearest` 的高处玩家回退分支 |
| `qx` | 受击复仇与普通寻敌独立；启用同伴协作时查询自身位置单位立方体扩展 `range,10,range`，只通知原始运行时类及其子类、无现有目标且不与攻击者结盟的同伴 | `Revenge` 按原始来源类层次识别同伴，避免现代共享 Java 类将尸鬼与亡魂混在一起 |
| `og`、`nn` | 保存目标 UUID；载入后每原始 10 tick 仅在自身 AABB 扩展 `32,8,32` 查找；第 1000 tick 仍未解析则清除 | `SourceMonsterTargetMixin` 与 `restorePendingTarget`；不跨越附近范围使用全世界 UUID 查询 |
| `og`、`qy` | 没有目标时外部指定目标进入临时 `AI_retarget`，下次普通目标任务优先消费，先于类别和随机检查 | `SourceMonsterTargetMemory` 的临时引用；只在 NeoForge 接受后的实际目标字段写入点记录，取消的目标变更不产生幽灵引用 |
| `qa` | 近战攻击冷却 20；重算路径间隔 `4+nextInt(7)`；没有现代额外的 `canUse` 世界时间 20 tick 节流 | `SourceMeleeGoal` |
| `qa` | 近处无法打到时每 offset 10 tick 尝试以速度 2 靠近；仙人掌阻挡会取消该动作；附近掉落方块使攻击与抑制计时至少为 10 | `SourceMeleeGoal.canUse` 与 `suppress` |
| `qa`、`EntityAIWatchAnimal` | 挖掘任务优先，但发现已经可以立即近战时允许 `qa` 接管；高于自身的目标可要求挖目标脚下方块 | `tickWhenDigging`、`R196DiggerGoal.setBlockToDig` 桥 |
| `og`、`of` | 新 AI 怪物近战范围为 `1.5 + 工具触及加成×0.6`；以到目标脚底的三维真距离比较；从攻击者 75% 高度分别检测目标 50%、90%、10% 高度，任意一条物理射线通畅即可 | `canStrike`；视觉透明并不意味着可以隔玻璃攻击 |
| `of`、`RaycastPolicies`、`Raycast` | 视觉射向目标 25% 和 75% 高度；玻璃、冰、门、栅栏与栏杆不遮挡视觉；植物/树叶阻抗每块 50，累加到 100 才遮挡；不是随机 50%；旧目标重寻可忽略树叶 | `canSee`；四个原点微偏移分别计算阻抗，保留原始射线边缘检测意图 |

## 族别与边界

只为 R196 中实际启用新 AI 调度的族安装这些目标任务。原版普通与村民僵尸、亡魂等四种亡灵、土/火元素、各类骷髅、苦力怕与女巫按各自来源构造器配置。猪人、蜘蛛、蠹虫等旧 `on` AI 不替换成这套 `qy/re` 任务；其共享目标 UUID 恢复仍可使用同一桥。

尸鬼、僵尸具有玩家、村民、动物三组目标；土元素具有玩家和村民两组；亡魂、隐形潜行者、暗影、火元素、骷髅及女巫主动寻找玩家。亡魂等构造器存在村民近战任务，不代表它们存在主动寻找村民的目标任务。苦力怕玩家目标优先级 1、复仇优先级 2；其他上述来源族通常相反。骷髅、女巫与苦力怕的复仇不自动召集同类，其他明确传入 `true` 的来源族才协作。

原版与新增 MITE 工具从已提取的 `MiteTools.Definition.reach` 读取触及加成。新版本独有装备未出现 R196 来源触及数值时使用该机制的基础范围，不虚构额外武器加成。

## 现代适配

- 2026-10-27 按用户反馈加入受击转火：已有目标的来源怪物和使用常规报复目标任务的原版怪物，受到同世界、可见、合法玩家的有效伤害时，优先反击新攻击者。创造、旁观、同队、无效或取消的伤害不触发；猪灵等 Brain/中立物种继续使用专有仇恨规则。
- 目标切换仍通过可取消的 `Mob.setTarget`；只记录最终接受的玩家目标，停止旧导航并取消对旧目标尚未命中的武器攻击，保留已消耗的攻击恢复时间。新反击目标获得 100 游戏刻的短期保持，防止来源最近目标的随机刷新立即抢回；新的合法命中仍可再次转火，不按单纯距离每刻反复争抢目标。该保持状态不写入存档，目标失效后不继续保护。

本次 `MonsterRetaliationGameTests` 验证正在运行的报复任务从 A 转 B 后连续选择器更新、C 再次命中、玩家箭命中且保留远程任务、短期保持到期、取消/零伤/完全吸收、创造/旁观/同队/遮挡、目标事件取消与重定向，以及 Better Combat 转火取消旧蓄力且保留原恢复边界。2026-10-27 普通与 Better Combat 模式各 165 项必需 GameTest 全部通过，完整日志见 `build/verification/monster-ai-20261027-verified-runtime.log` 和 `build/verification/monster-ai-20261027-verified-bc-runtime.log`。双人手测时让 A 先被追击，再由近处 B 实际命中；怪物应转向 B，B 停止攻击后不应因旧任务立即回到 A，C 再命中仍可触发新的反击。真实客户端联机尚未手测。

- 2026-10-27 用户追加反馈的存档快照确认了主动发现阶段的两个问题：卫道士与生存玩家相距约 14.74 格，实际 `FOLLOW_RANGE=12.13668`；掠夺者被当时的早期出生装备锁清空弩，原生弩攻击任务无法启动。两者均为 `NoAI=0`，玩家脚底高两格。
- `NativeIllagerCombat.join` 将新生成及旧存档卫道士的跟随范围基础值补到至少 32，保留更大的自定义基础值与原有属性修正。`NativeVindicatorTargetRangeMixin` 在现有原版 `findTarget` 入口刷新原有 `TargetingConditions.range`，使构造时缓存的旧范围跟随真实属性；不替换选择谓词、视线、隐身可见比例和队伍规则。
- 用户随后要求完全取消早期禁装备。从首日起保留原生装备、已有概率和隐藏备用武器，旧锁入场退役并有限修复明确缺失的原生武器，见 [装备规则](blood-moon-equipment.md)。掠夺者失去弩时仍保留唯一的 `UnarmedPillagerMeleeGoal`，接入既有原版地面近战追击；任一手持真实 `CrossbowItem` 即停用近战回退，使用原有弩攻击任务，重复加入世界不重复安装回退。

新增 `VanillaMonsterAcquisitionGameTests` 八项覆盖真实注册玩家创造转生存后的自然主动发现与实际伤害：卫道士 14/16/24 格、旧存档基础值 12 的修复、更大基础值 48 在 40 格的发现、实际范围内外及实心墙首次视线边界、首日自然持弩的掠夺者实际远程攻击、重复加入世界的回退唯一性，以及明确卸下弩、经两级真实一格台阶登上两格高台近战击中玩家并重新持弩恢复原生远程。新生实体经完整 FinalizeSpawn 事件，旧 NBT 经实际 `addLegacyChunkEntities` 加载事件；主链执行完整世界 tick，不手动赋予目标。

最终构建与 1,770 项 JUnit 通过，普通模式及 Better Combat 2.4.0 模式各 206 项必需 GameTest 通过，实际 Modern ITE 0.2.15 下 18 项自然索敌、旧档加载和装备兼容测试通过。对应日志为 `build/verification/native-aggro-20261027-verified-runtime.log`、`native-aggro-20261027-verified-bc-runtime.log` 和 `native-aggro-20261027-verified-ite-runtime.log`，产物及逐类校验记录为 `build/verification/native-aggro-20261027-acceptance.json`。此前两种模式各 165 项的日志保留为前一轮增强追击验证记录。

- 保留现代世界负数高度、当前方块形状、已加载区块和现代寻路器；显式传入来源寻路距离，而不是传错现代 `createPath` 的到达精度参数。
- 使用现代方块族映射草、作物、树叶、门、玻璃与栏杆。新加入的遮光玻璃保留现代遮挡，不因为其透明方块基类而被误当作 R196 普通玻璃。
- 近战靠近由现代 MoveControl 执行；不在 `Goal.canUse` 中额外执行一次实体物理移动，避免单刻重复移动。
- 不移植旧版锦标赛、幽灵玩家或已不存在的特殊用户名例外；现代创造、旁观、无敌和队伍限制由现代实体接口处理。
- 女巫可借用 32 格三维范围内蝙蝠的视野，持续 20 tick；这是临时感知缓存，不写入存档。
- 新增目标 NBT 含版本 1。最后伤害者记忆与当前目标 UUID 是两个不同来源字段；本桥不把二者合成一个计时器，也不借目标恢复伪造一次新的受击复仇事件。

## 本地 API 核对

已读取固定的 Minecraft 1.21.1 / NeoForge 21.1.259 生成源码。
`Mob.setTarget` 先分发可取消 `LivingChangeTargetEvent`，仅未取消时实际写入 `target`，因此临时重选队列在实际写入点记录。
`PathNavigation.createPath(BlockPos, int, int)` 的第二参数是到达精度、第三参数才是显式寻路范围。
`GroundPathNavigation.canOpenDoors()` 在此固定版本实际返回 `nodeEvaluator.canPassDoors()`；门规划验收需读 `getNodeEvaluator().canOpenDoors()`，不能用该命名有歧义的 getter 代替。

对应自动验收：`MonsterTargetMeleeGameTests`。实际运行结果由本批统一验证文档记录；本文件不将“已编写”测试冒充“已运行通过”。
