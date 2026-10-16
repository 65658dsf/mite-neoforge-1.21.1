# 本轮自动验证与测试包

开发署名：NingMeng123。验证日期：2026-10-16。玩家已确认并授权按七类分别提交、推送；以下为实现阶段的自动验证记录。

## 实际结果

| 检查 | 结果 |
|---|---|
| `gradlew build` | 成功，Java 21 / Minecraft 1.21.1 / NeoForge 21.1.259。 |
| JUnit | 1421 项通过，失败/错误/跳过均为0。 |
| 独立 GameTest 服务端 | 1441 项全部通过；主世界、地底、下界和末地加载并正常保存退出。 |
| `runData` | 成功生成并加载62项成就与相关伤害标签。 |
| 客户端实际类变换 | `GameRenderer`、`MultiPlayerGameMode`、`Gui` 的 Mixin 和调用入口验证通过；没有实例化 Minecraft、创建GL或控制游戏窗口。 |
| JAR 内容 | 62项成就、9本古籍、语言与注册 Mixin 齐全；无测试类，未打包 JEI/Jade/JUnit；仅原版与NeoForge是必需依赖。 |
| 已有工作区 | 44条旧路径的内容/原有编辑已保留；两份语言只追加本轮135键，旧提示删除仍保留。 |
| 玩家验收 | 用户已确认提交；自动检查与玩家实机结果分开记录。 |

最终服务端日志：`build/verification/selected-final-build-test.log`；全套使用独立目录 `build/verification/selected-features-verified-run`，不操作用户存档。包内容与受保护文件检查：`build/verification/selected-mechanics-package-audit.json`；客户端接线检查：`build/verification/selected-mechanics-client-audit.json`。

验证过程中修正了旧用例的站位/合格工具条件，补充了伐木附魔的精确位置内部许可及弱击的受击玩家速度同步。另补睡眠门槛排除死亡玩家、1000刻清效果和6000刻成就边界；爆破用例隔离到同区块高空，显式验证实体可见性，避免随机测试网格中未开始追踪的实体造成偶发失败。最终日志是这些修正之后的完整结果。

睡眠两层门槛用例调用实际变换后的服务端处理器并对照原生 `SleepStatus`，不是完整 `ServerLevel.tick` 端到端模拟；多人与实际界面仍列入手测。现代共享时钟不批量跳增、保留现代攻击冷却/界面和经验修补、结构战利品调用时机等适配见各规则文档，不声称旧版随机序列与新版本完全一致。

## 测试包

- 常规输出：`build/libs/mite-0.1.0.jar`。
- 同字节测试副本：`build/libs/mite-0.1.0-selected-mechanisms-test.jar`。
- SHA-256：`7ab7410642a650a140cbcac30ab0c6b03eaa5e184b8b4d3a5fcc681278bbaf8e`。
- 两个文件均有同名 `.sha256` 校验文件。

使用测试副本替换测试实例中的旧MITE JAR；同一实例只保留一份 `mite` 模组。不会自动复制到用户客户端、重启进程或启动游戏。

按 [14项手动测试手册](r196-selected-mechanisms.md) 验证。依照用户确认，按 [七类提交边界](selected-mechanisms-commit-groups.md) 分别提交中文 `feat` 信息并推送 GitHub。
