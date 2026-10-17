# R196 农业与养殖规则

维护者：NingMeng123。下列为原版静态证据，尚未实现为现代玩法。结构化数据见 `spec/r196-agriculture-rules.json`。

## plant_temperature

最适区间内倍率1；区间外 max(1 - 距离/容忍度,0)，默认容忍度1。

来源：`.reference/r196/decompiled/BlockGrowingPlant.java`，方法 `getTemperatureGrowthRateModifier`，CFR 行 26–36。

## plant_global_speed

默认全局生长倍率0.25；子类可覆写。

来源：`.reference/r196/decompiled/BlockGrowingPlant.java`，方法 `getGlobalGrowthRateModifierFromMITE`，CFR 行 19–21。

## plant_neighbor_speed

无同种正交邻居倍率0.5；孤立二株0.75；两个以上邻居或邻居继续相连倍率1。

来源：`.reference/r196/decompiled/BlockGrowingPlant.java`，方法 `getProximityGrowthRateModifier`，CFR 行 40–76。

## plant_light

默认生长光照下限和上限均15，子类覆写需单独适配。

来源：`.reference/r196/decompiled/BlockGrowingPlant.java`，方法 `isLightLevelSuitableForGrowth`，CFR 行 79–90。

## crop_temperature

普通作物默认最适温度0.8–1.2，湿度生长倍率1。

来源：`.reference/r196/decompiled/anr.java`，方法 `getLowestOptimalTemperature/getHighestOptimalTemperature`，CFR 行 36–49。

## crop_drought

生长率为0且下方无水源时，每随机tick有0.05概率枯死；成熟作物先按干旱掉落并移除，未成熟转枯死作物。

来源：`.reference/r196/decompiled/anr.java`，方法 `updateTick`，CFR 行 61–78。

## crop_blight_progression

染病后每随机tick先以1/64概率枯死；未枯死且随机布尔不提前返回时，在3×3×3邻域随机挑一点传播到活的未染病anr作物；代码不要求相同作物ID。

来源：`.reference/r196/decompiled/anr.java`，方法 `updateTick`，CFR 行 79–94。

## crop_blood_moon

血月夜、非独立服务器、天空遮挡/室外条件满足时，未染病作物有0.25概率染病。专服该分支禁用。

来源：`.reference/r196/decompiled/anr.java`，方法 `updateTick`，CFR 行 94–97。

## crop_natural_blight

自然染病概率=0.0005×max((1-温度到[1,1.2]区间距离)×(高湿?1.5:1),0)×(1-上方光照/16)。

来源：`.reference/r196/decompiled/anr.java`，方法 `updateTick/getBlightChanceModifierForBiome`，CFR 行 98–120。

## crop_growth_probability

未染病且本次自然染病判定未命中、适光、未成熟、非零生长率时，以1/(floor(25/生长率)+1)概率推进1生长阶段；成功生长后1/256概率消耗耕地肥力。

来源：`.reference/r196/decompiled/anr.java`，方法 `updateTick`，CFR 行 98–112。

## crop_soil_growth

正下方干燥耕地使生长率归0；否则计算3×3耕地加成、同种密植惩罚（任一对角同种，或横纵均有同种时除以2）、肥力1.5倍、温度/湿度/全局/邻近倍率。相邻耕地加成使用metadata>0而非仅湿度位。

来源：`.reference/r196/decompiled/anr.java`，方法 `k`，CFR 行 132–176。

## crop_harvest

只处理玩家采收、干旱、积雪或自落入口；染病无产出，阶段0返回种子，成熟且非自落返回成熟产物，其余无掉落；有效工具收获附魔增加倍率。

来源：`.reference/r196/decompiled/anr.java`，方法 `dropBlockAsEntityItem`，CFR 行 284–310。

## farmland_drying

干燥概率=max(群系温度-0.15-(高湿?0.5:0),0)。

来源：`.reference/r196/decompiled/aof.java`，方法 `chanceOfDrying`，CFR 行 41–48。

## farmland_water

同层和上层、水平±4内水材质，或水平±1且同层/上层雪或冰均可作为耕地供水条件。

来源：`.reference/r196/decompiled/aof.java`，方法 `m`，CFR 行 112–132。

## livestock_initial_state

服务端初始食物、水、自由度各自为0.8+随机float×0.2；默认粪便周期24000 tick。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `<init>`，CFR 行 21–35。

## livestock_wellness

三项数值限制[0,1]；均>=0.25为健康；食水<0.5为饥渴、<0.25为严重、<0.05为极度缺乏。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `isWell/isHungry/isThirsty`，CFR 行 120–157。

## livestock_crowding

不在室外即拥挤；否则扩展包围盒(2,0.5,2)内EntityLiving数量>2判拥挤。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `isCrowded`，CFR 行 204–214。

## livestock_wellness_update

附近食物/水/不拥挤各增加0.1；缺乏各减少0.005；无水但淋雨加水0.01；牛吃附近植物时有0.01破坏概率。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `updateWellness`，CFR 行 216–237。

## livestock_update_schedule

服务端每100tick，nextInt(10)>0才调用健康更新；健康且成年增加生产计数1。produceGoods仍在每100tick调用，不依赖本次是否增加计数。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `c`，CFR 行 240–253。

## livestock_manure

成年且不极度饥饿才递减粪便倒计时；到期掉落1粪便并设为period/2+nextInt(period)。首次倒计时为Math.random()*period。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `c/setManurePeriod`，CFR 行 249–254。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `setManurePeriod`，CFR 行 367–369。

## livestock_food_and_breeding

进食服务端增加0.5食物；仅健康时进入父类求偶逻辑。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `onFoodEaten/bX`，CFR 行 327–338。

## livestock_growth_inheritance

幼体在极度缺食水时停止增加年龄；幼体三项健康值各取父母较低值。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `c/adoptWellnessFromParents`，CFR 行 378–399。

## livestock_experience

牲畜经验值为0，不能泛化为所有非敌对实体。

来源：`.reference/r196/decompiled/EntityLivestock.java`，方法 `getExperienceValue`，CFR 行 372–375。

## chicken_production

生产计数>=100且nextInt(500)==0时恢复羽毛、扣100并返回；否则计数>=200且nextInt(20)==0时产蛋、扣200。鸡粪便周期为默认值×16。

来源：`.reference/r196/decompiled/rq.java`，方法 `produceGoods`，CFR 行 79–93。

## cow_milk

牛奶容量限制0–100，幼体读取为0；生产计数加入牛奶后清零。

来源：`.reference/r196/decompiled/rr.java`，方法 `setMilk/produceGoods`，CFR 行 29–91。

## 分析边界

- 仅覆盖共享作物/牲畜与鸡牛生产核心规则；子类、离线生长、完整掉落和全部AI路径另需审计。
- 语义为静态审计结果，不等于已完成游戏对照实测。
- 原版光照与温度单位需与现代版本分别映射，不能直接使用新群系值认定完全等价。
