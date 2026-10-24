# 简易刀剑兼容

开发：NingMeng123。适配 Simply Swords NeoForge 1.70.2 / Minecraft 1.21.1。

自动验证与实际客户端渲染记录见 [兼容验证](testing/simplyswords-compatibility.md)。

安装 Simply Swords 及它的依赖后自动启用；MITE 不内置它的代码或模组文件。不安装 Simply Swords 时，不注册兼容武器、不加载对应配方，怪物装备抽取也不消耗额外随机数。

## 独特武器禁用

同时加载时禁用风暴使者（`stormbringer`）等全部独特命名武器，包括未觉醒、觉醒和进化形态。按原模组独特武器基类识别，不依赖掉落白名单或物品名称；普通 15 类武器、符文系列、木制玩具及符文石板、宝石等材料继续沿用各自现有规则。

独特武器不出现在创造分类、创造搜索及 JEI 中，移除其产物配方、交易和掉落。同时移除只接受独特武器的 `simplyswords:unique_melting` 熔炼配方，避免在材料配方页显示已禁武器；残魂材料本身继续保留。已存在于背包、装备、容器（含潜影盒与收纳袋）或地上的独特武器会在原有装备清理流程中移除，交互和攻击入口也会拒绝使用。注册 ID 保留，避免旧存档出现缺失注册项。

## 武器与贴图

长剑、双头刃、细剑、武士刀、钗、长矛、长柄刀、战刃、弯刀、巨剑、巨斧、巨锤、环刃、战镰、戟，共 15 类。新增锈铁、铜、银、远古金属、秘银、艾德曼、振金七套共 105 把，金武器复用 Simply Swords 原有的 15 把。新增武器位于 MITE 战斗分类，注册名为 `mite:simplyswords_<材质>_<类型>`。

贴图按 Simply Swords 原有每类武器绘制 MITE 材质，保留原轮廓、像素明暗、比例、握柄、装饰和巨锤的三维模型 UV。已有金属色板取自现有 MITE 材质；振金使用参考版本的深蓝紫金属、青白高光，并以 [振金色板研究图](art/vibranium-palette.png) 确定七级明暗。素材来源及每个文件的来源记录在 `src/main/resources/mite/simplyswords-compat-assets.json`，保留原模组美术署名。

原模组剑类武器接入 MITE 耐久与品质。原生金属按对应 MITE 材料计算；钻石、下界合金沿用现有现代材质映射，符文使用 32 倍材料系数。独特武器保留旧存档所需的注册与耐久定义，但不再允许获取或使用。木制玩具仍使用木材系数。

钗按匕首、巨斧和戟按战斧、巨锤按战锤、战镰按镰刀计算组件与损耗，其余按剑计算。普通攻击只消耗一份 MITE 耐久，同时保留 Simply Swords 原打击音效、符文与宝石效果。

耐久分类不决定武器长度。安装 Better Combat 时，玩家与持械怪物读取每把简易刀剑实际解析的基础距离；默认同高振金长柄刀为 3.5 格、弯刀为 2.75 格，不统一套用普通原版/MITE 剑的 2.25 格。显式实体范围属性优先于武器范围配置，距离倍率、高差和当前招式限制继续生效；挖掘距离不受这项适配影响。未安装 Better Combat 时保留原有工具族回退规则。

投掷长矛在原模组批准命中后扣除一份对应的 MITE 攻击耐久，未命中和被拒绝的伤害不扣损，同一目标去重沿用原规则。回收和投射物存档保留同一把矛的品质及损耗，创造模式免损，破损矛不会回收成另一把武器。

## 双手武器负重

2026-10-24改为[按15家族分配伤害、最终攻速与玩家负重](equipment-balance-simplyswords-2026-10-24.md)：巨剑／巨斧／巨锤移速代价分别20%／22%／25%，普通双手武器15%；副手和背包不施加负重，持械怪物也不承受玩家负重。原有20%双手攻速属性由基础速度精确补偿，最终采用新表中的攻速，重复读取不叠加。品质、耐久和武器被动继续运行。

普通双手武器包括双头刃、武士刀、长柄刀、巨剑、巨斧、巨锤、战镰和戟。原生金属、符文、独特武器及新增 MITE 金属均按实际双手属性识别；例如 `soulrender` 与 `soulstalker` 虽然归入镰刀耐久族，实际为单手，不施加双手惩罚。长矛沿用原模组的单手动作设定。

安装 Better Combat 时优先读取它解析后的武器动作属性，支持数据包及武器动作预设覆盖；未安装时使用 `mite:simplyswords/two_handed` 物品标签。该标签由资源生成器根据原模组的 `two_handed`、父级继承和镰刀后备动作生成，所有条目均为可选引用。

## 制造与维修

90 个可制造武器配方沿用对应原铁武器的锭、粒和木棍数量，替换为 MITE 材料；其中振金武器使用振金锭和振金粒。锈铁仅作生成装备，没有合成配方。制造遵循 MITE 的工作台材料门槛、耗时、品质选择和经验要求。金使用原模组配方，并接入同一门槛。

新增金属武器以及原铁、金武器可在承载能力足够的铁砧上用对应金属粒维修，保留品质并执行 MITE 维修能力判定。原符文、独特、钻石、下界合金的原模组维修材料不被替换。附魔适用类型按 MITE 武器族判断，银和秘银保留吸血排除规则；Better Combat 动作继承对应原武器。

## 怪物装备

近战僵尸、骷髅、猪灵和卫道士等原生持械怪物在生成后的首次 tick 有 15% 概率使用兼容武器。已持有的普通 MITE 装备优先保持材质，替换时保留品质、附魔、损耗比例及主手掉落率。

空手怪物优先参考已有金属护甲；没有已确定材质时，备用抽取直接使用怪物装备的同一套日期材质分布。第 64 天以前仍仅使用锈铁；随后普通日与同一最高日期的血月共享档位，第 320 天以艾德曼为主，第 352 天起以振金为主，较高材质在早期也有较低概率出现。皮革、锁链阶段的刀剑映射为锈铁，其他阶段使用对应金属（包括原铁和振金）。15% 的换装概率及已经持久化的生成决定保持不变。当前规则见 [怪物装备生成](blood-moon-equipment.md)。

弓、弩、三叉戟、命名或定制装备、拾取后保证掉落的武器，以及 MITE 专属怪物的专用装备受到保护。生成决定持久化；读档、重新加载区块和跨维度不会重抽。命令生成的怪物保留明确给定的装备。

## 验证方式

资源重生成和独立校验：

```powershell
python tools/assets/generate_simplyswords_compat.py "run/mods/[简易刀剑] simplyswords-neoforge-1.70.2-1.21.1.jar" --check
python tools/testing/verify_simplyswords_resources.py "run/mods/[简易刀剑] simplyswords-neoforge-1.70.2-1.21.1.jar"
```

集成测试使用 `mite_simplyswords` 命名空间，可以在包含或不包含 Simply Swords 的独立目录执行：

```powershell
.\gradlew.bat runGameTestServer -PmiteGameTestDirectory=build/verification/simplyswords-present -PmiteGameTestNamespaces=mite_simplyswords
.\gradlew.bat runGameTestServer -PmiteGameTestDirectory=build/verification/simplyswords-absent -PmiteGameTestNamespaces=mite_simplyswords
```

开发客户端模型审核通过 `-PmiteSimplySwordsClientAudit=true` 启用，配合 `-PmiteClientDirectory=<独立运行目录>`。审核实际烘焙模型、贴图 atlas、105 张材质透明轮廓、巨锤三维面和各材质差异，生成报告后关闭该测试客户端。它不包含在发布包中。
