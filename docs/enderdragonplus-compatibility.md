# Ender Dragon Plus 兼容

开发者：NingMeng123，2026-10-28。

## 血量

Modern ITE 的原生末影龙基础生命为 7500。Ender Dragon Plus 0.2 在普通优先级的实体入场事件中将其覆盖为自己的默认 400；ITE 的初始化标记又会阻止读档时重做属性。

本体在入场事件结束时修复这项覆盖，同时保留 EDP 的强化召唤状态、技能和阶段数据。修复适用于首次生成以及已有的 400 血存档，按当前有效生命上限保留受伤比例，并保留独立属性修饰。已有明确的其他自定义基础血量会优先恢复。仅在同时安装 Modern ITE 和 EDP，且实体具有 ITE Boss 属性标记时启用。

兼容通过可选反射读取 EDP 的实际设置，并调用 Modern ITE 的公开属性初始化方法读取龙的基础生命；用于读取属性的临时实体不会入场或执行 tick。解析失败会记录一次警告并保留现有属性。没有将这两个模组加入必需依赖。

## 模型与贴图

Boss Refreshed v2 的龙模型使用五个 `neckN` 和十二个 `tailN` 部件，而 Minecraft 1.21.1 的龙渲染器重复绘制一个 `spine`。EMF 3.3.12 原先忽略了这些部件，剩余原版颈尾几何又使用了自定义贴图，出现长条和贴图错位。

兼容在 EMF 准备模型时识别完整的 17 段结构，将部件挂接到旧版 spine，并在原版每次绘制时选择对应段。原始部件 ID、几何、动画与资源包素材保留；绘制后及异常退出时恢复可见性。原本使用 `spine` 或结构不完整的模型不会重写，资源重载后重新适配。

EDP 的裂纹和脉络发光层使用原版 UV，不能直接套到替换模型上。本体仅在 EMF 自定义龙模型启用时过滤这两个贴图层，保留资源包本身的发光、EDP 阶段染色和场景技能特效；原版龙继续使用 EDP 自带发光。

## 验证入口

服务端集成测试命名空间为 `mite_enderdragonplus`，需要安装 EDP、Modern ITE 和支持 7500 以上有效生命的 AttributeFix 及其依赖。覆盖新龙、400 血旧存档、永久修饰、受伤比例、连续读档和显式自定义血量。

客户端审核位于 gametest 源集，不进入发布包。它在 `build/verification` 的隔离目录新建临时世界，以真实渲染器绘制三个阶段，检查 17 段绘制选择及异常恢复，记录模型、贴图和实际渲染缓冲，在资源重载前后保存截图：

```powershell
.\gradlew.bat runClient '-PmiteEnderDragonCompatibilityAudit=true' '-PmiteClientDirectory=build/verification/enderdragon-client' --console=plain
```

使用 Accelerated Rendering 时采用正式 NeoForge 运行环境，避免其开发映射文字 Mixin 与本功能无关的异常。最终结果以审核 JSON 的 `status`、`failures` 和实际截图为准。

## 本轮结果

1770 项 JUnit 和 3 项 EDP/ITE 服务端集成测试通过。正式 NeoForge 客户端分别完成 EMF+Boss Refreshed、额外安装 Accelerated Rendering、保留原版模型、缺少全部可选模组四组检查；四份 JSON 均为 `passed_real_dragon_rendering_and_resource_reload`，`failures` 为空。

加速渲染组验证了重载前后的 17 段实际绘制选择、异常恢复，以及三个阶段的正面、背面、侧面和独立尾部画面。尾部的实际旋转为 0°，资源包的旋转修正动画有效，完整渐细尾部及鳞片、背刺可见。原版模型组保留 EDP 两层发光；缺少可选模组时正常启动和渲染。

源码与验证在基线 `85575868` 加本轮变更的独立目录构建，避免同工作区其他进行中的铁砧改动影响产物。发布包为 `build/libs/mite-0.2.18.jar`；没有包含审核器、第三方模组或资源包素材。报告、截图和运行日志位于 `build/verification/enderdragon-*`。本轮没有进入或修改用户已有存档，也没有进行完整光影组合的世界战斗实玩。

已将本体 `mite-0.2.18.jar` 安装到用户 `mite–1.21.1/mods`，原 `mite-0.2.17.jar` 移至验证目录备份。新旧 SHA-256、备份路径见 `build/verification/enderdragon-install.json`。EDP、Modern ITE 与资源包无需替换，正在运行的游戏须完整重启；已有龙在重新加载时迁移生命属性。
