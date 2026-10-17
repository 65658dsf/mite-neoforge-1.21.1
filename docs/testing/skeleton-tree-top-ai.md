# 骷髅树顶交战增强

作者：NingMeng123

针对玩家站在树冠上时地面弓骷髅无法有效交战的问题，修正了索敌、放箭和瞄准之间不一致的树叶与掩体判断。

- 持弓骷髅可以首次发现并持续锁定树叶后的玩家。原有玻璃、门等视觉规则继续生效，实心玻璃仍然挡箭。
- 玩家上下方的检索统一使用实际跟随范围内的三维距离，取消仅向上 20 格的固定搜索上限。无敌、创造、旁观和实体家园限制仍然参与筛选。
- 眼睛位于树叶中的骷髅可以正常放箭。树叶保持完整，实心方块仍然阻挡箭矢。
- 放箭方向使用当前真正可见的身体中点、眼睛或下身，避免只看到头部却把箭射向被遮挡的身体。
- 持弓骷髅连续 10 tick 没有直接射线时，寻找附近可达的射击位置。每 20 tick 最多搜索一次，最多尝试 8 次原生寻路；落脚点必须有支撑、无碰撞且无液体，路径排除危险节点。重新获得射线后停下射击，不破坏地形。

保留普通 60 tick、狂暴 40 tick 的攻击间隔、30 格射击半径、原有散布、有限移动预判和发射后不追踪的箭矢物理。其他怪物不使用骷髅的穿叶索敌和射击选位。

自动回归覆盖真实高处箭矢命中、头部暴露命中、树叶内释放、实墙阻挡、初次与持续索敌、上下搜索范围、玩家适格性、实际安装的攻击目标选择侧向路径，以及悬空、水面和不可达射击点的拒绝。相关类为 `SkeletonAimRulesTest`、`MonsterTargetMeleeGameTests`、`SkeletonElevatedShotGameTests`、`SkeletonRangedGoalGameTests` 和 `SkeletonFiringPositionGameTests`。

2026-10-17 最终验证：`./gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/skeleton-ai-2026-10-17-final-run --console=plain` 成功，1526 项单元测试与 1838 项必需 GameTest 全部通过。最终日志为 `build/verification/skeleton-ai-final-build-gametest.log`，JUnit 报告为 `build/test-results/test/`。实际模组 JAR 包含新的射击选位类，未包含 GameTest 类或测试结构。新增断言也覆盖绕行途中经过 25 tick 后继续保留尚未完成的路线。

客户端手工复核：重启游戏加载新代码，在生存模式站到树冠上，让附近的持弓骷髅发现玩家；分别测试多层树叶、树干遮挡、只有头部露出及高低落差。检查骷髅能持续射击、会步行寻找侧向射线，箭仍能躲开且不会穿过树干或石墙。
