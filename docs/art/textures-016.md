# 016 原始 PNG 来源与模型适配

开发署名：NingMeng123。

## 来源与范围

- 来源：项目根目录 `MITE Resource Pack 1.6.4.zip`，8,210,397 字节，2,319 个 ZIP 条目。
- ZIP SHA-256：`4a9bb9f7ab5afee7c0e4e9f57f3b81ff185a0c619d29036b8c2f1ece7450e67a`。
- 本批原样复制 137 张 16×16 PNG：107 张本轮工具、15 张材料（含已有燧石碎片与筋）、1 张下界砂砾、14 张新工作台表面。所有目标文件逐字节等于源 ZIP 条目。
- 本轮工具集合与 `MiteTools.definitions()` 的 107 项完全一致：93 个新注册工具、已有燧石短斧与木棒、10 个原版铁/金工具、原版木铲及铁剪刀。目录排除的 8 把金属小刀与石匕首均未提取。
- 工具纹理由 `reference-data/runtime-registry.json` 的 `yc.cA` 精确定位。铁制原生工具使用运行时指定的平铺路径，金制工具使用 `tools/gold_*`；不按文件名相似度选择资源包中的重复图片。
- 依照最终用户指令，铜工具同样使用包内原图；铜粒保留现代铁粒轮廓和原有 `0xE7A779` 着色，铜锭沿用现代原版，铜工作台使用现代原版外观。其他金属、碎片和下界砂砾均不再额外着色。
- 未整体覆盖现代地形、钻石/下界合金装备、原版铁金粒锭、基础工作台或资源包中尚未实现的其他内容。

## 模型与方向

- `MaterialAssetsProvider` 的非铜新增材料模型直接引用各自原图；下界砂砾使用 `cube_all`，不使用旧的整块颜色乘算。
- `ToolAssetsProvider` 从共同工具定义生成 105 个模型，跳过已有静态燧石短斧与木棒模型，避免重复输出。剪刀沿用现代 `generated` 父模型，其余使用 `handheld`。
- 已读取源图和旧人工木棒图作方向核对：旧图粗端位于左上，原图粗端位于右上、握柄位于左下。旧模型额外的 ±90° 旋转是为旧图准备；换原图后移除该额外旋转，保留现代 `handheld` 的默认握持参数。PNG 本身未旋转、镜像、缩放或重编码。
- 已用图像查看工具检查木棒、燧石短斧、铜剑、木棍、钻石碎片、下界砂砾代表图；没有启动或操作游戏，实际第一/第三人称握持仍属于运行验收。
- 工作台只提取 silver/gold/iron/ancient_metal/mithril/adamantium 的 front/side 与 obsidian/flint 的 top；工作台注册、模型、配方与门槛由单独实现负责。

## 静态验证

- 137/137 目标 PNG 与原 ZIP 内容及 SHA-256 一致，尺寸均为 16×16；107/107 工具定义都有唯一纹理映射。
- 模型数据生成入口需注册 `ToolAssetsProvider`。此审计未运行构建、数据生成或实机验证。

## 原文件映射

目标路径以下相对 `src/main/resources/assets/mite/textures/`；源路径为 ZIP 内完整条目。SHA-256 同时适用于源与目标 PNG。

