# MITE: Modern 0.3.7

November 18, 2026. Developed by NingMeng123.

## Changes

- Fixed deaths at negative experience levels to clear experience-bar progress and add one level of experience debt, bounded at level -40.
- Fixed cross-thread random-source access when finalizing spiders during chunk generation. World-generation riders enter and save with the spider's passenger tree.
- Added a server-synchronized monster melee attack-damage line to Jade tooltips, including the contact-damage calculation for slimes and magma cubes.
- Added randomized startup-loading backgrounds with the current mod version and developer credit.
- Improved height transitions between underworld terraces, including connections across chunk boundaries and existing passages.
- Restored R196 destination-specific portal colors and animated textures, with matching particles and entry overlays. Portal block states identify the destination for the corresponding visuals. Chunk loading and runestone frame changes refresh stored portal routes and visuals.
- Fixed on-foot sprinting progress for structure exploration and added personal chat notifications for exploration-based structure unlocks.
- Enabled bare-hand destruction of ice and snow blocks. Drop eligibility follows the harvesting-tool rules.

Build file `MITE-Modern-0.3.7.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the mod on both client and server for multiplayer.

## 中文更新日志

- 修复负经验等级再次死亡时保留经验条进度的问题，死亡会清空当前进度并增加一级经验负债，最低为 -40 级。
- 修复区块生成过程中蜘蛛最终化回调跨线程访问随机源的问题，世界生成的骑手随蜘蛛乘客树入场和保存。
- 为 Jade 怪物提示新增由服务端同步的近战攻击力显示，覆盖史莱姆与岩浆怪的接触伤害计算。
- 启动加载界面新增随机背景，并显示当前模组版本与开发者署名。
- 优化地底台地之间的高度过渡，完善跨区块边界及既有通路的衔接。
- 恢复 R196 按目的地分色的传送门动画材质，并匹配粒子和进入屏幕效果。传送门方块状态记录目的地以选择对应显示效果，区块加载与框架符文石变动时刷新路由和外观。
- 修复徒步疾跑的结构探索进度统计，为通过探索解锁的结构补充个人聊天通知。
- 冰雪方块支持空手破坏，掉落物按采集工具条件结算。

构建文件为 `MITE-Modern-0.3.7.jar`。

运行环境为 Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。多人游戏的客户端与服务端均须安装本模组。

开发者 NingMeng123。
