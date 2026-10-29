# 苦力怕方块爆炸范围修复

开发署名：NingMeng123。

## 原因与适配

`R196CreeperExplosionMixin` 原先忽略现代 `Creeper.explodeCreeper` 传入的半径，把 R196 `tf.java` 的 `bs * 0.715` 直接传给现代爆炸引擎。普通苦力怕因此从原版方块强度 `3` 降到 `0.715`，带电与地狱变种同样按错误比例缩小，而且自定义 `ExplosionRadius` NBT 被丢弃。

R196 的 `0.715` 属于旧爆炸算法的参数，不能作为新版半径：`abr.java` 另有低抗性方块的 `2 * 1.25` 预清除阶段，`aqz.getExplosionResistance` 读取同一硬度字段；现代算法与各方块抗性并不相同。本次保留新版地形的爆炸算法及方块抗性，使用现代苦力怕实际传入的方块半径，并保留地狱变种翻倍。这是明确的现代适配，不宣称逐体素重演旧引擎。

| 生物 | 默认方块半径/强度参数 | R196 实体伤害半径 |
| --- | ---: | ---: |
| 普通苦力怕 | 3 | 4.4 |
| 带电苦力怕 | 6 | 8.8 |
| 地狱苦力怕 | 6 | 8.8 |
| 带电地狱苦力怕 | 12 | 17.6 |

实体伤害继续使用独立的源线性伤害与身体四分之一高度爆心；方块半径不会再反向截断实体范围。显式 `ExplosionRadius` NBT 使用现代参数等比计算。`mobGriefing`、NeoForge 爆炸取消/方块列表事件、方块自己的爆炸回调及掉落控制仍通过原有引擎执行，试炼密室天然方块、炉具及矿石掉落规则不作改动。

## 自动回归

`CreeperExplosionGameTests` 实际调用 `Creeper.explodeCreeper`，不是直接用 `Level.explode` 冒充苦力怕引爆。普通、带电、地狱、带电地狱及自定义 NBT 五种情况在两个固定随机种子下，与独立现代爆炸对照的全部射线位置及实际方块移除集合逐一比较；另验证 `mobGriefing=false` 与 NeoForge 取消事件均不会破坏或产出方块掉落。既有实体伤害 GameTest 继续验证独立的源半径及线性伤害。

## 2026-10-29：地狱爆炸残火

`R196CreeperExplosionMixin` 恢复地狱苦力怕的燃烧爆炸标记。低版本 `.reference/r196/decompiled/abr.java:92` 对 `EntityInfernalCreeper` 自动启用该标记，`225–234` 在完成地形破坏后，对受影响列表中为空气、下方为不透明完整方块的位置按 `1/3` 概率放置火。现代原生爆炸已有相同的地面放火流程，因此直接复用；普通及带电普通苦力怕仍不主动放火。

现代适配额外遵守 `mobGriefing`：关闭该规则时禁止残火；NeoForge 取消爆炸或移除受影响位置也会保护相应区域。火焰是实际火方块，没有专属固定持续时间，后续按正常火焰规则熄灭或传播。`doFireTick=false` 不阻止初始放置，只停止后续火焰 tick。

独立输出 `build/verification/infernal-fire-20261029/build` 构建及 JUnit 通过。`InfernalCreeperFireGameTests` 的6项残火回归与既有6项起爆范围回归，共 **12/12 服务端 GameTest 通过**；日志为 `build/verification/infernal-fire-20261029-tests.log`。覆盖普通／带电地狱残火、普通苦力怕无火、事件删位与取消、关闭生物破坏、空气和水面不悬空生火。既有范围比较将残火视为原方块已移除，并逐次清理本次新增火焰，保留全部射线及地形断言。

## 手动测试

在测试世界的空旷土地区域，切换创造模式并执行 `/gamerule mobGriefing true`。分别使用下列命令召唤，观察普通苦力怕能形成正常土坑，带电及地狱变种的坑更大：

```mcfunction
/summon minecraft:creeper ~ ~ ~ {Fuse:20s,ignited:1b}
/summon minecraft:creeper ~ ~ ~ {Fuse:20s,ignited:1b,powered:1b}
/summon mite:infernal_creeper ~ ~ ~ {Fuse:20s,ignited:1b}
```

每次换到未破坏的地点；随后将 `mobGriefing` 设为 `false` 再试，方块应保持完整。试炼密室里饰纹陶罐的内容与天然结构方块禁掉落仍按原规则处理。
