# 试炼天然方块掉落验证

开发署名：NingMeng123。日期：2026-10-15。基于提交 `55c270df94e8891b39eb03071709facd45a9d211` 的工作树实现；2026-10-15 用户反馈“好的没有问题，提交吧”，确认本批交付正常并授权提交；逐项画面及真实多人结果未另行记录。本轮未操作、打开或重启用户游戏窗口。

## 最终结果

| 验证 | 结果 |
|---|---|
| 编译与 JUnit | 1221 项全部通过，零失败、错误、跳过 |
| 全量 GameTest | `All 1107 required tests passed`、`BUILD SUCCESSFUL` |
| 本轮新增 | 31 项 GameTest 全部通过；另加强既有自然地底 FULL 区块生成测试的来源断言 |
| 来源与持久化 | 真实模板落块、Proto 晋升、原生 ChunkSerializer 往返、铜表面变化、活塞和落沙全部通过 |
| 打包与已有 Python 回归 | 800 个生产类逐字节匹配当前编译结果、2133 个 JSON、210 个 Mixin，11 项 Python 回归通过 |
| 无关工作 | 本轮开始时 44 个排除文件的 SHA-256 保持一致 |
| 客户端及真实多人 | 用户确认本批交付正常；逐项画面及真实多人未另行记录 |

构建产物：`build/libs/mite-0.1.0.jar`，7,203,392 字节；当前工作树 JAR 的 SHA-256：

```text
70919daad28cffb8073be3967fb8d5c8d8ad4a4d65b330c9ab230d98654db3f6
```

## 测试覆盖

- **13 项真实破坏与奖励测试**：两个维度的生存挖掘、精准/时运、正向经验对照；原生 TNT 引信 tick、TNT/MOB 爆炸及同一次爆炸里的混合来源归并；玩家新增/原位重放；容器单独散落、门双半边支撑连锁、灯泡属性；直接掉落与原始方块替换。天然饰纹陶罐的内容物保留，包含真实 `minecraft:pots/trial_chambers/corridor` 固定种子的延迟生成奖励，断言只产出相同物品及总数，不含罐体、砖或陶片。
- **5 项来源与存档测试**：九类铜形状的氧化/刮削/上蜡/除蜡；实际 LevelChunk 属性更新及拆后重放；不同材料/形状替换清理；原生 Proto 晋升与区块序列化往返及副本独立；无来源读取和未加载位置查询不分配附件或加载区块。
- **3 项真实结构模板测试**：使用注册结构的 `generate` 和 `placeInChunk`，在两维度分别实际放置主房间列与真实陶罐列；来源不通过手动标记建立，包含铜、凝灰岩、方块实体、未写入地形和后置普通方块的正反对照；实际模板写入异常以及嵌套非目标结构退出后不泄漏生成作用域。该测试用于验证模板入口，不主张平坦测试世界具备原版自然结构分布。
- **4 项原生活塞测试**：混合天然/玩家推送链、粘性回拉、移动中原生方块实体 NBT 重载和提前 `finalTick`、无法推动时保持原状态。推拉功能保留，玩家块不继承来源。
- **6 项原生掉落方块测试**：两个维度内天然与玩家砂的落地/破坏正反对照、实体保存重载、受阻落地、超时转物品及普通 `DropItem=false` 实体隔离。来源与禁物品设置都持久化。

掉落断言同步捕获真实实体加入事件，避免将随机网格区块尚未进入实体索引的空查询误判为没有掉落。正向对照必须实际产出，天然目标必须确实被破坏。

## 真实生成证据

本轮模板验证输出：

| 注册结构 | 实际放置列数 | 来源方块数 | 铜类 | 凝灰岩类 | 实际天然陶罐 |
|---|---:|---:|---:|---:|---|
| `minecraft:trial_chambers` | 2 | 7881 | 1715 | 6049 | `-257907, -32, 264630` |
| `mite:underworld_trial_chambers` | 2 | 8712 | 1770 | 6838 | `-258893, 206, 264638` |

坐标属于本次隔离测试夹具，不是玩家存档的通用定位坐标。另一个既有测试从地底生成器的真实结构候选经过自然 FULL 区块生成，找到真实试炼刷怪笼并确认来源记录已随 Proto 晋升保留。

## 日志与复现

```powershell
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/trial-structure-drops-gametest-final
python tools/testing/verify_stacking_030.py --report build/verification/trial-structure-drops-package-audit.json
```

使用 Java 21。复跑选择新的隔离 GameTest 目录，避免旧测试存档状态；不挪动真实测试客户端 `run/mods/`。

- `build/verification/trial-structure-drops-gametest-final.log`：完整回归 1107 项通过。
- `build/verification/trial-structure-drops-package-audit.json`：生产类、资源、Mixin 与 Python 回归。
- `build/verification/trial-structure-drops-commit-scope.json`：工作范围、JAR 和无关文件保留记录。

前轮日志保留：初次编译修正了测试 `AABB` 构造参数；两轮生成夹具依赖不足已按相交结构片段的完整模板边界和处理器读取范围补齐轻量 Proto 依赖，实际仍只放置目标列。另将旧降雪成熟作物测试的即时实体索引查询改为同步捕获，保留“真实降雪替换为雪”和“恰好一枚小麦”断言。未为通过这些用例修改作物生产机制或降低原有验收要求。

规则、旧存档范围与手测见[试炼天然方块掉落限制](../trial-block-drops.md)。
