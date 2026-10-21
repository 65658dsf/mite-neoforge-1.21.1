# 掉落物寿命与金属箱子破坏播报

作者：NingMeng123

本次按玩家要求进行现代玩法调整。

普通物品掉落实体的默认寿命由 6000 tick（5 分钟）延长到 18000 tick（15 分钟），包括玩家主动丢出的物品、死亡背包掉落和普通方块掉落。计时采用实体实际运行的 tick，正常 20 TPS 时为 15 分钟；区块卸载期间不计时。旧存档的默认寿命自动迁移，已存在时间保持不变，保存、重登不会重置倒计时。

第 5～15 分钟仍可正常合并物品堆。自定义寿命、无限寿命和原生到期事件继续有效，假物品仍于下一 tick 到期。经验球不属于物品掉落实体，继续使用既有规则。

非所有者玩家实际拆除有主金属箱子后，全服聊天栏显示“破坏者 破坏了属于 箱主 的金属箱子名称”。七种金属箱子均适用，生存与创造模式均会播报；箱主自行拆除、无主箱子、工具不足和被保护插件取消的破坏不会触发这条消息。箱子的所有者仍采用原有 UUID 与存档姓名记录。

自动验证覆盖完整 18000 tick 生命周期、主动丢弃和死亡背包掉落流程、存档迁移与剩余时间、特殊寿命、到期延长事件，以及七种箱子的实际破坏和聊天数据包。

验证结果：构建、1670 项 JUnit 和本次 10 项专项 GameTest 均通过。2026-10-21 提交前基于当前 0.2.6 版本再次构建并通过全部 10 项专项 GameTest，日志为 `build/verification/item-lifetime-strongbox-submit.log`，分发包为 `build/libs/mite-0.2.6.jar`；已核对生产类、Mixin 注册与中英文播报文本入包，测试类未入包。没有操作用户游戏窗口或进行画面手测。

2026-10-21 用户授权提交本批修改。

```powershell
.\gradlew.bat build compileGametestJava --console=plain
.\gradlew.bat runGameTestServer "-PmiteGameTestNamespaces=mite_item_lifetime,mite_strongbox_announcement" "-PmiteGameTestDirectory=build/verification/item-lifetime-strongbox-run" --console=plain
```

游戏内复测：玩家甲放置金属箱子，玩家乙持满足现有采集要求的镐拆除，在线玩家应收到包含乙、甲和箱子名称的一条聊天消息；再由甲拆自己的箱子，应无此消息。丢下一件普通物品，保持所在区块运行，5 分钟后仍在，15 分钟后清理。
