# 031 自动验证与待手测记录

开发者：NingMeng123。日期：2026-10-14。原作：Avernite。

本批自动验证已通过。2026-10-14 用户要求“提交github把”，明确授权提交当前交付；未另行提供逐组手测结果，客户端外观、扩展模组组合和真实多人操作不据此补记为逐项通过。保留 [手测清单](031-mob-spawning.md) 供后续反馈与复测；没有自动关闭、重启或操作用户的游戏窗口。

## 实际执行结果

| 验证 | 实际结果 | 本地记录 |
| --- | --- | --- |
| Java 21 编译、资源生成 | 成功；数据运行目录与用户客户端隔离 | `build/verification/031-final-build-data.log` |
| JUnit | 1152 项，失败/错误/跳过均为 0 | `build/test-results/test/TEST-*.xml` |
| 完整服务端 GameTest | 756 项全部通过，测试运行 38.42 秒 | `build/verification/031-tests-thirteenth.log` |
| 堆叠提取与映射 Python 回归 | 两组共 11 项通过 | `build/verification/031-stack-regression-audit.json` |
| 生物来源、资源与 JAR | 3828 项检查通过；29 种新增生物、44 PNG、44 OGG | `build/verification/031-mob-package-audit.json` |
| 当前注册与 JAR 一致性 | 1691 个物品、66 条配方批次、621 个生产类、150 条 Mixin 与可选 JEI 元数据通过 | `build/verification/031-stack-regression-audit.json` |
| 模型结构与资源 | 8 个模型层、44 张纹理及 4 个状态接口通过 CPU 检查 | `build/verification/031-client-model-audit.json` |

模型检查没有创建 Minecraft 客户端、世界或 OpenGL 上下文，未验证实际渲染帧。发布 JAR 不含 GameTest、反编译游戏类、JEI、Minecraft 或 NeoForge 的依赖字节码。原始包仅作本地提取来源，公开规格保存事实、来源位置与方法校验值，不包含完整反编译方法正文。

最终构建：`build/libs/mite-0.1.0.jar`，6,756,656 字节。

SHA-256：`782eac913dd6c9d725af11d3b0a422364240619e6e6c6523376ff8fd61785963`。

## 回归确认的边界

- 新增 29 种生物的注册、属性、尺寸、经验、状态和相应家庭行为；普通原版对应生物的来源属性、装备与死亡材料。
- 自然生成的来源池、深度映射、负 Y、光照、名额、月相、刷怪笼边界和初始动物配额。
- 蜘蛛的来源蛛网库存、跃击、毒、白天户外中立、普通蜘蛛骑乘及生成完成流程。户外判断读取真实开放列和可通行路线；完整屋顶下的亮屋不因侧向天光或光照传播延迟被误判为户外。
- 凋零天然防护 4、女巫间接魔法防护 10；NPC 防护后正数伤害最少为 1，不使用仅玩家具有的随机完全格挡。
- 死亡掉落进入可取消事务，来源幼体和关闭 `doMobLoot` 的死亡不返还储存的箭，回收状态清空且不重复生成。
- 挖掘 AI 在构造目标后读取实体存档时恢复既有进度；装备、库存、材料/热量、尺寸等持久与同步路径分别测试。
- 来源 NPC 的抗火不免熔岩，烧着的箭保留物理命中；落物按来源预算磨损头盔并保留取消事件；吸收后最低生命伤害和额外 NPC 吸收扣减遵从来源，前事件置零仍不制造伤害。
- 已有七种来源马铠在 `BODY` 槽位参与实际防护；金马铠实际和显示均为来源 3 点，普通防护不挡熔岩或绝对伤害。

这是固定场景的自动回归，并非对所有自然生成频率、视觉、完整养殖代谢或所有原版 Boss AI 的实机验收。当前范围与后续事项见 [031 范围说明](../mobs-031-status.md)。

## 复现

```powershell
$env:JAVA_HOME = 'D:/Coding/huanjing/jdk'
.\gradlew.bat compileGametestJava test runData '-PmiteDataDirectory=build/verification/031-data-run'
.\gradlew.bat build runGameTestServer '-PmiteGameTestDirectory=build/verification/031-gametest-thirteenth-run'
python tools/testing/verify_mobs_031.py
python tools/testing/verify_stacking_030.py --report build/verification/031-stack-regression-audit.json
```

以上为无游戏窗口的数据和服务端验证。开发客户端由用户保存退出后自行重新运行 `runClient`；独立实例安装最新 JAR。本次按用户明确授权以中文 `feat:` 信息提交；后续独立问题修复使用 `fix:`。
