# Jade 作物成熟倒计时与回家门地表落点

开发者：NingMeng123。日期：2026-11-02。保持现有版本 `0.3.0`。

本文记录首次随机估算倒计时交付。用户随后确认普通作物需要 60 分钟基准及 35 分钟硬下限，成熟计时现已替换为真实持久化方案；当前行为与新产物哈希见 [60/35 分钟真实成熟计时](crop-timed-maturity-2026-11-02.md)。本页保留的 JAR 哈希是前一份构建。

## 行为

- Jade 为普通食用作物、瓜茎、蓝莓、可可和下界疣增加预计倒计时。采用实际阶段、生长检定、土壤/肥力/布局和随机刻速率，显示剩余有效生长时间；瓜茎只计算茎长成。
- 持续观察时逐秒递减，夜间暂停；缺水、病害、不合适环境、关闭随机刻时提示暂停。实际阶段或速率变化后重估，归零后等待随机生长，成熟标签始终取真实方块状态。
- 不更改作物生长机制，不抽取额外随机数。旧服务端没有时间字段时保留原提示，不猜测时间。位置、维度、完整方块状态及时间有效期继续校验。
- 回家门原来先从顶到底搜索出生点整列，地表不安全时可能先选中地下洞穴。现在每列只检查高度图地表，再检查邻列；半径 8 格内没有安全地表则取消传送，保留地形。

完整显示边界见 [Jade 作物说明](../jade-crops.md)，传送门原因及手测步骤见 [回家门修复](home-portal-surface-landing.md)。

## 验证

- `test jar`：2,054 项 JUnit 全部通过；其中 7 项新测试覆盖计时公式、持续刷新、暂停恢复、目标与速率变化、离开/回退及到期。
- 第一轮服务端 89 项 required GameTest 全部断言通过，覆盖作物现有生命周期及 5 项新增倒计时场景、3 项新增回家门场景与原有跨维度传送。该进程随后在原生 `ChunkMap.processUnloads` 关服阶段持续忙循环，抓取线程栈后只结束了这一隔离测试进程；本轮不将该命令记为成功退出。
- 实际 Jade `15.10.6+neoforge` 的独立 `runData` 审计通过：倒计时格式、夜间暂停、到期等待、旧包兼容，以及位置/方块/维度/状态与 16 类无效数据回退均正常。
- 最终定向服务端回归：76 项 required GameTest 全部通过，世界保存及关服正常，Gradle 成功退出。日志为 `build/verification/crop-countdown-home-final-gametest.log`；复现时添加 `-PmiteCountdownSkipCrossDimension=true` 并使用新的独立运行目录。
- 两种语言 JSON 无重复键，新增提示项齐全；JAR 中修改后的生产类与实际编译输出一致，没有打包 Jade API 或 GameTest 类。

日志：`build/verification/crop-countdown-home-unit-build.log`、`build/verification/crop-countdown-home-gametest.log`、`build/verification/crop-countdown-jade-data.log`；Jade 报告：`build/verification/crop-countdown-jade-audit.json`。验证脚本：`build/verification/crop-countdown-home.init.gradle`。使用独立测试目录，没有修改玩家存档或客户端 `run/mods`。

为核对关服行为，定向复跑移除了原有跨维度测试，回家门夹具改用未入服 `FakePlayer`，仍通过真实 `NetherPortalBlock` 解析目标。首次定向复跑正常退出，但一个旧作物光照测试在原生天光尚未初始化时读到 0 而失败；其夹具已改为在独立批次等待原生天光传播完成，保留亮度 15/14/夜间 4 及实际增长的全部断言。没有修改生产光照逻辑。

自动化只检查服务端行为和 Jade API/提示组件，不视作游戏画面验收。

## 产物

`build/libs/mite-0.3.0.jar`，SHA-256：

```text
413a19ed5e0e0be3a3549e896e98ff943605a8227beb564154b25dda06e85b7e
```
