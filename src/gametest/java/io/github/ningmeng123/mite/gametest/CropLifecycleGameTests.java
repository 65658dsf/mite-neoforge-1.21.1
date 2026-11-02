package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.OnionCropBlock;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.rules.CropGrowthRules;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import io.github.ningmeng123.mite.survival.TimedCropGrowth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Deterministic R196 source scenarios against actual mixed-in crop blocks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CropLifecycleGameTests {
    private CropLifecycleGameTests() {}
    @GameTest(template="empty")
    public static void actualPlainsStemRowsUseOptimalTemperatureRatesAndKeepTheirExistingReference(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet());
            for(boolean melon:new boolean[]{false,true}) {
                BlockState seed=(melon?Blocks.MELON_STEM:Blocks.PUMPKIN_STEM).defaultBlockState();
                f.set(f.pos,seed); f.set(f.pos.east(),seed); f.set(f.pos.west(),seed);
                f.set(f.pos.north(),Blocks.AIR.defaultBlockState());f.set(f.pos.south(),Blocks.AIR.defaultBlockState());
                float expected=2.5F;
                float actual=SpecialPlantGrowth.stemGrowth(f.level,f.pos,seed);
                h.assertTrue(Math.abs(actual-expected)<.000001F,"Actual wet plains rows use the former optimal temperature rate and two open fruit sides");
                int threshold=CropGrowthRules.growthThreshold(actual,melon?2F:2.5F,11);
                double minutes=11D*4096D/(threshold/1_000_000D*3D*20D*60D*(12081D/24000D));
                h.assertTrue(Math.abs(minutes-(melon?48D:60D))<.001D,"Removing temperature loss preserves each species' existing growth-roll reference");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void everyFoodCropUsesItsPersistentStageBoundaryIncludingNativeBeetroot(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet());
            for(CropBlock crop:foodCrops()) {
                BlockState seed=crop.getStateForAge(0);
                for(BlockPos p:BlockPos.betweenClosed(f.pos.offset(-1,0,-1),f.pos.offset(1,0,1)))f.set(p,seed);
                h.assertTrue(Math.abs(CropLifecycle.growthRate(seed,f.level,f.pos)-1.25F)<.000001F,"The actual wet dense field matches the hourly reference");
                Rolls win=new Rolls(bound->bound==256?1:0,1F,false);
                seed.randomTick(f.level,f.pos,win);
                h.assertValueEqual(crop.getAge(f.level.getBlockState(f.pos)),0,"Even a winning random draw cannot bypass the elapsed growth clock");
                h.assertFalse(win.bounds.contains(1_000_000),"Food crops no longer draw random maturity rolls");
                h.assertFalse(win.bounds.contains(3),"Beetroot has no additional native one-third timing gate");
                int firstStage=(72000+crop.getMaxAge()-1)/crop.getMaxAge();
                f.clock(firstStage-1);
                h.assertValueEqual(crop.getAge(f.level.getBlockState(f.pos)),0,"The real clock cannot advance before the first earned stage boundary");
                f.clock(20);
                h.assertValueEqual(crop.getAge(f.level.getBlockState(f.pos)),1,"A native clock update advances after enough eligible ticks, for both seven- and three-stage crops");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void clearNativeDaylightMatchesTheHourlyCalendarReference(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            int fullLight=0;
            for(int tick=0;tick<24000;tick++) {
                f.weather(tick,0F);
                if(f.level.getMaxLocalRawBrightness(f.pos.above())==15)fullLight++;
            }
            h.assertTrue(Math.abs(fullLight-12081)<=4,"Native clear-day sky darkening retains the measured roughly fifty-percent full-light duty cycle: "+fullLight);
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void nineSoilsUseSourceMetadataContributionAndFertilizerExactlyOnce(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var crop=Blocks.WHEAT.defaultBlockState();
            f.set(f.pos,crop); f.set(f.pos.below(),wet());
            f.rate(h,.5F,"An isolated crop on its sole wet soil has rate 0.5");
            f.soils(dry()); f.set(f.pos.below(),wet());
            f.rate(h,.75F,"Eight dry neighboring soils each contribute one quarter before global and isolation factors");
            f.soils(wet()); f.rate(h,1.25F,"A complete wet 3 by 3 gives 1.25 for an isolated crop");
            f.set(f.pos.east().below(),dry()); f.rate(h,1.1875F,"A dry neighbor loses two quarters of soil contribution");
            f.set(f.pos.east().below(),dry().setValue(FoodAgriculture.FERTILIZED,true));
            f.rate(h,1.25F,"A dry-but-fertilized neighboring soil has nonzero source metadata and contributes three");
            f.set(f.pos.below(),wet().setValue(FoodAgriculture.FERTILIZED,true));
            f.rate(h,1.875F,"Central fertilizer multiplies once by precisely 1.5");
            f.set(f.pos.below(),dry().setValue(FoodAgriculture.FERTILIZED,true));
            f.rate(h,0F,"Fertility cannot bypass the center's zero-moisture early return");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void cropRowsIsolationAndDiagonalCrowdingUseIndependentSourceMultipliers(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var crop=Blocks.WHEAT.defaultBlockState(); f.soils(wet()); f.set(f.pos,crop);
            f.rate(h,1.25F,"A lone crop receives the one-half isolation multiplier");
            f.set(f.pos.east(),crop); f.rate(h,1.875F,"A pair receives the three-quarter isolation multiplier");
            f.set(f.pos.east(2),crop); f.rate(h,2.5F,"A continuous three-plant row removes isolation loss");
            f.set(f.pos.east(2),Blocks.AIR.defaultBlockState()); f.set(f.pos.west(),crop);
            f.rate(h,2.5F,"A centered straight row is not cross-axis crowding");
            f.set(f.pos.west(),Blocks.AIR.defaultBlockState()); f.set(f.pos.north(),crop);
            f.rate(h,1.25F,"A corner has neighbors on both axes and halves the rate");
            f.set(f.pos.east(),Blocks.AIR.defaultBlockState()); f.set(f.pos.north(),Blocks.AIR.defaultBlockState());
            f.set(f.pos.offset(1,0,1),crop); f.rate(h,.625F,"A lone diagonal applies both crowding and isolation loss");
            f.set(f.pos.offset(1,0,1),Blocks.CARROTS.defaultBlockState());
            f.rate(h,1.25F,"Another crop species cannot impersonate same-species neighbors");
            f.set(f.pos.offset(1,0,1),CropLifecycle.deadState(crop,false)); f.set(f.pos.east(),CropLifecycle.deadState(crop,false));
            f.rate(h,1.25F,"Dead plants had separate source IDs and cannot supply proximity or diagonal-crowding multipliers");
            f.set(f.pos.east(),blighted(crop));
            f.rate(h,1.875F,"A still-living diseased neighbor retains the original plant identity for proximity");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="crop_light_boundary",timeoutTicks=200)
    public static void foodCropsRequireActualLightFifteenAndDoNotGrowAtFourteenOrAtNight(GameTestHelper h) {
        Fixture f=new Fixture(h);
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info,GameTestRunner runner) { f.close(); }
            @Override public void testFailed(GameTestInfo info,GameTestRunner runner) { f.close(); }
        });
        f.soils(wet());
        var engine=f.level.getChunkSource().getLightEngine();
        engine.checkBlock(f.pos.above());
        var lighting=engine.waitForPendingTasks(f.pos.getX()>>4,f.pos.getZ()>>4);
        engine.tryScheduleUpdate();
        h.startSequence().thenWaitUntil(()->{
            engine.tryScheduleUpdate();
            for(int tasks=0;tasks<64&&f.level.getChunkSource().pollTask();tasks++) {}
            h.assertTrue(lighting.isDone(),"The native skylight engine completes the fixture's pending chunk updates");
            lighting.join();
            // Skylight storage is initialized asynchronously after a FULL chunk is published.
            // Check its real raw light before applying the synchronous day/rain/night darkening.
            h.assertValueEqual(f.level.getBrightness(LightLayer.SKY,f.pos.above()),15,
                    "The exposed crop position has fully propagated native skylight before the light-boundary checks");
        }).thenExecute(()->{
            var crop=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);f.soils(wet());
            long[] times={6000,6000,18000}; float[] rain={0F,.3F,0F}; int[] light={15,14,4};
            for(int index=0;index<times.length;index++) {
                f.weather(times[index],rain[index]); f.set(f.pos,crop);
                h.assertValueEqual(f.level.getMaxLocalRawBrightness(f.pos.above()),light[index],"The native light engine observes the independently selected source boundary");
                crop.randomTick(f.level,f.pos,new Rolls(bound->bound==256?1:0,1F,false));
                long before=TimedCropGrowth.remainingTicks(f.level,f.pos,crop);
                f.clock(20);
                long after=TimedCropGrowth.remainingTicks(f.level,f.pos,f.level.getBlockState(f.pos));
                h.assertValueEqual(((CropBlock)crop.getBlock()).getAge(f.level.getBlockState(f.pos)),2,"Random ticks and one clock second cannot jump a crop's growth stage");
                h.assertTrue(index==0?after<before:after==before,"Only actual light fifteen earns clock progress; light fourteen and nighttime remain paused");
            }
        }).thenSucceed();
    }
    @GameTest(template="empty")
    public static void everyFoodCropGrowsAtTheSameRateInActualHotColdAndHumidBiomes(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet());
            for(var biome:List.of(Biomes.PLAINS,Biomes.DESERT,Biomes.FROZEN_PEAKS,Biomes.JUNGLE)) {
                f.biome(biome);
                h.assertTrue(f.level.getBiome(f.pos).is(biome),"The real world fixture uses the requested biome");
                for(CropBlock crop:foodCrops()) {
                    BlockState seed=crop.getStateForAge(0); f.set(f.pos,seed);
                    f.rate(h,1.25F,"Temperature cannot pause or slow any food crop, and humidity adds no new multiplier");
                    seed.randomTick(f.level,f.pos,new Rolls(bound->bound==CropGrowthRules.GROWTH_ROLL_BOUND?0:1,1F,false));
                    h.assertValueEqual(crop.getAge(f.level.getBlockState(f.pos)),0,"A random update cannot accelerate maturation in any biome");
                    long before=TimedCropGrowth.remainingTicks(f.level,f.pos,seed);f.clock(20);
                    h.assertTrue(TimedCropGrowth.remainingTicks(f.level,f.pos,seed)<before,
                            "The real clock earns growth in hot, cold and humid biomes without a temperature pause");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void droughtUsesStrictFivePercentAndNearbyIcePreventsFalseDeaths(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            var crop=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,3); f.set(f.pos.below(),dry()); f.set(f.pos,crop);
            crop.randomTick(f.level,f.pos,new Rolls(bound->1,.05F,false));
            h.assertFalse(CubeCropBlight.dead(f.level.getBlockState(f.pos)),"A draw exactly at five percent survives");
            crop.randomTick(f.level,f.pos,new Rolls(bound->1,.049F,false));
            var dead=f.level.getBlockState(f.pos);
            h.assertTrue(CubeCropBlight.dead(dead)&&!CubeCropBlight.blighted(dead),"Drought records a dead uninfected plant");
            h.assertValueEqual(((CropBlock)crop.getBlock()).getAge(dead),3,"Immature drought death keeps its original age");
            f.biome(Biomes.FROZEN_PEAKS); f.set(f.pos,crop); f.set(f.pos.below().east(),Blocks.ICE.defaultBlockState());
            crop.randomTick(f.level,f.pos,new Rolls(bound->1,0F,false));
            h.assertFalse(CubeCropBlight.dead(f.level.getBlockState(f.pos)),"Nearby source ice irrigation prevents drought death despite dry metadata");
            f.set(f.pos.below().east(),Blocks.AIR.defaultBlockState()); f.set(f.pos.below().east(4),Blocks.WATER.defaultBlockState()); f.set(f.pos,crop);
            crop.randomTick(f.level,f.pos,new Rolls(bound->1,0F,false));
            h.assertFalse(CubeCropBlight.dead(f.level.getBlockState(f.pos)),"A water block on the outer four-block irrigation boundary prevents false death");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void diseaseDeathUsesOneOfSixtyFourAndMatureCropsLoseOneStageIncludingOnionsAndBeets(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet());
            for(CropBlock crop:foodCrops()) {
                BlockState mature=crop.getStateForAge(crop.getMaxAge());
                h.assertTrue(mature.hasProperty(crop instanceof OnionCropBlock?OnionCropBlock.BLIGHTED:CubeCropBlight.BLIGHTED),"Every managed species has a real saved disease property");
                BlockState infected=blighted(mature); f.set(f.pos,infected);
                CubeCropBlight.randomTick(f.level,f.pos,infected,new Rolls(bound->1,1F,true));
                h.assertFalse(CubeCropBlight.dead(f.level.getBlockState(f.pos)),"A nonzero sixty-four-way draw cannot kill the plant");
                CubeCropBlight.randomTick(f.level,f.pos,infected,new Rolls(bound->0,1F,true));
                BlockState dead=f.level.getBlockState(f.pos);
                h.assertTrue(CubeCropBlight.dead(dead),"A zero sixty-four-way draw records actual death");
                h.assertValueEqual(crop.getAge(dead),crop.getMaxAge()-1,"The mature diseased plant retreats exactly one age rather than dropping produce");
                h.assertFalse(dead.isRandomlyTicking(),"Dead food plants stop random ticking");
                BlockState saved=NbtUtils.readBlockState(f.level.holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(dead));
                h.assertTrue(CubeCropBlight.dead(saved),"Both original onion keys and shared vanilla keys survive native NBT serialization");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void blightSpreadsAcrossCropSpeciesButNeverRevivesDeadNeighbors(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet()); f.set(f.pos.east(2).below(),wet());
            var wheat=blighted(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,4));
            var carrots=Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,2);
            var onion=MiteFoodPlants.ONIONS.get().getStateForAge(2);
            f.set(f.pos,wheat); f.set(f.pos.east(),carrots); f.set(f.pos.east(2),onion);
            wheat.randomTick(f.level,f.pos,spreadEast());
            h.assertTrue(CubeCropBlight.blighted(f.level.getBlockState(f.pos.east())),"The actual wheat update infects a carrot");
            f.level.getBlockState(f.pos.east()).randomTick(f.level,f.pos.east(),spreadEast());
            h.assertTrue(f.level.getBlockState(f.pos.east(2)).getValue(OnionCropBlock.BLIGHTED),"A carrot spreads into the onion's separate saved key");
            BlockState dead=CropLifecycle.deadState(onion,false); f.set(f.pos.east(2),dead);
            f.level.getBlockState(f.pos.east()).randomTick(f.level,f.pos.east(),spreadEast());
            h.assertValueEqual(f.level.getBlockState(f.pos.east(2)),dead,"Spreading disease cannot alter or revive a dead neighbor");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void boneMealOnlyTreatsLivingDiseaseAndNeverAddsFoodCropGrowth(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet());
            for(CropBlock crop:foodCrops()) {
                BlockState state=crop.getStateForAge(1); f.set(f.pos,state);
                h.assertFalse(crop.isValidBonemealTarget(f.level,f.pos,state),"Healthy food crops reject bone meal");
                crop.performBonemeal(f.level,new Rolls(bound->0,0F,false),f.pos,state);
                h.assertValueEqual(f.level.getBlockState(f.pos),state,"Direct modern fertilizer calls cannot force healthy crop growth");
                BlockState infected=blighted(state); f.set(f.pos,infected);
                h.assertTrue(crop.isValidBonemealTarget(f.level,f.pos,infected),"Living disease is a valid treatment target");
                crop.performBonemeal(f.level,new Rolls(bound->0,0F,false),f.pos,infected);
                h.assertValueEqual(f.level.getBlockState(f.pos),state,"Treatment clears disease while retaining the exact crop age");
                BlockState dead=CropLifecycle.deadState(infected,true); f.set(f.pos,dead);
                h.assertFalse(crop.isValidBonemealTarget(f.level,f.pos,dead),"Dead plants are not treatment targets");
                crop.performBonemeal(f.level,new Rolls(bound->0,0F,false),f.pos,dead);
                h.assertValueEqual(f.level.getBlockState(f.pos),dead,"A direct treatment call cannot revive a dead plant");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void cropMiningUsesZeroInitialPointZeroTwoIntermediateAndPointZeroFourMatureHardness(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet()); var player=h.makeMockPlayer(GameType.SURVIVAL); player.giveExperienceLevels(-player.experienceLevel);
            player.getFoodData().setFoodLevel(6);
            for(CropBlock crop:foodCrops()) {
                BlockState seed=crop.getStateForAge(0),young=crop.getStateForAge(1),mature=crop.getStateForAge(crop.getMaxAge());
                h.assertValueEqual(MiningProgress.effectiveHardness(seed,0F),0F,"Healthy planted seeds have source hardness zero");
                h.assertValueEqual(MiningProgress.effectiveHardness(young,0F),.02F,"Intermediate crops have source hardness .02");
                h.assertValueEqual(MiningProgress.effectiveHardness(mature,0F),.04F,"Mature crops double hardness to .04");
                h.assertValueEqual(MiningProgress.effectiveHardness(blighted(seed),0F),.02F,"Blighted age-zero metadata is not the source's plain zero state");
                f.set(f.pos,seed); h.assertValueEqual(seed.getDestroyProgress(player,f.level,f.pos),1F,"The real block-mining path instantly breaks healthy unstarted crops");
                f.set(f.pos,young); float youngProgress=young.getDestroyProgress(player,f.level,f.pos);
                f.set(f.pos,mature); float matureProgress=mature.getDestroyProgress(player,f.level,f.pos);
                h.assertTrue(youngProgress>0F&&youngProgress<1F,"Native mining no longer treats intermediate crops as zero-hardness instant blocks");
                h.assertTrue(Math.abs(youngProgress-2F*matureProgress)<.000001F,"The actual mature mining progress is one half at the same player strength");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void randomTicksCannotConsumeFertilizerWithoutAnActualClockStageAdvance(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            f.soils(wet()); var crop=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,2);
            var fertile=wet().setValue(FoodAgriculture.FERTILIZED,true);
            f.set(f.pos.below(),fertile); f.set(f.pos,crop);
            var win=new Rolls(bound->0,1F,false); crop.randomTick(f.level,f.pos,win);
            h.assertFalse(win.bounds.contains(256),"A random crop tick cannot consume the clock's stage-linked fertilizer draw");
            h.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())),"No actual clock stage advance means no fertilizer consumption");
            f.set(f.pos.below(),fertile); f.set(f.pos,crop);
            var growthMiss=new Rolls(bound->bound-1,1F,false); crop.randomTick(f.level,f.pos,growthMiss);
            h.assertFalse(growthMiss.bounds.contains(256),"A failed growth roll never rolls fertilizer loss");
            h.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())),"No growth means no fertilizer consumption");
            FoodAgriculture.onCropGrowth(f.level,f.pos.below(),new Rolls(bound->1,1F,false));
            h.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())),"The retained source nonzero 256-way fertilizer branch preserves manure after an actual stage");
            FoodAgriculture.onCropGrowth(f.level,f.pos.below(),new Rolls(bound->0,1F,false));
            h.assertFalse(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())),"The retained source zero 256-way fertilizer branch removes manure after an actual stage");
        }
        h.succeed();
    }
    private static CropBlock[] foodCrops() {
        return new CropBlock[]{(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,(CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()};
    }
    private static BlockState wet() { return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7); }
    private static BlockState dry() { return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0); }
    private static BlockState blighted(BlockState state) { return state.setValue(state.getBlock() instanceof OnionCropBlock?OnionCropBlock.BLIGHTED:CubeCropBlight.BLIGHTED,true); }
    private static Rolls spreadEast() {
        int[] offsets={2,1,1}; int[] index={0};
        return new Rolls(bound->bound==64?1:offsets[index[0]++],1F,false);
    }
    private static final class Rolls extends LegacyRandomSource {
        private final IntUnaryOperator draw; private final float nextFloat; private final boolean nextBoolean;
        final List<Integer> bounds=new ArrayList<>();
        Rolls(IntUnaryOperator draw,float nextFloat,boolean nextBoolean) { super(196);this.draw=draw;this.nextFloat=nextFloat;this.nextBoolean=nextBoolean; }
        @Override public int nextInt(int bound) { bounds.add(bound);return Math.floorMod(draw.applyAsInt(bound),bound); }
        @Override public float nextFloat() { return nextFloat; }
        @Override public boolean nextBoolean() { return nextBoolean; }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final BlockPos pos; final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final LevelChunkSection section; final PalettedContainerRO<Holder<Biome>> biomes;
        final int quartX,quartY,quartZ,randomTickSpeed; final long time,gameTime; final float rain,thunder;
        Fixture(GameTestHelper h) {
            level=h.getLevel(); BlockPos ref=h.absolutePos(new BlockPos(2,2,2));
            pos=new BlockPos((ref.getX()&~15)+8,((level.getMaxBuildHeight()-56)&~15)+8,(ref.getZ()&~15)+8);
            var chunk=level.getChunkAt(pos);section=chunk.getSection(chunk.getSectionIndex(pos.getY()));biomes=section.getBiomes();
            quartX=QuartPos.fromBlock(chunk.getPos().getMinBlockX()); quartY=QuartPos.fromBlock(pos.getY())&~3;quartZ=QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            time=level.getDayTime();gameTime=level.getGameTime();rain=level.getRainLevel(1F);level.setRainLevel(1F);thunder=level.getThunderLevel(1F);level.setRainLevel(rain);
            randomTickSpeed=level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3,level.getServer());
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,2,2))) set(cell,Blocks.AIR.defaultBlockState());
            biome(Biomes.PLAINS);weather(6000,0F);
        }
        void set(BlockPos pos,BlockState state) { originals.putIfAbsent(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,state,2); }
        void soils(BlockState state) { for(BlockPos p:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,-1,1)))set(p,state); }
        void weather(long time,float rain) { level.setDayTime(time);level.setRainLevel(rain);level.setThunderLevel(0F);level.updateSkyBrightness(); }
        void clock(int ticks) {
            var data=(ServerLevelData)level.getLevelData();var chunk=level.getChunkAt(pos);
            TimedCropGrowth.tickChunk(level,chunk);
            for(int tick=0;tick<ticks;tick++) {
                data.setGameTime(level.getGameTime()+1L);TimedCropGrowth.tickChunk(level,chunk);
            }
        }
        void biome(ResourceKey<Biome> key) {
            var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            section.fillBiomesFromNoise((x,y,z,climate)->biome,level.getChunkSource().randomState().sampler(),quartX,quartY,quartZ);
        }
        void rate(GameTestHelper h,float expected,String reason) {
            h.assertTrue(Math.abs(CropLifecycle.growthRate(level.getBlockState(pos),level,pos)-expected)<.000001F,reason);
        }
        @Override public void close() {
            ((ServerLevelData)level.getLevelData()).setGameTime(gameTime);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(randomTickSpeed,level.getServer());
            level.setDayTime(time);level.setRainLevel(rain);level.setThunderLevel(thunder);level.updateSkyBrightness();
            originals.forEach((p,state)->level.setBlock(p,state,2));
            section.fillBiomesFromNoise((x,y,z,climate)->biomes.get(x&3,y&3,z&3),level.getChunkSource().randomState().sampler(),quartX,quartY,quartZ);
        }
    }
}
