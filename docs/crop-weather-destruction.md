# 作物降雪与流水破坏

开发署名：NingMeng123。该联动使用 R196 的破坏原因语义；来源见 `js.placeSnowfallAt`、`anr.dropBlockAsEntityItem`、`apt.dropBlockAsEntityItem` 与 `aqq.dropBlockAsEntityItem`。

`CropSnowfallMixin` 只扩展原版 `ServerLevel.tickPrecipitation` 中的落雪判断与实际雪层替换。降雨、`snowAccumulationHeight`、高度图选取、寒冷降雪气候、世界高度和方块光照小于 10 的约束均保留。普通冷天气不会自动杀作物，覆盖屋顶使雪先落到屋顶。该路径允许已纳入的作物和瓜藤被雪替换，并在原生放置雪层前以 `SNOW` 原因调用正常掉落流程。

R196 耕地虽外形为 15/16 高，但碰撞使用完整方块；现代耕地的碰撞也缩短了，原版雪层因此不能立在耕地上。数据提供器将原版耕地追加到 `minecraft:snow_layer_can_survive_on`，使原作落雪逻辑在现代碰撞机制下成立，而不修改地形、温度或全局雪线。

普通作物在雪中恢复年龄 0 的种植物或健康成熟产物，病害、死亡及中间阶段按原种类规则处理。茎的特殊规则严格先处理“下雪且年龄 0”，因此死亡的年龄 0 茎也返还 1 颗种子；普通玩家打掉死亡茎仍不返还。

`CropFloodMixin` 在原生 `WaterFluid.beforeDestroyingBlock` 的正常掉落调用处设置 `FLOOD` 作用域，随后完整恢复上一个作用域。实际水流依然由原版流体决定能否冲入方块。普通作物被流水冲毁不返种；活的中期或病害马铃薯保留原作 1 个毒薯例外；瓜藤不返种。

作用域仅匹配同一服务端世界、同一方块位置，不改变相邻方块或后续破坏原因。原生掉落事件和其他模组的取消处理仍参与最终流程。

`CropWeatherDestructionGameTests` 直接执行原生降水与水流刻，包含真实落雪收获、死亡幼茎返种、天气/游戏规则/遮挡保护、水流冲毁幼苗、毒薯例外与作用域恢复。构建与无窗口服务器测试由主任务统一运行；画面及手动验收另行记录。
