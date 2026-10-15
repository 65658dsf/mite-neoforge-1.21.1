# 月相机制自动验证

开发者：NingMeng123。日期：2026-10-15。基线：`11d9169c05303a31c44b01bf64c868df7e4c5a12`。本轮包含用户追加的彩色日期聊天提示。用户在核对绝对时间指令后反馈“没有问题了提交吧”，确认当前交付正常并授权提交 GitHub。没有启动、重启或操作用户游戏窗口。

## 执行结果

使用 Java 21、固定的 Minecraft 1.21.1 / NeoForge 21.1.259。

```powershell
$env:JAVA_HOME='D:/Coding/huanjing/jdk'
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/lunar-final-gametest-run
python tools/testing/verify_stacking_030.py --report build/verification/lunar-package-audit.json
```

- JUnit：1190 项，0 失败、0 错误；新增日历 7、天气 5、视觉公式 12，共 24 项。
- 无界面服务端 GameTest：992 项全部通过，新增 18 项；模拟测试运行 34.01 秒，完整 Gradle 任务成功。
- Python：既有堆叠规格与批次配方回归 11 项通过；1691 个物品映射和 66 条现代批量配方保持。
- JAR：737 个当前生产 class 逐字节核对、2066 个 JSON、183 个 Mixin 核对通过。不包含 GameTest、反编译源码或测试物资函数，不捆绑 JEI/Iris/Sodium 及其 API。
- 独立来源哈希：26 个原 class 及对应反编译文件、80 个必要方法、2 张来源光晕原图全部匹配；3 张最终图片另由客户端资源审计核对。42 个先前无关脏文件哈希均未改变。

| 新增 GameTest | 项数 | 实际路径 |
| --- | ---: | --- |
| `LunarEffectsGameTests` | 8 | 实际引擎月相与刷怪日历一致、成熟作物随机刻及单人/专服开关、感染窗口/屋顶、恐狼低吼、鱿鱼捕食、原生掉落事件及真实爆炸掉落 |
| `LunarWeatherSleepGameTests` | 5 | 原生天气 tick、0.01 插值、蓝/收获窗口、关闭天气循环、沙漠/屋顶/炼药锅、真实床与出生点、继续睡眠事件及维度隔离 |
| `LunarAnnouncementGameTests` | 4 | 真实服务端系统聊天包，四种颜色/本地化参数、同日/新玩家、跳时、普通日/维度隔离、无连接玩家、实际 NBT 和死亡/末地返回克隆 |
| `FoodFishingBaitGameTests` 新例 | 1 | 实际 `catchingFish` 在蓝月 12999/13000/23000/23001 边界、独立随机序列和咬钩窗口 |

上述世界时间、天气、规则和大范围临时屋顶均同步恢复。新增测试存在不等于全部农业已完成：源额外血月感染在专服关闭，普通原版作物完整气候/旱死仍未在本轮重写。

## 首轮失败及修正

首次生产编译发现 `SleepStatus` 的现代包名错误，已改为 `net.minecraft.server.players` 并同步 Mixin 方法描述符。修正后编译和规则测试通过。

第一轮 986 项服务端测试有 2 项失败，未计为通过：

1. 新钓鱼边界测试把旧浮点公式按有理数计算为 1499。实际单精度结果是 `1498.9998779296875`，整数截断为 1498；保留生产来源公式，修正独立字面期望。
2. 既有爆炸夹具在一格模板外注册目标，最终区块尚未进入实体刻状态，原查询未索引目标。四项真实爆炸用例改为临时原生区块票据、等待实际实体刻及查询索引后爆炸，并将四组场地按高度隔离。保留 4/8/9 格、真实暴露及精确线性伤害断言，增加爆前 100 HP 检查；完成、异常和超时均清理。生产爆炸代码没有变化。夹具首次编译的泛型推断需显式 `TicketType.<Integer>create`，修正后完整回归通过。

随后新增病马铃薯收获/爆炸两例及用户要求的聊天四例，最终完整 992 项通过。新增聊天持久状态采用版本化的 `PlayerPersisted`，与已有保存字段隔离；测试执行了实际保存/加载和 `restoreFrom`，不冒称进行了真实关服重启或多人实机。

## 客户端与交付边界

可选数据审计通过定义但不初始化客户端目标类，检查实际 Mixin 转换后的天空、光照图、雨幕及雨声调用链，并校验三张 PNG 的尺寸及 SHA-256。不创建 Minecraft 实例、不创建世界、不调用 OpenGL。

```powershell
.\gradlew.bat runData -PmiteDataDirectory=build/verification/lunar-data-run -PmiteLunarVisualAudit=true
```

最终原生空扩展模组目录、以及复制 `run/mods` 的 18 个 JAR 的独立组合目录均通过实际转换和资源审计；数据生成零资源改写，原 `run/mods` 不变。组合包括当前 Sodium、Sodium Extra、Iris、JEI、AppleSkin、Jade 等，具体 JAR 哈希记录于 `lunar-compat-mods.json`。最终报告为 `lunar-visual-audit-native.json` 和 `lunar-visual-audit-compat.json`。资源与字节码审计不能证明真实画面、光影效果或扩展模组的全部功能正常。

组合首轮暴露审计程序的原生星光调用“必须直接在 renderSky 中”假设：Iris/Sodium Extra 将调用移至包装方法。改为沿同类方法调用和 `invokedynamic` bootstrap 句柄追踪实际可达方法，仍严格核对具体 owner、方法签名与调用站点数量，并输出每个位置；没有用字符串匹配替代行为检查。

另确认 Iris 隐藏月盘时旧包装顺序会留下本模组光晕。直接共享原生 `PoseStack` 解决太阳路径倾斜，月盘 Mixin 优先级设为 900，使 Iris 开关位于外层；1100 方案经导出确认无效，未保留。最终专用回归断言实际 `renderSky` 只直接进入 Iris 开关，动态操作句柄才进入 Mite 月盘/光晕，关闭分支跳过整个 Mite 绘制并释放 mesh。无 Iris 时跳过该专用断言，未增加 Iris 前置。最终仅调整客户端包装顺序和审计，服务端规则保持已通过 992 项回归的实现。

原生基础亮度表、最低暗光和旧闪电衰减保留现代实现；直接捕获实际月盘姿态以共享其他模组的太阳路径倾斜。光影替换天空/光照图或主动隐藏月亮的场景需用户复核，不宣称支持全部光影设置。天气创建时间种子替换为现代持久世界种子；血月原清醒卧床界面适配为保留出生点后拒绝入睡。完整差异见 [视觉](../lunar-visuals.md)、[天气和睡眠](../lunar-weather-sleep.md)。

测试包：`build/libs/mite-0.1.0.jar`，7029378 字节，SHA-256：`2ead7f05bf1273536fcf9f4bce49a47b0f42d5a971cec7d270d596471ab3ffec`。

本机忽略目录中的完整日志/报告：`lunar-final-gametest.log`、`lunar-package-audit.json`、`lunar-source-audit.json`、`lunar-visual-audit-native.json`、`lunar-compat-mods.json`。用户操作和预期见 [月相手测](../lunar-manual-test.md)。用户已确认当前交付正常并授权提交；本记录不补写未提供的逐项天气、农业、光影、真实双客户端和实际重启结果。
