# 027 马铠旧图 UV 与现代模型适配

开发者：NingMeng123。

五张新增马铠实体图保留用户的 `MITE Resource Pack 1.6.4.zip` 原字节，尺寸均为128×128。1.21.1原生马模型和马铠模型使用64×64布局，不能把旧图直接传给原生马铠层，也不能仅缩放整张图修复：两版的身体、头、腿和鞍具UV位置及部件合并方式均不同。

`AnimalArmorItem.BodyType.EQUESTRIAN`按材料注册ID生成 `mite:textures/entity/horse/armor/horse_armor_<material>.png`；五个新ID与现有文件匹配。人形甲使用 `ArmorMaterial.Layer` 的 `textures/models/armor/<material>_layer_1/2.png`，26张64×32图与现代人形甲布局兼容。

旧马模型取未被R196修改的1.6.4原版 `bbp`，其128×128图坐标与每个 `bcp`/`bbs` 立方体面矩形共同作为独立来源。现代模型依据固定1.21.1 `HorseModel.createBodyMesh`、`LayerDefinitions.HORSE_ARMOR` 和 `ModelPart.Cube.compile`，没有推测材质颜色或重新绘画。

| 现代部件 | 旧模型部件及图起点 | 旧尺寸或分段 |
| --- | --- | --- |
| 身体 | `bbp.k`，0,34 | 10×10×24 |
| 颈 | `bbp.h`，0,12 | 4×14×8 |
| 头 | `bbp.a`，0,0 | 5×5×7 |
| 鬃毛 | `bbp.j`，58,0 | 2×16×4 |
| 合并嘴 | `bbp.b/c`，24,18及24,27 | 上3、下2两段 |
| 左后腿 | `bbp.o/v/w`，78,29/43/51 | 上9、小腿5、蹄3三段 |
| 右后腿 | `bbp.x/y/z`，96,29/43/51 | 上9、小腿5、蹄3三段 |
| 左前腿 | `bbp.A/B/C`，44,29/41/51 | 上8、小腿5、蹄3三段 |
| 右前腿 | `bbp.D/E/F`，60,29/41/51 | 上8、小腿5、蹄3三段 |
| 耳 | `bbp.d/e`，0,0 | 2×3×1 |
| 合并尾 | `bbp.l/m/n`，44,0、38,7、24,3 | 按旧尾长3、7、7映射三段 |
| 鞍及缰绳 | `bbp.I/P/Q/R/S/i`对应区域 | 使用旧各面，不复用整图缩放 |

`SourceHorseArmorModel`从真正的现代马铠mesh烘焙每个Cube，读取其已经膨胀后的顶点边界，保留全部23个有Cube的部件、现代位置、层级、可见性和姿态。只在独立模型的Cube绘制时改为旧各面UV，合并嘴、腿和尾把同一现代平面细分为来源区域的连续小平面；不更改轮廓。左/右、前/后腿使用各自来源图区域，并保留蹄区域；幼体腿复用对应同侧图，几何仍由现代原生烘焙决定。现代头部鞍具已拆为头套和嘴套，二者分别投影旧整头套的对应面，属于现代几何适配。

`client.HorseArmorUvMixin`只在五种新增马铠的渲染层生效。它复用原生父模型属性、`prepareMobModel`、`setupAnim`、染色判断、光照和overlay，保持现代运动、吃草、抬头、骑乘和幼体动画。原版与其他模组的马铠继续走原有渲染，不修改共享原生马模型或图片。

静态校验确认95张装备图片逐字节等于资源包并符合已有SHA-256清单；所有新马铠路径、人甲层路径均存在。`HorseArmorUvRulesTest`以独立旧模型坐标检查身体六面、同侧三段腿/蹄、全部23部件与128图边界。客户端仍需要用户手测五种马铠的站立、行走、转头、吃草、骑乘及幼体显示；无窗口测试不等于视觉验收。

同次资源检查还确认37张功能块图片均逐字节来自资源包。单强箱旧 `bbd` 与现代 `ChestRenderer.createSingleBodyLayer` 同用64×64图、盖0,0、主体0,19、锁0,0和相同Cube尺寸；7个 `mite:entity/chest/<material>_single` Material已列入 `assets/minecraft/atlases/chests.json`，无需马铠式UV适配。
