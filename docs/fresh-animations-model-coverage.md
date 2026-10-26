# Fresh Animations 模型覆盖审计

审计：NingMeng123，2026-10-20。

2026-10-26 后续：本页下表和数量记录保留 0.2.6 审计时的分类。四种蜘蛛现已通过专用模型与动态贴图适配接入资源包；七种共用旧人形模型现借用僵尸动画。最新实现及验证见[蜘蛛资源包兼容](spider-resource-pack-compatibility.md)和[类僵尸动画适配](zombie-variant-animations.md)，不再按下表旧分类视为待适配项。

同日 0.2.14 后续补丁进一步修复两种地狱苦力怕的随机 CEM 图集冲突，并为大力士、恶魂僵尸和红色食尸鬼补齐保形动画，见[特殊生物模型兼容](source-model-compatibility.md)。

## 范围与判定

检查主模组 `MobKind` 注册的 29 种生物及普通骷髅渲染器覆盖，以及扩展 `ExtremeKind` 注册的全部 45 种生物。依据当前渲染器、实际烘焙的 `ModelLayerLocation`、原模型网格和 UV，以及用户提供的 FreshAnimations 1.10.4 / FA+Player 1.1 ZIP。EMF 为 3.3.12。

这里的“已接入”表示代码会使用资源包提供的标准类别模型，或显式采样该模型的姿态。它是静态代码结论，不等同于本轮 GPU 实际渲染已经通过。没有专属资源包模型的特殊生物仍有原生待机、移动和攻击动作；“未接 FA”不表示它没有动画。

本次不复制、修改或分发第三方资源包内容，也不为 FA 未提供的物种编造专属动画。FA+Player 只影响玩家，与这些生物的模型类别覆盖无关。

## 主模组：29 种及普通骷髅

| 状态 | 生物 ID（`mite:`） | 实际入口与 UV | 判定 |
| --- | --- | --- | --- |
| 原本已接标准类别 | `revenant` | 标准 `ZombieRenderer` / `ZOMBIE`，64×64 | 使用 `zombie.jem`。 |
| 原本已接标准类别 | `hellhound`、`dire_wolf` | 标准 `WolfRenderer` / `WOLF`，64×32 | 使用 `wolf.jem`；保留自定义皮肤、体型及恐狼项圈绘制。 |
| 原本已接标准类别 | `infernal_creeper` | 标准 `CreeperRenderer` / `CREEPER`，64×32 | 使用 `creeper.jem`；充能层使用标准 `CREEPER_ARMOR`。 |
| 原本已接标准类别 | `netherspawn`、`copperspine`、`hoary_silverfish` | 标准 `SilverfishRenderer` / `SILVERFISH`，64×32 | 使用 `silverfish.jem`。 |
| 本轮修复标准层入口 | `longdead`、`longdead_guardian`、`bone_lord`、`ancient_bone_lord` | `LegacySkeletonModel` 改用 `ModelLayers.SKELETON`，64×32 | 原 `mite:source_skeleton` 层没有对应 FA 文件；原层与标准层均来自 `SkeletonModel.createBodyLayer`，改入口不改变原生网格。 |
| 本轮修复标准层入口 | `jelly`、`blob`、`ooze`、`pudding` | 主体改用 `ModelLayers.SLIME`，64×32；外层原本已走标准 `SLIME_OUTER` | 原 `mite:cube_inner` 与标准层均来自 `SlimeModel.createInnerBodyLayer`，可直接使用 `slime.jem`，同时保留各自皮肤与尺寸/挤压渲染。 |
| 保留独立原生动作 | `ghoul`、`wight`、`invisible_stalker`、`shadow` | `LegacyStalkerModel` / `mite:stalker`，64×32、4 像素四肢 | FA 没有这些物种的专属文件。可另行借用僵尸姿态，但不能把 64×64 的 `zombie.jem` 几何直接套到其 64×32 皮肤。 |
| 保留独立原生动作 | `fire_elemental`、`earth_elemental`、`clay_golem` | 同为 `LegacyStalkerModel` / `mite:stalker`，64×32 | FA 没有元素生物/黏土傀儡专属文件。人形外观可另做姿态借用；火元素本体透明，另需保留材质、轮廓及发光层语义。 |
| 保留独立原生动作 | `demon_spider`、`wood_spider`、`black_widow_spider`、`phase_spider` | `LegacyArachnidModel` / `mite:arachnid`，64×32 | FA 有 `spider.jem`，但原模型刻意保留未镜像的左腿 UV；FA 左腿使用镜像 UV，且改变腿轴及子节点。完整切换会改变这些细节；简单复制标准腿的位置会错位。 |
| 保留独立原生动作 | `vampire_bat`、`giant_vampire_bat`、`nightwing` | `LegacyBatModel` / `mite:legacy_bat`，旧式 64×64 网格 | FA 的现代 `bat.jem` 为 32×32，头身、翼轴及翼尖层级均不同，不能直接替换。 |

