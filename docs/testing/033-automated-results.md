# 033 自动验证与手测状态

开发者：NingMeng123。原作：Avernite。日期：2026-10-15。

本批自动验证通过。2026-10-15 用户反馈“没有问题了提交吧”，确认本批测试正常并授权提交当前交付。用户未另行提供逐项画面、扩展模组组合或多人测试记录，不补记这些结果。没有操作、启动、重启或关闭用户的游戏窗口。来源与实际启用范围见 [033 证据](../livestock-health-033-evidence.md)，操作与预期见 [033 手测](033-livestock-health.md)。

## 实际验证

| 检查 | 结果 | 本地记录 |
| --- | --- | --- |
| Java 21 编译、数据生成与测试编译 | 成功，生成资源无变化 | `build/verification/033-build-data-first.log` |
| JUnit | 1166 项，失败、错误、跳过均为 0 | `build/test-results/test/TEST-*.xml` |
| 完整服务器 GameTest | 892 项全部通过，测试运行 31.40 秒 | `build/verification/033-gametest-first.log` |
| 本批真实实体检查 | 新增 18 项，首轮全部通过 | `LivestockHealth033GameTests` |
| 客户端 Mixin 与资源 | 四个实际变换后的渲染器均接入病态 helper；四张 64×32 原图校验通过 | `build/verification/033-client-health-audit.json` |
| 来源与当前 JAR | 4693 项检查，0 失败；83 个来源方法、19 个来源类、50 个规则引用、682 个生产类、3564 个 JAR 条目 | `build/verification/033-livestock-health-package-audit.json` |
| 原图导入核对 | 四张图与原资源包、资产规格、最终 JAR 逐字节一致，共 8248 字节 | `tools/reference/import_livestock_health_assets_033.py --verify --jar build/libs/mite-0.1.0.jar` |
| 堆叠、配方批次及依赖隔离回归 | 1691 个映射、66 条批次配方、682 个生产类、171 条 Mixin、2066 个 JSON 通过 | `build/verification/033-stack-regression-audit.json` |
| Python 回归 | 两组共 11 项通过 | 同上 |
| 差异空白检查 | 通过 | `git diff --check` |

最终 JAR：`build/libs/mite-0.1.0.jar`，6902274 字节。

SHA-256：`f60ed30c503ec85111abebe25b79fa3a96bcfaeafc20ff926eb93f5784972f0b`。

发布 JAR 与当前全部生产类逐字节一致，不含 GameTest、原始反编译类或 Minecraft/NeoForge/JEI 依赖字节码。开发署名为 NingMeng123；JEI 仍为可选扩展。四张病态身体纹理使用用户指定 R196 资源包的原始字节，来源署名独立记录为 Avernite。

## 本批覆盖

覆盖三个资源各自低于 25% 与恰好 25% 的边界、四种成年/幼体病态身体选择、喂食/水碗/真实环境恢复、拥挤和露天自由度恢复、伤害与健康状态分离，以及红/棕蘑菇牛没有病态纹理的来源例外。

生产与互动回归覆盖健康繁殖入口、既存牛奶、鸡蛋/羽毛生产计数、病态剪毛/吃草与自然回血、无额外移动惩罚。保存检查覆盖完整 NBT 加载及实体入世界后同步；网络检查使用真实 `ClientboundSetEntityDataPacket` 编解码并应用到观察者，确认显示读取同步值而不是客户端持久数据。033 没有新增第二份病态存储或感染计时。

R196 来源检查扫描全部 1186 个 class 和 1183 个反编译文件，核对全部 16 个 `isWell` 调用点；结构化记录包括 19 个相关类、83 个方法摘要和 13 条规则。规格只记录位置、事实与校验值，不打包反编译源码，也没有实例化旧版 World 执行。

## 验证边界

客户端审计通过隔离数据生成加载渲染类，检查实际 Mixin 导出字节码和贴图资源；没有创建游戏窗口、请求 OpenGL 渲染或取得游戏内截图。因此不能将自动审计记为病态画面、羊毛/猪鞍图层、资源包/光影组合或真实双客户端显示通过。用户已确认本批交付正常，具体逐项测试过程未另行记录。

健康资源、生产、繁殖及掉落规则已在 032 接入，033 补齐明确遗漏的四种身体外观并独立回归。R196 没有独立随机感染、传染、治疗药物、病态持续扣血/减速或康复倒计时，本批不添加这些行为。

## 复现

```powershell
$env:JAVA_HOME = 'D:/Coding/huanjing/jdk'
.\gradlew.bat compileGametestJava test runData '-PmiteDataDirectory=build/verification/033-data-run' '-PmiteLivestockHealthAudit=true' *> build/verification/033-build-data-first.log
.\gradlew.bat build runGameTestServer '-PmiteGameTestDirectory=build/verification/033-gametest-first-run' *> build/verification/033-gametest-first.log
python tools/testing/verify_livestock_health_033.py --game-log build/verification/033-gametest-first.log
python tools/reference/import_livestock_health_assets_033.py --verify --jar build/libs/mite-0.1.0.jar
python tools/testing/verify_stacking_030.py --report build/verification/033-stack-regression-audit.json
git diff --check
```

每步成功后再继续；验证器读取实际完成日志，不自行启动游戏。以上运行目录与用户测试客户端隔离。复现时可更换日志和测试目录名，避免覆盖已有记录。
