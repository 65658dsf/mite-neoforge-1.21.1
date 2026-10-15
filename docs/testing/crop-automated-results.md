# 作物机制自动验证

开发署名：NingMeng123。日期：2026-10-15。基线为 `52dc38309f8cea754fc626e8f5a9b5f69aef36df`，2026-10-15 用户反馈“没有问题了，把这两个提交github吧要分开”，确认作物机制及其 Jade 扩展当前交付正常并授权分开提交。未补记未提供的逐项画面和真实多人结果。

本页保留添加 Jade 扩展之前的构建记录。当前 JAR 和新增兼容检查见 [Jade 作物验证](jade-crops-automated-results.md)。

## 最终结果

| 验证 | 结果 |
|---|---|
| Java 21 / NeoForge 21.1.259 构建 | 通过，`build` |
| JUnit | 1201 项，零失败、错误和跳过；新增 11 项 |
| 独立服务端 GameTest | 1041 项 required 全部通过；新增 49 项 |
| 历史 Python 回归及发布包审计 | 11 项 Python 通过；764 个生产类字节一致、2117 个 JSON、196 个 Mixin 声明全部核对 |
| 本轮独立来源和资源审计 | 1266 项检查；34 类、186 方法和 41 条来源规则；31 原图 SHA 与 16×16 尺寸；112 普通作物、32 洋葱和 16 耕地模型状态，70 模型、68 纹理引用 |
| 实际游戏窗口、客户端画面和多人 | 用户确认本功能正常；未打开、重启、关闭或操控用户游戏；逐项画面和真实多人结果未另行提供 |

新增 GameTest 分组：普通生命周期 10、收获与保存 7、耕地 10、特殊植物 11、农业附魔 6、真实降雪/流水 5。用来源数值和可控随机抽样验证边界，而非依靠长时间等待概率碰巧发生。

构建输出 `build/libs/mite-0.1.0.jar`，7,124,125 字节，SHA-256：

```text
69b602262116af051d96eaa0dc5f4b2c45338b7f3e127e1a549540427008a36c
```

测试代码、`SpawnedEntityCapture`、本机 ZIP、反编译文件和运行世界均未进入发布 JAR。JEI 和客户端扩展继续为可选，独立服务端测试未加载它们。

## 复现与日志

先独立生成数据，再构建和测试。GameTest 使用新目录，尤其真实生存挖掘/搭门用例会修改远处地底区块，不能反复把同一已挖完的测试世界当成未触碰的地形。

```powershell
$env:JAVA_HOME = 'D:/Coding/huanjing/jdk'
.\gradlew.bat runData -PmiteDataDirectory=build/verification/crop-data-run
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/crop-gametest-spawn-capture
python tools/testing/verify_crops.py --report build/verification/crop-resource-source-audit.json
python tools/testing/verify_stacking_030.py --report build/verification/crop-package-audit.json
```

上面的 GameTest 目录是本次最终成功证据，复跑请改成一个未使用的目录。最终日志为 `build/verification/crop-gametest-spawn-capture.log`；JUnit XML 位于 `build/test-results/test/`，两个审计 JSON 与运行日志位于 `build/verification/`。

## 验证中修正的问题

- 甜菜根独立定义年龄属性，没有调用父类状态注册；补独立疾病状态注入，验证成熟 3→病死 2。
- 洋葱使用自己的病、死属性；统一死亡转换时保留正确属性键，成熟病死退一龄。
- 普通死株不再算活株的邻接或拥挤，同 ID 死茎仍维持来源判断。
- 活病马铃薯由掉落表产生一枚毒薯，旧病害事件不再覆盖它，也不能复活已取消或清空的掉落。
- 肥沃附魔处理 NeoForge 先发工具事件、后求原生改土结果的顺序，真实锄与鹤嘴锄耕地均已验证。
- 核实默认物品调用链后，关闭未启用的点击耕地骨粉转发、瓜茎和蓝莓骨粉催熟分支。
- 修正甘蔗即时天空检查，新放屋顶或树叶不会因延迟光照而误长，玻璃保持透光。
- 雪天年龄零死茎先返种子的来源分支不再被旧病害事件清空；普通作物真实水淹保持来源例外。

全套回归也暴露了旧夹具的不稳定条件：单个 quart 的沙漠填充可能被原生模糊生物群系采样绕过；待恢复怪物目标直接移动可能跨到尚未可见的实体区块；矿物、炉火经验和鸡蛋的同步取样可能早于实体区块可见。相应测试改为完整恢复的气候夹具、同区块返近位置与可见性正控制，或临时捕获真实 `EntityJoinLevelEvent`。产量、经验、归属及拒绝条件的原断言保留，未修改上述游戏功能来迁就测试。捕获监听器在 `finally` 中移除；最终使用新测试世界全套通过。

首轮及中途失败日志保留为 `crop-gametest-first.log`、`crop-gametest-second.log`、`crop-gametest-diagnostic.log`、`crop-gametest-final.log`、`crop-gametest-clean.log`，不把这些运行记为通过。客户端视觉、扩展模组整合、长期农场性能及真实多人仍以用户后续反馈为准。