| 现代 ID / 表面 | ZIP 条目 | 目标纹理路径 | PNG SHA-256 |
|---|---|---|---|
| `mite:adamantium_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/adamantium/front.png` | `block/crafting_table/adamantium/front.png` | `ba2664c7f53ec9eb107886905968aebb7dfffaf583239a4852844b9e376ac3be` |
| `mite:adamantium_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/adamantium/side.png` | `block/crafting_table/adamantium/side.png` | `2e328c47b8cb45f0622cbefe45a62193645d6fa23874e5e961d8604e614ca822` |
| `mite:ancient_metal_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/ancient_metal/front.png` | `block/crafting_table/ancient_metal/front.png` | `3abdba0a26a96d6a753c911c51502075e9db4f138d0fa76b669ed9ee520e4e21` |
| `mite:ancient_metal_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/ancient_metal/side.png` | `block/crafting_table/ancient_metal/side.png` | `0d44488e99220c013536524fd2bc9ead00ab227654a98fbf6660589eeed3ac63` |
| `mite:gold_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/gold/front.png` | `block/crafting_table/gold/front.png` | `bd93d59016f6b1d6d464ddcff1846175bfbc723408fadf89b035067efea0e75a` |
| `mite:gold_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/gold/side.png` | `block/crafting_table/gold/side.png` | `1a9da31e16d19cc4d6d41515ea770132176197a8c1faa703fde6a3ccc851b6db` |
| `mite:iron_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/iron/front.png` | `block/crafting_table/iron/front.png` | `3366602b02a20efb070a19d38bf5cb4ab581a71b63b9a9612a7372ca79b3dd03` |
| `mite:iron_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/iron/side.png` | `block/crafting_table/iron/side.png` | `967e8b1f2012c058dd4dbd6decc7496a831675c4344ea4d777647a2c6831161d` |
| `mite:mithril_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/mithril/front.png` | `block/crafting_table/mithril/front.png` | `7e22f9ef7a88fb54fc4d7c7923cedce3721cb6afb84b92add54d1bcabd7e2a29` |
| `mite:mithril_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/mithril/side.png` | `block/crafting_table/mithril/side.png` | `a84b9d247c4c522c9b002bfe3a95919c2289853f3f92e9673b72bc4bb8072920` |
| `mite:obsidian_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/obsidian/top.png` | `block/crafting_table/obsidian/top.png` | `c209f2da72074fcd1efc711cc18aa336c4244fea91cf68a2f886a1fbaeeb739b` |
| `mite:silver_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/silver/front.png` | `block/crafting_table/silver/front.png` | `204422ffc6510e52e20fd499ee6a9070999b124e6e733fe9607914fd361bcda0` |
| `mite:silver_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/silver/side.png` | `block/crafting_table/silver/side.png` | `4c0c1c662476f3fc9e4e462197b3d6c2da19fa22c33246150ed28175186f7e68` |
| `mite:nether_gravel` | `assets/minecraft/textures/blocks/nether_gravel.png` | `block/nether_gravel.png` | `d3c9e2767fd95714956b9aa261239bc73388873415b00c7e8e5ce5a149f6fdd6` |
| `mite:adamantium_axe` | `assets/minecraft/textures/items/tools/adamantium_axe.png` | `item/adamantium_axe.png` | `32e421f3c5934c10b70ddcdca7fbf82312f360101e9a0ffe7036f2893baaaa07` |
| `mite:adamantium_battle_axe` | `assets/minecraft/textures/items/tools/adamantium_battle_axe.png` | `item/adamantium_battle_axe.png` | `6364bf667834ec58707365f1f2913b11d4917bf76ebb70eb7bd179deca2a3d2c` |
| `mite:adamantium_dagger` | `assets/minecraft/textures/items/tools/adamantium_dagger.png` | `item/adamantium_dagger.png` | `71ed6c93261d082e0bb22d52e137561ac9829b08946ba9c1420ade15b5bfa19e` |
| `mite:adamantium_hatchet` | `assets/minecraft/textures/items/tools/adamantium_hatchet.png` | `item/adamantium_hatchet.png` | `6e8c257e775f66d85a6202f3920561825495e0e07deddcfedacb36dac5645287` |
| `mite:adamantium_hoe` | `assets/minecraft/textures/items/tools/adamantium_hoe.png` | `item/adamantium_hoe.png` | `e56aa15df0efbee49e3ad62fcc19b8b0deffe359ae57e64b98850290073c11e9` |
| `mite:adamantium_ingot` | `assets/minecraft/textures/items/ingots/adamantium.png` | `item/adamantium_ingot.png` | `b2e1de3cc4473044bb8b58eb30d342f4316ec25099edafd859e09f7617a7abdf` |
| `mite:adamantium_mattock` | `assets/minecraft/textures/items/tools/adamantium_mattock.png` | `item/adamantium_mattock.png` | `bab5ddd0168efe549c8fa61212110587bfe48597ba0e07a502849775e9c240d1` |
| `mite:adamantium_nugget` | `assets/minecraft/textures/items/nuggets/adamantium.png` | `item/adamantium_nugget.png` | `a5ee929c3f9a48326b205ebf731036b820c6b2ee19a46d3083af4209bb724341` |
| `mite:adamantium_pickaxe` | `assets/minecraft/textures/items/tools/adamantium_pickaxe.png` | `item/adamantium_pickaxe.png` | `057b04c57f96ef248d904ad3f4a88986ce3c16319972ab541c995fd81190cf40` |
| `mite:adamantium_scythe` | `assets/minecraft/textures/items/tools/adamantium_scythe.png` | `item/adamantium_scythe.png` | `6146c658b6b9ccd736f73959c3e8e23699f44d91ecc13d94536a0e263576f8e5` |
| `mite:adamantium_shears` | `assets/minecraft/textures/items/tools/adamantium_shears.png` | `item/adamantium_shears.png` | `5ce657a773eeee043ff3528efd84b963b5017f45c4b0c15a43b192b4728ad987` |
| `mite:adamantium_shovel` | `assets/minecraft/textures/items/tools/adamantium_shovel.png` | `item/adamantium_shovel.png` | `c13fa8eb2ba758c05233e4a238e1c5e4b49373528d0e786594b241ef955e3908` |
| `mite:adamantium_sword` | `assets/minecraft/textures/items/tools/adamantium_sword.png` | `item/adamantium_sword.png` | `9da11a11d13420806ae9aca45cc8e7be51381ef40591ede6d437c6aca920b57d` |
| `mite:adamantium_war_hammer` | `assets/minecraft/textures/items/tools/adamantium_war_hammer.png` | `item/adamantium_war_hammer.png` | `6d35ace71615e9fca5d45d39c912940ff25dbd840ce4760dc96a1c23b5a012ae` |
| `mite:ancient_metal_axe` | `assets/minecraft/textures/items/tools/ancient_metal_axe.png` | `item/ancient_metal_axe.png` | `d6dcdf2e2326b6faadcc3045fa8bea68008aed04d8ce0fc7aff8c772fc052af2` |
| `mite:ancient_metal_battle_axe` | `assets/minecraft/textures/items/tools/ancient_metal_battle_axe.png` | `item/ancient_metal_battle_axe.png` | `08d06f6dd08a9154fb46ac1b4c5bea4c7ec19cebde1ef6b7ad89b66326e50091` |
| `mite:ancient_metal_dagger` | `assets/minecraft/textures/items/tools/ancient_metal_dagger.png` | `item/ancient_metal_dagger.png` | `0f369eaa3754060d0bbb51e74a441eb31240d1456296afd919c9deddf4001e04` |
| `mite:ancient_metal_hatchet` | `assets/minecraft/textures/items/tools/ancient_metal_hatchet.png` | `item/ancient_metal_hatchet.png` | `e5415b0cf4b87b0f08c6bb4a444b86ba3e8f55b19604e1263836b1cbc2b47796` |
| `mite:ancient_metal_hoe` | `assets/minecraft/textures/items/tools/ancient_metal_hoe.png` | `item/ancient_metal_hoe.png` | `93c747fdced57d22d01f205f1fb7d7b12bd96b688160cb3345a1464e2d2afbe5` |
| `mite:ancient_metal_ingot` | `assets/minecraft/textures/items/ingots/ancient_metal.png` | `item/ancient_metal_ingot.png` | `9c7244ca3a538460b20de71882d8c33371f8007cae6a463e49e3687001f981c8` |
| `mite:ancient_metal_mattock` | `assets/minecraft/textures/items/tools/ancient_metal_mattock.png` | `item/ancient_metal_mattock.png` | `90485b4258c6181fa732bbcbccd6266f20c32435210ed0d1828a21e8d34ab322` |
| `mite:ancient_metal_nugget` | `assets/minecraft/textures/items/nuggets/ancient_metal.png` | `item/ancient_metal_nugget.png` | `ba92521cef2a1bfdaf1b1643d7f20cd11da2366c5c93b70931bcff01dd36f289` |
| `mite:ancient_metal_pickaxe` | `assets/minecraft/textures/items/tools/ancient_metal_pickaxe.png` | `item/ancient_metal_pickaxe.png` | `b2fdc796311cf9c690aa046c535d40f278efd8ed3f682d6998ceab1f09fcdbf1` |
| `mite:ancient_metal_scythe` | `assets/minecraft/textures/items/tools/ancient_metal_scythe.png` | `item/ancient_metal_scythe.png` | `7e735821f22b4615538f2950ecd0838c07319f1f3fc38d38b8871c21afd51070` |
| `mite:ancient_metal_shears` | `assets/minecraft/textures/items/tools/ancient_metal_shears.png` | `item/ancient_metal_shears.png` | `d5b6d3cae5fcd8f3fe05fad3ba07537ed323a605b31b0de0c01bc0c0642327ae` |
| `mite:ancient_metal_shovel` | `assets/minecraft/textures/items/tools/ancient_metal_shovel.png` | `item/ancient_metal_shovel.png` | `a7784e3ce334a294c705d94a7b5a7291eba0deb1c0d641b1442845c8fb55802f` |
| `mite:ancient_metal_sword` | `assets/minecraft/textures/items/tools/ancient_metal_sword.png` | `item/ancient_metal_sword.png` | `3cd6b03cbb2350f5b8a9276748b862d9991dbe647276c7897937f2a06680ce9b` |
| `mite:ancient_metal_war_hammer` | `assets/minecraft/textures/items/tools/ancient_metal_war_hammer.png` | `item/ancient_metal_war_hammer.png` | `8bd8d0aca38a29b5a609f0813ece9a076f25c0a4597c41bca64b1aadc913002e` |
| `mite:copper_axe` | `assets/minecraft/textures/items/tools/copper_axe.png` | `item/copper_axe.png` | `6b0a270c8d6750e29f5998f6adb81a9b29221890bdb99318e44aff022e0c2054` |
| `mite:copper_battle_axe` | `assets/minecraft/textures/items/tools/copper_battle_axe.png` | `item/copper_battle_axe.png` | `3164129c2a2e7ee90448c37c3bdd1937c643dafed8bc87eb64f510a8fafc3df8` |
| `mite:copper_dagger` | `assets/minecraft/textures/items/tools/copper_dagger.png` | `item/copper_dagger.png` | `b953bd6d067b8c4c17f93b073abeea49c3edfaeacc66f0c36d98fd0c923acc9a` |
| `mite:copper_hatchet` | `assets/minecraft/textures/items/tools/copper_hatchet.png` | `item/copper_hatchet.png` | `417f94b803ba1348d64d1e54b2e354312b45cde25a5fb78be5e0c0f5c0af8e8f` |
| `mite:copper_hoe` | `assets/minecraft/textures/items/tools/copper_hoe.png` | `item/copper_hoe.png` | `96156369dd1c9dfebc0ecd33139e757764416c84a8abde8b9e4df4ea40616a35` |
| `mite:copper_mattock` | `assets/minecraft/textures/items/tools/copper_mattock.png` | `item/copper_mattock.png` | `07686028c095b1d388671d8c21021d3159d4786853eea4f7a5e6a3c5f2265750` |
| `mite:copper_pickaxe` | `assets/minecraft/textures/items/tools/copper_pickaxe.png` | `item/copper_pickaxe.png` | `a7a8bb8c63330d69df8bf6f6296c6fc88b9cd7f5a86d07b2be9e29bc8b51be65` |
| `mite:copper_scythe` | `assets/minecraft/textures/items/tools/copper_scythe.png` | `item/copper_scythe.png` | `ed1670479bf0ada05b9a041bfeceab5d49ad7364e90e98bbeaec26bc223d22f2` |
| `mite:copper_shears` | `assets/minecraft/textures/items/tools/copper_shears.png` | `item/copper_shears.png` | `18039a3abcf93681dc82eb9008030523d6ee1213e862ca89e0b2ff7727fc33ef` |
| `mite:copper_shovel` | `assets/minecraft/textures/items/tools/copper_shovel.png` | `item/copper_shovel.png` | `2766bef7476aaec6954de0b4227720547963133c6017b0d277d2ac431350c32b` |
| `mite:copper_sword` | `assets/minecraft/textures/items/tools/copper_sword.png` | `item/copper_sword.png` | `9024728bad56b9ba634d97f9c91065856b8ac81a1be4f01106152493313be5b1` |
| `mite:copper_war_hammer` | `assets/minecraft/textures/items/tools/copper_war_hammer.png` | `item/copper_war_hammer.png` | `1f737fb1ebade1502fee6628fea246402a4896ce363b6f3efa75350c45590416` |
| `mite:diamond_shard` | `assets/minecraft/textures/items/shards/diamond.png` | `item/diamond_shard.png` | `350cfd2c4fd7f10ed95a925127a9a4fb078dd07b645fdf7c8a96de9f309dd9e2` |
| `mite:emerald_shard` | `assets/minecraft/textures/items/shards/emerald.png` | `item/emerald_shard.png` | `4be7054626f9e9b9a2fac6208b4ad13bc9d5fefe725f83bd5f3bb09ec02aa047` |
| `mite:flint_axe` | `assets/minecraft/textures/items/tools/flint_axe.png` | `item/flint_axe.png` | `ea3307a4d8b6a072a9ce66ac3825decf70afd125249f6ef00b12f3258b2929ab` |
| `mite:flint_chip` | `assets/minecraft/textures/items/shards/flint.png` | `item/flint_chip.png` | `32e2c5ba754e0f732c8dfb79508f0419ed38450e4d79a2ba3b1d39f072310f81` |
| `mite:flint_hatchet` | `assets/minecraft/textures/items/tools/flint_hatchet.png` | `item/flint_hatchet.png` | `f43c8e6f84dd4736e53292ecd19f7238c2d5a9fc07d8fec37638d685f1aff655` |
| `mite:flint_knife` | `assets/minecraft/textures/items/tools/flint_knife.png` | `item/flint_knife.png` | `fc69b2c70cf46a76c23122b5574078fc5af441c1c3480f2ea54ed1c524e1a89e` |
| `mite:flint_shovel` | `assets/minecraft/textures/items/tools/flint_shovel.png` | `item/flint_shovel.png` | `410fc255611212fbbb634304a2c799a178e1961bc0f00230bd1fdd89cda4e064` |
| `mite:glass_shard` | `assets/minecraft/textures/items/shards/glass.png` | `item/glass_shard.png` | `e7f0beae723f86bbf9cace334fa163f5e025442869a3342c60b17a15f572c850` |
| `minecraft:golden_axe` | `assets/minecraft/textures/items/tools/gold_axe.png` | `item/golden_axe.png` | `af3797bac21957c03ad727fa46cfd62da15887d30c310184e33362a056afadd7` |
| `mite:golden_battle_axe` | `assets/minecraft/textures/items/tools/gold_battle_axe.png` | `item/golden_battle_axe.png` | `717458215da8fbc71df496d3725fa2e9b53a42b5a1c4f3aa814853e96b362918` |
| `mite:golden_dagger` | `assets/minecraft/textures/items/tools/gold_dagger.png` | `item/golden_dagger.png` | `da72a6065fc38ff658d0362ccf0f89985c5be5407f2bbd1c24702f42bec0a4b1` |
| `mite:golden_hatchet` | `assets/minecraft/textures/items/tools/gold_hatchet.png` | `item/golden_hatchet.png` | `61eef6cb6ea3581aa7a8bb175c44d9b8d4a72618690e4ff93dda323761e96dc4` |
| `minecraft:golden_hoe` | `assets/minecraft/textures/items/tools/gold_hoe.png` | `item/golden_hoe.png` | `566b4265eda9bb8e6a099efd0478b40095c80b354bed2b7a8ebdb2fa1404033d` |
| `mite:golden_mattock` | `assets/minecraft/textures/items/tools/gold_mattock.png` | `item/golden_mattock.png` | `65e3052e5a30541e79c74b6f24fc70daf7e61a769436f412df7df86b847e9210` |
| `minecraft:golden_pickaxe` | `assets/minecraft/textures/items/tools/gold_pickaxe.png` | `item/golden_pickaxe.png` | `3711b8a38c6604a4470bf5f2bbb1521bc16c2570e0f6193f6a4b6db6a8e348be` |
| `mite:golden_scythe` | `assets/minecraft/textures/items/tools/gold_scythe.png` | `item/golden_scythe.png` | `d93883cff084e1f183648d5a0d6961d5c00260f0196fafbf8b3a86ed2dd0ebd5` |
| `mite:golden_shears` | `assets/minecraft/textures/items/tools/gold_shears.png` | `item/golden_shears.png` | `afcdba6068d2520b3277ba1864e45c2cc7484ae893828618d28710fa17c0166e` |
| `minecraft:golden_shovel` | `assets/minecraft/textures/items/tools/gold_shovel.png` | `item/golden_shovel.png` | `49ed24089056528b3a49b92c0a1ac898ab08e78cc69e85ad7d02073c8710801b` |
| `minecraft:golden_sword` | `assets/minecraft/textures/items/tools/gold_sword.png` | `item/golden_sword.png` | `ec9c723ff0d81cf7254ec59819a4179c65a15edcc9148bddea569e54791b25da` |
| `mite:golden_war_hammer` | `assets/minecraft/textures/items/tools/gold_war_hammer.png` | `item/golden_war_hammer.png` | `549a8cff770219ff5dadb3eb2d879f2f8bbfb92daec83c1ed413b2e23644fb0b` |
| `minecraft:iron_axe` | `assets/minecraft/textures/items/iron_axe.png` | `item/iron_axe.png` | `8dea40bac06c6f14bb0ad9e8b47de63250f6d6a46ae9439b85ddd1377f1edb49` |
| `mite:iron_battle_axe` | `assets/minecraft/textures/items/tools/iron_battle_axe.png` | `item/iron_battle_axe.png` | `44ffe5b7cfd7731d0e907d29b5c2023c83d6984d18dff3307f1049579c545f3f` |
| `mite:iron_dagger` | `assets/minecraft/textures/items/tools/iron_dagger.png` | `item/iron_dagger.png` | `04fb6052defac4b31655793481c7a90ce17c9b0cad70713096da262f41b29816` |
| `mite:iron_hatchet` | `assets/minecraft/textures/items/tools/iron_hatchet.png` | `item/iron_hatchet.png` | `22a7f4c893fb4f58988afbdc560120d86f9ffc1c811f993a5aaf2c76e4fa4eaa` |
| `minecraft:iron_hoe` | `assets/minecraft/textures/items/iron_hoe.png` | `item/iron_hoe.png` | `4ed88a87c141168b4552041174e83105e5d5825ea9b96836dd4869c674848d69` |
| `mite:iron_mattock` | `assets/minecraft/textures/items/tools/iron_mattock.png` | `item/iron_mattock.png` | `74722c0747486b5acb717cecbac9bc195bab8339f3634ad72951bb98394f7654` |
| `minecraft:iron_pickaxe` | `assets/minecraft/textures/items/iron_pickaxe.png` | `item/iron_pickaxe.png` | `67305d8bd14e1d60633258f52055fce5aeaea7837c10e62d436fc16f163be627` |
| `mite:iron_scythe` | `assets/minecraft/textures/items/tools/iron_scythe.png` | `item/iron_scythe.png` | `a67aee8ef32a10dae656fba9a92ced7f44bfea5e22f2533145140af6953f2d29` |
| `minecraft:iron_shovel` | `assets/minecraft/textures/items/iron_shovel.png` | `item/iron_shovel.png` | `c9d36d59ec53ebc631bd24930f62087c316eef39bd237d8bb69cb2bb629dfae5` |
| `minecraft:iron_sword` | `assets/minecraft/textures/items/iron_sword.png` | `item/iron_sword.png` | `ed1fa2f83955583e70a19791455d13989e8bd93b1d7240e775a57141022bed6b` |
| `mite:iron_war_hammer` | `assets/minecraft/textures/items/tools/iron_war_hammer.png` | `item/iron_war_hammer.png` | `66fef16ed8e8ee41744618ab21337b34317c1fc09adf1a3fce06bced9311e403` |
| `mite:mithril_axe` | `assets/minecraft/textures/items/tools/mithril_axe.png` | `item/mithril_axe.png` | `cfc7cdb30b5d895e43befc0b0dcff6a096383c8fa0366e0521e85703d561f762` |
| `mite:mithril_battle_axe` | `assets/minecraft/textures/items/tools/mithril_battle_axe.png` | `item/mithril_battle_axe.png` | `7d0ed70d2514f1dad2c2080d8b6b93929a6a938a79059d4a913e71f1e9aa7107` |
| `mite:mithril_dagger` | `assets/minecraft/textures/items/tools/mithril_dagger.png` | `item/mithril_dagger.png` | `a5a5ed334cb35ab77124847ced33b7d255257093fd8215b99a3dd7083deca549` |
| `mite:mithril_hatchet` | `assets/minecraft/textures/items/tools/mithril_hatchet.png` | `item/mithril_hatchet.png` | `0b9b4c988b920313cc5e251c8049d995a3ec3008f9b26605890cc0b3ec730bc2` |
| `mite:mithril_hoe` | `assets/minecraft/textures/items/tools/mithril_hoe.png` | `item/mithril_hoe.png` | `d5ebcecdefca19b463377b43c772cba02459a2691d8d31bec92a79455b8b4c68` |
| `mite:mithril_ingot` | `assets/minecraft/textures/items/ingots/mithril.png` | `item/mithril_ingot.png` | `14b329dde567c0493d89338dfd666915708f06ad6c023c6935792b7037367422` |
| `mite:mithril_mattock` | `assets/minecraft/textures/items/tools/mithril_mattock.png` | `item/mithril_mattock.png` | `dd8983114c888dd44d3083207aacb1ee805f5e2dc78f379b69f59904dba20627` |
| `mite:mithril_nugget` | `assets/minecraft/textures/items/nuggets/mithril.png` | `item/mithril_nugget.png` | `5c2c8a9d131cf285b48cbae808ba0b568214f5258d3c3553e37b19e741acb3f2` |
| `mite:mithril_pickaxe` | `assets/minecraft/textures/items/tools/mithril_pickaxe.png` | `item/mithril_pickaxe.png` | `d19795a361cf9c01218d792c0f2dc77d4546d3e514b910274ce50c144d14574b` |
| `mite:mithril_scythe` | `assets/minecraft/textures/items/tools/mithril_scythe.png` | `item/mithril_scythe.png` | `d20d380d4c252dc262f56018d69f73bf2f28d0ba945c0aa9ae13a5caf0de7177` |
| `mite:mithril_shears` | `assets/minecraft/textures/items/tools/mithril_shears.png` | `item/mithril_shears.png` | `aac3316fdbec4ed5f795465ad439600588d082174bc24b0e3369ae788fcba58b` |
| `mite:mithril_shovel` | `assets/minecraft/textures/items/tools/mithril_shovel.png` | `item/mithril_shovel.png` | `f2e252ab6a5e4af557d3680f39310319bb67995d6d71ac758bb2ea450b68b20e` |
| `mite:mithril_sword` | `assets/minecraft/textures/items/tools/mithril_sword.png` | `item/mithril_sword.png` | `f478b1824d2a004d9a7865c7fad4b7b74fc5ffabdd13a5f7fc4962b70e02b6d0` |
| `mite:mithril_war_hammer` | `assets/minecraft/textures/items/tools/mithril_war_hammer.png` | `item/mithril_war_hammer.png` | `19a00733858d0ea3fb58cae6a92e6c61015d132618c2cc00ca0e1d6cd568af15` |
| `mite:obsidian_axe` | `assets/minecraft/textures/items/tools/obsidian_axe.png` | `item/obsidian_axe.png` | `3753210e761648198ee1bc98872f46d413e4184d4ede855c106539481099240c` |
| `mite:obsidian_hatchet` | `assets/minecraft/textures/items/tools/obsidian_hatchet.png` | `item/obsidian_hatchet.png` | `1aee77406c6b2abe5a9a7dc7de87ef8d6fc54fd0a7da4f5ac102f211b5a3a5b4` |
| `mite:obsidian_knife` | `assets/minecraft/textures/items/tools/obsidian_knife.png` | `item/obsidian_knife.png` | `c8a690e51058e0bf31a1dee183fb4fa7d9e0b1988411c400386d270cb4988050` |
| `mite:obsidian_shard` | `assets/minecraft/textures/items/shards/obsidian.png` | `item/obsidian_shard.png` | `0f2939daeb155e9a99c816257a10afe70439d1740cf0e2d44abb50beef41b968` |
| `mite:obsidian_shovel` | `assets/minecraft/textures/items/tools/obsidian_shovel.png` | `item/obsidian_shovel.png` | `c6eefaf9117784ef1588bca50b41e25ecfef13c5154f09aa2c51fa7865591ef7` |
| `mite:quartz_shard` | `assets/minecraft/textures/items/shards/quartz.png` | `item/quartz_shard.png` | `7b5ed1e5a67517cdefaff66254a9301da60acb444cdf832d3dd53aaed917212e` |
| `mite:rusted_iron_axe` | `assets/minecraft/textures/items/tools/rusted_iron_axe.png` | `item/rusted_iron_axe.png` | `ae30e1373118408702f38d6abf261848fb7a8fb2b8746c4cc9a54443c847e35f` |
| `mite:rusted_iron_battle_axe` | `assets/minecraft/textures/items/tools/rusted_iron_battle_axe.png` | `item/rusted_iron_battle_axe.png` | `252308f359d1547a6ff9abf2c26ab651dba4877ef4b0089eb4acb1b7d120cfa2` |
| `mite:rusted_iron_dagger` | `assets/minecraft/textures/items/tools/rusted_iron_dagger.png` | `item/rusted_iron_dagger.png` | `f0a424100cde0e6c7f6fc5f61ce4acf46502f3dab1b4c914063f488b5a64db91` |
| `mite:rusted_iron_hatchet` | `assets/minecraft/textures/items/tools/rusted_iron_hatchet.png` | `item/rusted_iron_hatchet.png` | `c2de1adc33f5cdb10b4b88aa3ee1a040fe1bab3771d398e44c11471d8b72edd3` |
| `mite:rusted_iron_hoe` | `assets/minecraft/textures/items/tools/rusted_iron_hoe.png` | `item/rusted_iron_hoe.png` | `eab21548797316d890bb69aed60fd33eb01e6c5e2751e9c9d827eb9ea423d263` |
| `mite:rusted_iron_mattock` | `assets/minecraft/textures/items/tools/rusted_iron_mattock.png` | `item/rusted_iron_mattock.png` | `06e100f7498d51e1ea081dae41df6a4b42a124ffb6ce44f522e75da6afd13e0d` |
| `mite:rusted_iron_pickaxe` | `assets/minecraft/textures/items/tools/rusted_iron_pickaxe.png` | `item/rusted_iron_pickaxe.png` | `cb7be8e2847002a8558dcf9b7fbaa3d9147b81dfecb4baeb8e8fc45fb849939c` |
| `mite:rusted_iron_scythe` | `assets/minecraft/textures/items/tools/rusted_iron_scythe.png` | `item/rusted_iron_scythe.png` | `fba0943b8de2581f9abe88992ff30e9b4a419ab7491ecc406f8f8890b9c3fa82` |
| `mite:rusted_iron_shears` | `assets/minecraft/textures/items/tools/rusted_iron_shears.png` | `item/rusted_iron_shears.png` | `c4a8ca8a9f47d86701a34b4bd32f3afbddf68444e94b7c4344647459f096a724` |
| `mite:rusted_iron_shovel` | `assets/minecraft/textures/items/tools/rusted_iron_shovel.png` | `item/rusted_iron_shovel.png` | `669736512c27692b93e7ba2942094f5308872b02e93918548746952ef416cdb3` |
| `mite:rusted_iron_sword` | `assets/minecraft/textures/items/tools/rusted_iron_sword.png` | `item/rusted_iron_sword.png` | `8912a18d10b9a921181f2b4bc6c8a8f877aca1072480c340b6d83b78b3279a7c` |
| `mite:rusted_iron_war_hammer` | `assets/minecraft/textures/items/tools/rusted_iron_war_hammer.png` | `item/rusted_iron_war_hammer.png` | `db4b1a9e798e71a41ecdb547eaf76c77c43d84933cbb8df6d996f8b78ff31361` |
| `minecraft:shears` | `assets/minecraft/textures/items/shears.png` | `item/shears.png` | `5297ebd3d3e1e6cdf874e76fd498b0495878fb085f630de55a4dc19f21eec15c` |
| `mite:silver_axe` | `assets/minecraft/textures/items/tools/silver_axe.png` | `item/silver_axe.png` | `47b6d5d774e5a46c56a6845e057745d9752a0552c9180f1d75d2d8e94179f1a6` |
| `mite:silver_battle_axe` | `assets/minecraft/textures/items/tools/silver_battle_axe.png` | `item/silver_battle_axe.png` | `db091fb03df162e71c651eef8294789e3c8cd3ecb297f08e7a69fcd80827c5d8` |
| `mite:silver_dagger` | `assets/minecraft/textures/items/tools/silver_dagger.png` | `item/silver_dagger.png` | `083b2a4fc3fccbe465a3fbc2bd2638baf4ccddc6571b31c867db9991237c4751` |
| `mite:silver_hatchet` | `assets/minecraft/textures/items/tools/silver_hatchet.png` | `item/silver_hatchet.png` | `6539125d0b357f78e683a3949f3978ad0fef2d692e570fd887789ec027bc14b1` |
| `mite:silver_hoe` | `assets/minecraft/textures/items/tools/silver_hoe.png` | `item/silver_hoe.png` | `cad77fa90d542c29b1ac257813ed40cea271533271b8ee7031693b6ae4cee8a3` |
| `mite:silver_ingot` | `assets/minecraft/textures/items/ingots/silver.png` | `item/silver_ingot.png` | `7c9d99fa4204c8373b076b56115e2b74f098ef6615c2d68a22b90d58e1d5350a` |
| `mite:silver_mattock` | `assets/minecraft/textures/items/tools/silver_mattock.png` | `item/silver_mattock.png` | `568ef2c881eb62a3fd1c426b6841a762b2286f46b2ae1f7614014307cb81076d` |
| `mite:silver_nugget` | `assets/minecraft/textures/items/nuggets/silver.png` | `item/silver_nugget.png` | `7cdb89c30134cadbe77dfe2e49ac3c012d06f8c6feebfa72a4584e9defbddf0c` |
| `mite:silver_pickaxe` | `assets/minecraft/textures/items/tools/silver_pickaxe.png` | `item/silver_pickaxe.png` | `83da18ca692347e92b1e0fa3aa88d5bf872241d5c8bb0d8312242e3744a33179` |
| `mite:silver_scythe` | `assets/minecraft/textures/items/tools/silver_scythe.png` | `item/silver_scythe.png` | `693b92dc3063449b76b74e1126856f2aa3cb10e340bd687085fc6668da0588b6` |
| `mite:silver_shears` | `assets/minecraft/textures/items/tools/silver_shears.png` | `item/silver_shears.png` | `f40b08cbe2f825e09109d6eda22deb27827dbe90169d0d23398df3a1502489d7` |
| `mite:silver_shovel` | `assets/minecraft/textures/items/tools/silver_shovel.png` | `item/silver_shovel.png` | `35a691fe92001eeb3ec223063b48108ed354236a2bd2d97f2da805fac9585e47` |
| `mite:silver_sword` | `assets/minecraft/textures/items/tools/silver_sword.png` | `item/silver_sword.png` | `03d31f8e871829f979f2eb3b5c2a263e686359df2d2e24225720476178ec0e10` |
| `mite:silver_war_hammer` | `assets/minecraft/textures/items/tools/silver_war_hammer.png` | `item/silver_war_hammer.png` | `0b5c6b8bb213f00cd2e55da9537b4161ca6333465064b7d6b67db6fae4fdb393` |
| `mite:sinew` | `assets/minecraft/textures/items/sinew.png` | `item/sinew.png` | `5cc87c07a946c29da3c3cdca634d380b110fbc7a53cbbc1018dcb2b948d6ab4c` |
| `minecraft:wooden_sword` | `assets/minecraft/textures/items/tools/wood_club.png` | `item/wooden_club.png` | `7513f6e8f237282b2bc9edea228b83f9169bc48eeb7a5eee558f9d9996821268` |
| `mite:wooden_cudgel` | `assets/minecraft/textures/items/tools/wood_cudgel.png` | `item/wooden_cudgel.png` | `c422ffd149ffb38b2e69c89b76a9a45bda1a7a1e767ad4be65ae72a0bd11f59b` |
| `minecraft:wooden_shovel` | `assets/minecraft/textures/items/wood_shovel.png` | `item/wooden_shovel.png` | `224aaee15532a88fde2589b749efe503009cbc57509303d6a1e5ce04922932bd` |
| `mite:flint_crafting_table` | `assets/minecraft/textures/blocks/crafting_table/flint/top.png` | `block/crafting_table/flint/top.png` | `cd6a7ed8ef0c899ee6a64ca1f5397b5956c9ea7ce82045e48743c951e9e85dda` |

