# R196 时运验证与 0.1.10 构建

开发者：NingMeng123。日期：2026-10-18。工作起点为 `326d9b1`，工作区最初无未提交修改。用户要求实现 R196 时运、测试、提交并推送 GitHub、构建 `0.1.10` JAR；按此次明确要求将工程原版本 `1.1.10` 改为 `0.1.10`。

## 实现与范围

- 继续使用已有的 R196 矿石逐单位额外复制、砂砾筛取和草块蚯蚓规则。普通附魔与装备成长副词条分别计算的适配保持生效。
- 矿石爆炸上下文即使由外部调用传入时运工具，也不读取时运、不给额外副本。
- 普通砂砾的基础掉落改为砂砾；有效玩家破坏再由 `FlintGravelDrops` 执行来源筛取，非玩家不再沿用现代 10% 燧石表。
- 短草、蕨、萤石移除现代时运数量增量。剪刀、精准采集、本次未涉及的基础概率和现代新增方块规则继续按现有适配。
- 下界疣原生正常掉落公式已经等价，既有 Mixin 也已禁止爆炸掉落，本次增加回归覆盖。

完整公式、数量、来源和适配边界见 [时运规则](../fortune-r196.md)。

## 自动结果

| 检查 | 结果 | 日志或报告 |
|---|---|---|
| Java 构建及 JUnit | 1,594 项，0 失败、0 错误、0 跳过 | `build/verification/fortune-0.1.10-build.log`、`build/test-results/test/` |
| 新增时运专项 GameTest | 10 项全部通过 | `build/verification/fortune-0.1.10-focused.log` |
| 全量服务端 GameTest 与构建 | 2,079 项全部通过 | `build/verification/fortune-0.1.10-regression.log` |
| 发布包审计 | 35 项检查，0 失败；解析 3,642 个 JSON | `build/verification/fortune-0.1.10-package.json` |

专项的首次启动发现缺少新命名空间的空结构模板，随后补入 `src/gametest/resources/data/mite_fortune/structure/empty.nbt`，专项与全量运行均已通过。未启动客户端游戏窗口，也未执行另装扩展模组的组合实测。

旧的通用 `verify_enchantments.py` 本次尝试时在解析既有可选标签对象处出现 `TypeError: unhashable type: 'dict'`，未列为通过项；该旧脚本未改动。此次附魔效果、来源分离和完整掉落事务由实际 GameTest 回归，发行包另由 `verify_fortune.py` 审计。

新增专项覆盖时运 0/I/II/III 在每个基础矿物单位上的严格 float 上下边界、青金石和红石 3–5 个基础单位的独立复制、13 种自然/放置金属矿石、精准采集、普通附魔加副词条、真实掉落实体与经验事务、晴雨蚯蚓的全部分母、成熟下界疣每个基础量与时运增量、未成熟恒 1、爆炸保护、萤石与种子不增量以及非玩家砂砾。数量测试使用受控随机边界而非 Monte Carlo 抽样。

复测命令：

```powershell
.\gradlew.bat runGameTestServer -PmiteGameTestNamespaces=mite_fortune -PmiteGameTestDirectory=build/verification/fortune-focused-run --console=plain
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/fortune-full-run --console=plain
python tools/testing/verify_fortune.py
```

## 发布文件

`build/libs/mite-0.1.10.jar`。包内 `META-INF/neoforge.mods.toml` 版本为 `0.1.10`、署名为 `NingMeng123`。审计核对源掉落表与 JAR 内对应资源逐字节相同、矿石没有第二次原版乘法、没有测试类或原始反编译资料入包。

SHA-256：`277c0fdfaec26c950b8ae7e601788530a8565894843d5fb4abd40e1a75342a97`。

## 可选游戏手测

1. 在测试存档使用四把满足采集门槛的镐/铲，分别保持无时运或执行 `/enchant @s minecraft:fortune 1`、`2`、`3`。使用不同工具避免已有精准采集导致互斥拒绝。
2. 挖自然钻石矿时，每块只能产出 1 或 2 颗；青金石/红石基础 3–5 个，时运后可能达到 10 个。少量采样不能用于验证 10%/20%/30% 的精确概率。
3. 比较自然金属矿和玩家重放的矿石：十二种浅/深铜、铁、金、银、秘银、艾德曼均掉对应粗矿，时运同样可额外给一份；下界金矿仍掉原矿，重放后每次只回收一块。精准采集取得原矿且不增加采矿经验。
4. 萤石粉仍为 2–4 个，普通短草/蕨的种子成功分支仍只给 1 粒。成熟下界疣随时运变为 2–4/5/6/7 个，未成熟恒为 1 个。
5. 砂砾每次仅产出一个筛取结果；精准采集仍得砂砾。非冻结草块晴雨测试按规则表比较，虫子出现时替代泥土。带有成长副词条的装备另按两来源适配说明核对。

## 2026-10-19 粗矿时运修复验证

开发者：NingMeng123。十二种普通/深层金属矿掉落粗矿后，不再因玩家放置标记而跳过时运；沿用 R196 每级增加 10% 概率额外复制一份的规则。下界金矿仍掉整块矿石，保留放置抑制。

`test jar runGameTestServer -PmiteGameTestDirectory=build/verification/raw-fortune-placement-run` 执行通过：1,658 项 JUnit、2,158 项服务端 GameTest，均无失败。测试覆盖时运 0–III 的浮点边界、自然/放置矿的相同随机序列、普通附魔与副属性叠加、真实放置及 NBT 往返、精准采集和爆炸掉落。

成品为 `build/libs/mite-0.2.3.jar`。审计确认时运实现类与编译输出一致、六份深层矿石/粗矿贴图与源文件一致，且没有 GameTest 类入包。日志为 `build/verification/raw-fortune-placement.log`，包核对报告为 `build/verification/raw-fortune-placement-package.json`。未执行客户端人工手测。
