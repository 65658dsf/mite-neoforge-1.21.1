# 2026-10-31 鸡圈持续恐慌复查

开发者：NingMeng123。

## 问题与证据

用户观察到鸡圈内动物高速乱跑，并确认停止攻击约三分钟后仍持续。截图本身不能区分恐慌、避捕食者、寻找食水或宽敞位置；也不能仅凭截图确认服务器 Tick 延迟由这些动物造成。

源码中存在明确的旧恐慌续期路径：`Flee.tick()` 每 10 个任务刻检查路线结束、卡住和威胁移动，必要时调用 `replan()`；原先 `movementPath()` 在没有可解析攻击者时又按恐慌概率调用 `startPanicking()`，鸡为 75%，将倒计时重设为 100～199 刻。圈内短路线反复结束，会反复给旧恐慌续期，即使已经没有新伤害、火焰或有效惊吓。

现有的群体报警传递原绝对截止时间和剩余攻击者记忆，没有发现该分支无限延长玩家报警。普通火把、灯笼也不属于避火代码识别的 `BaseFireBlock`。仍存在捕食者时持续避让属于另一条行为路径。

## 性能边界

重新完整遍历此前 `rAQu7kdCNX` 的服务线程调用树，300,000 ms 采样中，`Flee.canUse` 为 140 ms（约 0.0467%），其内部 `panicking` 为 70 ms，两者不能相加。该采样没有捕获 `Flee.start/tick/movementPath` 或 `AnimalEscapePath.find` 执行；这不能证明真实耗时为零，也不能排除截图时正在发生的问题。

该旧报告的主要动物开销仍是 `LivestockSeeking032.movementPath`，占主线程约 16.94%。采样不记录实例的 `water` 字段，无法把这个完整占比准确分成觅食与寻水。因此，本次确认的是一个行为和潜在计算负载漏洞，尚无修复后的同场景实服报告证明它是全部卡顿的原因。

## 实服只读检查

站在鸡群旁可读取最近一只鸡的状态：

```mcfunction
/data get entity @e[type=minecraft:chicken,sort=nearest,limit=1,distance=..16] NeoForgeData.mite_animal_ai_032
```

重点看 `panic_countdown`、`attacker_memory`、`attacker`、`spooked_until`、`herd_alarm_until`、`decided_to_flee` 和 `fleeing`。`*_until` 是游戏总刻截止时间，可用 `/time query gametime` 对照；最近一只选择器在动物移动后可能换到另一只。NeoForge 21.1.259 的持久化键确认为 `NeoForgeData`。

## 修复与验证

`Flee.start()` 在初次规划期间设置 `startingPath` 并在 `finally` 中恢复；随机恐慌只允许在该初始阶段掷骰。保留初次启动的两个原有判断及其随机顺序，运行中的重新选路不再随机续期。已知攻击者的逃跑选路、搜索范围、候选评分、速度和每 10 刻检查条件均未改变；新的真实伤害、有效惊吓和燃烧仍能刷新恐慌。

- 隔离构建成功，2043 项 JUnit 全部通过。
- 118 项动物相关 GameTest 全部通过，包括新增的 4 项恐慌生命周期测试，旧 14 项群体逃跑断言保持不变。
- 用上一交付包的原始 `Flee.class` 替换独立 `legacy-build` 中的对应编译类，运行完全相同的 18 项群体测试：17 项通过，新增续期回归在第 10 刻确定失败，本应剩余 110 刻却被重新设为 147。工作区源码、修正版编译目录和交付 JAR 均未被旧类替换。
- 修正版与上一包相比，仅 `Flee` 的指令发生变化；同源文件其他嵌套类的字节差异来自源码行号移动，已对照 `javap -c -p` 指令一致。版本、署名、资源及包内不含 GameTest 类均已核验。

日志及源码／包哈希记录在 `build/verification/animal-panic-20261031/acceptance.json`。测试包：`build/releases/animal-panic-fix-20261031/mite-0.2.19.jar`，10,047,832 字节；SHA-256 为 `ac5945212329d7cf9cb3109452b4e604afc3330155029fe01cb5f0f27079f1b1`。版本保持 0.2.19，包含上一轮异步寻路功能。此包先行交付恐慌漏洞修复，后续进一步降低 AI 负载的工作另行记录。
