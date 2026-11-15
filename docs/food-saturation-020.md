# 020：R196 营养与 AppleSkin 饱和度兼容

开发者：NingMeng123。MITE 原作：Avernite。版本依据为本地 R196 发布包；食品数值表和压缩包校验值保持不变。

## 当前规则（2026-11-14）

金色饱食层（营养及种子缓冲）先消耗，再消耗鸡腿饥饿条；两层耗尽后开始积累饿伤。实际扣减顺序为 **种子缓冲B → 营养N → 饱食值S**，其中AppleSkin金色叠层显示N＋B，鸡腿条读取原生 `foodLevel` 中的S。每次耗竭只扣一项，最后一点N耗尽后的下一次才扣S。

自然恢复以N大于零为条件，使用慢恢复公式，卧床倍率为8。旧 `mite_nutrition_hunger` 字段作为兼容数据保留，储备选择按B/N/S当前值处理。复测向量见[029消耗顺序](testing/029-food-consumption-order.md)。

## 用户选择与本轮边界

本轮按用户最新要求，食品摄入与消耗继续使用 R196 的饱食 S、营养 N。现代 `FOOD.saturation` 用于食品预览的兼容数据，不给普通食品新增第三份储备。AppleSkin 的饱和叠层显示既有 N，并合并小麦种子此前明确保留的缓冲；未安装 AppleSkin 不影响吃饭、存档、营养条或服务端判定。

小麦种子保留此前用户指定的例外：S/N=0/0，只加原版饱和度缓冲1，不增加蛋白、脂肪、植物营养或糖。本轮没有恢复旧种子的 S1/N0 与额外脂肪2000。

目录仍是 [019 食物目录](food-content-019.md) 的80个物品ID：64项 R196 来源、15项现代食品适配、1项种子用户适配。现代鲑鱼、兔肉、蜂蜜、甜菜等没有旧版原值，仍按明确的现代档案运行；不能把“消耗按 R196 双资源规则”写成全部现代食品都有 R196 原始数值。

## 原始字段与公式证据

| 来源 | 可确认的事实 |
| --- | --- |
| `ux.java:5–12`（R196 FoodStats） | 字段为 `int satiation`、`int nutrition`、`hunger`、`hunger_for_nutrition_only`、恢复和饥饿进度；没有现代 `saturationLevel` 储备 |
| `ux.java:14–24` | 初始 S=N=等级上限；`addFoodValue` 分别加食品 S、N，并由玩家补微量营养/糖抵抗 |
| `ux.java:45–58` | 代谢累积到4时按条件扣 S 或 N，没有先扣第三份饱和度的阶段 |
| `ux.java:64–85` | N决定饥饿和慢恢复，恢复成功另外加1饥饿累积；不是现代满饱和度快速恢复 |
| `ux.java:89–106` | 保存键是 `fullness`、`nutrition` 及代谢进度；不是现代 `foodSaturationLevel` |
| `yc.java:1114–1138` | 食品的 S/N 为两项独立整数；糖抵抗调整有效S，N单独返回 |
| `xx.java:40–47` | 来源食品完成使用调用 `player.addFoodValue`，只进入一次食品摄入 |
| `uf.java:1727–1740` | `isHungry` 为S=0，`isStarving` 为N=0，`hasFoodEnergy` 为S+N非0 |
| 固定1.21.1 `FoodProperties` / `FoodConstants` | `FoodProperties.saturation` 是绝对量；Builder的 `saturationModifier` 会计算 `nutrition×modifier×2`，两者不能混用 |

正常来源数值下，等级上限与摄入结果为：

```text
C = max(min(6 + (level / 5) × 2, 20), 6)  // 原Java整数除法
S_after = min(C, S_before + effective_food_S)
N_after = min(C, N_before + food_N)
```

原 `ux` setter只限制上界；生产对无效/越界现代输入额外防止负值，不能把这种防护写成原源码逐字公式。有效食品及正常资源状态下，上述数值不因此改变。S与N各自按C限制，**N可以大于S**，例如牛奶只加N。

原R196在无种子缓冲时，普通生存每tick累积饥饿 `H += 0.002F×湿冷/营养不良倍率`，营养专用累计 `HN += 0.0005F`。`H >= 4` 时每tick只处理一次，H减4。若S<1，或 `HN+0.001F >= 4` 且N>0，就扣1N并清HN，否则扣1S。N=0时饥饿进度每tick加0.002，N>0时恢复进度为 `(0.0004+0.00002×N)×营养不良倍率×睡眠倍率×再生倍率`。020阶段旧再生附魔尚未注册，当时生产对应附魔等级为0。

## 兼容数据与真实储备分离

