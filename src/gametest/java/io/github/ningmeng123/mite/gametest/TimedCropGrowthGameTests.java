package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.integration.CropInspection;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.TimedCropGrowth;
import io.github.ningmeng123.mite.survival.TimedCropGrowth.Data;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real clock callbacks, saved attachments and native crop states enforce the maturation floor. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TimedCropGrowthGameTests {
    private TimedCropGrowthGameTests() {}

    @GameTest(template="empty",batch="timed_crop_base",timeoutTicks=200)
    public static void everyFoodCropNeedsAllSixtyMinutesWithoutBonuses(GameTestHelper h) {
        ready(h,f->{
            for(var crop:foodCrops()) {
                f.clearPlants();f.soils(false);f.dense(crop.getStateForAge(0));
                h.assertValueEqual(TimedCropGrowth.remainingTicks(f.level,f.pos,f.state()),72_000L,
                        "Fresh unboosted crop starts with sixty minutes of eligible growth time");
                f.advance(71_999);
                h.assertTrue(crop.getAge(f.state())<crop.getMaxAge(),"Neither seven-stage crops nor modern beetroot mature before sixty minutes");
                f.advance(1);
                h.assertValueEqual(crop.getAge(f.state()),crop.getMaxAge(),"The authoritative clock matures after all sixty minutes have been earned");
            }
        });
    }

    @GameTest(template="empty",batch="timed_crop_fast",timeoutTicks=200)
    public static void optimalFertilizedRowsCannotMatureOneTickBeforeThirtyFiveMinutes(GameTestHelper h) {
        ready(h,f->{
            for(var crop:new CropBlock[]{(CropBlock)Blocks.POTATOES,(CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()}) {
                f.clearPlants();f.soils(true);f.row(crop.getStateForAge(0));f.maintainFertility=true;
                h.assertValueEqual(TimedCropGrowth.remainingTicks(f.level,f.pos,f.state()),42_000L,
                        "All layout and fertilizer bonuses together produce exactly thirty-five eligible minutes");
                f.advance(41_999);
                h.assertTrue(crop.getAge(f.state())<crop.getMaxAge(),"The fastest valid farm never reaches maturity even one tick before the hard minimum");
                f.advance(1);
                h.assertValueEqual(crop.getAge(f.state()),crop.getMaxAge(),"Continuously optimal conditions reach maturity at the thirty-five-minute boundary");
            }
        });
    }

    @GameTest(template="empty",batch="timed_crop_pause",timeoutTicks=200)
    public static void lightDroughtDiseaseAndZeroRandomTicksPauseButPositiveSpeedCannotAccelerate(GameTestHelper h) {
        ready(h,f->{
            f.soils(false);f.dense(Blocks.WHEAT.defaultBlockState());f.advance(200);
            long work=f.work();
            f.weather(18000L);f.advance(200);
            h.assertValueEqual(f.work(),work,"Darkness earns no hidden growth work");
            f.weather(6000L);f.set(f.pos.below(),wet(false).setValue(FarmBlock.MOISTURE,0));f.advance(200);
            h.assertValueEqual(f.work(),work,"Dry central farmland pauses the saved clock");
            f.set(f.pos.below(),wet(false));f.set(f.pos,f.state().setValue(CubeCropBlight.BLIGHTED,true));f.advance(200);
            h.assertValueEqual(f.work(),work,"Living disease preserves previous progress without earning new work");
            f.set(f.pos,f.state().setValue(CubeCropBlight.BLIGHTED,false));f.speed(0);f.advance(200);
            h.assertValueEqual(f.work(),work,"randomTickSpeed zero also pauses the clock");
            f.speed(1000);f.advance(200);long accelerated=f.work()-work;
            h.assertValueEqual(accelerated,1_400_000_000L,"Even extreme positive randomTickSpeed earns only the ordinary two hundred ticks");
            work=f.work();f.speed(3);f.advance(200);
            h.assertValueEqual(f.work()-work,accelerated,"Ordinary and elevated positive randomTickSpeed have identical clock speed");
        });
    }

    @GameTest(template="empty",batch="timed_crop_random",timeoutTicks=200)
    public static void winningRandomTicksNeverBypassTheSavedClock(GameTestHelper h) {
        ready(h,f->{
            f.soils(true);f.row(Blocks.POTATOES.defaultBlockState());f.maintainFertility=true;
            long work=f.work();
            var winning=new LegacyRandomSource(196L) {
                @Override public int nextInt(int bound) { return 0; }
                @Override public float nextFloat() { return 1F; }
            };
            for(int update=0;update<10_000;update++)f.state().randomTick(f.level,f.pos,winning);
            h.assertValueEqual(((CropBlock)f.state().getBlock()).getAge(f.state()),0,"Ten thousand winning random draws cannot award any premature stage");
            h.assertValueEqual(f.work(),work,"Random callbacks cannot credit clock work");
            f.advance(1200);
            h.assertTrue(((CropBlock)f.state().getBlock()).getAge(f.state())<7,"A minute in the fastest farm is still far below maturity");
        });
    }

    @GameTest(template="empty",batch="timed_crop_replant",timeoutTicks=200)
    public static void harvestAndReplantResetProgressWhileOldSavedAgesMigrateWithoutReadSideEffects(GameTestHelper h) {
        ready(h,f->{
            f.soils(false);f.dense(Blocks.WHEAT.defaultBlockState());f.advance(7200);
            h.assertTrue(f.work()>0,"The original plant has genuinely earned work through clock callbacks");
            f.set(f.pos,Blocks.AIR.defaultBlockState());f.set(f.pos,Blocks.WHEAT.defaultBlockState());
            h.assertValueEqual(f.work(),0L,"Breaking and replanting the same species cannot inherit the previous plant's clock");
            h.assertValueEqual(TimedCropGrowth.remainingTicks(f.level,f.pos,f.state()),72_000L,"The replacement plant receives a full new sixty-minute clock");
            f.set(f.pos,Blocks.CARROTS.defaultBlockState());
            h.assertValueEqual(f.work(),0L,"Changing crop species also resets earned work");
            f.set(f.pos,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,3));
            f.chunk.removeData(TimedCropGrowth.CLOCKS.get());
            var old=CropInspection.inspect(f.level,f.pos,f.state());
            h.assertTrue(old.timedGrowth()&&old.growthTicks()>0,"An old saved growth stage can be inspected with a conservative inferred clock");
            h.assertFalse(f.chunk.hasData(TimedCropGrowth.CLOCKS.get()),"Looking at an old crop cannot create or migrate its persistent attachment");
        });
    }

    @GameTest(template="empty",batch="timed_crop_save",timeoutTicks=200)
    public static void savedClockSurvivesCodecReloadAndNeverCatchesUpAnUntickedGap(GameTestHelper h) {
        ready(h,f->{
            f.soils(false);f.dense(Blocks.WHEAT.defaultBlockState());f.advance(12_340);
            long work=f.work();
            var before=f.chunk.getData(TimedCropGrowth.CLOCKS.get());
            var encoded=Data.CODEC.encodeStart(NbtOps.INSTANCE,before).getOrThrow();
            var reloaded=Data.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
            h.assertValueEqual(reloaded.entries(),before.entries(),"The registered attachment codec preserves position, species and exact fractional growth work");
            f.chunk.setData(TimedCropGrowth.CLOCKS.get(),reloaded);
            f.clockData.setGameTime(f.level.getGameTime()+500_000L);
            TimedCropGrowth.tickChunk(f.level,f.chunk);
            h.assertValueEqual(f.work(),work,"Loading after a long game-time gap awards no offline or unticked growth");
            f.advance(20);
            h.assertValueEqual(f.work(),work+140_000_000L,"A reloaded crop resumes with exactly twenty newly earned eligible ticks");
            work=f.work();f.clockData.setGameTime(f.level.getGameTime()+500_000L);
            TimedCropGrowth.tickChunk(f.level,f.chunk);
            h.assertValueEqual(f.work(),work,"A later discontinuity also cannot masquerade as continuous ticking");
        });
    }

    @GameTest(template="empty",batch="timed_crop_inspect",timeoutTicks=200)
    public static void jadeReadsTheSameFractionalClockWithoutMutatingTheWorldOrRandomSequence(GameTestHelper h) {
        ready(h,f->{
            f.soils(false);f.dense(Blocks.WHEAT.defaultBlockState());f.advance(200);
            long work=f.work();var entries=f.chunk.getData(TimedCropGrowth.CLOCKS.get()).entries();var state=f.state();
            long reseed=f.level.random.nextLong();
            try {
                f.level.random.setSeed(196L);long next=f.level.random.nextLong();f.level.random.setSeed(196L);
                for(int view=0;view<32;view++) {
                    var snapshot=CropInspection.inspect(f.level,f.pos,state);
                    h.assertTrue(snapshot.timedGrowth(),"Jade identifies an actual persistent clock rather than a random-growth estimate");
                    h.assertValueEqual(snapshot.growthTicks(),71_800L,"Jade reads earned fractional work inside age zero instead of restarting from the stage");
                    h.assertValueEqual(CropInspection.load(CropInspection.save(snapshot)),snapshot,"The authoritative clock survives optional viewer serialization");
                }
                h.assertValueEqual(f.level.random.nextLong(),next,"Read-only inspection never consumes the source disease or fertilizer RNG");
                h.assertValueEqual(f.work(),work,"Inspection never credits growth work");
                h.assertValueEqual(f.chunk.getData(TimedCropGrowth.CLOCKS.get()).entries(),entries,"Inspection never modifies the saved attachment entries");
                h.assertValueEqual(f.state(),state,"Inspection never changes actual crop age or health");
            } finally {f.level.random.setSeed(reseed);}
        });
    }

    private static void ready(GameTestHelper h,Consumer<Fixture> assertions) {
        Fixture f=new Fixture(h);
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info,GameTestRunner runner) { f.close(); }
            @Override public void testFailed(GameTestInfo info,GameTestRunner runner) { f.close(); }
        });
        var engine=f.level.getChunkSource().getLightEngine();engine.checkBlock(f.pos.above());
        var lighting=engine.waitForPendingTasks(f.chunk.getPos().x,f.chunk.getPos().z);engine.tryScheduleUpdate();
        h.startSequence().thenWaitUntil(()->{
            engine.tryScheduleUpdate();for(int task=0;task<64&&f.level.getChunkSource().pollTask();task++) {}
            h.assertTrue(lighting.isDone(),"Native sky-light updates finish before timed-growth assertions");lighting.join();
            h.assertValueEqual(f.level.getBrightness(LightLayer.SKY,f.pos.above()),15,"The native clock fixture has initialized direct skylight");
        }).thenExecute(()->{f.weather(6000L);assertions.accept(f);}).thenSucceed();
    }

    private static CropBlock[] foodCrops() {
        return new CropBlock[]{(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,
                (CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()};
    }
    private static BlockState wet(boolean fertilized) {
        return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7).setValue(FoodAgriculture.FERTILIZED,fertilized);
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final LevelChunk chunk;final BlockPos pos;final ServerLevelData clockData;
        final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final Data savedClocks;final long gameTime,dayTime;final float rain,thunder;final int randomTickSpeed;
        boolean maintainFertility,closed;
        Fixture(GameTestHelper h) {
            level=h.getLevel();var reference=h.absolutePos(new BlockPos(2,2,2));
            pos=new BlockPos((reference.getX()&~15)+8,((level.getMaxBuildHeight()-56)&~15)+8,(reference.getZ()&~15)+8);
            chunk=level.getChunkAt(pos);clockData=(ServerLevelData)level.getLevelData();
            savedClocks=chunk.hasData(TimedCropGrowth.CLOCKS.get())?new Data(chunk.getData(TimedCropGrowth.CLOCKS.get()).entries()):null;
            gameTime=level.getGameTime();dayTime=level.getDayTime();rain=level.getRainLevel(1F);thunder=level.getThunderLevel(1F);
            randomTickSpeed=level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);speed(3);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(chunk.getPos().x+x,chunk.getPos().z+z);
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,2,2)))set(cell,Blocks.AIR.defaultBlockState());
            weather(6000L);
        }
        void set(BlockPos position,BlockState state) { originals.putIfAbsent(position.immutable(),level.getBlockState(position));level.setBlock(position,state,2); }
        BlockState state() { return level.getBlockState(pos); }
        long work() { return TimedCropGrowth.work(chunk,pos); }
        void soils(boolean fertile) { for(BlockPos soil:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,-1,1)))set(soil,wet(fertile)); }
        void clearPlants() { maintainFertility=false;for(BlockPos plant:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(2,0,2)))set(plant,Blocks.AIR.defaultBlockState()); }
        void dense(BlockState crop) { for(BlockPos plant:BlockPos.betweenClosed(pos.offset(-1,0,-1),pos.offset(1,0,1)))set(plant,crop); }
        void row(BlockState crop) { set(pos,crop);set(pos.east(),crop);set(pos.west(),crop); }
        void speed(int value) { level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(value,level.getServer()); }
        void weather(long time) { level.setDayTime(time);level.setRainLevel(0F);level.setThunderLevel(0F);level.updateSkyBrightness(); }
        void advance(int ticks) {
            TimedCropGrowth.tickChunk(level,chunk);
            for(int tick=0;tick<ticks;tick++) {
                clockData.setGameTime(level.getGameTime()+1L);TimedCropGrowth.tickChunk(level,chunk);
                if(maintainFertility)set(pos.below(),wet(true));
            }
        }
        @Override public void close() {
            if(closed)return;closed=true;
            originals.forEach((position,state)->level.setBlock(position,state,2));
            if(savedClocks==null)chunk.removeData(TimedCropGrowth.CLOCKS.get());else chunk.setData(TimedCropGrowth.CLOCKS.get(),savedClocks);
            clockData.setGameTime(gameTime);level.setDayTime(dayTime);level.setRainLevel(rain);level.setThunderLevel(thunder);level.updateSkyBrightness();speed(randomTickSpeed);
        }
    }
}
