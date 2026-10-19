# 现代深板岩矿石与粗矿材质

开发者与材质集成：NingMeng123。日期：2026-10-19。

普通银、秘银、艾德曼矿图保持原资源包图案。原深板岩模型共用普通矿图，且没有独立深板岩 PNG；现在三种深层模型分别引用自己的 `deepslate_*_ore` 纹理。`OreAssetsProvider` 同时输出这些独立引用，重新数据生成后也会保留修复。

使用 `imagegen` 技能的内置 `image_gen.imagegen` 模式，经用户反馈后重做。最终六张图以模组实际普通 `silver_ore.png`、`mithril_ore.png`、`adamantium_ore.png` 为矿物身份与配色依据，没有使用金属锭配色；原版素材只提供石质与轮廓。深层图以普通矿图为编辑目标，参考固定 Minecraft 1.21.1 的 `deepslate.png` 替换石质底材；粗矿以原版 `raw_iron.png` 为编辑目标，保留棕灰石质基体、不规则块状轮廓和透明背景，将嵌入的金属部分改为对应普通矿图配色。用户提供的粗铁物品与深层铁矿截图用于核对原版画风。

编辑前将上述 16×16 参考图仅用最近邻放大为 1024×1024，便于模型识别原有像素排布，不重绘参考内容。最终稿每张使用一次内置编辑调用；前期试作已由这些最终稿替换。完整最终提示词、原始及放大参考图清单、选中生成文件和透明参数保存在 [`tools/assets/modern_ore_textures.prompts.json`](../../tools/assets/modern_ore_textures.prompts.json)。

| 矿物 | 深层方块纹理 | 粗矿物品纹理 |
|---|---|---|
| 银 | `textures/block/deepslate_silver_ore.png` | `textures/item/raw_silver.png` |
| 秘银 | `textures/block/deepslate_mithril_ore.png` | `textures/item/raw_mithril.png` |
| 艾德曼 | `textures/block/deepslate_adamantium_ore.png` | `textures/item/raw_adamantium.png` |

表中路径相对于 `src/main/resources/assets/mite/`。六张最终图片均为 16×16 RGBA PNG；银矿点为中性银白，秘银为接近白色的极浅冰蓝，艾德曼为近黑色及少量暗靛灰高光，避免把艾德曼改成亮紫宝石。深层保留普通矿点位置和线条特征，底材改为深色岩石；粗矿维持原版粗铁的石质基体，嵌入对应颜色的矿点。

生成原图副本位于被 Git 忽略的 `build/art/modern-ores/generated/`，文件名与最终纹理相同。导入脚本只做透明留白裁切、最近邻缩放和透明边距，不重绘内容；脚本另可生成带名称的放大联系表：

```powershell
python tools/assets/import_modern_ore_textures.py build/art/modern-ores/generated --preview build/verification/modern-ore-textures.png
```

已查看最终九格联系表：第一排为原普通银/秘银/艾德曼，第二排为对应深层矿，第三排为对应粗矿。确认矿色与普通矿身份一致，秘银细长浅色线条及艾德曼近黑矿点可识别，粗矿保留石质基体与透明边距。PNG 及模型引用的静态检查由矿石资源回归检查覆盖；客户端背包、放置方块及实际采掘仍应按 [023 矿石验收](../testing/023-ores.md) 检查。
