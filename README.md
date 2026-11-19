# MITE: Modern

将 Minecraft 1.6.4 MITE R196 的生存与成长规则重实现到 Minecraft 1.21.1 NeoForge，保留现代主世界地形、材质和功能内容，并逐项调整平衡。支持单人游戏和独立服务器。

开发者 **NingMeng123** · 当前源码配套版本 **0.3.6** · 模组 ID `mite`

[安装与版本配套](#安装与版本配套) · [主要内容](#主要内容) · [玩法配置](#玩法配置) · [本地开发](#本地开发) · [文档与历史记录](#文档与历史记录)

本仓库保存 MITE: Modern 本体的源码、资源和测试。Modern ITE 扩展在本体之上加入极难生物、宝石镶嵌、独立强化和后期成长内容。原作 Minecraft Is Too Easy 由 **Avernite** 创作。[Modern ITE 仓库](https://github.com/65658dsf/MITE-Modern-ITE)

项目持续开发中，功能实现与验证进度可查看实施状态，具体玩法附有来源依据和操作记录。[实施状态](docs/implementation-status.md)

## 安装与版本配套

| 项目 | 当前要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1.259，元数据声明范围为 `[21.1.259,21.2)` |
| Java | 21，64 位 |
| 本体文件 | `MITE-Modern-0.3.6.jar` |
| 可选 ITE 扩展 | `MITE-Modern-ITE-0.3.6.jar`，配套本体 0.3.6 或更新版本 |
| 联机安装 | 客户端与服务端均安装本体，使用相同的配套版本 |

1. 准备本体 JAR。源码构建产物位于 `build/libs/`，步骤见下方“本地开发”。
2. 为 Minecraft 1.21.1 安装对应 NeoForge，将本体 JAR 放入游戏实例的 `mods` 文件夹。
3. 需要 Modern ITE 时，同时放入配套扩展 JAR。加入可选联动模组时，按各模组说明安装相应前置。
4. 启动游戏，在模组列表确认 MITE: Modern。首次体验可创建新世界，从燧石工具和分级制造开始。

### 开局与制造

- 从砂砾等来源收集燧石材料，使用燧石、两根木棍与线或筋制作燧石手斧，再采集原木。
- 在背包 2×2 合成区，上排放燧石与线或筋，下排放木棍与原木，制作燧石工作台。也可竖放燧石短刀与原木制作。
- 在工作台加工木板并制作后续工具。配方受设备材质、工具能力与制造时间约束，制作高品质装备还需要相应经验。
- 随资源积累提高工作台、炉体和铁砧的材质，逐步进入金属制造、冶炼与维修。具体操作见专题说明。[燧石开局](docs/flint-start-adaptation.md)、[工作台与制造](docs/timed-crafting-016.md)、[装备品质](docs/quality-equipment-evidence.md)

### 可选兼容

| 模组或资源包 | 用途 |
| --- | --- |
| JEI | 查看分级工作台制造、炉体加工、装备升级及维修配方 |
| Jade | 查看作物健康、耕地湿度与肥力、铁砧耐久 |
| AppleSkin | 预览食品营养，并与本模组的双资源饮食系统联动 |
| Simply Swords | 十五类武器接入金属材质、品质、耐久、装备成长和怪物持械规则 |
| Better Combat | 接入玩家动作与近战距离；启用怪物兼容后，适用持械怪物使用前摇、连段及武器动作 |
| Dynamic Crosshair 9.11 | 实体攻击提示按实际近战距离、瞄准点和遮挡判定，攻击资格变化时刷新准星 |
| Mod Menu（NeoForge 版） | 打开本体配置界面，NeoForge 原生模组列表也提供配置入口 |
| EMF、ETF 与 Fresh Animations 资源包 | 适配部分特殊生物的模型、贴图和动画 |

Simply Swords 的声明版本为 1.70.2 及以上，Better Combat 为 `[2.4.0,3)`。具体联动范围与配置见对应文档。[简易刀剑](docs/simplyswords-compatibility.md)、[Better Combat 怪物适配](docs/bettercombat-mob-compatibility.md)、[蜘蛛动画](docs/spider-resource-pack-compatibility.md)

## 主要内容

| 系统 | 玩法 |
| --- | --- |
| 等级与生存 | 经验、负等级、等级生命与容量成长，以及随等级增加的近战距离 |
| 采集与制造 | 工具采集门槛、分级工作台、制造计时、炉温和材质铁砧 |
| 装备与战斗 | 八档品质、装备成长与副属性、材料升级、振金装备及分类型武器平衡 |
| 食物与恢复 | 饱食、营养、蛋白质、脂肪、植物营养和糖抵抗，食品加工及多种容器 |
| 农业与畜牧 | 作物气候、光照、湿度、肥力、布局和病害，家畜食物、水分与自由度 |
| 生物与月相 | 特殊生物、协同追击和破障，血月、蓝月、丰收月与月晕 |
| 日期进程 | 怪物生命随日期成长，出生装备的材质与附魔随世界进程提高 |
| 地底与探索 | 地底世界、矿物与传送规则，适配后的地牢、试炼密室和宝库奖励 |
| 功能与显示 | 金属容器、盾牌、材料箭、钓鱼竿、幻灵刃，以及营养面板和装备提示 |

### 当前基础规则

| 项目 | 本体默认规则 |
| --- | --- |
| 难度与亮度 | 困难难度，亮度选项固定最低值，夜视效果正常生效 |
| 玩家生命 | 0 级为 6 点，即 3 颗心；每 5 级提高 2 点，35 级起达到 20 点 |
| 经验与死亡 | 支持负等级，最低为 −40 级；死亡经验球按玩家归属回收，具体损失遵循经验规则 |
| 等级近战距离 | 按当前经验等级每满 10 级增加 0.1 格，100 级起封顶额外 1 格，在距离配置结算后叠加 |
| 饮食消耗 | 金色饱食层（营养及种子缓冲）先消耗，再消耗鸡腿饥饿条；两层耗尽后开始积累饿伤 |
| 卧床恢复 | 自然恢复速度为普通状态的 8 倍 |
| 武器基础距离 | 本体剑在两种战斗环境均为 2.25 格；简易刀剑长剑为 2.5 格、默认攻速每秒 1.3 次 |
| 重型与特殊武器 | 简易刀剑巨剑、巨斧、巨锤为 3.25 格，环刃为 2.8 格，钗为 2 格 |

武器表记录零级基础属性，装备成长、附魔、目标特性和距离设置参与最终结算。Modern ITE 提供进一步的生命、宝石与强化成长规则。[本体武器平衡](docs/equipment-balance-2026-10-24.md)、[简易刀剑平衡](docs/equipment-balance-simplyswords-2026-10-24.md)、[等级与近战距离](docs/player-combat-reach.md)

## 玩法配置

在游戏模组列表中选择 **MITE: Modern → 配置**。可通过矿石、怪物、附魔和其他玩法页面搜索、修改并保存参数。

| 内容 | 设置入口 |
| --- | --- |
| 主世界与地底矿物 | 矿石生成倍率页，可分别调整普通和深层矿石 |
| 怪物成长与生成 | 怪物成长、自然刷新率及对应高级参数 |
| 附魔 | 搜索附魔名称或 ID，调整禁用项、等级上限和随机候选概率 |
| 战斗与结构 | 近战距离倍率、固定扣减、高差修正、怪物 Better Combat 兼容及试炼生成开关 |
| 本体配置文件 | 游戏实例的 `config/mite-common.toml` |
| ITE 扩展 | 安装后从“ITE 扩展设置”进入，使用 `config/modern_ite-common.toml` |

多人玩法由服务端配置决定，客户端接收同步参数。设置首页的“导出配置”可生成服务端配置包，安装 ITE 时会一并包含其公共配置。保存后导出，将包内 TOML 文件复制到已停止的服务端 `config/` 目录，再启动服务端。生成类参数作用于后续生成流程，具体生效时机见配置说明。[模组菜单与配置导出](docs/mod-menu-settings.md)

## 本地开发

准备 Java 21，使用仓库自带的 Gradle Wrapper。首次构建需要联网获取 Gradle、Minecraft、NeoForge 与开发依赖。

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

当前构建产物为 `build/libs/MITE-Modern-0.3.6.jar`。macOS 和 Linux 可通过 `bash ./gradlew` 执行对应任务。构建配置见工程文件。[build.gradle](build.gradle)、[gradle.properties](gradle.properties)

| 命令 | 作用 |
| --- | --- |
| `.\gradlew.bat test` | 运行 JUnit 规则测试 |
| `.\gradlew.bat build` | 编译、测试并构建模组 JAR |
| `.\gradlew.bat runClient` | 启动开发客户端 |
| `.\gradlew.bat runServer` | 启动开发专用服务器 |
| `.\gradlew.bat runGameTestServer` | 运行游戏内服务端集成测试 |
| `.\gradlew.bat runData` | 重新生成配方、标签、模型等数据资源 |

客户端、开发专服和 GameTest 的默认目录分别为 `run/`、`run-server/`、`run-gametest/`。使用独立测试目录可以复现配置和存档条件，例如以下命令。

```powershell
.\gradlew.bat runClient -PmiteClientDirectory=build/verification/client-run
.\gradlew.bat runGameTestServer -PmiteGameTestDirectory=build/verification/gametest-run
.\gradlew.bat runData -PmiteDataDirectory=build/verification/data-run
```

更新生成资源时，先完成 `runData`，再构建和测试。JUnit 覆盖纯规则与边界值，GameTest 覆盖真实物品、方块、实体和操作入口；客户端与多人实测的环境、步骤和结果记录在专题验收文档中。

### 代码位置

| 位置 | 内容 |
| --- | --- |
| `src/main/java/io/github/ningmeng123/mite/` | 生存、战斗、制造、世界生成与生物逻辑 |
| `rules/`、`compat/`、`client/` | 上述 Java 包内的数值规则、可选模组联动和客户端界面 |
| `src/main/resources/` | 语言、纹理、模型与运行资源 |
| `src/generated/resources/` | 数据生成产出的配方、标签、掉落表等 |
| `src/test/`、`src/gametest/` | JUnit、游戏集成与客户端专项验证代码 |
| `docs/`、`spec/` | 使用说明、适配规格、来源依据和验收记录 |
| `reference-data/`、`tools/` | 来源规则数据与资源、验证辅助工具 |

## 常见问题

### 为什么空手挖不动原木

原木采集会检查主手工具及采集能力。开局可从燧石手斧入手，逐级提升工具材质，游戏内提示会说明受阻原因。[原木采集门槛](docs/log-harvest-adaptation.md)

### 为什么合成需要等待

制造具有设备和耗时要求，配方书与快捷操作也经过相同的制造流程。选择满足条件的工作台、材料和品质后，按进度完成每批制作。[制造计时](docs/timed-crafting-016.md)

### 为什么生命和食物显示与原版不同

生命和饮食容量随等级成长，食品分别补充饱食、营养与相关营养素。背包中的营养面板可查看营养、蛋白质、脂肪、植物营养和糖抵抗，AppleSkin 提供对应预览。[食物与营养](docs/foods-018-evidence.md)、[营养显示](docs/food-saturation-020.md)

### 如何反馈问题

在仓库反馈时，附上 Minecraft、NeoForge、本体和扩展版本、可选模组列表、复现步骤，以及相关截图或 `logs/latest.log`。崩溃问题可附 `crash-reports/` 中对应报告。功能变更的提交说明注明来源依据、验证方式和结果，开发署名统一为 `NingMeng123`。[问题反馈](https://github.com/65658dsf/mite-neoforge-1.21.1/issues)

## 文档与历史记录

| 主题 | 入口 |
| --- | --- |
| 实现进度与现代适配 | [实施状态](docs/implementation-status.md)、[参考分析](docs/reference-analysis.md) |
| 生命、经验与战斗 | [经验曲线](docs/experience-adaptation.md)、[死亡经验球](docs/death-orb-adaptation.md)、[玩家近战](docs/player-combat-reach.md)、[护甲与韧性](docs/modern-armor-defense.md) |
| 难度与画面设置 | [客户端设置](docs/client-settings.md) |
| 装备与附魔 | [装备成长](docs/equipment-progression.md)、[副属性权重](docs/equipment-affix-weights.md)、[材质升级](docs/equipment-material-upgrades.md)、[附魔规则](docs/enchantments-r196.md) |
| 生产与生存 | [炉体与热量](docs/furnaces-024-evidence.md)、[作物生长](docs/crop-growth.md)、[家畜健康](docs/livestock-health-033-evidence.md)、[坍塌](docs/collapse-physics.md) |
| 怪物与月相 | [怪物生命成长](docs/monster-day-scaling.md)、[装备进阶](docs/blood-moon-equipment.md)、[自然刷怪](docs/monster-natural-spawning.md)、[月相](docs/lunar-source-evidence.md) |
| 探索与特殊装备 | [地底世界](docs/underworld-depth-022.md)、[地底地牢](docs/underworld-dungeons.md)、[幻灵刃](docs/phantom-blades-2026-10-24.md) |
| 历史更新 | [0.3.6](docs/release-0.3.6.md)、[0.3.5](docs/release-0.3.5.md)、[0.3.4](docs/release-0.3.4.md)、[0.3.3](docs/release-0.3.3.md)、[0.3.2](docs/release-0.3.2.md)、[0.2.16](docs/release-0.2.16.md) |

## 来源与许可

原作规则、配方与生存资料可在项目网站查阅。[MITE 官网](https://avernite.ca/MITE/)、[原作配方](https://avernite.ca/MITE/crafting_recipes/)、[生存提示](https://avernite.ca/MITE/hints/)

规则参考包为 `MITE 1.6.4 R196.zip`，SHA-256 为 `fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。来源分析、现代适配和素材记录可在专题文档中查阅。[参考分析](docs/reference-analysis.md)、[振金素材](docs/vibranium-source.md)、[简易刀剑素材](docs/simplyswords-compatibility.md)

模组元数据的许可声明为 `All Rights Reserved`，复用素材的来源和许可见对应记录。开发接口参考 NeoForge 1.21.1 文档。[NeoForge 文档](https://docs.neoforged.net/docs/1.21.1/)