## 新剪刀与耐久等级的数据兼容

原始来源为固定 Minecraft 1.21.1 资源 JAR `neoforge-21.1.259-client-extra-aka-minecraft-resources.jar`，SHA-256 `8e9cf6f4b6eafb1c6bf745a67a61dd1736f31865cad409e5b2bec918667dab5e`。

在 27 份原版方块掉落表中，仅将 40 处 `minecraft:match_tool` 的 `predicate.items` 从精确 `minecraft:shears` 改为 `#mite:shears`。树叶、草、藤蔓、蛛网等的其他条件、几率、时运、精准采集、数量和随机序列保持原文件内容。村庄牧羊人箱子的剪刀产物不属于工具判断，未修改。`mite:shears` 标签由工具数据生成统一提供。

`data/minecraft/enchantment/unbreaking.json` 仅把 `max_level` 从 3 改为 5，其余成本、附魔对象及原版效果完全保留。本资源只开放等级上限，R196 的逐点耐久概率由独立服务端实现负责。

这些 28 份覆写保存在 `src/main/resources/data/minecraft/`，不再由另一 Provider 重复输出。可重现来源清单位于 `src/main/resources/mite/reference/vanilla-overrides-016.json`，记录每个原始条目、源/目标 SHA-256 及全部 JSON 修改路径；此私有路径不会注册为掉落表或附魔。验证逐文件逆替换后与原始字节完全相同，证明没有额外数值改动。

