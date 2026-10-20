# 升级模板贴图

作者：NingMeng123。日期：2026-10-20。

使用内置 imagegen 生成独立透明 PNG，再以像素中心最近邻采样转换为游戏使用的32×32 RGBA纹理。未复用原版锻造模板贴图，未添加外部图像依赖。生成原图保存在`equipment-upgrade-template-reference.png`，游戏贴图为`src/main/resources/assets/mite/textures/item/equipment_upgrade_smithing_template.png`。

设计为铜银金属框、金色扣件及中央青色钻石，对应合成材料。模型引用`mite:item/equipment_upgrade_smithing_template`，由`UpgradeTemplateAssetsProvider`生成，重新运行数据生成不会恢复原版贴图。

内置 imagegen 提示词：

```text
Use case: stylized-concept. Asset type: Minecraft 1.21 item texture for a custom equipment material upgrade template. Create ONE original low resolution pixel-art inventory sprite, an actual transparent PNG, square canvas. The entire design must be drawn on exactly a 16 by 16 logical pixel grid, enlarged with nearest-neighbor to the output resolution, so it can be sampled back to 16x16 with no loss of detail. One centered isolated flat frontal metal upgrade sigil, occupying about 14 by 14 logical pixels with a transparent one-pixel margin. Design: a compact beveled copper and silver mechanical plate with two small gold side clamps, and a very clear bright cyan diamond-shaped gemstone in the center pointing UP. Use a dark warm metal outline, a silver top clasp and copper corners, gold accent pixels, jewel colors cyan to teal. Strong silhouette, legible at tiny size, restrained Minecraft-compatible pixel shading with 12-18 flat colors. It should feel crafted from copper/silver, iron, gold, and a diamond, matching the recipe. Visibly DIFFERENT from vanilla Minecraft netherite upgrade smithing template: do not use the vanilla gray stone tablet or black arrow-shaped cutout, do not copy its silhouette. No text, no letters, no border frame around canvas, no UI, no scene, no duplicate sprites, no drop shadow outside the object, no antialiasing, no soft light, no gradients, no perspective. Background must be genuinely fully transparent, not a checkerboard painted into the image.
```

生成图细节多于提示中的16格目标，因此最终选择32×32采样以保留清晰的钻石和边框，不使用平滑缩放。