| 现代接口/显示 | 本轮含义 |
| --- | --- |
| `FoodProperties.nutrition` | 食品的S基础值，用于饥饿图标等预览；真实糖调整和资格由服务端规则处理 |
| 普通目录食品的 `FoodProperties.saturation` | 食品N的绝对值，仅作为兼容预览；不是恢复现代原版饱和倍率 |
| `DualFoodData.mite$getNutrition()` | 唯一实际N储备，摄入、消耗、恢复、饥饿和保存均读取此项 |
| 原生 `FoodData.saturationLevel` | 既有种子例外缓冲B；普通目录食品不因预览元数据而给它加N |
| AppleSkin 当前饱和叠层 | `min(C, N+B)` 的兼容显示，不是把显示值重新存成第三储备 |
| AppleSkin 食品预测 | 分别计算实际N与种子B的变化，再按C显示；不把N限制到当前S |

2026-11-15 按用户要求，面包调整为 S2/N8，烤土豆调整为 S2/N6，即鸡腿饥饿条各恢复 2，AppleSkin 金色饱和层分别恢复 8、6。熟牛肉为 S10/N10，铁奶桶为 S0/N4。食品的 `FOOD.saturation` 分别为 8、6、10、4，实际食用按目录补充 S/N。状态命令中的“原版饱和度”读取种子缓冲 B，普通食品在 B=0 时通过 N 提供金色饱和层。

真实摄入统一走食品档案。`PlayerFoodMixin` 拦截普通 `Player.eat`；铁奶桶通过 `MilkNutritionMixin`，蛋糕片通过 `CakeNutritionMixin` 独立进入同一 `mite$ingest`。这些特殊入口不能为了更新兼容显示再调用一次原版eat，否则会重复加食物或退容器。外部食品/无物品的FoodData API有独立兼容政策，不能仅凭FOOD有某个字段就推定其来源微量营养或特殊效果。

种子仍满足 `B_after=min(S,B_before+1)`，例如S4/N3/B0吃一份后为4/3/1；S0时B上限也是0，不能凭空增加N或缓冲。B>0时一轮4点exhaustion先扣1B，随后按N→S继续消耗。只有这条明确例外提供额外缓冲，不能把所有食品N塞入B再消费一遍。

## AppleSkin 3.0.9 的可选客户端适配

对用户本地 `appleskin-neoforge-mc1.21-3.0.9.jar` 的只读字节码核实：`FoodHelper.query` 从 `ItemStack.getFoodProperties` 得到食品预览；`SaturationOverlay.render` 读取 `FoodData.getSaturationLevel`，默认把未来饱和度限制到“当前S＋食品S”。这条现代假设与R196独立N冲突，奶S0/N4、N>S时尤其明显。

本轮可选客户端Mixin将叠层当前值改为N＋既有种子B，未来N按等级C限制，图标数最多C/2。种子预测仍保留B≤S的例外上限，不能在S0时闪出不存在的+1；普通食物的N不使用这条S上限。AppleSkin现代快速回血预测关闭，实际慢恢复仍由R196规则负责。

适配仅在AppleSkin目标类存在时作用，无安装依赖；不更改全局FoodData getter含义，不用预览写回服务端储备。营养与B仍由已有同步包分别同步/保存，显示合计是客户端视图。

## 用户手动核对

自行重启客户端加载本轮逻辑，在允许命令的测试世界执行：

```mcfunction
/gamemode survival
/difficulty normal
/experience set @s 35 levels
/effect clear @s
/mite food set reserves @s 0 0
/mite food set saturation @s 0
/mite food set nutrients @s 160000 160000 160000
/mite food set resistance @s 0
/give @s minecraft:bread 2
/give @s minecraft:cooked_beef 2
/give @s minecraft:milk_bucket 2
/give @s minecraft:wheat_seeds 8
```

35级C=20。每种食品前重设S/N=0/0、B=0，避免上种食品影响结果；吃/喝后用 `/mite food status @s` 读实际数值。自然代谢可能在操作间扣资源，记录前后时间，不用界面闪烁代替服务器数值。

| 测试 | 应看到的结果 |
| --- | --- |
| 手持面包预览，再吃一份 | 鸡腿预览 +2、金色饱和层预览 +8；实际 S2/N8/B0，消耗一份面包 |
| 手持烤土豆预览，再吃一份 | 鸡腿预览 +2、金色饱和层预览 +6；实际 S2/N6/B0，消耗一份烤土豆 |
| 手持熟牛肉预览，再吃一份 | 预览10/10；实际S10/N10/B0，不再额外加10缓冲 |
| 手持铁奶桶预览，再喝一桶 | 预览0/4；实际S0/N4/B0，退1铁桶；N大于S也不能被裁为0 |
| `/mite food set reserves @s 1 6`，B设0 | 当前兼容叠层显示N6，而不是被S1限制；拿奶桶时预测N升至10，饮用后实际S1/N10 |
| `/mite food set reserves @s 4 3`，B设0，吃一份种子 | 实际S4/N3/B1；叠层从3到4，仅变化1，P/F/V不增加 |
| 同前设B4再吃种子；另设S0/N6/B0吃种子 | B已达S上限时不再增长；S0时B仍0/N6，预览不能显示额外+1 |

