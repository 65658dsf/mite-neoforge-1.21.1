# 多人寻路性能修复验证

开发者：NingMeng123

日期：2026-10-27

## 首轮记录

本轮包含寻路复用、首次视线和地下可达发现三部分。合并后的 52 项专项服务端 GameTest 已全部通过，日志为 `build/verification/path-targeting-isolated-focused.log`。

专项测试位于 `MeleePathPerformanceGameTests` 和 `ZombieRoutePerformanceGameTests`，使用独立命名空间 `mite_path_perf`，覆盖跨入口复用、20→40→80 刻退避和错峰、目标/怪物移动与目标更换、显式失效、新开通道恢复、近身零步计划及离开攻击范围、门状态变化、僵尸失败计划复用。

最终交付在 `build/verification/path-targeting-scoped/` 独立源码快照验证：采用已提交基线、本轮 AI 修改及本轮开始时已有的 0.2.14 配置/资源。原工作区的其他并行修改保留。这样避免尚在调整的出生装备规则与扩展旧断言混入本轮验收。

本体完整 `build`、1770 项 JUnit 和全部 2546 项必需服务端 GameTest 通过，日志为 `build/verification/path-targeting-scoped-full.log`；扩展完整 `build`、38 项 JUnit 和全部 369 项必需服务端 GameTest 通过，日志为 `build/verification/path-targeting-scoped-ite.log`，均无跳过。已有 `ZombieRouteGameTests`、`MeleeDetourGameTests`、`MeleeTargetMemoryGameTests`、`MonsterOverheadDigGameTests` 和 `MonsterOverheadTargetMemoryGameTests` 一并检验真实绕路、半砖和分数坐标终点、破坏耗时、保护事件、目标记忆及实际移动。

本体基线为 `68fc8703351f5b8ed3d2bd104d621b6cacced37c`，扩展基线为 `83e798765fdf8645e94599eb12e038d1c7c6f038`。快照的生产输入哈希记录在 `source-inputs.json`；交付包的 JAR 逐类核对编译输出、署名和开发测试排除情况，校验值及最终数量见包内 `verification.json`。

## 自动复测

在项目根目录使用 Java 21 执行：

```powershell
.\gradlew.bat compileJava compileGametestJava test --console=plain
.\gradlew.bat runGameTestServer '-PmiteGameTestNamespaces=mite_path_perf,mite_targeting,mite_cave_discovery' '-PmiteGameTestDirectory=build/verification/path-targeting-focused-run' --console=plain
```

需要重新核对完整怪物行为时，使用独立游戏目录运行原有 `mite` 命名空间的服务端 GameTest，并记录通过数、失败项、日志路径及采用的源码状态。专项测试观察 `MeleePursuitPaths.searchStats` 中的实际原生搜索数、失败复用次数、连续失败次数和下一次重试时间；计数减少仅说明重复计算减少，不等同于实服 MSPT 降低相同比例。

## 实服对照

1. 将修复后的本体与对应扩展部署到测试服务器，保留同一存档、配置和模组组合。记录 JAR 版本、人数、模拟距离、已加载区块及主要实体数量。
2. 分别复现静止玩家隔墙或站在不可达高处、多个玩家分散探索、怪物集中追击三个场景。核对怪物仍能绕行、上台阶、进入真实攻击距离，且不会越过保护破坏方块。
3. 在失败退避期间移动玩家超过一格，确认同一失败目标在最短 10 游戏刻后恢复真实查询；玩家移动一次后停住也必须按该短截止时间重试。给僵尸更换工具、切换 `mobGriefing`、打开原本被选中的门，确认能立即重新决策。双方保持静止时另外打开新通道，确认退避到期后能恢复寻路，而非永久站住。
4. 卡顿发生时执行 `/spark profiler start --timeout 120`，保持正常联机行为；保存生成的链接、采样时段和当时玩家行为。对比 `tickServer` 下的寻路占比、MSPT 中位数/P95/最大值及 TPS，同时核对玩家数和实体数量，避免以不同负载直接计算提升幅度。
5. 若常规采样仍未抓到秒级停顿，再执行 `/spark profiler start --timeout 180 --only-ticks-over 100` 捕获慢 Tick；同时检查报告中的 GC、区块生成和其他模组调用，区分未覆盖的尖峰来源。

原始对照为 [TrYuX5d0VH](https://spark.lucko.me/TrYuX5d0VH)。该报告中的寻路约占 Tick 执行时间 42%，结束时约 20 TPS，没有直接记录截图里的 1.636 秒尖峰；修复后尚无同场景实服报告。

## 第二轮慢 Tick 修复验证

用户后续提供的 [MDzyKTceFq](https://spark.lucko.me/MDzyKTceFq) 确认为安装首轮 AI 修复后的单玩家集成服务端慢 Tick 报告。其运行中 0.2.15 的相关 AI 类与首轮已验证包逐类一致；此次不能归因于误装旧代码。

第二轮新增单次搜索的区块/方块缓存、失败目标移动时 10 刻最短查询间隔、挖掘活动和暂停检查的共享规划。`LoadedPathNavigationRegionGameTests` 覆盖负坐标、可变坐标对象、精确方块属性及流体、单次搜索缓存生命周期和不加载缺失区块；性能测试增加移动中反复请求、移动后静止截止时间、实际挖掘后的暂停分支。原有完整绕路移动响应、保护事件、近身攻击及矿洞发现测试同时复测。

日志位于 `build/verification/spark-MDzyKTceFq/`。自动回归验证逻辑和重复查询约束，不代表已完成第二轮真实多人或实服性能对照；当前玩家窗口和存档未作自动操作。

最终本体 `build`、1770 项 JUnit、2565 项必需服务端 GameTest 全部通过，最终完整日志为 `final-full.log`；此前扩大回归发现成功路线的移动响应受到间隔影响，已收窄为仅失败搜索需要最短间隔，并在最终全量重跑中通过原有绕路测试。58 项专项测试日志为 `focused.log`，最终全量同时包含这些专项。

使用新本体的附属回归完成 369 项 GameTest：366 项通过，3 项早期装备阶段断言失败。随后改用用户安装的原始 0.2.15 本体重跑，完整复现同样的 3 项失败，分别是 `loadedEarlyLocksClearHiddenCachesAndRestoreMissingBowsWithoutWashingCustomGear`、`sevenFinalizedEarlyArchersKeepPlainBowsEmptyStockAndRealRangedAiThroughDay35`、`earlyMeleeBossesStayEmptyAndFreshDay36RestoresTheirInternalEquipment`。本轮未改写这些既有断言，也不宣称附属全量通过；对照日志为 `extension.log` 和 `extension-baseline.log`。附属 38 项 JUnit 全部通过且无跳过。

交付包针对用户当前安装版本，通过 `-Pmod_version=0.2.15` 构建；仓库原有配套默认版本不变。JAR 逐文件与用户原包比较，只有本体寻路相关生产类发生变化，附属 JAR 与用户原附属校验值相同。包名为 `build/releases/mite-modern-0.2.15-slow-tick-fix.zip`，内含安装说明、SHA-256 和完整 `verification.json`。生产类与参与验证的编译输出一致，未打入开发测试。
