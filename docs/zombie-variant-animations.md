# 类僵尸生物动画适配

开发与验证：NingMeng123，2026-10-26。

按用户要求，黏土魔像、隐形潜行者、暗影、亡魂、地元素借用 Fresh Animations 的僵尸动画。同用 `LegacyStalkerModel` 的尸鬼和火元素也由同一入口驱动。这里借用动作，不替换这些物种的几何、64×32 皮肤、透明度、眼部或岩浆发光图层；没有改变战斗属性、AI 或存档。

渲染器保留旧模型，另外烘焙标准 `ModelLayers.ZOMBIE` 作为隐藏动画驱动。只有客户端安装 EMF 且该模型确实有 CEM 动画时才启用，未安装或未启用相关资源包时不进行逐帧动画采样。驱动在可见模型构造后延迟烘焙，避免 EMF 将标准僵尸根错误绑定到旧模型。

采样使用 EMF 官方动画 API；头、躯干、手臂及双腿根据真实立方体、累计父节点矩阵及中心偏移映射。FA 的原生头节点没有可见头部，实际头藏在 `headwear/head2`，因此不能只复制 `HumanoidModel.head` 的角度。映射保留这条节点链，仍使用原模型绘制身体、护甲和发光层。连续绘制不同实体时先恢复原模型姿态，避免前一个实体的位移或缩放污染后一个。

Better Combat 的原有注入在 `setupAnim` 后应用真实攻击关键帧，仍控制最终持械攻击；攻击结束后恢复资源包动作。

手动复核时在测试存档生成上述五种生物，分别观察原地待机、行走、追击和受击。隐形潜行者继续半透明，地元素不同岩石材质、黏土魔像皮肤以及岩浆发光保持。关闭 Fresh Animations 后应恢复旧动作。

隔离客户端审核：

```powershell
.\gradlew.bat runClient '-PmiteZombieVariantAudit=true' '-PmiteClientDirectory=build/verification/zombie-variant-run' --console=plain
```

审核目录单独放入 EMF / ETF 与启用的 Fresh Animations 包。使用一次性测试世界，不读取用户存档。自动审核结果另行记录，不能代替用户原存档、光影及联机实玩。

## 本轮验证

FA 1.10.4、EMF 3.3.12 / ETF 7.2.6 与 Better Combat 2.4.0 / Player Animator 2.0.4 的隔离客户端通过六行实体、24 个实际渲染样本。五种变种在待机、行走和攻击后恢复三阶段，共 90 次骨骼检查；每次以原模型立方体八个角点与真实可见 FA 几何的累计矩阵比较，包含 `headwear/head2` 头部。持械攻击阶段由真实 Better Combat 动画接管，结束后释放。

检查并修复了 EMF 隐藏原版方块抢先参与映射的问题，并以直接矩阵分解避免欧拉角转换造成摆臂偏差。没有放宽几何比较阈值。原 64×32 图集、四像素肢体及 UV 保持，隐形潜行者实际绘制顶点 alpha 为 13（约 5%），暗影为 255；地元素和黏土魔像的原发光层实际执行绘制。已查看 GPU 姿态截图。

无 EMF / ETF / Better Combat / Player Animator 的独立客户端也通过原动作回退检查，无可选类链接错误。报告为 `build/verification/zombie-variant-client-audit.json` 与 `zombie-variant-native-client-audit.json`，均通过、`failures` 为空。最终 1763 项 JUnit 和发布构建通过；游戏内原存档、光影与联网验收仍由用户实玩确认。
