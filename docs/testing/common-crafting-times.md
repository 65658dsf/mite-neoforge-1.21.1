# 日常制造计时与第 65 天生成验证

作者：NingMeng123。日期：2026-10-23。

## 合成计时

来源与迁移说明见 [日常配方计时](../common-crafting-times.md)。350 个补充静态 ID 全部存在于实际 RecipeManager，整批难度、工作台材料和 source order 逐条对应本地 R196 注册数据。原有显式规则优先，没有按输出数量重复放大或摊薄时间。

0 级、无笨拙、基础材料工作台下，箱子为 210 tick，木棍一批 4 根为 104 tick，碗一批 4 个为 126 tick，床为 180 tick。木棍在背包中为 126 tick；其它三类受网格大小限制。全部 16 色床、竹子制棒及床染色也有明确的配方族映射。

真实菜单回归覆盖普通点击、Shift 连续、直接 quickMove、数字键、副手交换、丢弃、收集、重复点击及 N−1/N 完成边界；红石自动合成器覆盖箱子完整计时和每脉冲一批。桦木门另覆盖完整计时、有限库存与满库存、鼠标持物、生存/创造模式及合法拆栈，原粗铜即时配方的批次守恒回归保留。

动态配方使用实际输入匹配、组装和计时：烟花火箭、烟火之星、褪色之星沿用 R196 order 1 / difficulty 100，地图复制沿用 155 / 100，地图扩展沿用 156 / 300。验证烟花组件、同地图 ID 复制及扩展标记。静态无星烟花 `firework_rocket_simple` 也沿用整批 difficulty 100。

## 检查结果

- `python tools/specs/generate_common_crafting_times.py --check` 通过。
- 1722 项 JUnit 通过；构建和打包通过。
- 10 项新增专项 GameTest 通过；最终本体全量 2358 项 GameTest 全部通过。
- 最终实际管理器有 1072 条静态计时配方、444 条静态即时配方、12 条需要真实输入的动态配方；350 个补表 ID 没有缺失。完整即时清单见 [运行时审计](../../spec/common-crafting-runtime-audit.json)。这些现代专属操作保留原规则，本轮没有按猜测统一补一个最短耗时。

首次全量中，四条旧门测试及一条烈焰粉流程测试仍假设即时取出，现已调整为实际完整制造；另一次美西螈临时桶用例出现同 tick 实体计数为零，未修改桶生产逻辑，最终全量未复现。未声称已定位该偶发问题。客户端手工操作与 JEI 画面本轮没有人工验收。

原始日志和运行时报告位于被忽略的 `build/verification/common-crafting-focused.log`、`common-crafting-final.log`、`common-crafting-coverage.json`。发布包检查位于 `common-crafting-package-audit.json`，确认包含 350 条规则及无星烟花规则，没有测试类。

## 极难版配套

ITE 自身自动生成统一至少第 65 天解锁。两预设均验证第 64 天末 tick `1535999` 拒绝、第 65 天首 tick `1536000` 解锁；保留更晚的物种日期、其它生成条件、已有实体以及命令和刷怪蛋。具体实现在相邻 `MITE Modern ITE` 工程。

最终本体与 ITE 配套构建、ITE 的 35 项 JUnit 和 301 项全量 GameTest 通过。最终配套审计在 ITE 的 `build/verification/final-crafting-day65/final-pair-firework-audit.json`；该版本本体 SHA256 为 `b078c37b33ed6d319370b9b34bf345587c1588bd821b541e7ecf693b66879368`，ITE 为 `1aaf25b70e0b755b24e362bf4e35bd83bcaba80bc2638d939bb107735b08b1ef`。

交付为本体 `build/libs/mite-0.2.8.jar` 与相邻 ITE 的 `build/libs/modern-ite-0.2.8.jar`。更新实例时各替换原来的一份对应 JAR。