普通 `minecraft:skeleton` 的自定义渲染器也从 `mite:source_skeleton` 改用标准 `ModelLayers.SKELETON`。其原皮肤选择和凋零型 1.2 倍缩放继续由渲染器处理。

本轮静态结果：29 种中 15 种走标准 FA 类别入口；其余 14 种保留独立原生动作，其中 7 种通用人形没有专属 FA 类别，4 种旧蜘蛛与 3 种旧蝙蝠需要专门保形适配。普通骷髅另计。

## 扩展：45 种

| 状态 | 生物 ID（`modern_ite:`） | 实际入口与说明 |
| --- | --- | --- |
| 标准僵尸类别，17 种 | `zombie_door`、`miner_zombie`、`zombie_chaos`、`zombie_jump`、`zombie_knockback`、`zombie_invisible_look_up`、`zombie_pumpkin`、`zombie_iron_bucket`、`zombie_siege`、`random_zombie`、`fishing_rod_zombie`、`mirror_zombie`、`mimic_zombie`、`zombie_boss`、`zombie_elite_lord`、`undying_zombie_emperor`、`zombie_lord` | 标准 `ZombieRenderer` / `ZOMBIE`，FA 可识别。倒立、透明及装备等效果还由各自渲染逻辑处理。 |
| 标准骷髅类别，10 种 | `exchanger`、`mirror_skeleton`、`skeleton_fast_arrow`、`skeleton_knockback`、`skeleton_lightning`、`skeleton_floating`、`skeleton_blaze`、`wither_skeleton_arrow`、`skeleton_elite_lord`、`annihilation_skeleton` | 标准 `SKELETON`，凋零骷髅射手为 `WITHER_SKELETON`；使用相应 FA 文件。 |
| 标准苦力怕类别，5 种 | `invisible_creeper`、`invisible_reversed_creeper`、`black_infernal_creeper`、`flash_creeper`、`wither_creeper` | 标准 `CREEPER`，FA 可识别；透明/颠倒等效果独立保留。 |
| 标准类别，2 种 | `dark_iron_golem`、`zombie_pigman_lord` | 分别为标准 `IronGolemRenderer` 与 `ZOMBIFIED_PIGLIN`，FA 有 `iron_golem.jem`、`zombified_piglin.jem`。 |
| 本轮新增姿态驱动，1 种 | `skeleton_zombie` | 可见模型保留 `modern_ite:skeleton_zombie` 的 64×64 粗四肢网格；隐藏标准骷髅模型采样 FA 姿态，避免套用 `skeleton.jem` 的 64×32 窄四肢几何。 |
| 本轮新增姿态驱动，2 种 | `witch_lord`、`wandering_witch` | `SourceWitchModel` 保留原 64×128 皮肤、眼部发光与连通帽子；从标准 `WITCH` 模型采样并映射头部及帽子动作。FA 虽同为 64×128，但其实际头部改挂在躯干下，不能只复制原生 head 节点。 |
| 保留独立原生动作 | `zombie_caller` | `CallerModel` / `modern_ite:zombie_caller`，64×80、宽躯干、长臂及偏移肩轴。以后如借用 zombie 姿态，应转换各部位初始轴偏移，保留体型和受伤动作。 |
| 保留独立原生动作 | `zombie_fly_away` | `VariantZombieModel` / `modern_ite:zombie_fly_away_uv64`。原 PNG 是 64×32，但原模型刻意以 64×64 UV 取样并留空 hat；另有双臂上勾拳，不宜换成标准 CEM 几何。 |
| 保留独立原生动作 | `red_ghoul` | `ZombieModel` / `modern_ite:legacy_zombie`，64×32。可另行采样 zombie 姿态，不能直接改用 64×64 的完整 FA 模型。 |
| 保留独立原生动作 | `zombie_ghast`、`dragon_zombie` | `VariantZombieModel` / `modern_ite:headless_zombie`，64×64 空头网格，另绘制恶魂头/龙头层。以后只应借用躯干和四肢姿态，不重新长出标准僵尸头。 |
| 保留独立原生动作 | `run_creeper`、`phase_creeper`、`chaos_creeper` | 各自 `VariantCreeperModel` 自定义层，64×32，但奔跑者腿长、相位者细长体型、混乱者箭环/头部摇摆不同。FA 有通用 creeper 动作；进一步接入需保留初始轴、比例和附加部件。 |

