# 2026-10-30 Spark 寻路性能分析与优化

开发者：NingMeng123

## 报告与结论

原始报告：[v5dCEGnalj](https://spark.lucko.me/v5dCEGnalj)。采样时间为北京时间 2026-10-30 00:55:54–01:01:54，持续 360.548 秒，记录 4192 个游戏刻，期间实际推进约 11.63 TPS。本体与附属均为 0.2.18。采样只包含 `Server thread`，间隔 4 ms；下面的百分比以该线程全部采样为分母，包含子调用，各行不能相加。

| 指标 | 报告值 |
| --- | --- |
| 结束时最近一分钟 TPS | 10.89 |
| 结束时最近一分钟 MSPT 平均 / 中位数 | 89.70 / 82.49 ms |
| 结束时最近一分钟 MSPT P95 / 最大值 | 174.55 / 265.16 ms |
| 结束时最近五分钟 MSPT 最大值 | 308.37 ms |
| 玩家 | 采样窗口由 11 人降到 9 人，结束时 9 人 |
| 实体 | 结束时 1004 个，其中掉落物 253 个、鸡 87 只 |
| CPU / Java 堆 | i9-14900KF，32 逻辑线程；堆使用约 2.10 GiB，上限 12 GiB |

| 调用 | 主线程采样占比 | 含义 |
| --- | --- | --- |
| `PathFinder.findPath` | 65.00% | 原生寻路总热点 |
| `MeleePursuitPaths.find` | 59.24% | 本体近战寻路，包含于上游 AI 和规划调用 |
| `ZombieRoutePlanner.plan` | 56.42% | 僵尸比较步行、挖掘和破门的共享规划 |
| `WalkNodeEvaluator.checkNeighbourBlocks` | 39.23% | 路径节点周围危险类型检查 |
| `LivestockSeeking032.movementPath` | 10.41% | 牲畜觅食、寻水，其中几乎全部来自鸡 |
| `LivestockSeeking032.collect` | 4.90% | 螺旋扫描资源候选 |

主要问题是服务端 AI 寻路计算占用主线程。Java 进程约 4.5% 的总体 CPU 是按多逻辑线程统计，不能由此推断游戏主线程空闲。报告不足以证明内存不足或 GC 是主要原因，也不能把服务器 TPS 问题直接解释为客户端绘制帧率问题。

## 本轮改动

优先减少相同输入的重复计算，保持寻路距离、节点预算、重试间隔、攻击距离、危险规避以及原有目标选择规则。

1. 在每次近战原生搜索使用的节点求值器里缓存单格完整路径类型。原生已有的缓存是生物包围盒聚合后的结果，相邻节点的身体占据范围仍会重叠，重复执行单格分类及其 24 个邻格检查。新缓存按坐标保存 `super.getPathType` 的完整结果，继续调用原生与 NeoForge 的方块／流体钩子，保留后续门、铁路、体型和危险代价处理。缓存仅在一次同步搜索中有效；准备／结束搜索及导航策略改变时清空，其他上下文绕过缓存。项目的木栅栏门／活板门 Mixin 会根据开门策略改写分类，因此策略失效是必要条件。
2. 牲畜资源候选按原有螺旋和垂直顺序扫描，遇到候选立即试路，在找到原规则接受的第一条路径后结束。不再预先收集后面不会使用的整批候选。保持搜索范围、候选数量上限、失败候选后的继续搜索和随机回退。

资源完全缺失时，第二项仍可能扫描整个范围；本轮不增加长期资源缓存或空间索引。实服 MSPT 收益不能由热点占比直接推算，仍需相似负载下复测。

## 多线程判断

当前寻路不能直接包进线程池：`PathNavigationRegion` 持有实时区块，`PathfindingContext` 使用服务端共享路径类型缓存，原生求值器在开始／结束时调用实体回调，本体也会读取实时碰撞和实体状态。把这些调用直接并行执行可能产生竞态或错误路径。

安全的后续方案是主线程采集不可变地形和实体参数，工作线程使用独立节点与类型缓存计算，再由主线程验证目标、装备、地形和路径后应用；过期任务需要取消或丢弃。快照、校验和等待会增加成本，也可能改变结果返回时机。应先完成本轮同步计算优化并再次采样，确认剩余瓶颈后决定是否进行这项改造。

## 验证

原始数据、解码元数据和调用树保存在 `build/verification/spark-v5dCEGnalj/`。使用 Java 21 / NeoForge 21.1.259，独立构建目录与测试世界，按当前工作区 0.2.18 生产源码完成验证，未操作实际服务器或玩家存档。

| 验证 | 最终结果 | 记录 |
| --- | --- | --- |
| 编译、构建阶段及全部 JUnit | 2043 项全部通过，无失败、错误或跳过 | `build.log`、`final.log`、`verified-build/test-results/test/` |
| 本轮新增服务端 GameTest | 9 项全部通过 | `CachedWalkNodeEvaluatorGameTests` 3 项、`LivestockSeekingPerformanceGameTests` 6 项 |
| 寻路及动物行为专项 | 250 项中 248 项通过，2 项既有射程断言失败 | `final.log` |
| 移除本轮两项优化接入的对照 | 7 项射程测试中 5 项通过，完整复现同样的 2 项失败 | `baseline-runtime.log`、`baseline-inputs.json` |
| JAR 内容核对 | 本轮 7 个生产类／内部类与验证编译输出一致，版本与署名正确，无 GameTest 类混入 | `jar-verification.json` |

两项失败分别为 `sourceGoalsKeepTheirHistoricalGateAndUseTheCapAtPlannedAttackPositions`、`nativeArmedAttacksRecheckTheShorterCapAfterTheTargetRetreats`。它们在直接检查来源／原版近战距离时失败，尚未执行寻路；对照采用相同工作区其他生产源码，仅将 `MeleePursuitPaths` 和 `LivestockSeeking032` 替换为提交 `4773560889457290463343799b37e63b5931cc72` 的优化前实现。已有普通剑射程变更使测试的“靠近”距离不再满足历史怪物近战门槛。本轮保留这两项断言，不宣称专项全量通过。

新增测试对照 21 种地形和 3 种门策略，验证相邻危险钩子、上下文隔离、新搜索看到地形更新、候选螺旋／垂直顺序、失败候选预算、实际路线与随机回退。固定重复分类样本中的邻接钩子调用从 64 次变为 1 次；固定“第一个候选已可用、后续没有资源”的扫描样本从 122317 次资源检查变为 1 次。这些是工作量断言，不是实服加速比例。

首次集成运行的门策略缓存问题已修正；新测试的花朵夹具已补足土壤支撑。原有区块就绪等待在首次运行有一项超时，最终整组复跑通过，未修改其等待断言或生产查询逻辑。保留首次 `gametest.log`，不将其记为通过。验证使用本体环境，未复刻报告中的完整附属模组组合或 9–11 人实服负载。

验证命令（隔离脚本与输入保存在上述目录）：

```powershell
.\gradlew.bat --no-configuration-cache -I build/verification/spark-v5dCEGnalj/focused.init.gradle build runGameTestServer '-PmiteGameTestNamespaces=mite,mite_path_perf,mite_targeting,mite_cave_discovery,mite_mob_reach,mite_bettercombat,mite_hunting' '-PmiteGameTestDirectory=build/verification/spark-v5dCEGnalj/gametest-run-final' --console=plain
.\gradlew.bat --no-configuration-cache -I build/verification/spark-v5dCEGnalj/baseline.init.gradle runGameTestServer '-PmiteGameTestNamespaces=mite,mite_targeting,mite_mob_reach' '-PmiteGameTestDirectory=build/verification/spark-v5dCEGnalj/baseline-gametest-run' --console=plain
```

交付 JAR 为 `build/verification/spark-v5dCEGnalj/verified-build/libs/mite-0.2.18.jar`。本轮保持原版本，不使用版本覆盖参数；尚无优化后的同场景实服采样，不能承诺恢复 20 TPS。

实服复测建议使用相同模组配置，尽量匹配人数、鸡和怪物数量、维度和活动场景，执行 `/spark profiler start --timeout 360`；对比 TPS、MSPT 平均／P95、上述调用占比和实体分布。保留相同的服务器配置与 AI 参数，避免负载不同导致误判。
