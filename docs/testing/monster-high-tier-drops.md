# 怪物自带高阶装备禁掉验证

开发者：NingMeng123。日期：2026-10-18。

按用户要求，怪物自带秘银及以上装备固定不掉落，包含秘银、艾德曼、振金。不论普通附魔、独立副词条、品质、磨损或名称，均使用真实装备材质判断。拾取／原掉率大于 1 的保证装备仍沿原规则归还；古金及以下继续沿用此前的三分之一和附魔减半规则。完整说明见 [怪物装备掉率](../monster-equipment-drops.md)。

死亡使用 `MobEquipmentDrops.keep`，缴械和换装只使用无随机数的 `canDrop` 材质门槛。缴械照常移除武器并保留 40 tick 效果；换装照常接收新装备，只禁止抛出旧的自然高阶装备。原版换装读取替换前的掉率，避免新装备的拾取标记放行旧装备。非怪物分类和金属原料保持原规则。

## 自动验证

Java 21 / NeoForge 21.1.259。在仓库外的独立构建及游戏目录串行执行 `gradlew.bat build runGameTestServer`：

- **1589 项 JUnit 全部通过**，无失败、错误或跳过。
- **2053 项服务端 GameTest 全部通过**，服务器正常保存退出，Gradle `BUILD SUCCESSFUL`。
- 发布包包含统一规则、原版换装／死亡 Mixin、骷髅换装和缴械入口，不含测试类或测试结构。

新增回归覆盖来源七槽、原生六槽的自然装备禁掉和拾取归还；共享物品表包含 37 种真实高阶工具武器、护甲、链甲、马铠、弓、盾和钓竿，选装 Simply Swords 时再扩展 45 种武器。另覆盖真实玩家攻击触发缴械、原版换装、普通及来源骷髅双持换装、抢夺跨越 100% 后仍不能掉自然高阶装备、改名不绕过、原随机调用保持，以及古金／秘银原料边界。

首轮有两处测试夹具问题：护甲拾取会消耗输入栈，已改为与调用前的副本比较；古金锁链甲的测试物品 ID 已改为实际注册的 `ancient_metal_chainmail_chestplate`。修正后完成上述全量通过。未操作用户客户端，自动验证不代表实际画面验收。

日志：`build/verification/monster-high-tier-drops-final.log`。包审计：`build/verification/monster-high-tier-drops-audit.json`。

## 测试包

`build/libs/mite-0.1.0-monster-high-tier-no-drops.jar`，8834366 字节。

SHA-256：`2ee8d0e180ac83115a62f7f2e6a381430db3e391f99d428bb1396cc470ffa6d8`。
