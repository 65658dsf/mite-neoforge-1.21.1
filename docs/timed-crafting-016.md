# 016 服务端制造计时

开发署名：NingMeng123。

2026-10-23 补充：箱子、木棍、碗、床及其他遗漏的 R196 日常配方现已纳入统一计时，详见 [日常配方计时补全与审计](common-crafting-times.md)。下文的 016 批次覆盖范围保留为历史记录，不代表当前完整清单。

本批为默认品质的制造计时实现，尚未接入品质选择、品质制造经验费用、笨拙诅咒状态或职业模式。已有物品默认品质仍受各自材料上限限制；耐久品质组件由独立系统维护。

## 覆盖与算术

`src/main/resources/mite/timed-crafting-016.json` 保存 162 个配方 ID 的 R196 整批 difficulty、工作台要求及 runtime order。范围为本批有效工具配方、独立材料工作台、015 金属/碎片兑换、十一种原木/竹块加工及已实现的燧石碎片/筋/木棒/短斧；未列入的现代配方保持即时。原版自动合成器自身的制造配方属于现代内容，当前仍即时；这不免除自动合成器加工本表配方时的设备与计时要求。

按 `docs/crafting-time-evidence.md` 的 float 运算顺序计算 U(D)、等级乘数与工作台修正，最低每批 25 tick。不按结果数量摊薄周期。没有材料门槛的配方使用任意工作台均为 +0.2；背包为 0。笨拙翻倍的纯公式已保留，但当前没有游戏诅咒状态入口，因此生产调用传 false。

## 手工入口

- 普通左键结果槽只启动一批，完成后停止，即使还有材料；Shift＋左键启动连续制造，每批独立计时，直到材料不足或取消。这里的一批指配方原有产量，例如原木加工一次仍出 4 块木板。右键结果取消是本次现代交互适配，原版 R196 的右键品质选择尚未接入。
- 服务端 Session 保存菜单对象、配方 ID、所在 Level 和每个物理格的完整 ItemStack 拷贝（含数量与组件）；输入改变立即取消，即便同一 tick 又放回相同材料。每 tick 重新校验配方、设备、结果和输入；受损组件不能作为本批正常制造输入。依照 uf:2648-2649，脚底以上一格的上半身包围盒接触蛛网也拒绝制造。
- 进度仅由 `TimedCraftingController.tick(ServerPlayer)` 一次推进一刻；生产 `PlayerTickEvent.Post` 调用同一入口。测试玩家没有免计时分支。重复点击不会推进、重置、追加批次或改变现有相同 Session 的模式；需要切换单次/连续时，先右键取消再重新开始。
- 普通结果拾取、数字键、丢弃、双击收集与直接 `quickMoveStack` 无法取得本表的结果。完成时调用真实 `ResultSlot.remove` 和 `onTake`，由当前配方一次消费与处理剩余物/成就；输出进入背包，空间不足则掉落。
- 库存合并绕开创造模式 `Inventory.add` 的溢出丢弃行为，九锭结果按上限 8 分成 8+1；所有模式均保留产出数量。`ResultSlot.onTake` 的返回物入包也通过 `TimedCraftingRemainderMixin` 窄包装保护：仅在当前线程执行计时完成事务且库存属于该事务时使用真实插入，放不下的余量返回 false，交原版后续掉落分支处理。标记由 finally 清理，不修改玩家创造模式能力或其他制造流程。无成本批次允许继续制造，但每一批重新从 0 计时。
- 服务端载荷在容器上方显示制造进度条，并保留 actionbar 百分比与取消提示；客户端仅显示进度，不能提交完成状态。无已协商显示通道时不发送该可视更新，不影响权威制造流程。当前现代单食物值为零时暂停，保留进度；这是对尚未实现的 R196 双资源能量的显式暂代。受伤从进度减 `min(round(damage×10F),32767)×5`，最低零。
- 关闭容器、死亡、退出、重生与跨维度会清理 Session。Session 不持久化，重启后没有半成品或预扣材料。

## 自动化

`TimedCraftingCrafter` 拦截实际 `CrafterBlock.dispenseFrom`。每次红石制造请求建立一批会话，以物理库存、禁用格和配方作快照，随后每游戏 tick 安排一次检查。额外红石脉冲不能让同一 gameTime 重复加进度。周期使用等级 0、默认品质、无食物/经验账户，对没有工作台材料门槛的配方，自动合成器自身视为基础设备（容量 1、速度修正 +0.2）；有材料门槛的配方必须有六个相邻方向之一的真实材料工作台，所需等级与手工相同。取相邻设备最高容量计算速度。

