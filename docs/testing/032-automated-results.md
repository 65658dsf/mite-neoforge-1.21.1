# 032 自动验证与手测状态

开发者：NingMeng123。原作：Avernite。日期：2026-10-15。

本批自动验证通过。2026-10-15 用户反馈“好的没有问题了提交吧”，确认本批测试正常并授权提交当前交付。用户没有另行提供逐项记录，因此不补记具体动画、扩展模组组合或高密度农场性能结果。没有操作、重启或关闭用户的游戏窗口。操作步骤与现代适配见 [032 手测入口](032-animal-ai.md)，包括 [牲畜](032-livestock.md)、[专属动物](032-special-animals.md) 和 [消失保护](032-animal-despawn.md)。

## 实际验证

| 检查 | 结果 | 本地记录 |
| --- | --- | --- |
| Java 21 编译与独立数据生成 | 成功，生成资源与已有数据一致 | `build/verification/032-final-data.log` |
| JUnit | 1166 项，失败/错误/跳过均为 0；本批新增 14 项 | `build/test-results/test/TEST-*.xml` |
| 完整服务器 GameTest | 874 项全部通过，测试运行 1.739 分钟 | `build/verification/032-gametest-fourth.log` |
| 本批实际实体验收 | 共用 AI 17、牲畜 34、专属动物 48、消失规则 19，共新增 118 项 | 四个 `*032GameTests` 类 |
| 动物来源与当前 JAR | 63580 项检查，0 失败；107 类、2919 方法索引、160 个规则方法引用、17 个新 Mixin | `build/verification/032-animal-package-audit.json` |
| 堆叠、配方批次与依赖隔离 | 1691 个映射、66 条批次配方、677 个生产类、167 条全部 Mixin、2066 个 JSON 通过 | `build/verification/032-stack-regression-audit.json` |
| Python 提取与映射回归 | 两组共 11 项通过 | 同上 |
| 既有生物与原资源回归 | 3885 项检查，29 生物、44 PNG、44 OGG 通过 | `build/verification/032-mob-regression-audit.json` |

最终 JAR：`build/libs/mite-0.1.0.jar`，6887818 字节。

SHA-256：`24633f9ce4857a3eadfa62d094b083493c473cb3b2fcda16c66dbc7d96ff1968`。

JAR 与当前全部生产类逐字节一致，不含 GameTest、原始反编译类或 Minecraft/NeoForge/JEI 依赖字节码。开发署名为 NingMeng123，JEI 继续是可选扩展。源码和规格只记录来源摘要、方法位置与校验值；旧版 World 没有被实例化执行。

## 回归中的修正

源码与固定现代 API 交叉检查后，修正同级任务构造顺序、遗漏捕食者、碰墙上浮、坐下宠物近身拾食、空炼药锅成长边界、植物践踏掉落、惊群/咀嚼门槛和恐慌临时状态。原版 `Animal.actuallyHurt` 在事件判定前清繁殖状态，现改为最终伤害产生后执行，前事件置零不会凭空制造恐惧或打断繁殖。

首轮 800 项中有两项失败，分别定位上述实际伤害事务问题和水中测试场景。加入完整专属与消失验收后的第三轮有四项专属测试输入问题：来源浮点相等边界、伤狼不能直接设置恋爱、船刻数由服务器外层递增、豹猫生成前现代 `Mob.finalizeSpawn` 已消费世界随机数。依据源码和真实 API 调整独立输入，没有删除测试或调低来源规则。第四轮包含最终全部 874 项并通过。

## 验证边界

已测试真实实体创建、事件注册、资源更新、互动消耗、出生与生产、伤害、目标任务、存档读取、转换、船碰撞和实际消失入口。测试使用固定场景和独立来源常量，不等于确认全部自然寻路观感、动画、光影/扩展模组组合或高密度农场性能。

马繁殖属性写到亲本、豹猫实际驯服入口依赖第二种鱼的来源行为保留；现代实体拆分、负 Y、寻路及保存适配明确写在手测文档，不冒充 R196 原 NBT 或同种子执行结果。用户已确认本批交付，后续按实际反馈继续修复和复测。

## 复现

```powershell
$env:JAVA_HOME = 'D:/Coding/huanjing/jdk'
.\gradlew.bat compileGametestJava test runData '-PmiteDataDirectory=build/verification/032-data-run' *> build/verification/032-final-data.log
.\gradlew.bat build runGameTestServer '-PmiteGameTestDirectory=build/verification/032-gametest-fourth-run' *> build/verification/032-gametest-fourth.log
python tools/testing/verify_animals_032.py --game-log build/verification/032-gametest-fourth.log
python tools/testing/verify_stacking_030.py --report build/verification/032-stack-regression-audit.json
python tools/testing/verify_mobs_031.py
```

先确认每步 Gradle 日志末尾为 `BUILD SUCCESSFUL` 再继续；审计工具读取实际完成段，不自行启动游戏。所有目录与用户测试客户端隔离。
