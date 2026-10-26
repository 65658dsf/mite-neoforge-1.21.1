# 蜘蛛资源包兼容

开发与验证：NingMeng123，2026-10-26。

## 修复范围

`mite:demon_spider`、`mite:wood_spider`、`mite:black_widow_spider`、`mite:phase_spider` 现在会在客户端安装 EMF 并加载蜘蛛 CEM 时，使用标准 `minecraft:spider` 模型层。此前固定使用 `mite:arachnid`，无法命中 Fresh Animations 的 `spider.jem`。每个变种单独烘焙模型，步态和自定义部件由 EMF 正常执行。

用户提供的 Fresh Animations 1.10.4 使用 64×32 蜘蛛图集，FA+Spiders 2.2 则改用 64×64 图集和双节腿、口器、触肢。旧 MITE 皮肤和眼睛都是 64×32，直接替换模型层会造成 UV 错位。因此加载时读取当前蜘蛛资源包贴图，按 MITE 自有皮肤的体色和眼色生成对应尺寸的动态贴图。保留资源包的纹理明暗、透明区域与眼睛位置；原皮肤文件不变，不将第三方模型或贴图打进发布 JAR。

FA+Spiders 的两张传统 `spider_eyes.png` 都为空，实际发光来自 `spider_e.png`。适配优先读取该遮罩，再按原变种的眼色生成独立发光图层；相位蜘蛛为绿眼，其余三种为红眼。魔蛛的体型、另外三种的 0.6 倍缩放继续由原渲染器处理。

没有 EMF、没有命中的蜘蛛 CEM，或图集无法适配时，继续使用原 `LegacyArachnidModel`、原皮肤和眼睛。带独立部件纹理覆盖的其他 CEM 目前走此回退，避免错误套图。本次没有修改任何蜘蛛属性、碰撞箱、AI、掉落或存档。

## 游戏内复核

更新主模组后重启游戏。保持 Fresh Animations 在下、FA+Spiders 在上的资源包顺序，保留已安装的 EMF / ETF。无需修改资源包 ZIP 或极难版模组。

在测试存档依次生成普通蜘蛛、洞穴蜘蛛、魔蛛、树蜘蛛、黑寡妇和相位蜘蛛，观察待机、行走、跳跃和暗处眼睛。四种 MITE 蜘蛛应与资源包使用相同的双节腿和口器模型，分别保持暗红、棕色、黑灰、青蓝体色；相位蜘蛛应显示绿眼。关闭资源包后应恢复旧蜘蛛外观。

隔离客户端审核命令：

```powershell
.\gradlew.bat runClient '-PmiteSpiderResourcePackAudit=true' '-PmiteClientDirectory=build/verification/spider-pack-run' --console=plain
```

审核目录需单独放入 EMF 3.3.12、ETF 7.2.6 与上述两个资源包并启用它们。审核在一次性超平坦世界中运行，不读取用户存档，生成 JSON 和实际渲染截图后退出。无可选模组的审核使用另一个空目录。构建与自动审核结果另行记录；自动样本不代替用户存档、光影和联机实玩。

## 本轮验证

0.2.13 的 `build compileGametestJava` 和 1763 项 JUnit 通过，其中新增 5 项像素适配测试。使用用户原始 FA+Spiders 2.2、基础 FA 1.10.4、EMF 3.3.12 / ETF 7.2.6，六种蜘蛛各绘制待机、行走、腾空，共 18 个姿态；四种自定义模型根互相独立，身体图集均为 64×64 且颜色各异，眼睛图集各保留 20 个有效像素。实际 GPU 正面截图已查看，腿部、口器、体色和红／绿眼睛布局正常。

另一个只启用基础 FA 的实例通过相同渲染检查，动态图集为 64×32；完全无 EMF / ETF 的实例通过旧模型回退检查。三份报告分别为 `build/verification/spider-resource-pack-client-audit.json`、`spider-base-fa-client-audit.json`、`spider-native-client-audit.json`，状态均通过、`failures` 为空。

发布包 `build/libs/mite-0.2.13.jar` 不含第三方类、JEM / JPM、审核器或资源包内容。未操作用户正在运行的游戏，未记录用户原存档或光影组合的手动验收。
