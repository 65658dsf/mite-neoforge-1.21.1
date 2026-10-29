# 白色与黑色腐蚀史莱姆的近战音效

开发者：NingMeng123。日期：2026-10-29。本地交付沿用工作区版本0.2.18，本次修复未修改版本配置。

低版本 `EntityGelatinousCube.onMeleeAttacked` 即使面对免疫伤害的灰色软泥、黑色布丁，也会处理攻击者手持物的腐蚀；但只有 `InventoryPlayer.takeDamage` 返回成功时才触发 `steam_with_hiss`。创造模式、徒手和耐腐蚀物品均可能不触发，因此此前仅恢复实际装备腐蚀时的声音，仍不能满足创造模式测试中“攻击白/黑史莱姆就有腐蚀声”的预期。

现在在真正成立的服务端玩家近战回调中，为灰色软泥和黑色布丁增加纯反馈兜底：装备已发生腐蚀时沿用原有反馈；没有发生腐蚀时补一次相同声音及蒸汽。创造模式仍不损耗装备，耐酸物品和空手不会因此被消耗。普通绿色、黄色和红色史莱姆不增加此兜底；投射物、挥空、未知实体、越距或被取消的攻击也不会触发。怪物攻击玩家时的库存腐蚀仍聚合为一声，无敌帧拒绝的接触不会重复播放。

声音使用低版本 `random.fizz` 对应的原生 `minecraft:block.fire.extinguish`，音量0.7，音调 `1.6 + (rand-rand) × 0.4`，范围1.2～2.0；在攻击者身体中部广播，并附带5粒云状蒸汽。没有新增声音文件或修改音量配置。

验证：2036项JUnit通过；`mite_corrosion_sound` 的7项GameTest通过，包括既有两项装备/接触回归和五项真实攻击包测试。新测试将玩家注册到实际PlayerList、将史莱姆加入实体跟踪，通过 `ServerboundInteractPacket` 进入 `Player.attack`，并检查客户端连接确实收到恰好一个 `ClientboundSoundPacket` 及一个蒸汽包，同时核对免伤、装备损耗、普通史莱姆和拒绝攻击边界。声音坐标使用原生声音包编码比较，以适配随机测试位置的远距离浮点精度。

- 构建：`build/verification/cube-corrosion-audio-build.log`。
- 服务端：`build/verification/cube-corrosion-audio-packets.log`。
- 精准运行：`runGameTestServer -PmiteGameTestNamespaces=mite_corrosion_sound`；新命名空间也已加入默认回归集合。
- 交付：`build/releases/cube-corrosion-audio-2026-10-29/`，包含主模组JAR和带SHA-256的验证记录。
