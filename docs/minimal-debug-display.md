# R196 F3 显示

开发者：NingMeng123。以本地 MITE R196 正常发布模式为来源；按用户要求保留天数及指定的硬件、内存摘要，并增加游戏内时间和昼夜状态，保留现代版本的管理员权限和测试指令。

## 原版证据

以下行号对应 `.reference/r196/decompiled/` 中的现有文件。

| 行为 | 来源 | 确认结果 |
| --- | --- | --- |
| 普通 F3 开关 | `atv.java:1455-1458` | 发布模式切换调试显示 `u.ab`，同时将分析图 `u.ac` 固定为 false |
| 普通 F3 内容 | `avj.java:346-350` | 左上角 `(2,2)` 仅绘制一个裸 FPS 数字，文字颜色 `0xE0E0E0`，不绘制文本背景 |
| 文字阴影 | `avk.java:97-98`、`avi.java:162-178` | HUD 的字符串绘制入口最终传入 `true`，启用文字阴影 |
| FPS 取值 | `bfe.java:808-813` | 渲染帧计数约每秒更新一次，显示真实帧率 |
| 坐标与方向 | `avj.java:287-296` | 脚部坐标、偏航、方向和俯仰仅在开发模式绘制 |
| 详细信息 | `avj.java:298-345` | 群系、选中对象、实体、区块、怪物数量、内存等均在 `Debug.is_active` 分支 |
| 发布模式边界 | `atv.java:51,59,210-215,1460-1467` | 发布包开发模式为 false；右 Ctrl+F3 进入详细调试的入口也要求开发模式，不是普通玩家例外 |
| 分析图门槛 | `atv.java:706-715,1455-1458` | 图表要求 `u.ab && u.ac`；普通 F3 将后者关闭，Alt/Shift 不改变这条逻辑 |
| HUD 隐藏 | `atv.java:1452-1453`、`avj.java:346` | F1 切换 HUD 模式，FPS 仅完整 HUD 模式可见 |
| 准星 | `avj.java:140-143` | 使用普通准星，不随 F3 显示三维坐标轴 |
| 其他调试快捷键 | `atv.java:1438-1446` | F3+A 重载区块、F3+H 高级提示、F3+B 碰撞箱仍可用 |

旧赛事模式可以改为显示赛事分数和剩余配额时间（`avj.java:279-285,349-350`）；普通会话默认没有配额（`avj.java:903-905`、`atv.java:2102-2109`）。本次不引入旧赛事运行模式。

## 现代适配

F3 开启时，左上角 `(2,2)` 直接绘制 `Minecraft.getFps()` 的十进制数字，下一行 `(2,12)` 显示本地化的“第 x 天”或“Day x”，第三行 `(2,22)` 显示“游戏时间：HH:mm（白天）”或“游戏时间：HH:mm（黑夜）”；英文为“Game time: HH:mm (Daytime)”或“Game time: HH:mm (Night)”。三行均使用颜色 `0xE0E0E0`、文字阴影和透明背景；再次按 F3 关闭。跳过原版左侧完整世界信息采集，不显示坐标、朝向、群系、世界实体与区块等列表。

游戏时间与天数共用每次绘制读取的同一个 `level.getDayTime()`，使用 24 小时制，精确到分钟。原始时间 `0` 对应 `06:00`，`6000` 对应 `12:00`，`12000` 对应 `18:00`，`18000` 对应 `00:00`，`23999` 对应 `05:59`；每个小时为 1000 tick，分钟向下取整。先对 24000 取非负余数再加 6000 的钟面偏移，负值和 long 极值也不会溢出。单人暂停或关闭昼夜推进时，时间停止；恢复推进后继续，`/time set` 会同步改变显示。

时间后附带的“白天 / 黑夜”也读取同一个同步时钟，并复用 `LunarCalendar.daytime` 现有规则：每天原始 tick `13000..23000`（含两端）为黑夜，其余为白天。因此 `19:00` 起显示黑夜，原始 tick `23000` 对应的 `05:00` 仍为黑夜，下一 tick `23001` 起显示白天；分钟显示向下取整，此时仍显示 `05:00`。该状态遵循存档昼夜时钟，跨维度与游戏时间保持一致。

