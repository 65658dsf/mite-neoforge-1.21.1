# MITE: Modern 0.3.6

November 17, 2026. Developed by NingMeng123.

## Changes

- Restored mouse-look slowdown while caught in cobwebs or affected by Slowness. Speed and Free Action resistance participate in the calculation.
- Restored ladder and vine horizontal grip and status-dependent climbing speed.
- Corrected cactus contact damage for slow-moving mobs, including standing on cactus, crowding and block-collision conditions.
- Weighted pressure plates now count the number of dropped items within stacks. Gold plates reach full signal at 64 items and iron plates at 640 items.
- Corrected ordinary-fire contact timing and the ignition duration requested by fire and lava. These durations continue through the fire-protection and burning-duration modifiers.
- Removed the extension food-capacity limit of 20 points. Hunger icons, AppleSkin nutrition overlays and food previews now wrap across rows and reserve the corresponding HUD height.
- Added extension hooks for combined armor movement-speed bonuses and durability-loss prevention, allowing ITE socket effects to participate in the same calculations as enchantments.
- Restored native aquatic spawning schedules and separate category allowances for fish, water creatures, underground water creatures and axolotls. Submerged hostiles use their native water-depth and light checks.
- Restored modern species to their native biome and structure spawning pools, including husks, strays, bogged, piglins, hoglins, outpost pillagers and monument guardians. Donkeys also use their native natural and initial chunk-population entries.
- Restricted natural spawning to entity-ticking positions inside the world border. Inactive, nonpersistent natural monsters outside entity-ticking range release their active population allowance.
- Applied structure spawning overrides at their actual piece or whole-structure boundaries, covering trial chambers, ancient cities and the Nether fortress brick-floor rule. Spawn-weight settings also discover structure-defined monster entries.
- Added recovery for stronghold candidates whose chunks completed structure generation before the stronghold unlock. Replacement candidates use unexplored chunks, persist across restarts and feed the shared placement, locating and Eye of Ender position list.
- Fixed active miner digging stalling while later route queries wait for asynchronous pathfinding or the main-thread budget. Valid digging continues through its cooldown and work stages, with block eligibility and protection checks.
- Improved bat carriers to remain airborne and steer skeleton and creeper riders toward their prey's feet. Natural spawning commits the carrier and rider together and rolls back the group when finalization or entity admission fails.
- Removed automatic crafters from recipes and creative inventory and blocked their placement. Saved crafter blocks are removed on loading or use, releasing their stored ingredients.
- Adjusted dead bushes to have a 40% chance of dropping one stick. Shears collect the bush itself.
- Added game-test coverage for phantom insomnia, rest, sky access, altitude and actual natural entry.

Build file `MITE-Modern-0.3.6.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the mod on both client and server for multiplayer.

## 中文更新日志

- 恢复陷入蜘蛛网或受到缓慢效果时的视角转动减速，并计入迅捷与自由行动抗性。
- 恢复梯子和藤蔓的水平抓附效果，根据状态效果调整攀爬速度。
- 校正低速移动生物受到仙人掌接触伤害的条件，覆盖站在仙人掌上、受到挤压及与方块碰撞的情况。
- 测重压力板按掉落物堆中的物品数量计算信号，金质压力板在 64 件物品时达到满信号，铁质压力板为 640 件。
- 校正普通火焰的接触时序，以及火焰和熔岩请求的着火时长，并接入火焰保护与燃烧时长修正。
- 解除扩展饱食容量被限制为 20 点的问题，饥饿图标、AppleSkin 营养覆盖层及食物恢复预览支持多行排列，并预留对应的 HUD 高度。
- 新增护甲移速加成与耐久损耗免除的扩展接口，使 ITE 镶嵌效果和附魔参与同一套计算。
- 恢复鱼类、水生动物、地下水生动物及美西螈的原生自然刷新调度和独立类别额度，水中的敌对生物按原生水深与光照条件判断。
- 恢复现代生物的原生群系与结构生成候选，覆盖尸壳、流浪者、沼骸、猪灵、疣猪兽、前哨站掠夺者及海底遗迹守卫者，并恢复驴的自然生成与区块初始生成入口。
- 自然生成位置须位于实体活动范围及世界边界内，活动范围外暂停更新、无需持久保留的自然怪物释放对应的活动人口额度。
- 按实际组件或完整结构边界执行结构刷怪覆盖，覆盖试炼密室、远古城市及下界要塞的地狱砖地面规则，刷新权重设置也会读取结构定义的怪物候选。
- 新增要塞候选恢复。候选区块在要塞解锁前已经完成结构生成时，可在未探索区块中补充候选位置，恢复记录随存档保存，结构放置、定位与末影之眼共用更新后的位置列表。
- 修复矿工挖掘因后续路线查询等待后台寻路或主线程预算而停滞的问题，已启动的合法挖掘持续推进冷却和阶段，并检查方块资格与破坏保护。
- 完善蝙蝠坐骑飞行，载客蝙蝠持续飞行，搭载骷髅或苦力怕时朝猎物脚部接近。自然生成将坐骑和骑手整组提交，最终化或实体入场失败时整组回滚。
- 移除自动合成器的配方与创造物品栏入口，并阻止放置。旧存档中的自动合成器在加载或使用时清除，释放内部保存的材料。
- 枯灌木调整为 40% 概率掉落 1 根木棍，剪刀采集获得枯灌木本身。
- 补充幻翼失眠、休息、露天、海拔及实际自然入场的游戏内回归验证。

构建文件为 `MITE-Modern-0.3.6.jar`。

运行环境为 Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。多人游戏的客户端与服务端均须安装本模组。

开发者 NingMeng123。