扩展静态结果：34 种本来走可识别的标准类别；3 种本轮增加保留原几何的姿态驱动；其余 8 种保留独立原生动作。`ExtremeKind.Family` 虽有 `GHAST`、`PILLAGER` 枚举，但当前 45 个注册项没有使用这两类。箭、投射火球及主模组投射蛛网不属于本表生物动画目标。

`fishing_rod_zombie` 的大头比例、`zombie_door` 的门盾姿态属于“FA 绘制阶段是否覆盖物种附加姿态”的单独检查项。两者已经命中标准 zombie 类别，不应计为漏接资源包；需要通过实际渲染检查这些功能是否保持。

相位苦力怕的紫色发光眼另修复了资源包纹理冲突：原来借用 `minecraft:textures/entity/enderman/enderman_eyes.png`，FA 将该纹理的可见像素迁到其自定义眼部 UV，导致原生苦力怕前脸采到透明区域。现改用扩展自有 `modern_ite:textures/entity/source/phase_creeper_glow.png`，在 64×32 透明底的苦力怕前脸 UV 上绘制 8 个紫色像素，避免其它物种的资源包眼部布局影响它。该遮罩为程序生成的独立素材，没有复制 FA 纹理；它只修复眼睛，不改变相位苦力怕保留原生动作的分类。

## 特殊模型后续接入的边界

- 同网格、同 UV 的层可切换到对应标准 `ModelLayers`；本轮主骨架和方块属于此类。
- 人形模型不同 UV 或不同体型时，可用隐藏标准模型采样，再把姿态转换到原模型。需要保留原生初始轴、缩放、特殊头部和物种攻击；Better Combat 仍应在最终攻击阶段拥有受控骨骼。
- 旧蜘蛛的 FA 腿轴位置依赖自定义子模型抵消，头/腹和腿尖也在子模型上动画；旧蝙蝠的主要翼动作位于 FA 的 `left_pivot` / `right_pivot`、`*_wing2`。不能把这些模型当作普通人形那样直接复制顶层姿态。需明确转换整条部件链，或由用户选择接受完整 FA 几何。
- 没有 FA 专属文件的通用人形可继续使用原动作。若选择借用 zombie 等已有类别，应明确说明借用关系，不宣称资源包原生支持这些自定义物种。

## 本轮实际渲染验证

使用原始 FA 1.10.4 / FA+Player 1.1 ZIP、EMF 3.3.12 / ETF 7.2.6，以及 0.2.6 的隔离构建，主客户端通过 50 行实体、250 个渲染样本。六类指定骨架均执行真实 FA 待机/走路，四种方块的主体和外层也实际执行 EMF 动画；护甲、衣物、共享模型与攻击恢复检查通过。方块没有人形近战或走路骨架，相关列只记录其模型渲染，不将其声称为持械攻击。

扩展在安装 FA 和未安装 EMF 的两个独立实例中，分别通过 45 个注册实体的纹理读取及 15 个重点实体绘制。两种女巫各 15 个原生方块的几何和 UV 完全一致，真实 FA 头部映射及帽子支点检查通过，暗光头像红眼像素均超过 70。骷髅僵尸的 7 个原生方块保持 64×64 几何及 UV，原生游泳头部连续两帧插值与旧 SkeletonModel 完全一致。相位苦力怕独立紫眼通过可见发光差异检查，修复前为 0。

记录为 `fa-followup-combat.json`、`fa-followup-witch-fa.json` 与 `fa-followup-native.json`，相对主仓库 `build/verification`。主模组 1666 项 JUnit 和扩展 30 项 JUnit 均通过；交付为 `build/releases/mite-0.2.6-fa-models.jar` 与 `modern-ite-0.2.6-fa-models.jar`。

这些 GPU 样本不替代对其余特殊模型的适配，也不将静态标准层命中等同于全部 74 种生物的逐项视觉验收。完整说明见 [0.2.6 回归记录](testing/fresh-animations-models-026.md)。
