# 2026-10-29 环刃伤害与距离

开发者：NingMeng123。

全部环刃家族的总基础攻击伤害降为此次调整前的 80%，家族伤害系数由 `0.8` 改为 `0.64`。现有材质、配置偏移、独特武器独立基数及金属伤害系数继续参与计算，品质、升级和独立词条仍按既有规则随后结算。铁环刃原总基础伤害为 `9 × 0.8 × 0.8 = 5.76`，现在为 `9 × 0.64 × 0.8 = 4.608`，对应基础攻击修饰为 `3.608`。旧存档保存的基础修饰也在属性读取时归一化，不重复乘算。

基础攻击距离从 5.5 统一为 4.25。Better Combat 使用绝对 `attack_range: 4.25` 与 `range_bonus: 0`；原有连击、动作、伤害倍率、音效不变。实际注册覆盖七种新增材质、原生铁/金/钻石/下界合金/符文五种材质，以及上游归类为 chakram 的 chompolotl、mjolnir、tempest 三种特殊武器，共 15 项。禁用武器仍有注册定义，也纳入数值审计；既有禁用政策不变。原有全局距离设置及显式距离属性组件仍按既有优先级生效。

上游 `magic_estoc` 虽有 JSON 和翻译，但 Simply Swords 1.70.2 的 `ItemsRegistry` 没有对应物品注册，因此不能把它算作第四种实际特殊武器。仍生成这份遗留 profile 的覆盖，以及 Gobber 和 Mythic Metals 的 21 份可选兼容配置；30 份上游覆盖是资源数量，与当前实际注册的 15 个含新增材质的环刃定义不同。

`tools/assets/generate_simplyswords_compat.py` 从上游包自动识别并生成 30 份原生/特殊/可选兼容覆盖与七份新增材质配置，来源清单记录在 `src/main/resources/mite/simplyswords-compat-assets.json`。

原生武器的同名资源在 classpath 中可能先找到 Simply Swords 自带的旧配置。`SimplySwordsReach` 现在优先从 MITE 自身模组文件读取覆盖，再回退到上游资源，因此不依赖可选 Better Combat 的内置距离读取也使用同一套数值；显式物品距离组件仍保留原有优先级。

验证入口：`SimplySwordsBalanceRulesTest` 检查总伤害，`mite_simplyswords` 检查真实注册及旧存档属性，`mite_bettercombat` 检查全部实际注册的环刃、提示距离、Better Combat 实际解析结果及 4.15/4.35 距离的真实攻击数据包边界。`tools/testing/verify_simplyswords_resources.py <Simply Swords JAR>` 逐份对比原招式内容，只允许两个距离字段变化。

最终验证：主工程构建成功，2036项JUnit全部通过。安装Simply Swords 1.70.2与Better Combat 2.4.0的95项GameTest全部通过；保留Simply Swords并移除Better Combat的95项也全部通过。日志分别为 `build/verification/chakram-hands-final-build.log`、`chakram-verified-with-bc.log`、`chakram-verified-without-bc.log`。资源审计确认37份范围配置与原招式一致。

与宝石箱双手可见性修复一起交付于 `build/releases/gem-box-hands-chakram-2026-10-29/`，包含主模组与附属两个0.2.16 JAR、实际第一/第三人称截图、客户端完整报告和带SHA-256的 `verification.json`。版本号未修改。
