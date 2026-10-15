# 作物 Jade 扩展验证

开发署名：NingMeng123。日期：2026-10-15。本轮在作物机制上添加 Jade 扩展。2026-10-15 用户反馈“没有问题了，把这两个提交github吧要分开”，确认两项当前交付正常并授权分开提交；未打开、重启或操作游戏窗口。

## 结果

| 验证 | 结果 |
|---|---|
| 构建和单元测试 | `build` 成功；1201 项 JUnit，零失败/错误/跳过 |
| 无 Jade 独立服务端 | 全部 1050 项 required GameTest 通过，新增 9 项作物查看测试；明确验证 Jade 模组及其 compile-only API 均不在运行时 |
| 有 Jade 的客户端 API 审计 | 实际 Jade `15.10.6+neoforge`，插件注解扫描 1 个，10 类不重叠的目标各注册一次，普通作物无方块实体也可请求数据 |
| 提示行为 | 10 组健康/病害/枯死/洋葱/甜菜根/瓜茎/耕地/蘑菇用例；原版成熟度去重、关闭选项和未接管植物保留原提示；甘蔗空数据回退保留原版行 |
| 数据校验 | 接受当前目标的详细数据，拒绝位置、方块、维度、完整状态、年龄、健康状态、类型不匹配及未知版本、畸形湿度等 10 种负例 |
| 查看无副作用 | 32 次真实服务端查看和序列化不改变作物、耕地或世界随机数序列 |
| 打包 | 771 个生产类字节一致、2117 个 JSON、196 个 Mixin 声明；11 项既有 Python 回归通过；没有打包 Jade API、Jade JAR 或测试类，没有声明 Jade 必需依赖 |
| 游戏内画面与真实联机 | 用户确认当前交付正常；逐项界面和真实多人未另行反馈，API 审计不冒充渲染或网络实机验收 |

客户端 API 审计不创建 Minecraft 实例、世界或图形上下文，不运行渲染。服务端世界内的环境诊断、光照、灌溉和持久状态由 GameTest 验证；用户已确认当前交付正常，逐项记录及真实多人表现可后续按 [手测](../jade-crops.md) 补充。

## 当前 JAR

`build/libs/mite-0.1.0.jar`，7,141,088 字节，SHA-256：

```text
30d5fc9e568a805c4f52c83a050ffa7a891690753033f451e6437654504fba3a
```

当前文件包含作物机制与本轮 Jade 扩展。前一份作物验证文档中保留的 JAR 大小和哈希是添加本扩展之前的历史构建，不能用来校验当前文件。

## 运行证据

- `build/verification/crop-jade-data-audit.log`、`crop-jade-audit.json`：加载实际 Jade 的隔离 `runData` 与 CPU 接口审计。
- `build/verification/crop-jade-gametest-final.log`：新独立世界的最终 1050 项全通过；`build/test-results/test/` 为 JUnit XML。
- `build/verification/crop-jade-package-audit.json`、`crop-jade-optional-dependency-audit.json`：完整发布包与可选依赖检查。
- Jade 15.10.6 JAR SHA-256：`276f1f65a20d04f536989c4f311949d770b52c440c23c2b904a08860f3c93810`。

复现时先把这个外部 Jade JAR 复制到隔离数据运行目录的 `mods/`，然后：

```powershell
.\gradlew.bat runData -PmiteDataDirectory=build/verification/crop-jade-data-run -PmiteCropJadeAudit=true
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/crop-jade-gametest-final
python tools/testing/verify_stacking_030.py --report build/verification/crop-jade-package-audit.json
```

Java 使用 21。复跑 GameTest 请把示例目录改成未使用的新目录，且不要往该服务端目录放 Jade，以保持“无 Jade”测试前提。普通测试客户端继续读取原有 `run/mods/`，本轮没有移除或替换用户的模组。

首轮全套只有旧制造测试的即时掉落取样失败，新增 Jade 测试全部通过；失败日志保留于 `crop-jade-gametest.log`。该旧用例改为复用既有临时实体生成事件捕获，保留 9 锭产量、满包分配、输入消耗及拒绝瞬时交换的全部断言，未改制造或掉落生产代码；最终全套通过。
