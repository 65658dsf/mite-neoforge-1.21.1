# 010 开局物品材质制作记录

开发者与材质集成：NingMeng123。原作 MITE：Avernite；本轮没有复制旧版贴图。

## 生成方式与交付

2026-10-12 使用内置 `image_gen.imagegen`，每种资产独立调用一次，参数 `transparent_background: true`。使用 imagegen 技能的默认内置工具模式，没有调用外部 API 或 CLI，没有手绘替代。三次调用均为全新生成，没有参考图与编辑目标；返回图像已通过 `generatedImage(result)` 展示。

游戏资产是下面三个 **16×16、RGBA、透明 PNG**。原图为 1254×1254，仅保存在已被 Git 忽略的 `build/art/010/`，不进入资源包或模组 JAR。

| 物品 | 最终资源路径 | 原图副本 | 最近邻缩放内容尺寸 |
| --- | --- | --- | --- |
| 燧石碎片 | `src/main/resources/assets/mite/textures/item/flint_chip.png` | `build/art/010/flint_chip-source.png` | 10×14，居中补透明至 16×16 |
| 筋 | `src/main/resources/assets/mite/textures/item/sinew.png` | `build/art/010/sinew-source.png` | 14×13，居中补透明至 16×16 |
| 燧石短斧 | `src/main/resources/assets/mite/textures/item/flint_hatchet.png` | `build/art/010/flint_hatchet-source.png` | 13×14，居中补透明至 16×16 |

内置工具原始输出目录为 `$CODEX_HOME/generated_images/01a12ed8-a938-75b1-bab2-3e056d851425/`；各原始文件名如下。项目资源均已复制和导入工作区，不依赖该本机默认目录。

| 资产 | 内置工具原始文件 |
| --- | --- |
| `flint_chip` | `exec-6b8df6df-8c07-45ab-978d-36d4911c6a00.png` |
| `sinew` | `exec-fdd847f8-7fbf-47c6-8daa-a3cac536a34c.png` |
| `flint_hatchet` | `exec-19483fab-1f89-4560-a74b-a594daab6a64.png` |

## 导入与检查

使用 Node.js 的 `sharp` 做导入格式适配：寻找 alpha ≥ 16 的内容边界，裁切外围透明留白，再按比例使用 `kernel: nearest` 缩放到最长边 14 像素，居中补透明像素到 16×16。只在计算裁切边界时使用该阈值；图案内部原始 alpha 保留，没有填充背景、重绘轮廓或进行 Python 图像处理。

原图周围存在极低 alpha 的零散噪点；若按任意非零 alpha 计算边界，会使图案在物品栏内过小。最终裁切矩形 `left,top,width,height` 分别为：

- 燧石碎片：`433,323,438,614`。
- 筋：`189,233,897,850`。
- 燧石短斧：`240,201,774,842`。

用 `view_image` 逐张查看最终资源，并查看保存于 `build/art/010/*-preview.png` 的 256×256 最近邻放大预览。静态核验结果：

- 三张最终图均为 16×16，存在真实透明背景，无文字、边框、场景或背景棋盘格。
- 燧石碎片为有缺口的深灰尖石片；筋为米黄与棕色的短纤维束；短斧保留深灰石刃、浅色绑扎与棕色木柄，刃位于左上、柄伸向右下。
- 透明像素数分别为 170、209、173；alpha ≥ 128 的内容像素数分别为 85、44、80。
- 生成器没有严格遵守提示词中的 8 色上限，最终保留生成图的有限灰色/土色系取样；没有声称这是精确 8 色调色板。
- 本记录的静态检查已由实际客户端显示检查补充：手持短斧与背包三种物品均正常显示，见 [010 功能验收与截图](../testing/010-flint-start.md)。

## 精确提示词

以下是实际发送的完整英文提示词，逐字保留；三次调用的透明背景参数均为 `true`。

### flint_chip

