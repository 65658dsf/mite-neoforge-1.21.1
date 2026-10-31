# 坐船遮罩、镰刀耐久与鸡肉掉落

开发者：NingMeng123

日期：2026-10-31

## 行为

- 坐船：用户确认启用 FA+Objects。本地 v2.1.2 的 `boat.jem`、`chest_boat.jem` 通过 `bottom_no_water` 替换 EMF 的 `water_patch`，引入带动画和位移的较厚深度遮罩。该遮罩只写深度，可能把座位以上的乘客腿部裁掉。有玩家乘客且遮罩存在自定义子模型时，现在改用直接烘焙的原版遮罩；船体、桨和箱子继续使用资源包动画。原版遮罩、无玩家乘客和竹筏沿用原有路径。未修改资源包 ZIP 或玩家模型。
- 镰刀：用户确认异常发生于打怪。旧农镰规则每次成功命中扣 400 点，铜镰最大耐久 3200，仅能命中 8 次；锋利、吸血原先分别将损耗降为 200、100。现统一改为剑的 50 点，适用于本体镰刀与简易刀剑战镰；攻击附魔效果、材质最大耐久、品质和耐久附魔机制保留。无减耗时普通铜／铁镰分别可命中 64／128 次。草与小麦的耐久回调仍每次扣 2 点。
- 鸡肉：成年鸡固定掉 1 块鸡肉，保留死亡时的生熟肉判断、羽毛库存、幼体和 `doMobLoot` 限制。其他动物的肉量保持原规则。

## 验证

| 检查 | 结果 | 记录 |
|---|---|---|
| 全部 JUnit | 2,043 项、107 套件通过，0 失败、0 错误 | `build/verification/boat-scythe-chicken-compile-test.log`、`build/verification/boat-scythe-chicken-build-compat.log` |
| 本体服务端回归 | 50 项通过 | `build/verification/boat-scythe-chicken-gametest.log` |
| 简易刀剑 1.70.2 + Better Combat 2.4.0 + Player Animator 2.0.4 | 同一 50 项通过，实际执行战镰命中测试 | `build/verification/boat-scythe-chicken-compat.log` |
| 客户端遮罩检查（EMF 3.3.12、ETF 7.2.6） | 渲染器实际变换、3 个朝向的原版遮罩顶点与回退顶点逐点一致；读取两份真实 FA 船模型结构 | `build/verification/boat-water-mask-audit.json`、`build/verification/boat-water-mask-data.log` |
| 构建与 JAR | 构建通过；全部 1,612 个生产类与编译输出一致，无 GameTest 类混入 | `build/verification/boat-scythe-chicken-build-compat.log` |

镰刀回归执行真实 `Player.attack` 并验证目标确实受伤、每次仅扣 50 点；也覆盖附魔、可触及的无敌目标及创造模式。草／小麦测试直接调用 `mineBlock` 验证耐久回调，不等同于完整玩家破坏作物流程。鸡肉检查通过真实死亡事务验证不同随机种子、玩家／环境死亡、火焰附加、箭与生熟肉路径。

坐船检查使用真实 `ModelPart.render` 的 CPU 顶点和 Mixin 导出字节码，但自定义几何夹具为合成层级；没有启动图形客户端加载 FA 资源包或验收实际画面。重启后应保留 FA+Objects，分别检查普通船与箱船、第一／第二座位及划船时的第三人称腿部显示。

首次兼容环境启动缺少 Fzzy Config 所需 Kotlin for Forge，未运行测试；补齐本地依赖后复测 50 项通过。通过日志仍包含上游可选联动配方缺失和 Yggdrasil 公钥联网超时，不影响该批测试，不记录为零错误日志。

## 产物

版本保持进入本轮时已有的 `0.2.19`，没有使用版本覆盖参数。

- `build/libs/mite-0.2.19.jar`
- 大小：10,093,985 字节
- SHA-256：`c0a128c6d605f62b29b3215e63c1bc1593ad33fa6f8b6f86f232d9bae1dabd0b`
- 包内作者：NingMeng123
