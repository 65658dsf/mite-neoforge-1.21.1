# 镜像僵尸 Better Combat 命中回归

开发与验证：NingMeng123，2026-10-19。

## 问题与修复

ITE 的镜像僵尸复制玩家武器后，已经通过 `SourceMeleeGoal` 接入本体的 `BetterMobCombat`，近战目标没有被删除。但控制器在攻击前摇结束时用实体移动朝向 `getYRot()` 判断攻击夹角，而原版及源近战 AI 通过 `LookControl` 更新头部朝向。近身停步、玩家绕到侧后方时，头部可以持续看向玩家，实体移动朝向仍旧背向玩家，导致攻击持续挥空。

`BetterMobCombat.inReach` 改用 `getYHeadRot()` 检查实际瞄准方向。前摇、冷却、伤害、射程、遮挡及装备变化取消规则保持原有流程；目标绕到尚未转向的头部后方仍能躲过攻击。此修复位于主模组，更新主模组 JAR 即可覆盖使用该控制器的镜像僵尸。

## 自动验证

环境为 Java 21、Minecraft 1.21.1、NeoForge 21.1.259、MITE 0.2.0；可选联动使用 Better Combat 2.4.0、Player Animator 2.0.4，以及扩展测试中的 Simply Swords 1.70.2。

主模组 `BetterCombatMobGameTests` 增加两项真实延迟命中用例：真实 LookControl 转头但实体与身体仍背向时命中；前摇中目标绕到头部后方时挥空。

扩展 `MITE Modern ITE` 新增 `MirrorCombatGameTests`，使用真实注册的镜像僵尸、玩家、装备复制能力、原生装备属性刷新及 `SourceMeleeGoal.tick → BetterMobCombat.tick → 原生玩家受伤` 链路，覆盖重复复制与再次攻击、没有 Better Combat 的即时近战、本体铜剑与 Simply Swords 双手锤、同物品属性组件变更、剑与空手／弓／镐的切换。

| 运行范围 | 结果 | 日志（相对各自仓库） |
| --- | --- | --- |
| 主模组构建、专项测试源码编译 | 通过 | `build/verification/mirror-combat-build.log` |
| 主模组，安装 Better Combat | 22 项通过 | `build/verification/mirror-combat-present.log` |
| 主模组，不安装 Better Combat | 22 项通过 | `build/verification/mirror-combat-absent.log` |
| 扩展 + 旧主模组 + Better Combat + Simply Swords | 4 项中 1 项失败，准确复现背向无法命中 | `build/verification/mirror-combat/baseline-present.log` |
| 扩展 + 修复主模组 + Better Combat + Simply Swords | 4 项通过 | `build/verification/mirror-combat/fixed-present.log` |
| 扩展 + 修复主模组，不安装 Better Combat | 4 项通过 | `build/verification/mirror-combat/fixed-absent.log` |
| 扩展构建、JUnit、全部扩展测试源码编译 | 通过 | `build/verification/mirror-combat/extension-build.log` |

测试按联动是否安装选择对应分支，不适用的可选联动分支会直接通过。扩展联动环境中有 Simply Swords 自带的两条缺少可选第三方材料的配方解析错误；本次回归范围为战斗，不包含这些配方。此轮未进行多人实机游玩。

本次主模组构建使用 `build -x test compileGametestJava` 加专项 GameTest；没有将整套 JUnit 或其它 GameTest 的历史通过记录计作本次验证。JAR 中的控制器字节码已与本次编译输出核对一致，未打包测试类。

扩展本轮 14 项 JUnit 全部通过。主模组产物为 `build/libs/mite-0.2.0.jar`，SHA-256 为 `5397ada18cba7cf83c670c37e198b36f980249cf0c7bfff757fd3fbb4f195aeb`；与修复前冻结的同版本主模组 JAR 比较，仅 `BetterMobCombat.class` 内容不同。
