# R196 信标金属基座

开发署名：NingMeng123。

铜块、银块、秘银块和艾德曼块现在通过增量 `minecraft:beacon_base_blocks` 标签加入原生信标基座。各层可以混用这些材料与原生合法材料；缺块、层级、光柱、付款和状态效果仍使用现代原生信标逻辑。

旧依据为 `.reference/r196/decompiled/arw.java:56–70`，其中第 61 行明确允许这四种金属。`aqz.java:2428–2431` 核对了方块身份。远古金属不在该白名单，本项没有扩大到远古金属块、氧化/切制铜块等现代铜衍生方块。

生产来源为既有 `MiteBlockTagsProvider`，生成文件为 `src/generated/resources/data/minecraft/tags/block/beacon_base_blocks.json`，没有 `replace:true`。因此现代铁、金、绿宝石、钻石、下界合金基座继续生效。

`SourceBeaconBaseGameTests` 的两项 GameTest 将真实方块放进世界，再调用原生 `BeaconBlockEntity.updateBase` 验证四种来源金属各一至四层、混合材料、缺块截断、远古金属拒绝以及现代合法材料仍属于加载后的标签。它们验证真实原生多方块识别，不代表已完成光柱、菜单或客户端画面实测。

数据生成、构建和两项真实信标 GameTest 均已通过，结果见[本轮统一验证](testing/r196-parity-implementation.md)。客户端光柱、菜单及画面仍待用户手测。
