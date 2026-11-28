# 振金资料来源与现代适配

开发者：NingMeng123

本次来源同时包含用户提供的 Extreme LH 0.6.6 附加包，以及用户链接的 MITE：破晓条目。两者并非同一版本，下面分别记录事实和本项目采用的适配方式。附件只做 ZIP 读取和静态反编译，没有执行其中的 class。

## 固定来源

附件 `.reference/[极难柳昊定制]Extreme-lh-0.6.6 (3).jar` 的 SHA-256 为 `594c152bb7cfe655f9b0744aea2ed45d71696779cd842c71be2501189c540d98`。其 `fml.mod.json` 声明依赖 `mite_ite >= 2.0.25`；振金材料、护甲、工具和弓的实际定义由该基础模组提供。

基础资料固定到 [MITE-ITE 2.0.25](https://github.com/MinecraftIsTooEasy/MITE-ITE/tree/61cbf7c954fab8ac941fb5a24b74e2ec32c49766)，提交 `61cbf7c954fab8ac941fb5a24b74e2ec32c49766`，不以可变的 master 分支作为构建输入。上游元数据列出的创作者为 XiaoYu233 和 Xy_Lose；原始资源归属上游项目，本项目的移植开发署名为 NingMeng123。上游许可证为 LGPL 2.1，完整原文随资源保存在 `src/main/resources/META-INF/licenses/miteite-LGPL-2.1.txt`。

原始路径、固定 raw URL、Git blob、SHA-256、字节数及目标路径逐文件记录在 `src/main/resources/mite/vibranium-native-assets.json`。其中 62 张 PNG 和 46 个动画元数据文件均按原始字节复制，另附许可证一份；不改变尺寸、透明度、动画帧或原始颜色。

```powershell
python tools/assets/import_vibranium_assets.py
python tools/assets/import_vibranium_assets.py --offline
python tools/assets/import_vibranium_assets.py --verify
```

## 材料与装备数值

[EarlyRiser.java](https://github.com/MinecraftIsTooEasy/MITE-ITE/blob/61cbf7c954fab8ac941fb5a24b74e2ec32c49766/src/main/java/net/xiaoyu233/mitemod/miteite/EarlyRiser.java) 注册的材料耐久系数为 `512`、附魔能力为 `120`、品质上限为 `EnumQuality.legendary`。

[MaterialVibranium.java](https://github.com/MinecraftIsTooEasy/MITE-ITE/blob/61cbf7c954fab8ac941fb5a24b74e2ec32c49766/src/main/java/net/xiaoyu233/mitemod/miteite/item/material/MaterialVibranium.java) 声明材料伤害 `9`、材料效率 `3.5`、采掘等级 `6`、材料保护值 `15`，修复材料为振金粒；材料不受岩浆伤害。`setMetal(false)` 的参数指是否受酸侵蚀，调用后仍是金属，因此采掘等级不会减为 5。[ItemBowTrans.java](https://github.com/MinecraftIsTooEasy/MITE-ITE/blob/61cbf7c954fab8ac941fb5a24b74e2ec32c49766/src/main/java/net/xiaoyu233/mitemod/miteite/trans/item/ItemBowTrans.java) 与材料接口共同确认振金弓耐久 `512`，箭矢速度加成 `75%`。

本项目的工具沿用既有 MITE 家族公式：基础伤害加材料伤害，家族效率乘 `3.5`，耐久为 `4 × 组件数 × 512 × 100`，继续使用原有的逐次磨损与品质倍率。这里的耐久单位包含原版 MITE 的 100 倍磨损精度，不能直接与破晓的普通工具耐久比较。

用户提供的[破晓振金套装条目](https://www.mcmod.cn/item/635978.html)列出的现代属性与旧版材料保护值采用不同的计量方式：

| 部位 | 破晓盔甲值 | 破晓韧性 | 条目击退抗性显示值 | 破晓耐久 | 本项目普通品质耐久 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 头盔 | 5 | 3.3 | 1.2 | 407 | 5120 |
| 胸甲 | 8 | 5.3 | 2 | 592 | 8192 |
| 护腿 | 7 | 4.7 | 1.8 | 555 | 7168 |
| 靴子 | 4 | 2.7 | 1 | 481 | 4096 |

本项目采用条目的 `24` 点总盔甲与 `16` 点总韧性；击退抗性按现代属性比例写入 `0.12 / 0.20 / 0.18 / 0.10`。旧版材料保护值 `15` 与上述现代盔甲值分开保存，避免混用。耐久和品质仍采用本仓库既有 MITE 公式；条目所称的最高“神器”不新增一个越过本仓库 `LEGENDARY` 的品质枚举。

## 配方事实与差异

附件 `cn/wensc/mitemod/extreme/register/EXItemsRegistryInit.class` 的振金锭配方为 `NIN / IDI / NIN`：四个秘银粒、四个艾德曼锭和一个钻石，产出一个振金锭。这也对应用户提供的[破晓振金锭条目](https://www.mcmod.cn/item/635952.html)中 V1.1.2 及之后的配方；更早的聚变催化剂配方不启用。

下表依据固定版本的 [MITEITEItemRegistryInit.java](https://github.com/MinecraftIsTooEasy/MITE-ITE/blob/61cbf7c954fab8ac941fb5a24b74e2ec32c49766/src/main/java/net/xiaoyu233/mitemod/miteite/item/MITEITEItemRegistryInit.java)，以及附件的 `RecipesToolsMixin.class` 和 `RecipesWeaponsMixin.class`。`I` 为振金锭，`S` 为黑曜石棒，`A` 为被升级的原装备；空格是配方矩阵中的空格。

| 产出 | 从上到下的矩阵 | 原装备 A |
| --- | --- | --- |
| 头盔 | `IAI` / `I I` | 艾德曼头盔 |
| 胸甲 | `I I` / `IAI` / `III` | 艾德曼胸甲 |
| 护腿 | `IAI` / `I I` / `I I` | 艾德曼护腿 |
| 靴子 | `A I` / `I I` | 艾德曼靴子 |
| 镐，Extreme 覆盖 | `III` / ` A ` / ` S ` | 艾德曼镐 |
| 剑，Extreme 覆盖 | ` I ` / ` I ` / ` A ` | 艾德曼剑 |
| 铲 | `I` / `A` / `S` | 远古金属铲 |
| 斧 | `II` / `AI` / `S ` | 远古金属斧 |
| 匕首 | `I` / `A` | 远古金属匕首 |
| 战锤 | `III` / `IAI` / ` S ` | 远古金属战锤 |
| 战斧 | `I I` / `IAI` / ` S ` | 远古金属战斧 |

振金弓为 `NSL / AVL / NSL`：`N` 为振金粒，`S` 为黑曜石棒，`L` 为丝线，`A` 为远古金属弓，`V` 为振金锭。黑曜石棒是两个黑曜石上下排列，产出一个。基础模组的装备升级配方使用 `extendsNBT().keepQuality()`；本项目升级配方保留原装备的品质、附魔、名称及相应组件，并按新耐久上限保留已损耗比例。

破晓网页展示的是按锭直接制造护甲，附件所依赖的基础模组则采用升级护甲：四件分别消耗 `4 / 7 / 6 / 3` 个新锭，加上对应的艾德曼装备。这里保留升级路线。

固定基础版本实际注册的振金家族只有上述七种近战工具、四件护甲和弓。上游还包含小刀、短斧、锄、鹤嘴锄、镰刀的纹理，但没有对应的振金注册项；剪刀、盾牌和十五种简易刀剑也属于本项目补全。它们沿用本仓库已有家族行为和振金材料公式，新增纹理单独记录，不标作逐字节上游资源。上游未注册独立振金箭；保留弓目录中的原始振金箭搭弦图，不据此增加可获得的振金箭物品。

振金锭可从现有艾德曼制作阶段取得，再制作振金工作台，最后制作振金装备，避免要求首块振金必须先拥有振金工作台而形成循环依赖。工作台、铁砧和储存块接入本项目现有对应系统。

## 怪物装备成长扩展

振金是艾德曼之上的最高材质档，艾德曼使用索引 8，振金使用索引 9。默认第 128 天进入艾德曼档，第 144 天进入振金档。普通日和血月使用同一日期的材质分布，各空槽按设置中的相应概率独立生成。每个档位都可能生成其他材质。新生装备的附魔概率和强度随最高日期提高。详见 [怪物装备生成](blood-moon-equipment.md)。

简易刀剑优先继承实际装备材质。需要抽取材质时，第 16 天起使用普通日和血月共用的分布。武器替换概率为 15%。确切概率见 [怪物装备成长](blood-moon-equipment.md)。
