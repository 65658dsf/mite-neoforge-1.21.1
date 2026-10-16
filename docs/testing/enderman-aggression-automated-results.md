# 末影物品敌视自动验证

开发者：NingMeng123。日期：2026-10-16。自动验证对象为基于 `b7be6d6690a5a44d9dbbe1159141c52bfef3de3c` 的末影物品敌视工作树，下述校验值对应当时交付的测试包；用户随后确认当前交付正常并授权提交。规则、明确适配与手测入口见 [末影物品敌视](../enderman-item-aggression.md)，结构化依据见 [规格](../../spec/enderman-item-aggression.json)。

## 结果

| 检查 | 实际结果 |
| --- | --- |
| JUnit | 1340 项全部通过，失败、错误和跳过均为 0 |
| 独立服务器 GameTest | 全新存档 1388 项必需测试全部通过；本批新增 11 项，另修正 2 项既有末影诅咒测试 |
| 测试 JAR | 构建通过；920 个生产类与编译输出逐字节一致，2241 个 JSON 可解析，264 个登记 Mixin 无重复且类均存在 |
| 接线与依赖 | 旧两个末影诅咒 Mixin 已由统一获取目标 Mixin 替代，旧类不在 JAR；必需依赖仍只有 Minecraft、NeoForge，不捆绑测试或可选扩展类 |
| 既有工作 | 自动验证时，开始前 44 个未提交文件字节内容保持不变；这些既有改动排除在本批提交范围外 |

包内没有新增客户端界面或网络载荷；沿用末影人原生目标状态、动画和声音同步，不额外叠播凝视音效。

## 重点验证

- 精确检查 RNG 边界和调用次序：先 `nextInt(3)`，诅咒成功优先于手持立即规则；手持不抽库存随机数，库存为 0 不抽，数量至少 2000 时仍抽一次，`roll < N` 严格比较。使用可脚本校验 bound 与调用次数的测试随机源，不靠碰巧抽中。
- 真实生存玩家、原生已注册 Goal：珍珠/末影之眼在主副手共四种组合，戴南瓜且背对时均在同一次 `start()` 锁定，不重复掷骰或消耗物品；背包成功/失败临界值进入真实目标生命周期。
- 两种数量跨主背包、快捷栏、护甲和副手合计，主手不重复计数；鼠标栈、合成格、普通容器、末影箱、潜影盒和收纳袋内部内容排除。
- 先选最近合法可见玩家，较近未受激怒玩家会阻止跳到更远携带者；墙体、创造/旁观、64 格及更远候选均在随机抽取前排除。
- 待锁定阶段重新检查当前原因，撤掉物品或转至副手生效；已锁定后不重新执行物品概率，交出物品不直接清除目标；原生凝视冻结和追击继续使用真实凝视。
- 两项诅咒回归改用完整源链：墙体先排除候选且不消耗诅咒随机数，移除墙体后精确 1/3；原生 `isLookingAtMe` 不承载获取目标时的额外随机规则。

完整 R196 调用链证实 `tg → og → abw` 先要求视线，前批把 `tg.f` 内提前返回解释为“穿过外层视线”的实现有误。本批修正该行为及来源规格，原有验证文档保留当时运行记录，不将错误预期继续作为验收标准。

## 复现与产物

Java 21、Minecraft 1.21.1、NeoForge 21.1.259、ModDevGradle 2.0.148。

```powershell
.\gradlew.bat test jar runGameTestServer -PmiteGameTestDirectory=build/verification/enderman-aggression-first-run --console=plain
python build/verification/audit-enderman-package.py
```

本机忽略目录中的证据：

- `build/verification/enderman-aggression-first-test.log`：`All 1388 required tests passed`、`BUILD SUCCESSFUL`。
- `build/test-results/test/TEST-*.xml`：JUnit 原始报告。
- `build/verification/enderman-aggression-package-audit.json`：类、资源、Mixin、概率规格和保留文件审计。

`build/libs/mite-0.1.0-enderman-aggression-test.jar` 与正常构建产物内容一致，大小 7,495,427 字节，SHA-256：

```text
70cf42107009c17840ba7e8fc1a76f8e2c11d0460f37ba78bfe8469481175647
```

没有操作、重启或关闭用户游戏。2026-10-16 用户反馈“提交吧”，确认当前交付正常并授权提交；未另行提供逐项画面/声音、真实多人或长期运行结果，不补记这些项目已通过。