天数从 1 起算，与现有 `R196StructureGeneration.displayDay` 及 R196 `getDayOfWorld()` 的文字显示日期一致。令 `safe = max(dayTime,0)`，使用无溢出等价式 `safe/24000 + (safe%24000+6000)/24000 + 1`；第 1 天覆盖原始时间 `0..17999`，第 2 天从 `18000` 开始。此文字日期与 `LunarCalendar` 判断特殊月相使用的未调整日期不同，不改变现有月相规则，不能因文字已换日而提前触发特殊月相。原始区别另见 `docs/lunar-source-evidence.md`。

只读取客户端当前世界已同步的 `level.getDayTime()`，不查询本地集成服务器或专用服务器对象，也不使用维度天空的固定视觉时间或另加地底世界偏移。原生非主世界维度的 `DerivedLevelData.getDayTime()` 委托主世界数据，服务器按玩家所在维度发送该时钟，客户端时间包更新当前世界；因此主世界、地底世界、下界和末地使用同一存档时钟的显示日期和游戏时间。源码见 `MinecraftServer.java:406-413,1074-1082`、`DerivedLevelData.java:39-40`、`ClientPacketListener.java:990-994`。

右侧只保留本次指定的信息：Java、Mem、Allocation rate、Direct Buffers、Allocated、Off-Heap、CPU、Display 分辨率与厂商，以及 Display 紧邻的显卡 renderer、OpenGL 版本两行。沿用原版右对齐和文字背景，保留原有文本和格式控制符。Direct Buffers、Off-Heap 只有在当前可选模组或原生运行环境真实提供时才显示，不补造 0 或额外默认行。

系统信息及 NeoForge 调试文本事件仍正常执行，以取得上述真实数据；**最终绘制前再次执行确定性筛选**，删除 Sodium Renderer、Pool、TransferQueue、ChunkBuilder、Task、ModernFix 品牌版本和其他未列入的调试行。识别前缀时才移除文本颜色控制符，输出仍使用原字符串；空行仅保留组间单行，不保留首尾空行。筛选生成新列表，不修改原生或事件提供的列表。

世界信息隔离在固定版本的原生方法分支完成：仅将 `DebugScreenOverlay.getSystemInformation()` 内的 `Minecraft.showOnlyReducedInfo()` 结果改为 true，使原版在硬件行之后、目标方块/流体/实体之前直接返回。这个局部变化不改全局简化调试选项，不影响其他系统对它的判断；目标的坐标、注册 ID、properties 和标签均不会进入右侧列表。该方法中的演示世界时间分支也局部跳过，避免将世界倒计时带入摘要区域。事件的左侧列表完全不绘制，右侧列表在事件执行后仍须经过上述筛选。

该固定版本的原生源码证据位于 `net/minecraft/client/gui/components/DebugScreenOverlay.java`：`132-168` 为原始渲染与事件流程，`202-204` 为系统信息收集，`212-232` 为右侧文字布局，`514-536` 为硬件列表，`537-546` 为演示时间和简化信息返回，`547-580` 为被隔离的全部目标信息。Sodium 0.8.13 的 `features.gui.hooks.debug.DebugScreenOverlayMixin` 在硬件列表创建时追加 `Off-Heap: +…MB` 及渲染器统计，仍早于上述返回；Iris 1.8.14-beta.1 的 `MixinDebugScreenOverlay.iris$appendShaderPackText` 在系统信息方法返回时加入 `Direct Buffers: +…`；ModernFix 5.27.26 的 `ModernFixClientForge` 通过调试文本事件右侧列表追加信息。相关收集链路继续执行，但只有最终符合用户指定范围的行才绘制。它们均为可选兼容，不作为模组前置。

禁用分析、帧率和网络图表的显示入口。Minecraft 1.21.1 原生图表快捷键为 F3+1、F3+2、F3+3，这些入口改为切换同一组 FPS、天数、游戏时间与性能信息；Alt/Shift+F3 也不会获得额外图表。F1 继续按原版机制隐藏 HUD，F3 下仍使用普通准星和原版攻击指示。

不修改 `KeyboardHandler` 或快捷键权限处理；区块重载、高级提示、碰撞箱、创造模式及已授权的管理员测试指令继续使用原有流程。此调整针对原版 F3 显示，不关闭 Jade 或其他模组独立提供的界面。

## 用户手动验收