完成后只放行一次原版 `dispenseFrom`，保持原配方产物、剩余物和材料消耗流程。下一批需要新的制造请求。设备移走、不足、输入或禁用格改变则取消，材料保持原位；进度不写 NBT，重启后重新计时。

## 集成与验证状态

`TimedCraftingClickMixin`、`TimedCraftingPickupMixin`、`TimedCraftingMenuMixin`、`TimedCraftingCrafterMixin`、`TimedCraftingRemainderMixin` 已加入共同 Mixin 配置。事件类自行订阅公共事件总线。公共测试入口为 `start(player,menu)`（默认单次）、`start(player,menu,continuous)`、`tick(player)`、`period(player,menu)`、`progress(player)` 和 `cancel(player)`。

已添加独立公式 JUnit 向量及真实菜单 GameTest：12 类铜工具的 N−1/N 边界、重复 Shift、受伤回退、空食暂停、右键取消、同刻输入改回、两批分别计时与材料守恒。2026-10-13 追加背包/工作台单次与连续点击回归，构建、172 项 JUnit、118 组 GameTest 全部通过；用户已确认本批功能及点击方式修正正常，并授权提交 GitHub，详见 [016 验收](testing/016-tools-workbenches-durability.md)。

本批静态核对：当前 ToolRecipes 中全部实际配方 ID 都在表中，162 条难度逐条等于其 source_order 对应的独立 R196 runtime 数据。固定表按配方 ID 绑定；数据包替换同 ID 配方时仍沿用本表难度，后续配置/数据包扩展应显式维护该绑定。

