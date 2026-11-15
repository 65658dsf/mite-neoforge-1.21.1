# MITE: Modern 0.3.5

November 15, 2026. Developed by NingMeng123.

## Changes

- Updated food consumption to spend the golden saturation layer first, including the seed buffer and nutrition, then the hunger bar. Starvation damage begins after both food reserves are empty.
- Corrected bread to restore 2 hunger points and 8 nutrition points, and baked potatoes to restore 2 hunger points and 6 nutrition points. Nutrition appears in the golden saturation layer with AppleSkin.
- Fixed Dynamic Crosshair 9.11 entity attack indicators to follow actual melee reach, the aimed hit position and block obstruction. The indicator refreshes when the target enters or leaves attack range.
- Limited Polaris animation overrides to daggers and sai, and improved Punchy transitions between custom weapon attacks to prevent the previous attack's displacement from accumulating during combos.
- Added Shift-held descriptions for equipment growth affixes, with English and Chinese text. Enchantment Descriptions also uses Shift to display enchantment details when installed.
- Fixed maintenance-recipe result checks in inventory and workbench crafting, allowing eligible recipes such as ITE's Protection Tome conversion to display and produce their results correctly.
- Fixed shared armor model state restoration after ISMAH first-person rendering, preventing its arm-only visibility and pose changes from affecting later armor rendering.
- Updated the L2Tabs attribute panel to show MITE's effective block and entity interaction distances. Added an effective melee reach row and Shift-held calculation details, including the current level bonus and applicable attack limits.

Build file `MITE-Modern-0.3.5.jar`.

Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.259 or newer within the 21.1 series. Install the mod on both client and server for multiplayer.

## 中文更新日志

- 调整食物消耗顺序，优先消耗金色饱食层中的种子缓冲与营养，再消耗鸡腿饥饿条。两层都耗尽后开始积累饥饿伤害。
- 校正面包与烤土豆的恢复数值。面包恢复 2 点饥饿与 8 点营养，烤土豆恢复 2 点饥饿与 6 点营养，营养通过 AppleSkin 的金色饱食层显示。
- 修复 Dynamic Crosshair 9.11 的实体攻击提示，按实际近战距离、瞄准命中位置和方块遮挡判断，并在目标进入或离开攻击范围时刷新提示。
- 将 Polaris 动画覆盖范围限定到短剑与钗，改善 Punchy 自定义武器攻击之间的过渡，修复连击中上一击位移叠加的问题。
- 为装备成长词条新增按住 Shift 显示的效果说明，补齐中英文文本。安装 Enchantment Descriptions 时，附魔详情也统一通过 Shift 显示。
- 修复背包与工作台合成中的装备维护配方结果判定，使 ITE 防护宝典转换等符合条件的配方能够正常显示并取出产物。
- 修复 ISMAH 第一人称绘制后的共享护甲模型状态恢复，避免仅手臂可见性与姿态变化影响后续护甲绘制。
- 校正 L2Tabs 属性面板的方块与实体交互距离显示，新增有效近战距离条目。按住 Shift 可查看当前等级加成和适用攻击上限等距离构成。

构建文件为 `MITE-Modern-0.3.5.jar`。

运行环境为 Minecraft 1.21.1、Java 21，以及 NeoForge 21.1.259 或更新的 21.1 系列版本。多人游戏的客户端与服务端均须安装本模组。

开发者 NingMeng123。