1. 替换模组 JAR 并重启后进入世界，按 F3：左上角应依次出现实时 FPS 数字、“第 x 天”和“游戏时间：HH:mm（白天）”或“游戏时间：HH:mm（黑夜）”，带文字阴影，没有矩形背景；切换英文后第二行为“Day x”，第三行为“Game time: HH:mm (Daytime)”或“Game time: HH:mm (Night)”。观察第三行随游戏昼夜推进，单人暂停时停止，恢复游戏后继续。右侧只显示 Java、Mem、Allocation rate、Direct Buffers、Allocated、Off-Heap、CPU、Display 与其后显卡和 OpenGL 信息；未真实提供的 Direct Buffers、Off-Heap 不显示。再次按 F3 后整组信息消失。
2. 分别尝试 Shift+F3、Alt+F3、F3+1、F3+2、F3+3。均不能打开分析饼图、帧率图或网络图；图表快捷键仅切换这组 FPS、天数、游戏时间与性能信息。
3. 开启 F3 后按 F1，确认 FPS、天数、游戏时间与右侧信息随 HUD 隐藏；再次按 F1 恢复 HUD 后可见。开关 F3 时准星始终是普通准星，不出现三维坐标轴。
4. 检查 F3+A 区块重载、F3+H 高级物品提示、F3+B 碰撞箱，确认这些已有功能仍能正常切换。普通准星及攻击指示不应因 FPS 显示而改变。
5. 面向普通方块、带多种状态的方块、流水、熔岩和实体分别检查 F3，左右两侧均不应出现世界坐标、朝向、群系或目标对象的注册 ID、properties、标签；左侧天数仍正常显示。Jade 独立提示正常保留。
6. 在安装 Sodium、ModernFix 的客户端重复检查，确认真实提供的 Direct Buffers、Off-Heap 可以显示，但没有 Sodium Renderer、Pool、TransferQueue、ChunkBuilder、Task、ModernFix 品牌版本等额外行，也不会恢复左侧完整世界列表。连接服务器、重连和切换维度后，也应使用相同规则。
7. 在允许指令的专用测试存档先执行 `/gamerule doDaylightCycle false`，分别设置 `/time set 0`、`/time set 17999`、`/time set 18000`、`/time set 24000`、`/time set 42000`：应依次显示第 1、1、2、2、3 天。等待时间同步后跨主世界、地底世界、下界和末地检查日期不因维度视觉时间而跳变；测试后恢复原有时间及游戏规则。
8. 执行 `/gamerule doDaylightCycle false`，分别执行 `/time set 0`、`/time set 6000`、`/time set 12000`、`/time set 18000`、`/time set 23999`，游戏时间应依次为 `06:00`、`12:00`、`18:00`、`00:00`、`05:59`，等待现实时间不会改变显示。跨维度检查游戏时间与同一存档时钟保持一致；测试后恢复原有时间及游戏规则。
9. 执行 `/gamerule doDaylightCycle false`，分别设置 `/time set 12999`、`/time set 13000`、`/time set 23000`、`/time set 23001`，第三行应依次显示 `18:59（白天）`、`19:00（黑夜）`、`05:00（黑夜）`、`05:00（白天）`。跨维度和重连后仍按同一时钟显示；测试后恢复原有时间及游戏规则。

## 验证状态

2026-10-28：新增左上角游戏时间 `HH:mm`，时间后显示“白天 / 黑夜”。`build`（含单元测试）通过；`runData -PmiteMinimalDebugAudit=true -PmiteMinimalDebugAuditNoMods=true` 无窗口审计通过，确认天数、时间和昼夜共用同步的 `dayTime`、新行位于 `(2,22)`、保留 F1/F3 显隐逻辑，并实际执行 14 组时钟和 14 组昼夜边界用例。游戏内实际画面仍待用户手动验收。

2026-10-16：构建成功，1,455 项单元测试、1,605 项服务端 GameTest 全部通过。带 Sodium、Reese、Sodium Extra、Iris、ModernFix 和真实无第三方模组两种环境的客户端无窗口验证均通过。检查实际变换后的渲染入口、右侧筛选、原生硬件采集、目标信息隔离、F1/F3 与准星；实际执行图表开关、10 组日期边界及 10 组摘要筛选用例。没有构造 Minecraft、窗口或世界，没有执行渲染或自动操作游戏；客户端画面与操作仍待用户手动验收。
