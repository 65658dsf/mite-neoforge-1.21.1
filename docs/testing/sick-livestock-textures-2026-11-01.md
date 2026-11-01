# 生病动物贴图兼容修复

开发者：NingMeng123。日期：2026-11-01。

## 原因与修复

本地 FA+Details v2.2.1 的牛、猪模型和身体贴图使用 64×64 图集；四种旧版病态贴图都是 64×32。直接替换贴图会让自定义模型采样错误 UV，导致身体透明、缺面。Fresh Animations 基础包的这两种动物仍使用 64×32，但其它定制模型也可能改变 UV，因此不能只根据图片尺寸判断兼容。

用户复核要求生病和恢复中的身体呈灰白色。此前的 RGB 乘色仍保留棕色、粉色并带绿调，因此改为在运行时逐像素去色、提亮。`SickLivestockTextures` 用亮度 `Y=(77R+150G+29B+128)>>8` 计算 `gray=88+(152Y+127)/255`，可见像素设为 `(gray, gray, gray)`，保留深浅斑纹、UV 尺寸、全部 alpha 和透明区原始像素。原生模型从来源病态图生成灰白图；原始 PNG 字节保持不变。

`SickLivestockVisuals` 使用可选 EMF API 判断当前模型是否经过定制。CEM 主身体在 ETF 已选定真实随机变体、眨眼或生成图之后读取实际绑定图集，再生成同布局灰白图，通过 EMF 的原 RenderType 工厂替换主身体 consumer。顶点颜色、光照及受伤叠加参数原样传递。FA+Details 将羊毛移入身体 CEM 的独立贴图子部件，因此纹理作用域仅将显式重新绑定的同一基础图集重定向到对应灰图；独立羊毛、角和猪鞍保持原图与原色。作用域在绘制结束或异常时还原，恢复完成后立即使用正常图集。

另查明旧病鸡贴图在原生头底面 UV（x=7..10、y=0..2）有 12 个透明像素。`SickChickenTexture` 仅将这些透明像素补为该贴图头顶已有的病态颜色，在运行时上传动态贴图；其它 UV、已有半透明像素及原始 PNG 字节保留。支持整数倍高清图集，未知布局不改写。资源重载时释放动态纹理并清空缓存。

这次修改仅涉及客户端外观，不改变健康判定、恢复时间和动物行为，保持工作区现有模组版本 `0.3.0`。

## 验证

- `gradlew.bat build compileGametestJava` 通过；2047 项 JUnit 全部通过，无失败或跳过。
- `ChickenHeadUvTest` 使用实际来源 PNG 检查 12 个缺口补齐、其它像素完全不变，并覆盖高清图集、半透明像素和重复调用。
- `SickLivestockPaletteTest` 使用四张实际来源 PNG 验证可见像素完全去色、alpha 与透明像素保留、斑纹灰阶仍有差异，并覆盖半透明和 ABGR 通道顺序。
- `SickLivestockClientAudit` 在独立测试目录创建临时世界，使用同一动物和渲染器检查健康、生病、恢复、恢复倒计时，并捕获真实 GPU 正反面画面及实际渲染顶点颜色。覆盖牛、猪、鸡、红羊毛羊、白羊毛羊、带鞍猪和剪毛羊。
- 无 EMF 的原生客户端与 FA 1.10.4 + FA+Details 2.2.1 + FA+Emissive 1.6 / EMF 3.3.12 / ETF 7.2.6 / Accelerated Rendering 1.0.14 / Sodium 0.8.13 的客户端，均完成七组样本及 21 个健康阶段，恢复倒计时检查通过。两组正反面 GPU 截图已目视确认身体完整、羊毛颜色及猪鞍保留。
- 安装 EMF/ETF 但不启用定制模型的独立实例也通过七组样本、21 个阶段和恢复倒计时，确认原生灰图经过 ETF 后仍正确绑定；报告在 `build/verification/sick-livestock-gray-emf-native-results/`。
- 临时 CEM fixture 同时显式指定牛的顶层基础贴图和身体部件贴图，验证重新绑定分支。生病及恢复延迟期间，牛的 352 个身体顶点全部绑定灰图，原彩色基础图集没有身体顶点；健康和痊愈阶段恢复原图。全部 21 个阶段通过，fixture 使用后恢复隔离实例原 options，报告在 `build/verification/sick-livestock-gray-fa-explicit-results/`。
- FA 组合实际使用 64×64 牛猪贴图。红毛使用 ETF 选择的 `sheep_wool6.png`（1459 个红色像素），白毛使用 `sheep_wool.png`，在健康、生病、恢复时均保持对应图集和白色顶点颜色；身体也保持白色顶点，通过真实灰度图呈现灰白病态。
- 灰白修正的报告与截图在 `build/verification/sick-livestock-gray-native-results/`、`build/verification/sick-livestock-gray-fa-results/`；最终发布 JAR 不含审计类，四张来源 PNG 与原 ZIP 和清单逐字节一致。

包含 Accelerated Rendering 的初次 Gradle 开发运行因第三方 `FormattedText$2Mixin` 的 `val$text` 字段映射失败而无法启动，保留为 `sick-livestock-fa-launch.log`。FA 组合改用本机已安装的生产 NeoForge 运行时完成检查，生产代码和第三方 JAR 均未为绕过该启动问题而修改。

审计启用参数：

```powershell
./gradlew.bat runClient '-PmiteSickLivestockAudit=true' '-PmiteClientDirectory=build/verification/sick-livestock-native' '-PmiteSickLivestockAuditExpectCustom=false' '-PmiteSickLivestockAuditOutput=build/verification/sick-livestock-gray-native-results'
```

FA+Details 实例另外设置 `-PmiteSickLivestockRequireExtendedAtlas=true` 和 `-PmiteSickLivestockAuditExpectCustom=true`，要求实际加载定制模型与牛、猪的 64×64 资源后才允许通过。该审计源码仅属于 gametest 源集，不包含在发布 JAR 中。
