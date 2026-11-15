# 2026-10-30 剑与棒攻击范围

开发者：NingMeng123。

2026-11-15 第一人称动作兼容更新。本体在 Punchy 读取外部 Polaris 动作优化 1.4 时，将其动作和配置限定到短剑与钗，避免覆盖普通剑及其他武器，并兼容 Punchy 2.8g 与 3.1b 的配置读取入口。针对外部自定义 `mite_*` 武器攻击，本体使用 0.07 秒实时交叉淡化，避免连续攻击叠加上一击的位移；普通剑与 Polaris 动作保持各自的播放逻辑。本仓库不包含或发布独立武器动作资源包。`mitePunchyClubAudit` 验证外部 Polaris 的作用范围；`mitePunchyClubInputAudit` 仅在可选的完整动作包已启用时执行全武器截图阶段，否则记录跳过原因并完成范围检查。

普通原版剑、MITE 全部材质剑、木棒/木棍棒及 ITE 八种材质棒，平地默认总攻击距离统一为 3 格。此数值是完整攻击距离，不额外叠加原来的武器距离奖励。客户端提示、原版攻击包和 Better Combat 共用这一基准。既有高度调整、全局距离配置、延伸附魔和独立距离属性仍按原规则处理，方块交互距离不变。

Simply Swords 各特殊武器家族保留自身招式与距离，环刃仍为 4.25 格；长矛、长柄刀、巨锤等也不因继承 SwordItem 类而被压成普通剑。

两种木棒和八种 ITE 金属棒都使用 Better Combat 的 `bettercombat:sword` 动作组合。安装Punchy时，它们也在动画分类中使用当前剑组，直接继承正在启用的资源包动作；这不会把棒子加入游戏的剑标签，也不改变附魔、伤害或扫击类型。没有这些动画模组时使用原版挥手。伤害和攻速不变。ITE 棒移除右键格挡动作及其正面减伤；旧存档的防御附魔和免附魔防御配置也不能恢复棒子的格挡，其他符合条件的工具仍可使用防御附魔。

实机反馈目录未安装Better Combat，使用Punchy 2.8g、Polaris和Untitled Punchies；原先只提供Better Combat动作定义不能覆盖该组合。新增可选`PunchyClubAnimationMixin`覆盖Item和ItemStack的剑组识别，保留资源包优先级。隔离客户端用用户的两个动画包验证十种棒的新/存档物品分类与真实组合动作，并通过原生攻击键队列分别触发秘银剑和秘银棒；两者都进入`sword_attack_1`实际播放并捕获变化的第一人称攻击帧。没有调用播放接口模拟鼠标输入，也没有修改用户目录或资源包。

后续实机核对发现用户未安装 Better Combat，而是通过 Punchy 2.8g 与 Polaris/Untitled Punchies 资源包提供剑动作。Punchy 原来只按 `minecraft:swords` 标签分组，棒仍被当作普通物品。现在通过可缺省的 `PunchyClubAnimationMixin`，在 Punchy 自己的 ItemStack/Item 动画分类中将 CLUB/CUDGEL 识别为 SWORD，自动继承当前动画包的剑动作和手臂分组，不修改全局剑标签、附魔、扫击或伤害，也不复制或替换用户动画资源。

`-PmitePunchyClubAudit=true -PmiteClientDirectory=build/verification/punchy-club-client-run runClient` 在全新一次性平坦世界中反射执行真实 Punchy 分类器与 AttackStateMachine 组合动作选择，验证八种金属棒和两种木棒的新/存档物品都与实际秘银剑选择同一活动 attack entry 和加载后的全部 combo clips；专项明确要求 Better Combat 未安装。

生成入口：主工程 `tools/assets/generate_bettercombat_tool_attributes.py`，附属工程 `tools/generate_content.py`。测试入口为 `mite_bettercombat` 的 `ordinarySwordAndClubRangeIsThreeBlocksAcrossRealPackets`（2.9 格命中、3.1 格拒绝）与 `allOrdinarySwordAndClubIdentitiesShareTheThreeBlockBaseline`（全注册表、真实 Better Combat 组合）；附属 `modern_ite` 的 `everyMetalClubCannotStartRightClickGuardEvenWithOldDefenseEnchantment` 覆盖八种棒的默认与旧存档附魔形态。
