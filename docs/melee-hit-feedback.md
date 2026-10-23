# 近战命中反馈核查

开发署名：NingMeng123。核查日期：2026-10-23。

## 快速连续点击

报告条件为零级默认属性，主要在快速连续点击时出现动物击退或转身、没有红闪和受伤声音。为避免旧测试的高攻击速度掩盖问题，新审计保留原生空手攻击属性 1、攻击速度 4、零级最大生命 6 和食物能量，通过实际连接的 `ServerPlayer.attack` 攻击牛、羊、鸡。

基础环境、Better Combat 2.4.0＋极难版 0.2.8，以及同时安装 Simply Swords 1.70.2 与 SimplyTooltips 0.1.5 的完整扩展组合均得到相同结果：

| 场景 | 实际生命变化 |
|---|---|
| 蓄满后空手攻击 | 减少 1 点 |
| 同一目标立即再打 | 受伤免疫拒绝，减少 0 点 |
| 每 tick 连点时的有效攻击 | 约减少 0.272 点 |
| 等待免伤结束并恢复蓄力 | 再次减少 1 点 |
| 木剑卸下后立即攻击新目标 | 仍有小数伤害，恢复蓄力后为 1 点 |
| 僵尸空手或木剑攻击 | 首次扣血，立即重复被拒绝，免伤结束后恢复扣血 |

每个环境记录 138 次攻击，其中 54 次扣血、84 次零伤。零伤的重复攻击必须同时满足：没有新增速度、不刷新 `hurtTime`、不刷新 `invulnerableTime`。这些断言已通过，不能把每次快速点击都当作一次应扣血的命中。

原生 `LivingEntity.hurt` 的 20 tick 受伤免疫计时中，剩余大于 10 tick 时拒绝不高于上次的伤害；空手蓄力则使用现代攻击冷却。两者独立保留。`SourcePlayerCombat` 中低生命、食物能量耗尽或攻击属性不足时的弱击规则也保留，默认健康且有食物的玩家未进入弱击。

## 确认并修复的横扫问题

本地 1.21.1 NeoForge 源码的 `Player.attack` 对横扫副目标先调用额外 `knockback`，再调用 `hurt`。此前 MITE 只包装后一个调用来检查触及，因此原生候选范围内、MITE 近战触及外的副目标仍会被推动。处于受伤免疫期的副目标也会出现同样的零伤额外击退。

`SourceEnchantmentPlayerMeleeMixin` 现在先保存本次副目标的原生额外击退操作，随后验证触及并执行实际伤害；只有 `hurt` 接受且生命或吸收确实减少时才调用保存的操作。操作在伤害回调前消耗，每次攻击独立，避免跨目标及递归调用复用。

五项真实 `Player.attack` 回归验证了正常横扫、超距拒绝、受伤免疫拒绝、纯吸收命中、NeoForge 击退事件取消。主目标击退和伤害内部击退沿用原流程；有效副目标仍获得两次原生击退。顺序由“横扫额外击退→伤害内部击退”改为相反顺序，两个方向不同时最终速度可能略有变化。

## 证据边界与复现

默认属性审计关闭动物 AI、固定目标位置，隔离攻击事务本身；没有模拟真实鼠标输入、客户端插值、受伤渲染或动物逃跑转身。首次有效攻击后的残留击退与逃跑可能和后续免伤点击重叠，但本次测试不能证明这就是用户所见动作的全部原因。原生免伤判断前还会修改 `walkAnimation`，该动作不等同于生命损失或新的击退。

专项运行命令：

```powershell
.\gradlew.bat runGameTestServer '-PmiteGameTestDirectory=build/verification/melee-feedback-base' '-PmiteGameTestNamespaces=mite_damage_audit' --console=plain
```

审计 JSON 默认输出到 `build/verification/default-melee-audit.json`，可用 JVM 属性 `mite.defaultMeleeAuditOutput` 覆盖。基础环境、Better Combat＋极难版、完整扩展组合的留档分别为 `build/verification/melee-feedback-base-audit.json`、`build/verification/melee-feedback-bettercombat-ite-audit.json`、`build/verification/melee-feedback-all-mods-audit.json`；对应日志均在同一目录。

最初的完整扩展审计因模拟连接未协商 `simplyswords:observer_status_effects` 客户端载荷而停止。夹具改用 NeoForge 标准 GameTest API `NetworkRegistry.configureMockConnection` 完成已注册模组通道的模拟协商后，六项专项全部通过；未屏蔽网络校验或攻击代码，仍不代表真实客户端输入与渲染已经验证。

## 整体回归结果

为避免其他任务同时编译而覆盖共享 class 输出，在 `build/verification/melee-feedback-project` 保存源码副本并独立构建。1722 项 JUnit 全部通过，模组 JAR 生成成功。2378 项完整服务端测试中 2368 项通过、10 项失败，不能记为全套通过；日志为 `build/verification/melee-feedback-isolated-regression.log`。

失败均为同期提交 `5f5ccd7e` 新增“第 35 天前清空怪物装备”规则与原装备断言冲突：两项试炼轮次、弓手寻路、凋零骷髅剑、原生及地下蜘蛛骑手、蜘蛛骑士、入场保留合法盔甲、两项地下刷怪笼替换。相关路径没有调用本次修改的 `Player.attack`。本次未改动这些装备规则或用例。
