# 地底地牢自动验证

开发署名：NingMeng123。日期：2026-10-15。用户已确认当前交付正常并授权提交，未另行提供逐项画面、操作及真实多人记录；本轮未打开、关闭、重启或操作用户的游戏窗口。

本页保留地底地牢初次交付的历史验证和 JAR 哈希。后续宝库奖励调整的最新构建、1076 项全量回归及奖励概率见[地底宝库验证](underworld-vault-loot-automated-results.md)；原地牢相关测试已随该次回归再次通过。

## 本次新增验证

- 20 项纯规则单元测试：固定身份映射、未接管身份保留、两种领主排除、幂等及映射表不可变。
- 5 项世界生成 GameTest：真实结构集合和原版试炼参数、自然结构起点与实际试炼方块、仅地底注入普通地牢、洞口/地基的原版房间条件、自然普通地牢及其箱子和刷怪身份。
- 8 项刷怪笼 GameTest：普通笼当前与加权候选保存、真实笼生成与装备初始化、两套试炼配置及存档轮次保留、有光铜地板上的七类替换体、直接写入领主后的不祥试炼、原有自定义光照限制、递归乘客排除、主世界保持原身份。
- 2 项完整试炼波次 GameTest：普通与不祥模式各自通过真实 `tickServer` 刷两只怪，核对存活期间 UUID 跟踪和数量限制，逐只击杀、实际钥匙奖励、冷却及第二轮再次选择 MITE 身份。只有 `id` 的配置还检查 `TRIAL_SPAWNER` 原因和 R196 武器初始化。

上述 15 项新增 GameTest 随最终全量回归全部通过，日志为 `underworld-dungeons-gametest-release.log`。

## 自然生成证据

测试从真实地底生成器的注册表、随机结构集合和生物群系特征开始，没有用 `/place` 命令或人工房间代替自然验证。

| 类型 | 测试世界种子 | 找到的位置 | 实际加载验证 |
|---|---:|---|---|
| 试炼密室 | 0 | 试炼刷怪笼 `134474, 221, -134604` | 自然组装超过 5 个拼图部件，检查 2 个实际生成区块即找到功能方块实体；各部件位于已有洞穴高度带，地幔和顶基岩仍在 |
| 普通地牢 | 0 | 普通刷怪笼 `144050, 248, -143796` | 第 1 个实际验证区块找到石质地板、箱子、普通笼与合法 MITE 身份；样本中出现过 `revenant` 与 `demon_spider` |

普通地牢先独立检查原始天然地形与原生候选随机序列，再对几何候选执行真实 FULL 区块生成，控制测试成本；不改世界随机数、生产生成频率或天然地形。此预筛选不代替最后的实际区块检查。

原版 GameTest 世界默认关闭结构，因此试炼自然测试仅在夹具内暂时启用世界选项和该维度结构管理器的结构开关，结束时恢复。生产代码仍尊重玩家存档的结构设置。

## 最终结果

最终完整回归通过：`All 1065 required tests passed`，`BUILD SUCCESSFUL`。全部验证使用本次工作树的实际编译输出。

| 验证 | 结果 |
|---|---|
| 数据生成 | 成功；5 份世界生成 JSON 由新 provider 维护 |
| 单元测试 | 1221 项，通过；零失败、错误、跳过 |
| 本次新增 GameTest | 15 项全部通过，含两个完整试炼波次 |
| 全量 GameTest | 1065 项 required 全部通过，零失败；本次新增 15 项 |
| 打包与既有 Python 回归 | 778 个生产类、2122 个 JSON、198 个 Mixin 与 11 项 Python 回归通过；未打包测试或可选集成 API |
| 其他未提交内容 | 原有 42 个文件的 SHA-256 保持一致 |
| 客户端和真实联机 | 用户确认当前交付正常；逐项及真实多人未另行记录 |

构建产物：`build/libs/mite-0.1.0.jar`，7,156,357 字节，SHA-256：

```text
7bb004953cc32fac6712630dc47f9996b273ba6421bbe026d93db735d4465305
```

## 运行记录与复现

- `build/verification/underworld-dungeons-compile.log`：编译与单元测试。
- `build/verification/underworld-dungeons-datagen.log`：隔离数据生成。
- `build/verification/underworld-dungeons-gametest-release.log`：最终完整回归。
- `build/verification/underworld-dungeons-package-audit.json`：生产类、资源、打包与 Python 回归。
- `build/verification/underworld-dungeons-resource-audit.json`：世界生成资源和原有文件保留检查。

```powershell
.\gradlew.bat runData -PmiteDataDirectory=build/verification/underworld-dungeons-data-run
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/underworld-dungeons-gametest-release
python tools/testing/verify_stacking_030.py --report build/verification/underworld-dungeons-package-audit.json
```

使用 Java 21。复跑请换成未使用的隔离目录；真实测试客户端继续沿用 `run/mods/`，本轮没有挪动或替换用户模组。

首轮及修复过程日志保留为 `underworld-dungeons-gametest.log`、`underworld-dungeons-gametest-final.log`、`underworld-dungeons-gametest-verified.log` 和 `underworld-dungeons-gametest-confirmed.log`。修复限于测试夹具：补齐人工房间地基，按解码后的值比较原生范围编码并处理重复不祥配置的省略，使用合法且独占的完整区块依赖并等待真实实体可见，旧制造/目标边界/发射器断言等待或捕获实际实体。原有产量、总数、40 格边界和防重复断言保留，没有为通过测试修改制造、目标距离或堆叠生产逻辑。

手动验证见 [地底地牢操作步骤](../underworld-dungeons.md)。2026-10-15 用户反馈“没有问题了提交吧”，确认当前交付并授权提交；不补写未反馈的逐项手测结果。