| 配方 ID | 难度 | 需求材料 | R196 runtime order |
|---|---:|---|---:|
| `minecraft:copper_ingot` | 3600.0 | 无 | 463 |
| `minecraft:copper_ingot_from_waxed_copper_block` | 3600.0 | 无 | 463 |
| `minecraft:gold_ingot_from_gold_block` | 3600.0 | 无 | 465 |
| `minecraft:gold_ingot_from_nuggets` | 400.0 | 无 | 56 |
| `minecraft:gold_nugget` | 400.0 | 无 | 839 |
| `minecraft:golden_axe` | 1250.0 | gold | 209 |
| `minecraft:golden_hoe` | 850.0 | gold | 210 |
| `minecraft:golden_pickaxe` | 1250.0 | gold | 27 |
| `minecraft:golden_shovel` | 450.0 | gold | 368 |
| `minecraft:golden_sword` | 825.0 | gold | 376 |
| `minecraft:iron_axe` | 2450.0 | iron | 203 |
| `minecraft:iron_hoe` | 1650.0 | iron | 204 |
| `minecraft:iron_ingot_from_iron_block` | 7200.0 | 无 | 466 |
| `minecraft:iron_ingot_from_nuggets` | 800.0 | 无 | 57 |
| `minecraft:iron_nugget` | 800.0 | 无 | 840 |
| `minecraft:iron_pickaxe` | 2450.0 | iron | 12 |
| `minecraft:iron_shovel` | 850.0 | iron | 365 |
| `minecraft:iron_sword` | 1625.0 | iron | 373 |
| `minecraft:shears` | 1600.0 | iron | 291 |
| `minecraft:wooden_shovel` | 130.0 | 无 | 362 |
| `minecraft:wooden_sword` | 185.0 | 无 | 370 |
| `mite:adamantium_axe` | 76850.0 | adamantium | 207 |
| `mite:adamantium_battle_axe` | 102450.0 | adamantium | 24 |
| `mite:adamantium_crafting_table` | 25805.0 | mithril | 343 |
| `mite:adamantium_dagger` | 25625.0 | adamantium | 411 |
| `mite:adamantium_hatchet` | 25650.0 | adamantium | 294 |
| `mite:adamantium_hoe` | 51250.0 | adamantium | 208 |
| `mite:adamantium_ingot_from_nuggets` | 25600.0 | 无 | 59 |
| `mite:adamantium_mattock` | 102450.0 | adamantium | 23 |
| `mite:adamantium_nuggets_from_ingot` | 25600.0 | 无 | 842 |
| `mite:adamantium_pickaxe` | 76850.0 | adamantium | 22 |
| `mite:adamantium_scythe` | 51275.0 | adamantium | 26 |
| `mite:adamantium_shears` | 51200.0 | adamantium | 295 |
| `mite:adamantium_shovel` | 25650.0 | adamantium | 367 |
| `mite:adamantium_sword` | 51225.0 | adamantium | 375 |
| `mite:adamantium_war_hammer` | 128050.0 | adamantium | 25 |
| `mite:ancient_metal_axe` | 4850.0 | ancient_metal | 211 |
| `mite:ancient_metal_battle_axe` | 6450.0 | ancient_metal | 34 |
| `mite:ancient_metal_crafting_table` | 1805.0 | iron | 335 |
| `mite:ancient_metal_dagger` | 1625.0 | ancient_metal | 415 |
| `mite:ancient_metal_hatchet` | 1650.0 | ancient_metal | 298 |
| `mite:ancient_metal_hoe` | 3250.0 | ancient_metal | 212 |
| `mite:ancient_metal_ingot_from_nuggets` | 1600.0 | 无 | 60 |
| `mite:ancient_metal_mattock` | 6450.0 | ancient_metal | 33 |
| `mite:ancient_metal_nuggets_from_ingot` | 1600.0 | 无 | 843 |
| `mite:ancient_metal_pickaxe` | 4850.0 | ancient_metal | 32 |
| `mite:ancient_metal_scythe` | 3275.0 | ancient_metal | 36 |
| `mite:ancient_metal_shears` | 3200.0 | ancient_metal | 299 |
| `mite:ancient_metal_shovel` | 1650.0 | ancient_metal | 369 |
| `mite:ancient_metal_sword` | 3225.0 | ancient_metal | 377 |
| `mite:ancient_metal_war_hammer` | 8050.0 | ancient_metal | 35 |
| `mite:copper_axe` | 1250.0 | copper | 199 |
| `mite:copper_battle_axe` | 1650.0 | copper | 4 |
| `mite:copper_crafting_table` | 605.0 | flint | 319 |
| `mite:copper_dagger` | 425.0 | copper | 403 |
| `mite:copper_hatchet` | 450.0 | copper | 286 |
| `mite:copper_hoe` | 850.0 | copper | 200 |
| `mite:copper_ingot_from_nuggets` | 400.0 | 无 | 54 |
| `mite:copper_mattock` | 1650.0 | copper | 3 |
| `mite:copper_nuggets_from_ingot` | 400.0 | 无 | 837 |
| `mite:copper_pickaxe` | 1250.0 | copper | 2 |
| `mite:copper_scythe` | 875.0 | copper | 6 |
| `mite:copper_shears` | 800.0 | copper | 287 |
| `mite:copper_shovel` | 450.0 | copper | 363 |
| `mite:copper_sword` | 825.0 | copper | 371 |
| `mite:copper_war_hammer` | 2050.0 | copper | 5 |
| `mite:diamond_from_shards` | 1600.0 | 无 | 62 |
| `mite:diamond_shards` | 1600.0 | 无 | 845 |
| `mite:emerald_from_shards` | 800.0 | 无 | 61 |
| `mite:emerald_shards` | 800.0 | 无 | 844 |
| `mite:flint_axe` | 375.0 | 无 | 214 |
| `mite:flint_chips_from_flint` | 100.0 | 无 | 477 |
| `mite:flint_crafting_table` | 270.0 | 无 | 311 |
| `mite:flint_crafting_table_from_knife` | 270.0 | 无 | 419 |
| `mite:flint_from_chips` | 100.0 | 无 | 305 |
| `mite:flint_hatchet` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_1` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_10` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_11` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_12` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_2` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_3` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_4` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_5` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_6` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_7` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_8` | 175.0 | 无 | 300 |
| `mite:flint_hatchet_binding_9` | 175.0 | 无 | 300 |
| `mite:flint_knife` | 150.0 | 无 | 302 |
| `mite:flint_knife_from_workbench` | 25.0 | 无 | 492 |
| `mite:flint_shovel` | 175.0 | 无 | 213 |
| `mite:glass_pane_from_shards` | 200.0 | 无 | 64 |
| `mite:glass_shards` | 10.0 | 无 | 847 |
| `mite:gold_crafting_table` | 605.0 | flint | 327 |
| `mite:golden_battle_axe` | 1650.0 | gold | 29 |
| `mite:golden_dagger` | 425.0 | gold | 413 |
| `mite:golden_hatchet` | 450.0 | gold | 296 |
| `mite:golden_mattock` | 1650.0 | gold | 28 |
| `mite:golden_scythe` | 875.0 | gold | 31 |
| `mite:golden_shears` | 800.0 | gold | 297 |
| `mite:golden_war_hammer` | 2050.0 | gold | 30 |
| `mite:iron_battle_axe` | 3250.0 | iron | 14 |
| `mite:iron_crafting_table` | 1005.0 | copper | 331 |
| `mite:iron_dagger` | 825.0 | iron | 407 |
| `mite:iron_hatchet` | 850.0 | iron | 290 |
| `mite:iron_mattock` | 3250.0 | iron | 13 |
| `mite:iron_scythe` | 1675.0 | iron | 16 |
| `mite:iron_war_hammer` | 4050.0 | iron | 15 |
| `mite:mithril_axe` | 19250.0 | mithril | 205 |
| `mite:mithril_battle_axe` | 25650.0 | mithril | 19 |
| `mite:mithril_crafting_table` | 6605.0 | ancient_metal | 339 |
| `mite:mithril_dagger` | 6425.0 | mithril | 409 |
| `mite:mithril_hatchet` | 6450.0 | mithril | 292 |
| `mite:mithril_hoe` | 12850.0 | mithril | 206 |
| `mite:mithril_ingot_from_nuggets` | 6400.0 | 无 | 58 |
| `mite:mithril_mattock` | 25650.0 | mithril | 18 |
| `mite:mithril_nuggets_from_ingot` | 6400.0 | 无 | 841 |
| `mite:mithril_pickaxe` | 19250.0 | mithril | 17 |
| `mite:mithril_scythe` | 12875.0 | mithril | 21 |
| `mite:mithril_shears` | 12800.0 | mithril | 293 |
| `mite:mithril_shovel` | 6450.0 | mithril | 366 |
| `mite:mithril_sword` | 12825.0 | mithril | 374 |
| `mite:mithril_war_hammer` | 32050.0 | mithril | 20 |
| `mite:obsidian_axe` | 795.00006 | 无 | 216 |
| `mite:obsidian_crafting_table` | 410.0 | 无 | 347 |
| `mite:obsidian_crafting_table_from_knife` | 410.0 | 无 | 423 |
| `mite:obsidian_from_shards` | 200.0 | 无 | 65 |
| `mite:obsidian_hatchet` | 315.0 | 无 | 301 |
| `mite:obsidian_knife` | 290.0 | 无 | 303 |
| `mite:obsidian_knife_from_workbench` | 25.0 | 无 | 496 |
| `mite:obsidian_shards` | 240.00002 | 无 | 848 |
| `mite:obsidian_shovel` | 315.0 | 无 | 215 |
| `mite:quartz_from_shards` | 900.0 | 无 | 63 |
| `mite:quartz_shards` | 900.0 | 无 | 846 |
| `mite:silver_axe` | 1250.0 | silver | 201 |
| `mite:silver_battle_axe` | 1650.0 | silver | 9 |
| `mite:silver_crafting_table` | 605.0 | flint | 323 |
| `mite:silver_dagger` | 425.0 | silver | 405 |
| `mite:silver_hatchet` | 450.0 | silver | 288 |
| `mite:silver_hoe` | 850.0 | silver | 202 |
| `mite:silver_ingot_from_nuggets` | 400.0 | 无 | 55 |
| `mite:silver_mattock` | 1650.0 | silver | 8 |
| `mite:silver_nuggets_from_ingot` | 400.0 | 无 | 838 |
| `mite:silver_pickaxe` | 1250.0 | silver | 7 |
| `mite:silver_scythe` | 875.0 | silver | 11 |
| `mite:silver_shears` | 800.0 | silver | 289 |
| `mite:silver_shovel` | 450.0 | silver | 364 |
| `mite:silver_sword` | 825.0 | silver | 372 |
| `mite:silver_war_hammer` | 2050.0 | silver | 10 |
| `mite:sinew_from_leather` | 50.0 | 无 | 491 |
| `mite:wooden_cudgel` | 105.0 | 无 | 417 |
| `minecraft:oak_planks` | 120.00001 | wood | 503 |
| `minecraft:spruce_planks` | 120.00001 | wood | 504 |
| `minecraft:birch_planks` | 120.00001 | wood | 505 |
| `minecraft:jungle_planks` | 120.00001 | wood | 506 |
| `minecraft:acacia_planks` | 120.00001 | wood | 503 |
| `minecraft:dark_oak_planks` | 120.00001 | wood | 503 |
| `minecraft:cherry_planks` | 120.00001 | wood | 503 |
| `minecraft:mangrove_planks` | 120.00001 | wood | 503 |
| `minecraft:crimson_planks` | 120.00001 | wood | 503 |
| `minecraft:warped_planks` | 120.00001 | wood | 503 |
| `minecraft:bamboo_planks` | 120.00001 | wood | 503 |

原木加工的 oak/spruce/birch/jungle 分别使用 R196 order 503–506 的 `120.00001F` 整批难度与 wood(0.5)门槛；其余现代木种明确采用同一加工难度。竹块维持新版每次两木板的真实产出，不把周期按产量折半。这些配方仍受背包 2×2 禁止原木加工的既有规则约束。
