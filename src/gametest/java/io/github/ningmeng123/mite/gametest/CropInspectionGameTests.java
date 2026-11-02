package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.OnionCropBlock;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.integration.CropInspection;
import io.github.ningmeng123.mite.integration.CropInspection.Condition;
import io.github.ningmeng123.mite.integration.CropInspection.Health;
import io.github.ningmeng123.mite.integration.CropInspection.Kind;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.FungalGrowth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent viewer scenarios exercise the actual registered crop states without Jade. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CropInspectionGameTests {
    private CropInspectionGameTests() {}

    @GameTest(template="empty")
    public static void fiveFoodCropsExposeTheirActualStagesAndSeparateSavedDiseaseKeys(GameTestHelper h) {
        CropBlock[] crops={(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,
                (CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()};
        int[] maxima={7,7,7,3,7};
        for(int index=0;index<crops.length;index++) {
            var crop=crops[index];var healthy=crop.getStateForAge(2);var snapshot=CropInspection.state(healthy);
            h.assertValueEqual(snapshot.kind(),Kind.FOOD,"Only real managed food crops receive the food inspection");
            h.assertValueEqual(snapshot.health(),Health.HEALTHY,"Fresh crop state is healthy");
            h.assertValueEqual(snapshot.age(),2,"Viewer reads the actual saved growth stage");
            h.assertValueEqual(snapshot.maxAge(),maxima[index],"Sweet beets retain modern three-stage maximum; original plants have seven");
            var ill=healthy.setValue(crop instanceof OnionCropBlock?OnionCropBlock.BLIGHTED:CubeCropBlight.BLIGHTED,true);
            h.assertValueEqual(CropInspection.state(ill).health(),Health.BLIGHTED,"Original onion blighted key is recognized independently of vanilla crop keys");
            var dead=ill.setValue(crop instanceof OnionCropBlock?OnionCropBlock.DEAD:CubeCropBlight.DEAD,true);
            h.assertValueEqual(CropInspection.state(dead).health(),Health.DEAD,"Death takes precedence over the still-saved disease flag");
            h.assertValueEqual(CropInspection.state(dead).condition(),Condition.DEAD,"A dead plant cannot be described as currently growing");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void sickMaturePlantsNeverReceiveTheGreenHealthyHarvestLabel(GameTestHelper h) {
        var mature=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7);
        var healthy=CropInspection.lines(CropInspection.state(mature));
        h.assertTrue(has(healthy,"tooltip.mite.crop.mature"),"A living healthy fully-grown crop is ready for harvest");
        var illness=CropInspection.lines(CropInspection.state(mature.setValue(CubeCropBlight.BLIGHTED,true)));
        h.assertFalse(has(illness,"tooltip.mite.crop.mature"),"Disease at age seven must not promise a normal mature harvest");
        h.assertTrue(has(illness,"tooltip.mite.crop.growth"),"The saved seven-of-seven growth stage remains visible as growth rather than a healthy harvest label");
        h.assertTrue(has(illness,"tooltip.mite.crop.cure"),"Living disease offers its actual bone-meal treatment hint");
        Component sickName=argument(h,line(h,illness,"tooltip.mite.crop.health"),0);
        h.assertValueEqual(key(sickName),"tooltip.mite.crop.health.blighted","The client receives a translatable disease name");
        h.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(sickName.getStyle().getColor()),"Disease name carries the warning color without requiring a client language table");
        var death=CropInspection.lines(CropInspection.state(mature.setValue(CubeCropBlight.BLIGHTED,true).setValue(CubeCropBlight.DEAD,true)));
        h.assertFalse(has(death,"tooltip.mite.crop.mature")||has(death,"tooltip.mite.crop.growth")||has(death,"tooltip.mite.crop.cure"),"A dead plant neither promises a harvest nor advertises a cure");
        h.assertTrue(has(death,"tooltip.mite.crop.no_cure"),"Death explicitly says treatment cannot revive the plant");
        Component deadName=argument(h,line(h,death,"tooltip.mite.crop.health"),0);
        h.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(deadName.getStyle().getColor()),"Death uses the distinct red status color");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void nonDiseasePlantsShowTheirNativeProgressWithoutInventedHealthStates(GameTestHelper h) {
        BlockState[] states={Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE,1),
                Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,2),
                MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE,2),
                Blocks.BROWN_MUSHROOM.defaultBlockState().setValue(FungalGrowth.GROWTH,2)};
        Kind[] kinds={Kind.COCOA,Kind.WART,Kind.BERRY,Kind.MUSHROOM};int[] ages={1,2,2,2},maxima={2,3,3,3};
        for(int i=0;i<states.length;i++) {
            var s=CropInspection.state(states[i]);var shown=CropInspection.lines(s);
            h.assertValueEqual(s.kind(),kinds[i],"Each special plant retains its real growth mechanism");
            h.assertValueEqual(s.age(),ages[i],"The actual special plant stage is displayed");
            h.assertValueEqual(s.maxAge(),maxima[i],"The special plant uses its actual maximum, without borrowing food-crop seven");
            h.assertValueEqual(s.health(),Health.NONE,"Plants without the R196 crop disease property must not claim susceptibility");
            h.assertFalse(has(shown,"tooltip.mite.crop.health")||has(shown,"tooltip.mite.crop.cure"),"No made-up disease or treatment appears for cocoa, wart, berries or mushrooms");
            h.assertTrue(has(shown,i==3?"tooltip.mite.crop.giant_progress":"tooltip.mite.crop.growth"),"Mushroom manure progress is distinguished from ordinary plant growth");
        }
        h.assertTrue(CropInspection.state(Blocks.OAK_LOG.defaultBlockState())==null,"Ordinary building blocks are not misidentified as crops");
        h.assertTrue(CropInspection.lines(null).isEmpty(),"Unrecognized blocks safely produce no viewer lines");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void farmlandDisplaysRealMoistureFertilizerAndBoundaryWaterWithoutChangingThem(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var soil=wet().setValue(FoodAgriculture.FERTILIZED,true);f.set(f.pos,soil);
            var initial=CropInspection.inspect(f.level,f.pos,soil);
            h.assertValueEqual(initial.moisture(),7,"The soil view shows the saved seven-of-seven moisture");
            h.assertTrue(initial.fertilized(),"The saved fertilizer flag is visible");
            h.assertValueEqual(initial.irrigation(),0,"Wet metadata alone must not pretend a permanent water source exists");
            f.set(f.pos.offset(4,1,4),Blocks.WATER.defaultBlockState());
            var irrigated=CropInspection.inspect(f.level,f.pos,f.level.getBlockState(f.pos));
            h.assertValueEqual(irrigated.irrigation(),1,"Water at the source radius-four upper diagonal is reported");
            h.assertValueEqual(f.level.getBlockState(f.pos),soil,"Looking at soil does not water it, consume fertilizer or replace it");
            f.set(f.pos.offset(4,1,4),Blocks.AIR.defaultBlockState());f.set(f.pos.east().above(),Blocks.ICE.defaultBlockState());
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,soil).irrigation(),1,"The same inspection recognizes source frozen-water irrigation");
            var lines=CropInspection.lines(irrigated);
            h.assertTrue(has(lines,"tooltip.mite.crop.soil")&&has(lines,"tooltip.mite.crop.irrigation"),"Soil wetness/fertilizer and source presence are distinct viewer facts");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void cropDiagnosisReadsRealDrySoilAndDaylightWithoutClimateGatesOrOverridingDisease(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var plant=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);f.set(f.pos.below(),wet());f.set(f.pos,plant);
            var sunny=CropInspection.inspect(f.level,f.pos,plant);
            h.assertValueEqual(f.level.getMaxLocalRawBrightness(f.pos.above()),15,"The high outdoor fixture really has native daylight fifteen");
            h.assertValueEqual(sunny.condition(),Condition.GROWING,"A valid watered crop under optimal native conditions is ready to grow");
            f.set(f.pos.below(),wet().setValue(FarmBlock.MOISTURE,0));
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.DRY,"Dry central farmland takes precedence even in broad daylight");
            f.set(f.pos.below(),wet());f.weather(18000L);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.LIGHT,"Actual nighttime sunlight cannot be misreported as a growing day");
            f.weather(6000L);f.biome(Biomes.FROZEN_PEAKS);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.GROWING,"The real frozen-peaks biome no longer prevents a wet sunlit crop from growing");
            var ill=plant.setValue(CubeCropBlight.BLIGHTED,true);f.set(f.pos,ill);f.set(f.pos.below(),wet().setValue(FarmBlock.MOISTURE,0));
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,ill).condition(),Condition.BLIGHTED,"Dry soil or cold climate must not mask the more urgent existing disease");
            var dead=ill.setValue(CubeCropBlight.DEAD,true);f.set(f.pos,dead);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,dead).condition(),Condition.DEAD,"A dead crop never receives a false growth diagnosis");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void coldAndHotBiomesKeepFoodAndStemDiagnosticsFocusedOnWaterAndLight(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var plants=List.of(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2),
                    Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,2),
                    Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE,2),
                    ((CropBlock)Blocks.BEETROOTS).getStateForAge(2),
                    MiteFoodPlants.ONIONS.get().getStateForAge(2),
                    Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE,2),
                    Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,2));
            for(var biome:List.of(Biomes.FROZEN_PEAKS,Biomes.DESERT)) {
                f.biome(biome);
                h.assertTrue(f.level.getBiome(f.pos).is(biome),"The actual inspection position uses the requested cold or hot native biome");
                for(var plant:plants) {
                    f.weather(6000L);f.set(f.pos.below(),wet());f.set(f.pos,plant);
                    h.assertTrue(plant.canSurvive(f.level,f.pos),"The real crop or stem remains planted legally in this biome");
                    h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.GROWING,
                            "Wet sunlit food crops and stems remain eligible in both extreme climates");
                    f.set(f.pos.below(),wet().setValue(FarmBlock.MOISTURE,0));
                    h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.DRY,
                            "Removing the temperature rule does not hide actual drought");
                    f.set(f.pos.below(),wet());f.weather(18000L);
                    h.assertValueEqual(CropInspection.inspect(f.level,f.pos,plant).condition(),Condition.LIGHT,
                            "Removing the temperature rule does not hide actual nighttime light limits");
                }
                f.weather(6000L);f.set(f.pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());
                var berry=MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE,1);f.set(f.pos,berry);
                h.assertValueEqual(CropInspection.inspect(f.level,f.pos,berry).condition(),Condition.UNSUITABLE,
                        "Blueberries outside their independent forest habitat retain their actual planting restriction");
            }
            f.biome(Biomes.FOREST);f.weather(6000L);
            var berry=f.level.getBlockState(f.pos);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,berry).condition(),Condition.GROWING,
                    "A legal sunlit blueberry still reports that its growth conditions are satisfied");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void melonAndPumpkinStemsDistinguishConnectedFruitFromPermanentDeath(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.set(f.pos.below(),wet());f.set(f.pos.east(),Blocks.MELON.defaultBlockState());
            var connected=Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.EAST);f.set(f.pos,connected);
            var attached=CropInspection.inspect(f.level,f.pos,connected);
            h.assertValueEqual(attached.kind(),Kind.STEM,"Modern attached stems retain their source stem identity");
            h.assertValueEqual(attached.age(),7,"The connected modern stem represents a mature source stem");
            h.assertValueEqual(attached.condition(),Condition.FRUIT_PRESENT,"An existing connected fruit explains why the stem is not spawning another");
            var exhausted=connected.setValue(CubeCropBlight.DEAD,true);f.set(f.pos,exhausted);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,exhausted).condition(),Condition.DEAD,"Death remains the primary status even when an attached fruit still exists");
            var pumpkin=Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,4).setValue(CubeCropBlight.DEAD,true);f.set(f.pos,pumpkin);
            var snapshot=CropInspection.inspect(f.level,f.pos,pumpkin);
            h.assertValueEqual(snapshot.health(),Health.DEAD,"The pumpkin stem's persisted drought/exhaustion flag is visible");
            h.assertFalse(has(CropInspection.lines(snapshot),"tooltip.mite.crop.mature"),"A permanently dead stem is not advertised as harvestable");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void caneAndCactusShowWholeColumnHeightWithoutGuessingAFullGrowthDiagnosis(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.set(f.pos.below(),Blocks.SAND.defaultBlockState());f.set(f.pos.below().east(),Blocks.WATER.defaultBlockState());
            for(int y=0;y<3;y++)f.set(f.pos.above(y),Blocks.SUGAR_CANE.defaultBlockState());
            f.weather(18000L);
            var cane=CropInspection.inspect(f.level,f.pos.above(),f.level.getBlockState(f.pos.above()));
            h.assertValueEqual(cane.kind(),Kind.CANE,"Native sugar cane gets its own growth view");
            h.assertValueEqual(cane.height(),3,"Looking at the middle piece counts both the lower and upper stalk");
            h.assertValueEqual(cane.heightLimit(),3,"Sugar cane exposes the source height limit");
            h.assertValueEqual(cane.condition(),Condition.NONE,"A full-height column cannot misleadingly claim that light alone is preventing growth");
            for(int y=0;y<3;y++)f.set(f.pos.above(y),Blocks.AIR.defaultBlockState());
            f.set(f.pos.below().east(),Blocks.AIR.defaultBlockState());f.set(f.pos,Blocks.CACTUS.defaultBlockState());f.set(f.pos.above(),Blocks.CACTUS.defaultBlockState());
            var cactus=CropInspection.inspect(f.level,f.pos,f.level.getBlockState(f.pos));
            h.assertValueEqual(cactus.height(),2,"Cactus inspection counts the actual column rather than native age metadata");
            h.assertValueEqual(cactus.condition(),Condition.NONE,"Cactus source seed and random branches do not become an invented growth diagnosis");
            h.assertTrue(has(CropInspection.lines(cactus),"tooltip.mite.crop.height"),"The useful column fact is still displayed");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void inspectionPayloadRoundTripsAndRejectsUnknownVersionsEnumsAndImpossibleRanges(GameTestHelper h) {
        var onion=MiteFoodPlants.ONIONS.get().getStateForAge(6).setValue(OnionCropBlock.BLIGHTED,true);
        var expected=CropInspection.state(onion);CompoundTag encoded=CropInspection.save(expected);
        h.assertValueEqual(CropInspection.load(encoded),expected,"The optional viewer payload retains all independent saved onion state facts");
        var version=encoded.copy();version.putInt("version",2);
        h.assertTrue(CropInspection.load(version)==null,"An unknown future payload falls back to block-state inspection");
        for(String field:List.of("kind","health","condition")) {
            var malformed=encoded.copy();malformed.putString(field,"UNRECOGNIZED_FUTURE_VALUE");
            h.assertTrue(CropInspection.load(malformed)==null,"Unknown enum strings do not throw or silently invent a state");
        }
        for(String field:List.of("age","moisture","light","irrigation","height","heightLimit")) {
            var malformed=encoded.copy();malformed.putInt(field,2048);
            h.assertTrue(CropInspection.load(malformed)==null,"Impossible network value is rejected: "+field);
        }
        h.assertTrue(CropInspection.load(new CompoundTag())==null,"An empty or unrelated server payload safely falls back");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void foodMaturityClockUsesActualStagesFertilizerAndNeighbourLayout(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)f.set(f.pos.offset(x,-1,z),wet());
            CropBlock[] crops={(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,
                    (CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()};
            for(var crop:crops) {
                var plant=crop.getStateForAge(1);f.set(f.pos,plant);
                var snapshot=CropInspection.inspect(f.level,f.pos,plant);
                // Nine wet cells: (1 + 3 + 8*3/4) * 1/4 * isolated-plant 1/2 = 1.25.
                h.assertValueEqual(snapshot.growthTicks(),expectedFoodTicks(crop.getMaxAge(),1,1.25D),
                        "Countdown uses the plant's real maximum, including three-stage modern beetroot");
                h.assertTrue(snapshot.timedGrowth(),"Food crops expose the authoritative persistent growth clock");
                h.assertTrue(has(CropInspection.lines(snapshot),"tooltip.mite.crop.countdown.actual"),"Healthy server data exposes actual remaining growth time");
            }
            var wheat=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,1);f.set(f.pos,wheat);
            f.set(f.pos.below(),wet().setValue(FoodAgriculture.FERTILIZED,true));
            var fertilized=CropInspection.inspect(f.level,f.pos,wheat);
            h.assertValueEqual(fertilized.growthTicks(),expectedFoodTicks(7,1,1.875D),"Central fertilizer feeds the capped clock speed from its real layout multiplier");
            f.set(f.pos.below(),wet());f.set(f.pos.east(),wheat);f.set(f.pos.west(),wheat);
            var row=CropInspection.inspect(f.level,f.pos,wheat);
            h.assertValueEqual(row.growthTicks(),expectedFoodTicks(7,1,2.5D),"Two row neighbours improve clock speed without crowding across axes");
            f.set(f.pos.north().east(),wheat);
            var crowded=CropInspection.inspect(f.level,f.pos,wheat);
            h.assertValueEqual(crowded.growthTicks(),expectedFoodTicks(7,1,1.25D),"A diagonal living crop removes the row's speed bonus");
            h.assertTrue(crowded.growthTicks()>row.growthTicks(),"Countdown lengthens when the actual layout slows growth");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void growthClockIgnoresPositiveRandomTickSpeedAndPausesForNightOrDisabledTicks(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var plant=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);f.set(f.pos.below(),wet());f.set(f.pos,plant);
            var sunny=CropInspection.inspect(f.level,f.pos,plant);
            f.weather(18000L);
            var night=CropInspection.inspect(f.level,f.pos,plant);
            h.assertValueEqual(night.condition(),Condition.LIGHT,"Nighttime remains an explicit growth pause");
            h.assertValueEqual(night.growthTicks(),sunny.growthTicks(),"Night keeps the same estimate of eligible growth ticks, without counting darkness as progress");
            h.assertTrue(has(CropInspection.lines(night),"tooltip.mite.crop.countdown.paused"),"The paused line replaces a falsely running countdown");
            h.assertFalse(has(CropInspection.lines(night),"tooltip.mite.crop.countdown.actual"),"Nighttime does not display a running maturity timer");
            f.weather(6000L);f.tickSpeed(6);
            var fast=CropInspection.inspect(f.level,f.pos,plant);
            h.assertValueEqual(fast.growthTicks(),sunny.growthTicks(),"Positive randomTickSpeed changes cannot accelerate the real crop clock");
            f.tickSpeed(0);
            var disabled=CropInspection.inspect(f.level,f.pos,plant);
            h.assertValueEqual(disabled.condition(),Condition.RANDOM_TICKS_DISABLED,"Disabled random ticks explain why a wet sunlit crop cannot advance");
            h.assertValueEqual(disabled.growthTicks(),-1L,"A stopped random-tick clock cannot promise a finite maturity time");
            h.assertTrue(has(CropInspection.lines(disabled),"tooltip.mite.crop.countdown.paused"),"Jade explicitly pauses the clock when random ticks are disabled");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void specialCropClocksUseSourceChanceAndStopAtStemMaturity(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.biome(Biomes.FOREST);f.set(f.pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());
            var berry=MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE,1);f.set(f.pos,berry);
            var berries=CropInspection.inspect(f.level,f.pos,berry);
            h.assertValueEqual(berries.condition(),Condition.GROWING,"The berry fixture has valid forest soil and daylight");
            h.assertValueEqual(berries.growthTicks(),109227L,"Two remaining berry stages average 2*4096*40/3 eligible ticks");
            f.set(f.pos,Blocks.AIR.defaultBlockState());f.set(f.pos.north(),Blocks.JUNGLE_LOG.defaultBlockState());
            var cocoa=Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING,Direction.NORTH).setValue(CocoaBlock.AGE,1);f.set(f.pos,cocoa);
            var pod=CropInspection.inspect(f.level,f.pos,cocoa);
            h.assertValueEqual(pod.condition(),Condition.GROWING,"The cocoa pod is attached to its actual jungle log");
            h.assertValueEqual(pod.growthTicks(),54614L,"Cocoa uses the source 1/40 chance rather than vanilla 1/5 growth");
            f.set(f.pos.north(),Blocks.AIR.defaultBlockState());f.set(f.pos.below(),Blocks.SOUL_SAND.defaultBlockState());
            var wart=Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,1);f.set(f.pos,wart);
            var wrongWorld=CropInspection.inspect(f.level,f.pos,wart);
            h.assertValueEqual(wrongWorld.condition(),Condition.WRONG_DIMENSION,"Soul sand in the Overworld still cannot grow source nether wart");
            h.assertValueEqual(wrongWorld.growthTicks(),-1L,"No nether-wart timer runs outside its permitted dimension");
            h.assertTrue(has(CropInspection.lines(wrongWorld),"tooltip.mite.crop.countdown.paused"),"The wart clock shows its dimension pause");
            f.set(f.pos.below(),wet());
            var stem=Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE,6);f.set(f.pos,stem);
            var growingStem=CropInspection.inspect(f.level,f.pos,stem);
            h.assertTrue(growingStem.growthTicks()>0&&growingStem.timedGrowth()&&has(CropInspection.lines(growingStem),"tooltip.mite.crop.countdown.actual_stem"),"The persistent stem clock measures full stem growth");
            var fullStem=stem.setValue(StemBlock.AGE,7);f.set(f.pos,fullStem);
            h.assertValueEqual(CropInspection.inspect(f.level,f.pos,fullStem).growthTicks(),-1L,"A grown stem does not invent a fruit-production deadline");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void missingDataDiseaseDeathAndElapsedPredictionNeverInventMaturity(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var plant=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);f.set(f.pos.below(),wet());f.set(f.pos,plant);
            var snapshot=CropInspection.inspect(f.level,f.pos,plant);
            var elapsed=CropInspection.lines(snapshot,0L);
            h.assertTrue(has(elapsed,"tooltip.mite.crop.countdown.actual_waiting"),"A client prediction that reaches zero waits for the next authoritative server growth update");
            h.assertFalse(has(elapsed,"tooltip.mite.crop.mature"),"The estimate reaching zero cannot claim the block actually matured");
            h.assertFalse(has(CropInspection.lines(CropInspection.state(plant)),"tooltip.mite.crop.countdown.actual"),"State-only fallback has no guessed environment or maturity time");
            var sick=plant.setValue(CubeCropBlight.BLIGHTED,true);f.set(f.pos,sick);
            var illness=CropInspection.inspect(f.level,f.pos,sick);
            h.assertValueEqual(illness.growthTicks(),-1L,"Disease stops the maturity estimate until treated");
            h.assertTrue(has(CropInspection.lines(illness),"tooltip.mite.crop.countdown.paused"),"A living diseased crop advertises its paused clock");
            var dead=sick.setValue(CubeCropBlight.DEAD,true);f.set(f.pos,dead);
            var death=CropInspection.inspect(f.level,f.pos,dead);
            h.assertValueEqual(death.growthTicks(),-1L,"Dead crops have no maturity estimate");
            h.assertFalse(has(CropInspection.lines(death),"tooltip.mite.crop.countdown.paused"),"A dead plant cannot suggest that its growth could resume");
            var mature=plant.setValue(CropBlock.AGE,7);f.set(f.pos,mature);
            var ready=CropInspection.inspect(f.level,f.pos,mature);
            h.assertValueEqual(ready.growthTicks(),-1L,"Already mature crops do not need a countdown");
            h.assertTrue(has(CropInspection.lines(ready),"tooltip.mite.crop.mature"),"Only actual mature state provides the ready-to-harvest label");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void growthTimingPayloadRetainsLegacyFallbackAndRejectsImpossibleEstimates(GameTestHelper h) {
        var expected=new CropInspection.Snapshot(Kind.FOOD,Health.HEALTHY,2,7,7,false,Condition.GROWING,15,1,-1,-1,500L,true);
        var encoded=CropInspection.save(expected);
        h.assertValueEqual(CropInspection.load(encoded),expected,"Eligible growth time survives the optional server round trip");
        var old=encoded.copy();old.remove("growthTicks");old.remove("timedGrowth");
        var legacy=CropInspection.load(old);
        h.assertValueEqual(legacy.growthTicks(),-1L,"Version-one servers without timing data keep the unknown sentinel");
        h.assertFalse(legacy.timedGrowth(),"Older payloads without an explicit clock mode remain compatible without inventing an authoritative timer");
        h.assertFalse(has(CropInspection.lines(legacy),"tooltip.mite.crop.countdown"),"Legacy diagnostics never fabricate a countdown");
        Map<String,java.util.function.Consumer<CompoundTag>> invalid=new LinkedHashMap<>();
        invalid.put("string timing",tag->tag.putString("growthTicks","500"));
        invalid.put("integer timing",tag->tag.putInt("growthTicks",500));
        invalid.put("string clock mode",tag->tag.putString("timedGrowth","true"));
        invalid.put("negative timing",tag->tag.putLong("growthTicks",-2));
        invalid.put("non-growing kind",tag->tag.putString("kind","MUSHROOM"));
        invalid.put("clock applied to random special crop",tag->tag.putString("kind","COCOA"));
        invalid.put("clock applied to random berries",tag->tag.putString("kind","BERRY"));
        invalid.put("diseased plant",tag->tag.putString("health","BLIGHTED"));
        invalid.put("dead plant",tag->tag.putString("health","DEAD"));
        invalid.put("mature plant",tag->tag.putInt("age",7));
        invalid.put("dry condition",tag->tag.putString("condition","DRY"));
        invalid.put("unknown condition",tag->tag.putString("condition","NONE"));
        invalid.forEach((name,mutation)->{
            var corrupt=encoded.copy();mutation.accept(corrupt);
            h.assertTrue(CropInspection.load(corrupt)==null,"Impossible growth time is rejected: "+name);
        });
        h.succeed();
    }

    @GameTest(template="empty")
    public static void legacyTemperaturePayloadKeepsCropFactsWithoutRevivingAnObsoleteGrowthWarning(GameTestHelper h) {
        var wheat=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);
        var facts=CropInspection.state(wheat);
        var encoded=CropInspection.save(facts);encoded.putString("condition","TEMPERATURE");
        var decoded=CropInspection.load(encoded);
        h.assertValueEqual(decoded,facts,"A legacy temperature diagnosis safely becomes unknown while actual crop facts survive");
        h.assertFalse(has(CropInspection.lines(decoded),"tooltip.mite.crop.condition"),"Old payloads must not display an obsolete stopped-growth warning");
        h.assertValueEqual(CropInspection.save(decoded).getString("condition"),"NONE","Re-encoding an old payload never generates the retired condition");
        var legacy=new CropInspection.Snapshot(facts.kind(),facts.health(),facts.age(),facts.maxAge(),facts.moisture(),facts.fertilized(),
                Condition.TEMPERATURE,facts.light(),facts.irrigation(),facts.height(),facts.heightLimit());
        h.assertValueEqual(legacy.condition(),Condition.NONE,"Direct snapshot callers also cannot revive the retired temperature message");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void commonInspectionWorksWithoutJadeAndNeverAdvancesWorldRandomOrCropState(GameTestHelper h) {
        h.assertFalse(ModList.get().isLoaded("jade"),"The dedicated optional-mod regression run deliberately has no Jade mod");
        try {
            Class.forName("snownee.jade.api.IWailaPlugin",false,CropInspectionGameTests.class.getClassLoader());
            h.fail("The compile-only Jade API must not leak into the dedicated runtime");
        } catch(ClassNotFoundException expected) {
            try(var f=new Fixture(h)) {
                var soil=wet().setValue(FoodAgriculture.FERTILIZED,true);var plant=MiteFoodPlants.ONIONS.get().getStateForAge(4);
                f.set(f.pos.below(),soil);f.set(f.pos,plant);
                long reseed=f.level.random.nextLong();
                try {
                    f.level.random.setSeed(196L);long expectedNext=f.level.random.nextLong();f.level.random.setSeed(196L);
                    for(int repeat=0;repeat<32;repeat++) {
                        var inspected=CropInspection.inspect(f.level,f.pos,f.level.getBlockState(f.pos));
                        h.assertTrue(!CropInspection.lines(inspected).isEmpty(),"The common helper runs normally without linking any Jade API");
                        CropInspection.load(CropInspection.save(inspected));
                    }
                    h.assertValueEqual(f.level.random.nextLong(),expectedNext,"Repeated inspection/serialization does not consume game RNG or change crop probabilities");
                    h.assertValueEqual(f.level.getBlockState(f.pos),plant,"Repeated inspection does not grow, infect, cure or kill the crop");
                    h.assertValueEqual(f.level.getBlockState(f.pos.below()),soil,"Repeated inspection does not water or exhaust the farmland");
                } finally {f.level.random.setSeed(reseed);}
            }
        }
        h.succeed();
    }

    private static BlockState wet() {return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);}
    private static long expectedFoodTicks(int maxAge,int age,double rate) {
        // Old crops migrate once from their saved stage; new plants start at work zero.
        long total=504_000_000_000L,work=total*age/maxAge;
        long speed=Math.round(7_000_000D+(Math.clamp(rate/1.25D,1D,3D)-1D)*2_500_000D);
        return (total-work+speed-1)/speed;
    }
    private static String key(Component c) {return c.getContents() instanceof TranslatableContents translated?translated.getKey():"";}
    private static boolean has(List<Component> lines,String key) {return lines.stream().anyMatch(c->key(c).equals(key));}
    private static Component line(GameTestHelper h,List<Component> lines,String key) {
        var result=lines.stream().filter(c->key(c).equals(key)).findFirst();h.assertTrue(result.isPresent(),"Expected localized viewer line: "+key);return result.orElseThrow();
    }
    private static Component argument(GameTestHelper h,Component line,int index) {
        var contents=(TranslatableContents)line.getContents();h.assertTrue(contents.getArgs()[index] instanceof Component,"A localized nested status remains a component");return (Component)contents.getArgs()[index];
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final BlockPos pos;final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final LevelChunkSection section;final PalettedContainerRO<Holder<Biome>> biomes;final int quartX,quartY,quartZ;
        final long time;final float rain,thunder;final int randomTickSpeed;
        Fixture(GameTestHelper h) {
            level=h.getLevel();var ref=h.absolutePos(new BlockPos(2,2,2));
            pos=new BlockPos((ref.getX()&~15)+8,((level.getMaxBuildHeight()-56)&~15)+8,(ref.getZ()&~15)+8);
            var chunk=level.getChunkAt(pos);section=chunk.getSection(chunk.getSectionIndex(pos.getY()));biomes=section.getBiomes();
            quartX=QuartPos.fromBlock(chunk.getPos().getMinBlockX());quartY=QuartPos.fromBlock(pos.getY())&~3;quartZ=QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            time=level.getDayTime();rain=level.getRainLevel(1F);level.setRainLevel(1F);thunder=level.getThunderLevel(1F);level.setRainLevel(rain);
            randomTickSpeed=level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);tickSpeed(3);
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,3,4)))set(cell,Blocks.AIR.defaultBlockState());
            biome(Biomes.PLAINS);weather(6000L);
        }
        void set(BlockPos p,BlockState state) {originals.putIfAbsent(p.immutable(),level.getBlockState(p));level.setBlock(p,state,2);}
        void weather(long time) {level.setDayTime(time);level.setRainLevel(0F);level.setThunderLevel(0F);level.updateSkyBrightness();}
        void tickSpeed(int speed) {level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(speed,level.getServer());}
        void biome(ResourceKey<Biome> key) {
            var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            section.fillBiomesFromNoise((x,y,z,climate)->biome,level.getChunkSource().randomState().sampler(),quartX,quartY,quartZ);
        }
        @Override public void close() {
            level.setDayTime(time);level.setRainLevel(rain);level.setThunderLevel(thunder);level.updateSkyBrightness();
            tickSpeed(randomTickSpeed);
            originals.forEach((p,state)->level.setBlock(p,state,2));
            section.fillBiomesFromNoise((x,y,z,climate)->biomes.get(x&3,y&3,z&3),level.getChunkSource().randomState().sampler(),quartX,quartY,quartZ);
        }
    }
}