```text
Use case: stylized-concept
Asset type: Minecraft 1.21.1 item inventory sprite for a NeoForge mod.
Style: modern vanilla Minecraft low-resolution pixel art. Design on an exact 16 by 16 logical pixel grid, enlarged with nearest-neighbor blocks for delivery. Every logical pixel is a flat square of one solid color; crisp stepped edges, no antialiasing. Restrained shared earthy palette: charcoal and cool gray stone, muted brown wood, pale tan and cream binding fibers, at most 8 opaque colors. Sparse top-left pixel highlights, readable silhouette at native 16 by 16 size.
Backdrop: genuinely transparent alpha everywhere outside the sprite, including gaps; not a checkerboard drawing. Square canvas, a single centered object within the central 14 by 14 logical pixels, one logical pixel transparent padding.
Avoid: any text, labels, signature, border, drop shadow, glow, ground, scenery, frame, photorealistic material, smooth gradients, perspective background, multiple icons.
Subject: one small fractured FLINT CHIP, a thin dark-gray knapped stone flake, noticeably smaller and more irregular than a complete flint stone. Asymmetric sharp point toward upper-left, a visible missing notch on the right edge, two or three broad angular cool-gray fracture facets. No handle, no metal, no wood, no fibers. The broken flake silhouette must read immediately.
```

### sinew

```text
Use case: stylized-concept
Asset type: Minecraft 1.21.1 item inventory sprite for a NeoForge mod.
Style: modern vanilla Minecraft low-resolution pixel art. Design on an exact 16 by 16 logical pixel grid, enlarged with nearest-neighbor blocks for delivery. Every logical pixel is a flat square of one solid color; crisp stepped edges, no antialiasing. Restrained shared earthy palette: charcoal and cool gray stone, muted brown wood, pale tan and cream binding fibers, at most 8 opaque colors. Sparse top-left pixel highlights, readable silhouette at native 16 by 16 size.
Backdrop: genuinely transparent alpha everywhere outside the sprite, including gaps; not a checkerboard drawing. Square canvas, a single centered object within the central 14 by 14 logical pixels, one logical pixel transparent padding.
Avoid: any text, labels, signature, border, drop shadow, glow, ground, scenery, frame, photorealistic material, smooth gradients, perspective background, multiple icons.
Subject: SINEW crafting material, a small tidy bundle of just a few dry cream and muted tan animal tendon fibers. Short parallel uneven strands run diagonally from lower-left to upper-right, lightly cinched by one small brown tie at the center. Pale fibrous strands with restrained brown edge pixels. Non-bloody, clean, no red, no flesh. Distinct narrow bundle silhouette, no ball of yarn, no large rope coil, no stone or tool.
```

### flint_hatchet

```text
Use case: stylized-concept
Asset type: Minecraft 1.21.1 item inventory sprite for a NeoForge mod.
Style: modern vanilla Minecraft low-resolution pixel art. Design on an exact 16 by 16 logical pixel grid, enlarged with nearest-neighbor blocks for delivery. Every logical pixel is a flat square of one solid color; crisp stepped edges, no antialiasing. Restrained shared earthy palette: charcoal and cool gray stone, muted brown wood, pale tan and cream binding fibers, at most 8 opaque colors. Sparse top-left pixel highlights, readable silhouette at native 16 by 16 size.
Backdrop: genuinely transparent alpha everywhere outside the sprite, including gaps; not a checkerboard drawing. Square canvas, a single centered object within the central 14 by 14 logical pixels, one logical pixel transparent padding.
Avoid: any text, labels, signature, border, drop shadow, glow, ground, scenery, frame, photorealistic material, smooth gradients, perspective background, multiple icons.
Subject: one FLINT HATCHET, primitive compact hand axe: short muted-brown wooden handle runs diagonally toward lower-right; irregular dark-gray knapped stone blade at upper-left, with its cutting edge on the left, and pale tan sinew wrapping fastening it around the top of the handle. Short handle, small notched angular blade, readable stone facets and two simple binding bands. No metal, no polished steel, no extra objects.
```
