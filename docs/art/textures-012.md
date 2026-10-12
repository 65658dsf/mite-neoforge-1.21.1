# 012 木棒材质制作记录

开发者与材质集成：NingMeng123。日期：2026-10-12。

## 工具与交付

使用 imagegen 技能的默认内置工具模式，调用 `image_gen.imagegen` **一次**，参数 `transparent_background: true`。本次为全新生成，无参考图或编辑目标，没有使用 CLI、外部 API、手绘替代或 Python 图像处理。生成结果已通过 `generatedImage(result)` 展示。

- 最终游戏资产：`src/main/resources/assets/mite/textures/item/wooden_club.png`。
- 工作区原图副本：`build/art/012/wooden_club-source.png`。
- 256×256 最近邻预览：`build/art/012/wooden_club-preview.png`。
- 内置工具原图：`$CODEX_HOME/generated_images/01a12ed8-a938-75b1-bab2-3e056d851425/exec-14958a3b-e635-4219-ac6c-5d6136892a19.png`。

最终游戏资源已保存到项目内，不依赖工具默认生成目录。原图与放大预览均位于被 Git 忽略的 `build/`，不进入发布 JAR。

## 导入过程与静态核验

原图尺寸为 1254×1254，带真实 alpha。用 Node.js `sharp` 找出 alpha ≥ 16 的内容边界，裁切矩形为 `left=137, top=131, width=983, height=993`。仅边界检测使用此阈值，图案内原始 alpha 保留。

将裁切内容按比例使用 `kernel: nearest` 缩放为 14×14，再在四边各补 1 像素透明区域，输出 16×16 RGBA PNG；没有描边、重绘或添加背景。

已用 `view_image` 查看最终原尺寸文件及 256×256 最近邻放大预览：

- 轮廓为左上圆钝粗木头、右下较细短握柄，使用棕色木纹与少量明暗像素。
- 无剑刃、护手、金属、钉刺、文字、边框或场景背景。
- 最终共 188 个完全透明像素、66 个 alpha ≥ 128 的可见内容像素。
- 与 010 资产采用相同的 16×16 导入尺度与现代 Minecraft 像素风格。

资产文件和静态视觉检查完成；物品中文名称、模型引用、背包及第一人称显示已在实际客户端确认，见 [012 功能验收](../testing/012-wooden-equipment.md)。

## 精确提示词

以下为实际发送的完整提示词，透明背景参数为 `true`：

```text
Use case: stylized-concept
Asset type: Minecraft 1.21.1 inventory item sprite for a NeoForge mod, matching modern vanilla Minecraft pixel art and a rustic flint starter-tool set.
Primary request: a single WOODEN CLUB, a thick rounded blunt wooden head at upper-left and a narrower short wooden grip extending toward lower-right. It must read as a solid simple wooden bludgeon, not a sword, axe, bat with fittings, or ordinary thin stick. Show restrained brown wood-grain pixels and a few warm tan highlight pixels with dark brown edge shading.
Style/medium: low-resolution pixel art composed on a 16 by 16 logical pixel grid, enlarged with nearest-neighbor squares for delivery. Crisp stair-step edges, flat square pixels, limited earthy brown palette, clear silhouette at native 16 by 16 size. Use the modern Minecraft item style, not high-resolution painted concept art.
Composition/framing: square canvas, one centered diagonal club inside the central 14 by 14 logical pixels, one transparent logical pixel of padding. Bulky rounded top-left head, slim short lower-right grip.
Scene/backdrop: genuine transparent alpha outside the item and in gaps; no drawn checkerboard.
Avoid: sword blade, sharp cutting edge, crossguard, metal, nails, spikes, stone, straps, extra objects, text, signature, border, glow, shadow, ground, scenery, gradients, antialiasing or perspective background.
```
