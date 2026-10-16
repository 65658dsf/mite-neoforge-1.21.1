# 首领僵尸生成生命修正

开发者：NingMeng123。日期：2026-10-16。

## 原因与修复

原版 `Zombie.finalizeSpawn` 最后调用 `handleAttributes`，首领分支只提高最大生命，没有同步当前生命。尸壳继承这一流程，所以可能刚生成就显示 `20/49`。MITE 的来源僵尸属性分支及冤魂独立生成方法也有相同缺口。

生成开始前记录生命上限和新生状态；生成结束后，仅当实体尚未生成过、未加入世界、未经历实体刻、原本满血且最大生命确实提高时，将当前生命设为最终上限。来源僵尸与现代尸壳、溺尸共用父类生成包装；冤魂自己的覆盖方法使用同一辅助逻辑。

没有在实体加载或每刻更新时补血。受伤实体、已初始化的实体及明确指定的 `Health` 数据保留伤情。生命上限、首领概率、属性随机抽取和装备生成数值均未改变。

生产位置：

- `SourceZombieDifficulty.captureSpawnHealth` / `finishSpawn`。
- `SourceZombieDifficultyMixin` 包装原生 `Zombie.finalizeSpawn`。
- `R196Zombie.finalizeSpawn` 覆盖中的相同初始化处理。

## 自动验证

`ZombieLeaderHealthGameTests` 使用真实 `EventHooks.finalizeMobSpawn` 和实际首领随机属性，不人工注入首领生命修饰符。

- 普通僵尸、僵尸村民、僵尸猪灵、尸壳、溺尸及冤魂。
- 自然生成、普通刷怪笼、刷怪蛋、增援、命令生成。
- 新生首领当前生命等于实际提高后的最大生命。
- 受伤后原生 NBT 重载、显式 `Health`、受伤实体重复初始化均不回血。
- 非首领生成仍保持原始生命上限和满血。

2026-10-16 自动验证：Java 21 / NeoForge 21.1.259，`build runGameTestServer` 通过；**1455 项 JUnit、1505 项 GameTest 全部通过**。服务端测试完成正常保存与退出，未操作用户客户端。

测试包：`build/libs/mite-0.1.0-monster-progression-test.jar`（7702562 字节）。

SHA-256：`39624b434db9a82af487a3de13b89daf55935ff744e43d26a29b96aa5ac615e5`。

日志：`build/verification/monster-periodic-final-build-test.log`；打包审计：`build/verification/monster-progression-package-audit.json`。只需 Minecraft 与 NeoForge，JEI/Jade 为可选拓展，未将测试类或拓展本体打进模组 JAR。

## 手动验证

1. 替换测试实例中的旧 MITE JAR，同一实例只保留一份。
2. 在此前能刷出首领的位置重新使用僵尸、尸壳、溺尸刷怪蛋，或执行不带 NBT 的 `/summon minecraft:husk`。首领是随机生成，需要观察生命上限高于普通个体的目标。
3. 新生目标在未受到日晒、窒息或战斗伤害时，Jade 应显示当前生命等于最大生命，例如 `49/49`。
4. 打伤它后保存退出、重新进入，当前生命应保持受伤值，不会因为本次修复被补满。

请用新生成的首领验证；旧存档中原有的 `20/49` 无法与正常受伤的同血量目标可靠区分，因此不会追溯补血。

测试包同时包含现代护甲与韧性、日期成长和刷怪调整。日期成长会进一步提高最终生命上限，首领验证应检查是否达到其实际显示的最终上限，不要求固定为 49。
