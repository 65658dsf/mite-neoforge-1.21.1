# 多人寻路性能修复验证

开发者：NingMeng123

日期：2026-10-27

## 当前记录

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
3. 在失败退避期间移动玩家超过一格、给僵尸更换工具、切换 `mobGriefing`、打开原本被选中的门，确认能及时重新决策。双方保持静止时另外打开新通道，确认退避到期后能恢复寻路，而非永久站住。
4. 卡顿发生时执行 `/spark profiler start --timeout 120`，保持正常联机行为；保存生成的链接、采样时段和当时玩家行为。对比 `tickServer` 下的寻路占比、MSPT 中位数/P95/最大值及 TPS，同时核对玩家数和实体数量，避免以不同负载直接计算提升幅度。
5. 若常规采样仍未抓到秒级停顿，再执行 `/spark profiler start --timeout 180 --only-ticks-over 100` 捕获慢 Tick；同时检查报告中的 GC、区块生成和其他模组调用，区分未覆盖的尖峰来源。

原始对照为 [TrYuX5d0VH](https://spark.lucko.me/TrYuX5d0VH)。该报告中的寻路约占 Tick 执行时间 42%，结束时约 20 TPS，没有直接记录截图里的 1.636 秒尖峰；修复后尚无同场景实服报告。