设置S6/N2/B0、微量营养满，在干燥环境站立等待，必要时 `/tick rate 200` 加速，结束恢复20。每次耗竭累计达4，状态依次为 **S6/N2 → S6/N1 → S6/N0 → S5/N0**，金色叠层随同一N下降，随后鸡腿条下降。每次扣减的实际间隔由活动、湿冷、营养不良、回血和总体食耗倍率共同决定。营养N归零后自然恢复暂停，饱食层与鸡腿条均耗尽后积累饿伤。

未安装AppleSkin：自行退出客户端，将苹果皮JAR临时移出当前测试配置的mods目录后重启，再吃面包、熟牛肉、喝奶桶。S/N、容器和保存结果应相同，仅AppleSkin叠层不出现；测试后可放回JAR重启。此操作由用户手动执行，本轮没有替用户关闭或操作游戏。

## 历史实现与验收记录

### 2026-11-13 的来源消耗调整

该阶段曾采用B先吸收耗竭、通常随后消耗S的规则。HN累计达到阈值或S清空时扣N，N为零时开始积累饿伤。对应调整已经归档在[029消耗顺序历史记录](testing/029-food-consumption-order.md#历史记录)。

### 2026-10-13 的020兼容实现

2026-10-13，`gradlew.bat build compileGametestJava runGameTestServer` 成功：**268 项 JUnit、241 组 GameTest 全部通过**，日志 `build/verification/020-food-display-gametest.log`。新增实际消费与消耗回归验证面包显示 N2 后第一轮消耗仍直接扣 S、七种奶桶 S0/N4 不受 S 限制且只摄入一次、奶后消耗直接 N4→3、种子缓冲例外保留且奶酪不复制缓冲。

`gradlew.bat runData -PmiteAppleSkinAudit=true` 也成功，使用用户当前 AppleSkin 3.0.9 JAR 强制转换四个目标类而不初始化 Minecraft：确认 3 处当前营养读取、独立容量、来源预览、图标范围及现代快速回血 HUD 预测钩子均已实际注入。报告为 `build/verification/appleskin-compatibility-audit.json`，日志 `build/verification/020-appleskin-audit.log`。

随后在 `build/verification/020-no-appleskin-run` 的隔离空模组目录运行相同数据加载，成功初始化 MITE，报告明确跳过不存在的 AppleSkin。该测试不移走或修改用户 `run/mods` 中的模组，日志为 `build/verification/020-no-appleskin-audit.log`；它验证无窗口加载，不代替完整客户端画面验收。

当前 JAR：`build/libs/mite-0.1.0.jar`，SHA-256：`8fc2409025a19057ac41ada7904c8c122ec7d14dd397cfcceb5d011694f12fbb`。包含可选客户端兼容类，不含 GameTest、审计器或原始模组/参考 ZIP。数据和网络结构保持原样，无存档迁移；`/mite food status` 将独立原生字段标为“种子饱和缓冲”，与 N 显示区分。

2026-10-13 用户确认修复“没有问题”并授权提交 GitHub，已以 [fix 提交](https://github.com/65658dsf/mite-neoforge-1.21.1/commit/683e5efcb5421fdee51ffd46a33120e0d26ab9fe) 推送到 main；本轮食品扩展另以 [feat 提交](https://github.com/65658dsf/mite-neoforge-1.21.1/commit/622226e8558af5af8aaae87b408ad9023ee6166a) 保存，开发署名均为 NingMeng123。分别导出暂存快照执行构建、JUnit 和 GameTest 源码编译：019 为242项、020为268项，均通过；保留此前实际241组服务端测试及两种无窗口审计结果。用户确认未逐条附带低等级、全部联机或无 AppleSkin 窗口场景的独立记录；不将无窗口数据加载写成真实客户端场景已逐项实测。助手未操作或重启用户客户端。当前固定 AppleSkin 3.0.9 原生不为无 FOOD 的手持蛋糕做预测，本轮也未伪造蛋糕手持 FOOD；放置蛋糕的六片 S2/N2 规则保持独立。018/019 历史结果和哈希继续保留，不能作为 020 当前 JAR 证明。
