# Fresh Animations 战斗兼容验证

开发与验证：NingMeng123，2026-10-19。

## 环境与实现

Minecraft 1.21.1、NeoForge 21.1.259、MITE: Modern ITE 0.2.1、Better Combat 2.4.0、Player Animator 2.0.4、Cloth Config 15.0.140、EMF 3.3.12、ETF 7.2.6。资源包按 FreshAnimations_v1.10.4.zip、FA+Player-v1.1.zip 的顺序启用。完整 FA 渲染审核使用版本标记 0.2.3 的开发构建，随后将同一兼容实现版本标记更新为 0.2.4，完成无 EMF、无可选模组启动与交付构建。

`FreshAnimationsCombatHook` 使用 EMF API 11 的 `onAnimationStart` / `onAnimationEnd`。EMF 在模型渲染时执行资源包公式，晚于原生 `setupAnim` 与 MITE 的怪物关键帧应用。回调保存并恢复受控原生骨骼的位置、旋转和三轴缩放，保留资源包变量、面部、皮肤显示开关及自定义几何。灾厄村民的双臂展开状态单独跟随战斗姿态。

玩家通过 Player Animator 的真实 Better Combat 2000 优先级攻击层 `ModifierLayer.isActive()` 判断，包括取消淡出。Player Animator 2.0.4 未提供读取层的公共接口，因此缓存一次对 `AnimationStack.layers` 的反射；无法读取时仅禁用玩家部分的兼容并记录一次警告。第三方类隔离在可选依赖分支，专用服务端不加载客户端回调。

## 客户端审核

在独立目录放入上面的第三方模组和两个资源包，启用资源包。不要把已有存档用作审核目录。审核自动新建一次性超平坦世界，按页渲染并截图，保存 JSON 后退出：

```powershell
./gradlew.bat runClient '-PmiteFreshAnimationsCombatAudit=true' '-PmiteClientDirectory=build/verification/fresh-animations-client' --console=plain
```

可通过 `-PmiteFreshAnimationsCombatAuditOutput=...` 指定 JSON 输出。审核代码仅位于 gametest 源集，发布 JAR 不包含它。检查范围：

- 本地、远端玩家的真实 Better Combat 动画；取消攻击后的 6 刻淡出及自然动画结束释放兼容状态。
- 僵尸、普通骷髅、凋零骷髅、猪灵、卫道士，以及安装的极难版全部注册人形怪；每种绘制待机、前摇、命中、恢复四阶段。
- EMF 真实动画回调前后与最终渲染姿态、实际护甲模型、衣物跟随、连续不同实体共享模型隔离、攻击后资源包动画继续执行。
- 牛的资源包动画不受战斗兼容影响。测试实体放在客户端玩家位置，避免 EMF 距离优化影响审核。

普通骷髅及部分极难版自定义模型没有这两个 ZIP 提供的 FA 文件，验证其战斗动画和模型隔离，不将“缺少资源包模型”误报为兼容覆盖失败。标准凋零骷髅单独验证真实 FA 骷髅模型。

## 验证记录

本轮 1658 项 JUnit 通过。客户端使用用户提供的原始 JAR / ZIP 和极难版 0.2.1，通过 42 行实体、168 个姿态样本，包含全部 34 种注册极难版人形怪；本地、远端玩家的取消淡出和自然结束检查也通过。报告状态为 `passed_real_fa_emf_combat_rendering`，`failures` 为空，保存 6 页实际渲染截图。

安装 Better Combat / Player Animator、未安装 EMF / ETF 的独立客户端同样通过原有 `BetterCombatMobClientAudit`，状态为 `passed_real_keyframe_models_armor_and_framebuffer`；既有原生模型、护甲跟随和渲染后恢复检查通过。

没有 Better Combat、Player Animator、EMF、ETF 的独立客户端通过启动和自动退出审核，状态为 `passed_optional_mods_absent`，没有可选类链接错误。最终 `build compileGametestJava` 通过，交付包为 `build/libs/mite-0.2.4.jar`；JAR 不含第三方库、资源包内容和 gametest 审核器。

客户端专项报告、截图、编译与启动日志保存在被 Git 忽略的 `build/verification/fresh-animations-*` 与 `freshanimations-combat-audit-page-*.png`。最终运行结果以 JSON 的 `status` 和 `failures` 为准，Gradle 正常退出本身不代表审核通过。

手动复核时在普通第三人称与第一人称各使用单手和双手武器，观察攻击与取消淡出；让极难版持械人形怪追击，检查头部外形、护甲和武器对齐，再解除武器观察原动作。本轮自动审核不代替多人联网实玩与光影组合观感检查。
