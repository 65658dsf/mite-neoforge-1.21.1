# 女巫诅咒自动验证

开发者：NingMeng123。日期：2026-10-16。自动验证对象为基于 `302fbea135e3fa82450a76e74b73f19e3fc0562a` 的女巫诅咒工作树；下述校验值对应当时交付的测试包。用户随后确认当前交付正常并授权提交。规则见 [R196 来源](../witch-curses-source.md)，操作见 [手测清单](../witch-curses-manual-test.md)。

## 结果

| 检查 | 实际结果 |
| --- | --- |
| JUnit | 1340 项，失败、错误、跳过均为 0；本批新增 7 项 |
| 独立服务器 GameTest | 全新存档 1377 项必需测试全部通过；本批新增 40 项 |
| 客户端 CPU 审计与数据生成 | 通过；实际变换 Gui、LocalPlayer、LivingEntity 字节码及资源校验，没有创建 Minecraft 窗口或调用 OpenGL |
| 测试 JAR | 构建通过，919 个生产类与编译输出逐字节一致；2241 个 JSON 可解析，265 个登记 Mixin 均有类且无重复 |
| 资源与可选扩展 | 诅咒图标与 R196 原图 SHA-256 一致；中英文各新增 43 个键；必需依赖仍只有 Minecraft 和 NeoForge，不捆绑测试、JEI 或 Jade 类 |
| 既有工作 | 42 个非语言文件保持开始前的字节内容；两份语言文件保留用户已有的提示删除，仅另外加入本批诅咒键 |

测试包 `build/libs/mite-0.1.0.jar`，大小 7,492,911 字节，SHA-256：

```text
624a857a118c229a6808578d311a68c90cc9b1e4cd484e2010fb9efd9e932a36
```

另提供内容相同的 `build/libs/mite-0.1.0-witch-curses-test.jar` 便于识别。

## 覆盖范围

- 来源随机选择、32 位加法溢出、女巫种子 NBT、真实目标 Goal 的候选筛选、自然消失与取消死亡。
- 6000 tick 生效边界、世界账本序列化重载、跨维度、死亡重生、下线与施咒者卸载；模拟连接重建玩家镜像，不冒充真实多人客户端验证。
- 16 种效果及其允许边界：90 点空气、疾跑、独立动植物食品属性、现代饮品与投掷物、装备磨损、笨拙制造品质/费用/时间与付款重验、植物移动输入、穿甲、箱子、睡眠、四类 75% 近战恐惧。
- 原生右键与副手穿甲不转移或吞物；真正处于分配器查询内的生存玩家受咒时拒绝装备，解除后同一分配器正常消耗一顶头盔并穿上。
- 末影人实际注册的目标 Goal：受咒时额外 1/3 判定先于南瓜和视线，每个候选只抽一次；未受咒的普通怒气仍检查视线，已有目标的原生注视行为保留。
- 牛奶保留诅咒同时清除普通效果；祛魔瓶解除并返还空瓶；施咒女巫正常死亡解除其所有在线/离线玩家记录，其他女巫与取消死亡不误解除。
- 全部类型的网络编解码及真实管理员命令；状态使用仅向本人发送的显式通道同步，客户端请求只能揭示自身当前诅咒。
- 实际客户端变换接线：低画质下暗角、原生混合公式的最大暗度、陆地隐藏呼吸诅咒气泡而不改真实空气、水下沿用真实空气、普通向前走不误揭示疾跑诅咒；未知/已知状态、提示及中英文/原图校验。

## 运行与证据

Java 21、Minecraft 1.21.1、NeoForge 21.1.259、ModDevGradle 2.0.148。主命令如下：

```powershell
.\gradlew.bat test jar runData -PmiteWitchCurseAudit=true -PmiteDataDirectory=build/verification/witch-curses-client-completion-run --console=plain
.\gradlew.bat test jar runGameTestServer -PmiteGameTestDirectory=build/verification/witch-curses-release-candidate-run --console=plain
python build/verification/audit-witch-package.py
```

本机日志与报告保存在忽略的 `build/verification/`：

- `witch-curses-client-completion-test.log`、`witch-curse-client-audit.json`。
- `witch-curses-release-candidate-test.log`，终结行为 `All 1377 required tests passed` 与 `BUILD SUCCESSFUL`。
- `witch-curses-package-audit.json`；JUnit 原始 XML 位于 `build/test-results/test/`。

前期修复了附件自动同步对模拟/未协商通道的连接发送载荷、护甲槽不可用时原生判定回退到主手，以及两处测试夹具的实体可见性问题。原有燧石短斧掉落测试改用实体加入事件捕获，保留三个原木与耐久断言；分配器夹具改为等待可访问分区内的实际范围查询，不以玩家列表或 UUID 可见代替。

没有操作、重启或关闭用户游戏。2026-10-16 用户反馈“可以提交了”，确认当前交付正常并授权提交；未另行提供逐项画面、光影组合、真实客户端联机或长期运行结果，不补记这些项目已通过。
