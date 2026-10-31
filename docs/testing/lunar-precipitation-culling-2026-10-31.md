# 血月降水与 AsyncParticles 天气裁剪崩溃

开发者：NingMeng123。日期：2026-10-31。

## 原因与修复

用户提供的 `crash-2026-10-28_16.07.44-client.txt` 显示，客户端在
`LevelRenderer.renderSnowAndRain` 中调用 MITE 的 `bloodPeriodPrecipitation`，
随后在 `Biome.getPrecipitationAt` 的 MixinExtras bridge 对空 biome 解引用。
世界时间为 `744003`，即第 32 天白昼，满足血月强制降水条件。

核对本机报告对应的 `AsyncParticles-21.1.4.10+1.21.1.jar` 后确认：其天气裁剪
Mixin 用 `Holder.direct(null)` 标记不可见的天气列，并通过
`shouldRenderWeatherColumn` 阻止这些列进入降水绘制。MITE 原来的表达式修改
将这个 `false` 与血月条件做或运算，重新放行被裁剪的列。

客户端降水 gate 改为接收实际 biome 的 `WrapOperation`，空值直接返回 `false`；
雨雪类型包装同样在调用原始 operation 前将空值返回为 `NONE`。
不重新查询 biome、不修改 Mixin 优先级，也不绕过天气裁剪。
有效 biome 继续沿用原生雨雪及既有血月干旱地区降水规则。

## 回归方式

扩展 `LunarVisualAudit` 并新增仅在开发环境加载的 `LunarPrecipitationAudit`。
从实际导出的 `LevelRenderer` 中提取两个已合并包装方法的字节码，放入普通对象夹具执行；
空 biome 搭配会直接抛错的原始 operation，确认两个入口都提前返回。

同时验证崩溃时刻 `744003`、血月昼夜、蓝月、有效的原生雨雪、暖冷干旱地区血月降水，
以及普通日和其他维度不强制降水。验证实际雨幕／雨声入口仍可达这些包装方法，
并在加载 AsyncParticles 时验证其天气裁剪包装仍在调用链中。

日历夹具仅使用无构造的 `ClientLevel` 并填充时间和维度字段。
不初始化或构造渲染器，不创建 Minecraft 实例，不加载世界，不调用 OpenGL。
这项审计不代替真实整合包画面的游戏内复测。

首轮 AsyncParticles 审计的新增崩溃回归全部通过，但旧的夜间停雨审计要求
取消回调直接位于 `tickRain` 入口，因此失败。检查实际导出类后确认原生体被
`WrapMethod` 移至 `tickRain$mixinextras$wrapped$145`，其中取消分支仍早于原生天气处理。
审计已沿实际方法调用及 operation lambda 找到每个原生体，严格检查同一
`CallbackInfo` 的 `isCancelled → IFEQ → RETURN` 位于 `effects()` 之前，并记录方法名和指令位置。
没有跳过原断言或改变生产夜间行为。

## 验证记录

工作区原有的未提交 `LivestockSearchBudget.java` 引用了尚不存在的
`LivestockSearchSession`，完整构建出现 5 个无关编译错误。
没有改写这些开发内容；另从提交 `552517fd5058c5d5c88ebcefa5eac8b5bd90d0e2`
导出隔离副本，复制本次生产修复、审计代码及当前 `gradle.properties` 验证。
版本保持工作区已有的 `0.2.19`。

日志和报告保存在 `build/verification/lunar-precipitation-20261031/`。

- 隔离副本 `build compileGametestJava` 成功；2,043 项 JUnit 全部通过，无失败、错误或跳过。
- 原生客户端审计通过，并在更新夜间取消审计后再次通过。
- AsyncParticles `21.1.4.10` 的最终客户端审计通过，实际裁剪包装仍可达。
- AsyncParticles `21.1.4.10` + Sodium `0.8.13` + Iris `1.8.14-beta.1` 的客户端审计通过，
  同时保留 Iris 月盘开关和 MITE 月盘包装链。
- 三组环境都执行相同的空 biome、真实雨雪及日历／维度隔离回归。
- 未运行服务端 GameTest，本次没有服务端行为改动；未启动真实游戏窗口。

隔离目录内使用以下命令，分别将 `native-run` 换为 `async-run`、`shader-run`：

```powershell
.\gradlew.bat build compileGametestJava
.\gradlew.bat runData '-PmiteDataDirectory=build/verification/native-run' '-PmiteLunarVisualAudit=true'
```

每轮默认审计输出 `build/verification/lunar-visual-audit.json`，随后分别复制到本轮根目录的
`native.json`、`async.json`、`shader.json`。组合 JAR 的 SHA-256 记录在 `compat-mods.json`。
隔离构建不包含工作区其他未完成的功能更改，不能作为这些更改的整包验证。
