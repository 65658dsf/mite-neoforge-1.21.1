# 玩家翻越围栏

开发署名：NingMeng123。设计日期：2026-10-23。

按用户要求采用现代适配：栅栏（包括木栅栏与下界砖栅栏）、围墙和关闭的栅栏门保持原有外观与选中框，玩家无需跳跃提升或垫方块即可用普通跳跃翻过。玩家在地面直接行走仍会被挡住；打开栅栏门后正常通行。

玩家的实体碰撞只保留原形状底部一格，水平轮廓保持原样。实现位于通用的 `BlockStateBase.getCollisionShape(BlockGetter, BlockPos, CollisionContext)` 返回处，客户端移动和服务端移动校验采用相同规则。普通完整方块不受影响。

非玩家实体、无人上下文的碰撞查询及碰撞缓存继续使用原形状，怪物、动物和载具仍受到原来的围栏阻挡。原有蜘蛛攀爬、怪物破坏方块等能力仍按既有规则运行。继承 `FenceBlock`、`WallBlock` 或 `FenceGateBlock` 的模组方块也适用。

## 自动验证

2026-10-23 完整构建与 1722 项 JUnit 全部通过，4 项专项 GameTest 全部通过。专项使用测试玩家的原生 `jumpFromGround`、`travel` 与内部 `Entity.move`，验证四类围栏普通跳跃翻越、平走受阻、僵尸同样跳跃受阻、动物和空上下文保留 1.5 格碰撞、选中框不变、打开门通行及两格石墙仍阻挡玩家。运行命令：

```powershell
.\gradlew.bat runGameTestServer '-PmiteGameTestDirectory=build/verification/player-barrier-jump' '-PmiteGameTestNamespaces=mite_barrier' --console=plain
```

构建日志：`build/verification/player-barrier-build.log`；专项日志：`build/verification/player-barrier-gametest.log`。测试类与测试结构不进入发布 JAR。测试包：`build/releases/mite-0.2.9-player-barrier-jump.jar`。

客户端人工操作与真实多人联机尚未验收。

## 游戏内复测

1. 在平地分别放一排木栅栏、下界砖栅栏、圆石墙和关闭的栅栏门。
2. 生存玩家向前走应被挡住，向前加普通跳跃应能翻过，不需要疾跑或药水。
3. 让地面近战怪物追击，或将动物围住，确认它们仍不能靠普通跳跃翻过；打开栅栏门后应可通行。