| 覆写资源 | 修改路径数量 |
|---|---:|
| `data/minecraft/loot_table/blocks/acacia_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/azalea_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/birch_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/cherry_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/cobweb.json` | 1 |
| `data/minecraft/loot_table/blocks/dark_oak_leaves.json` | 3 |
| `data/minecraft/loot_table/blocks/dead_bush.json` | 1 |
| `data/minecraft/loot_table/blocks/fern.json` | 1 |
| `data/minecraft/loot_table/blocks/flowering_azalea_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/glow_lichen.json` | 1 |
| `data/minecraft/loot_table/blocks/hanging_roots.json` | 1 |
| `data/minecraft/loot_table/blocks/jungle_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/large_fern.json` | 2 |
| `data/minecraft/loot_table/blocks/mangrove_leaves.json` | 1 |
| `data/minecraft/loot_table/blocks/nether_sprouts.json` | 1 |
| `data/minecraft/loot_table/blocks/oak_leaves.json` | 3 |
| `data/minecraft/loot_table/blocks/seagrass.json` | 1 |
| `data/minecraft/loot_table/blocks/short_grass.json` | 1 |
| `data/minecraft/loot_table/blocks/small_dripleaf.json` | 1 |
| `data/minecraft/loot_table/blocks/spruce_leaves.json` | 2 |
| `data/minecraft/loot_table/blocks/tall_grass.json` | 2 |
| `data/minecraft/loot_table/blocks/tall_seagrass.json` | 1 |
| `data/minecraft/loot_table/blocks/twisting_vines.json` | 1 |
| `data/minecraft/loot_table/blocks/twisting_vines_plant.json` | 1 |
| `data/minecraft/loot_table/blocks/vine.json` | 1 |
| `data/minecraft/loot_table/blocks/weeping_vines.json` | 1 |
| `data/minecraft/loot_table/blocks/weeping_vines_plant.json` | 1 |
| `data/minecraft/enchantment/unbreaking.json` | 1 |
