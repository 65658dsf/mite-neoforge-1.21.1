# 骑乘甩竿来源修正

开发署名：NingMeng123。

R196 `uf.java:2608–2621` 的 `canCastFishingRod` 先拒绝已有鱼钩，再直接允许 `sq` 船和 `rs` 马；其余情况需要着地、没有悬浮液体、头部方块不是液体。[已跟踪类索引](../reference-data/class-index.json) 明确 `sq=EntityBoat`、`rs=EntityHorse`。027阶段将后者误读为矿车，本次纠正代码和来源说明。

`RangedFishingRodMixin` 使用 `Boat` 和 `AbstractHorse` 作为骑乘例外，保留现代船/箱船及马、驴、骡、骷髅马、僵尸马家族的实际骑乘状态。没有发现对旧矿车例外的独立现代设计授权，因此移除该误映射；矿车现在通过普通着地和液体条件决定能否甩竿，不能据此推断所有矿车状态绝对禁止钓鱼。船和马的例外与来源一样位于液体/着地检查之前。收竿、九种钩材质、左右手、鱼饵、咬钩和耐久规则不变。

`FoodFishingBaitGameTests.mountedBoatAndHorseCastWithoutFootingButMinecartDoesNotReceiveTheHorseException` 创建实际船、马及矿车，真正骑乘并调用原生钓竿使用，验证在明确离地状态下前两者产生鱼钩、矿车无此例外。既有离地、水下头部、左右手和九种钩材质测试继续保留。上述验证均已执行通过，结果见[本轮统一验证](testing/r196-parity-implementation.md)；尚未替用户操作客户端。

手测可骑驯服的马和船、持任一种来源钓竿尝试甩出/收回，随后在同样离地条件下用矿车对照。单纯看到骑乘姿势或持有钓竿不算验收，需确认真实鱼钩产生。
