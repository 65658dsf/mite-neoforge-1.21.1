# MITE: Modern 0.3.2

November 7, 2026. Developed by NingMeng123.

## Changes

- Restored R196-style cooldowns after successful placement. Ordinary blocks use a 250 ms client input cooldown. Emptying buckets uses 500 ms, including powder snow buckets. The first placement takes effect immediately.
- Unified single-click and held-use timing. Hotbar changes and hand switching share the same cooldown deadline, measured in elapsed time.
- Applied placement cooldowns to Survival and Creative clients. Opening containers, using crafting tables and eating follow the active placement cooldown.
- Updated the server to process valid placement requests immediately while applying placement checks, accommodating variations in network packet timing.
- Updated the default build filename to `MITE-Modern-0.3.2.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series.

## 中文更新日志

- 按 R196 机制调整成功放置后的冷却。普通方块采用 250 毫秒客户端输入冷却，倒出桶采用 500 毫秒，粉雪桶同样按桶计时。首次放置立即生效。
- 点按、长按、热栏换槽及主副手切换共用同一冷却截止时间，按实际经过时间计算。
- 生存与创造模式客户端均执行放置冷却。放置后的冷却也约束开箱、使用工作台和进食等右键操作。
- 服务端即时处理有效放置请求并执行放置校验，适配网络收包间隔的波动。
- 默认构建文件名更新为 `MITE-Modern-0.3.2.jar`。

运行环境为 Minecraft 1.21.1、Java 21、NeoForge 21.1.259 或更新的 21.1 系列版本。

开发者 NingMeng123。
