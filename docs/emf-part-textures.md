# EMF 独立子贴图兼容

开发者：NingMeng123，2026-10-26；本体兼容补丁，Modern ITE 不需要更改。

## 原因与修复

用户组合为 NeoForge 1.21.1、EMF 3.3.12、ETF 7.2.6、Accelerated Rendering 1.0.14 alpha，以及 Fresh Animations 1.10.4 和 FA+Details 2.2.1。

FA+Details 的掠夺者头盔和羊毛部件拥有自己的 `texture`。Accelerated Rendering 的 EMF 快路径递归复用父模型的顶点缓冲，跳过子部件的纹理选择，导致掠夺者帽檐出现父级皮肤中的黑红条纹、羊毛侧腹显示身体贴图中的棕色矩形。

MITE 在 `EMFModelPart.renderLikeVanilla` 外层检查当前实际模型树。当树中含有独立 `textureOverride` 时，建立线程局部的兼容作用域，让 Accelerated Rendering 的 `shouldUseAcceleratedPipeline` 在该作用域内返回 `false`，由 EMF 正常逐部件选择纹理。作用域支持嵌套并在 `finally` 恢复。没有独立子贴图的模型继续使用原加速路径。

不缓存模型树，避免随机外观切换或资源重载后使用过期结果。EMF 类引用隔离到内部实现；两个 Mixin 均为可选目标。没有修改第三方资源包、实例配置、怪物属性或 AI。

## 客户端审核

`EntityAtlasClientAudit` 仅放在 `gametest` 源集中，不进入正式 JAR。它在 `build/verification` 下创建临时世界，绘制六个固定 UUID 的掠夺者，以及健康、病态、染色、剪毛羊的正反面，记录模型、纹理来源、独立子贴图和实际渲染事件，保存三页 GPU 截图。JSON 的截图状态不代替人工看图验收。

```powershell
.\gradlew.bat runClient '-PmiteEntityAtlasAudit=true' '-PmiteClientDirectory=build/verification/entity-atlas-client' '-PmiteEntityAtlasAuditOutput=build/verification/entity-atlas-full' --console=plain
```

测试目录的模组和资源包来自用户提供的 `run`，排除开发源代码已提供的旧 `mite` JAR；资源包顺序来自用户的 `mite–1.21.1` 实例。实际结果记录在 `build/verification/entity-atlas-*`。

Accelerated Rendering 1.0.14 的文字 Mixin 在开发重编译映射下还会触发无关的 `FormattedText$2.val$text` 字段错误。本次实际 GPU 对照使用安装完成的正式 NeoForge 运行环境和临时审核 JAR：分别将原本体、修复本体与审核类组合，仅在隔离目录加载；交付 JAR 不包含审核类。

修复前的正式运行精确复现了两种错误；修复后的三页截图恢复了掠夺者脸部、随机头盔以及白色、红色羊毛，剪毛和病态样本也完成渲染。另在只安装 Accelerated Rendering、缺少 EMF/ETF 的组合中完成相同审核，确认可选引用不会导致启动或渲染错误。三份报告均有 90 次真实实体渲染事件，`failures` 为空。项目构建与 1763 项 JUnit 测试通过。

额外的光影世界场景审核不是已通过的项目：第一次受 SuperResolution 自身 OpenGL 检测异常影响，在临时目录隔离它后曾加载 Complementary Reimagined r5.9.3 并完成三页 GUI 截图，但测试实体被 Modern ITE 自动生成规则拦截。审核样本已改用 `/summon` 同样的显式命令标记并通过编译，下一次运行却遇到 GLFW 65542／无法创建 OpenGL 窗口。因此没有宣称开启光影后的真实世界场景已验收；没有改动用户的显卡设置或移除用户实例中的模组。

正式 `build/libs/mite-0.2.14.jar` 已原子替换到用户实例和 `run/mods`，旧文件备份与新旧 SHA-256 记录在 `build/verification/entity-atlas-install.json`。现有游戏进程需要重启才能加载代码补丁。

## 指定版本的本体交付

用户另要求提供版本号为 `0.2.13` 的本体 JAR，可在同一份修复源码上执行：

```powershell
.\gradlew.bat build '-Pmod_version=0.2.13' --console=plain
```

产物为 `build/libs/mite-0.2.13.jar`，内部模组元数据同样为 `0.2.13`。构建参数只影响本次产物，仓库默认版本保留 `0.2.14`。当前 `modern-ite-0.2.14.jar` 声明本体依赖 `[0.2.14,)`，因此不能直接与此指定版本的本体配套启动。
