# 特殊骷髅与怨魂的索敌眼色

开发者：NingMeng123。原作：Avernite。适用 Minecraft 1.21.1 / NeoForge 21.1.259。

## 修复范围

| 生物 | ID | 使用的原眼部纹理 |
| --- | --- | --- |
| 远古骷髅守卫 | `mite:longdead_guardian` | `skeleton/longdead_guardian_glow.png` |
| 骷髅领主 | `mite:bone_lord` | `skeleton/bone_lord_glow.png` |
| 远古骷髅领主 | `mite:ancient_bone_lord` | `skeleton/longdead_guardian_glow.png` |
| 怨魂 | `mite:revenant` | `zombie/revenant_glow.png` |

这四种生物没有存活的生存/冒险玩家目标时绘制普通受光的黑眼；服务端接受该玩家为当前攻击目标后，绘制全亮红眼。清空目标、转向非玩家、目标死亡或变为创造/旁观模式后恢复黑眼。普通远古骷髅和其他物种不增加此表现。

`Mob.getTarget()` 不自动同步到客户端。本修复在两种具体实体类内添加一个默认 false 的 `SynchedEntityData` 布尔值；`setTarget` 先执行原实现，读取 NeoForge 取消或替换后的实际目标，再更新眼色；服务端 `aiStep` 再校正目标死亡和模式变化。渲染只读取同步值，观察者客户端不需要有目标引用。该表现不写入额外永久愤怒状态；重新载入由实际目标恢复重新计算。

## 来源与明确适配

- 本地 R196 包 SHA-256：`fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`。
- 用户提供的资源包 SHA-256：`4a9bb9f7ab5afee7c0e4e9f57f3b81ff185a0c619d29036b8c2f1ece7450e67a`。
- 来源 `bhm` 指定守卫/远古领主和骷髅领主身体路径；`bhz` 指定怨魂路径；`bgm.setTexture` 自动发现同名 `_glow.png`；`bhb.renderModelGlowing` 使用全亮光照渲染。
- 原包这三张眼图已存在，但此前两个现代渲染器没有接入。现在逐字节导入原图，不重绘。资源及源码校验见 [来源记录](../spec/monster-target-eyes-assets.json)。
- 三张身体图的眼部四个像素是透明的；眼图仅在 `(9,12)`、`(10,12)`、`(13,12)`、`(14,12)` 有不透明像素。待机时使用同一遮罩乘黑色并按普通光照绘制，避免直接关闭红眼层后透出背景。身体 PNG 保持原字节。
- “未索敌黑、锁定玩家红”来自用户本次明确要求，原 R196 自动发光渲染本身没有该条件，不将此条件冒称为原版默认规则。
- 红眼使用原版 `RenderType.eyes` 和全亮光照；黑眼使用普通 cutout 渲染。眼部遵守深度遮挡，不穿透不透明头盔；实体隐身时隐藏整个新增眼层。

## 手测步骤

自行重启客户端或更新 JAR 后，使用有作弊权限的测试存档。先进入创造模式，在夜间或遮阳场地依次召唤：

```mcfunction
/gamemode creative
/time set midnight
/summon mite:longdead_guardian ~5 ~ ~ {Tags:["eye_test"],PersistenceRequired:1b}
/summon mite:bone_lord ~5 ~ ~4 {Tags:["eye_test"],PersistenceRequired:1b}
/summon mite:ancient_bone_lord ~5 ~ ~8 {Tags:["eye_test"],PersistenceRequired:1b}
/summon mite:revenant ~5 ~ ~12 {Tags:["eye_test"],PersistenceRequired:1b}
/item replace entity @e[tag=eye_test] armor.head with minecraft:air
```

1. 创造模式下，四者没有玩家目标时应为黑眼，不能透出背景或保持红眼。
2. 在附近、同一高度切换 `/gamemode survival`，让每只实际锁定你，眼睛应变成红色。可隔着玻璃观察，避免近战或箭直接打到；领主可能召唤随从，建议分只测试。
3. 切回 `/gamemode creative`，眼睛应恢复黑色。重新进入生存并被锁定后应再次变红。
4. 在暗处对比：只有眼睛发红光，不能把整个头或身体变成全亮；待机黑眼不应发红。恢复头盔后检查不透明部分仍遮挡眼部。
5. 可选联机补测：一个生存玩家吸引目标，另一个创造/旁观玩家观察，双方应看到同一红眼状态；目标离开或死亡后双方恢复黑眼。没有玩家目标时，村民或动物目标不能激活红眼。

清理本次召唤的实体：`/kill @e[tag=eye_test]`。用户未提供逐项画面或真实联机结果前，不记录为已经通过。

## 自动验证

`MonsterTargetEyesGameTests` 覆盖四种真实实体、普通远古骷髅排除、非玩家目标、可取消/替换目标事件、目标死亡和创造/旁观模式，以及真实 metadata 编解码往返。客户端 CPU 审计检查原图字节、四像素遮罩、实际两个渲染器构造接线和同步值读取，不创建窗口、世界或 OpenGL，也不替代游戏内画面验收。

2026-10-15：Java 21 构建、1166 项 JUnit、974 项服务端 GameTest（新增 8 项）及 11 项 Python 回归全部通过。隔离 `runData` 的客户端 CPU 审计通过：3 张眼图原字节与 4 个实体映射、每图 4 个眼部像素、两个实际渲染器构造及同步接口调用均已核对，未执行渲染或自动操作游戏窗口。

当前 713 个生产类、2066 个 JSON、177 个 Mixin 的发布包检查通过，无 GameTest、审核代码或 JEI API 捆绑。JAR 文件为 `build/libs/mite-0.1.0.jar`；校验值和本批文件范围记录在本机忽略目录 `build/verification/monster-target-eyes-audit.json`、`monster-target-eyes-commit-scope.json`。完整日志为 `monster-eyes-validation.log`，客户端审计为 `monster-eyes-client-audit.log`；这类本机输出不进入发布包。

2026-10-15用户反馈“好的没有问题，可以提交了”，确认当前交付正常并授权提交 GitHub。未另行提供逐项截图、真实客户端联机或光影组合结果，不额外补记为通过。自动同步包与 CPU 接线审计的验证范围保持上述说明。
