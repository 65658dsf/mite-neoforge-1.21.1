# 035 自动验证与交付状态

开发者：NingMeng123。日期：2026-10-15。基线：`4cf3356154f3242b10c782aa6c6bb2b5bdf323b0`。2026-10-15用户在唱片误播放与命名修正后反馈“好的没有问题了，提交github把”，确认当前交付正常并授权提交。没有启动、重启或操作用户游戏窗口。

## 最终自动结果

使用 Java 21 和固定的 Minecraft 1.21.1 / NeoForge 21.1.259：

```powershell
$env:JAVA_HOME='D:/Coding/huanjing/jdk'
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/monster-sound-gametest-run
python tools/testing/verify_stacking_030.py --report build/verification/035-stack-regression-audit.json
```

- JUnit：1166 项，0 失败、0 错误。
- 独立无界面 GameTest 服务端：966 项全部通过，新增 73 项；服务端报告模拟测试运行 36.67 秒，整个构建任务成功。
- Python：既有堆叠规格与配方回归 11 项通过，1691 个物品映射、66 条现代批量配方和来源覆盖保持。
- 发布 JAR：710 个当前生产类逐字节核对、2066 个 JSON、177 个已声明 Mixin 核对通过；不包含 GameTest/测试函数/反编译源码，不捆绑 JEI 或其 API。
- 本批新增六个 common Mixin 随服务端启动实际应用，默认 `require=1`；目标写入拦截、门路径、仙人掌接触及移除路径均执行了真实测试。

本机完整日志位于忽略目录 `build/verification/monster-sound-validation.log`，堆叠/打包审计为 `035-stack-regression-audit.json`，本批范围及包核对为 `035-audit.json`、`035-commit-scope.json`。这些本机输出不打包进模组。

| 新增测试类 | 项数 | 主要验证 |
| --- | ---: | --- |
| `MonsterObstaclesGameTests` | 26 | 真实 Goal、门户种类/难度、材料等级、11阶段、存储恢复、工具掉落与耐久、土元素占用捷径 |
| `MonsterTargetMeleeGameTests` | 22 | 寻敌高度/距离、实际生存玩家、同类协作、60/61刻视线、源射线、近战触及、保存恢复与事件取消 |
| `CreeperTreeGameTests` | 15 | 起爆/续爆分支、实际仙人掌伤害与计数、螺旋树候选、最后1刻强化、真实扩火与脚底接触 |
| `MonsterIntegrationGameTests` | 10 | 实体加入时安装、禁用继承泄漏、智能学习、狂暴不叠加、实际远程箭、玻璃板/金属栏/树叶探线、凋灵骷髅武器切换 |

## 失败定位与复测

首轮 963 项中有三项失败，未当作通过：

1. 旧 031 挖掘用例写完 NBT 后直接调用未激活 Goal 的 `tick`。035 需要实际选择器 `canUse/start` 才延迟恢复 ForgeData。补全真实生命周期后，原 40 刻初始等待与 11 阶段断言保持。
2. 高柱弓手用例的 `makeMockServerPlayerInLevel()` 在固定版本中覆写 `isCreative()` 恒为 true，修改 game mode 仍不能作为生存目标。改为实际登记的生存 ServerPlayer，保留 20 层及超限拒绝边界。
3. 旧爆炸用例在一格模板外同步搬移目标并立即爆炸。夹具改为先加载最终场地区块、在最终位置登记目标，额外断言实体查询包含目标且实际暴露率为正；4/8/9 格及线性伤害断言保持，未改生产爆炸公式。

第二轮修复以上三项后，旧 032 马繁殖用例出现合法随机结果导致的失败：来源平均值可以恰好仍为母亲原生命 40，不能断言随机值必定不同。固定独立已验证种子 2 后，加强为母亲生命及基础值精确 41、父亲两者仍为 60，保留子代 53/.225/.7 默认断言；生产公式未改。

随后增加凋灵骷髅任务切换及树候选测试、强化火焰边界检查，最终完整 965 项全部通过。JUnit、包审计和 Python 也通过；不将失败轮日志隐藏为成功记录。

## 敲门唱片误播放与代码命名修正

用户反馈敲门时误播放唱片。原因是沿用 R196 事件 1010，但 1.21.1 的该编号是唱片播放；旧破门 1012 也不再适用。现使用新版 `SOUND_ZOMBIE_WOODEN_DOOR`、`SOUND_ZOMBIE_DOOR_CRASH`、`PARTICLES_DESTROY_BLOCK` 语义常量。

新增真实玩家网络连接捕包测试：固定抽签触发敲击，实际推进至破门，确认门坐标出现 1019/1021，并且没有 1010/1012。新增测试后完整 966 项 GameTest、1166 项 JUnit、11 项 Python 及当前生产类打包审计全部通过。

20 个新增 Java 文件、类名及 Mixin 桥方法已去掉批次后缀；同步更新注册、调用和文档引用。发布 JAR 确认不存在带 `035` 的 class；已有 NBT、SavedData 身份保持兼容。文档验收编号继续用于查阅历史。

## 用户验收与记录边界

用户确认当前交付测试正常并明确授权推送；本记录不补写未提供的逐项动画、路径、扩展模组组合、两名真实玩家联机、专服重启或长时间负载结果。GameTest 已验证序列化/重载接口和服务端交互，不冒称实际关闭游戏再重进。用户测试步骤见 [035 手测清单](../monster-ai-035-manual-test.md)。本批按中文 `feat:` 信息提交。

可安装文件：`build/libs/mite-0.1.0.jar`，6967238 字节，SHA-256：`33f3ae6373bb3d9220942d8ed0933cfcfe10141f4c1c403202d2f1572ed4bbbf`。
