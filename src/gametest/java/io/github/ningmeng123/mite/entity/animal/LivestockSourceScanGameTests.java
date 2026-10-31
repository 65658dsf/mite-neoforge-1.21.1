package io.github.ningmeng123.mite.entity.animal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real section palettes retain source behavior and avoid per-block reads only when absence is proven. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class LivestockSourceScanGameTests {
    private static final AtomicInteger IDS=new AtomicInteger();

    @GameTest(template="empty",batch="livestock_section_food")
    public static void cowAndChickenFoodKeepFullOrderAcrossNegativeSectionBoundaries(GameTestHelper h) {
        for(boolean chicken:new boolean[]{false,true}) {
            Fixture f=new Fixture(h,chicken);
            f.set(f.center,Blocks.SHORT_GRASS.defaultBlockState());
            f.set(f.center.east(),Blocks.DANDELION.defaultBlockState());
            f.set(f.center.south(),Blocks.GRASS_BLOCK.defaultBlockState());
            f.set(f.center.north(),Blocks.FERN.defaultBlockState());
            f.set(f.center.west(),Blocks.TALL_GRASS.defaultBlockState());
            f.set(f.center.above(),Blocks.LARGE_FERN.defaultBlockState());
            List<BlockPos> expected=originalCandidates(f,false),actual=filteredCandidates(f,false);
            h.assertValueEqual(actual,expected,"All food candidates retain the original spiral, vertical order and species rules");
            h.assertTrue(actual.contains(f.center.east())!=chicken,"Only cows include dandelions");
            h.assertTrue(actual.contains(f.center.south())==chicken,"Only non-cow livestock include grass blocks");
            h.assertTrue(actual.contains(f.center.above()) && actual.contains(f.center.west()),
                    "Both large and tall vegetation remain possible sources");
            h.assertTrue(f.center.getX()<0 && f.center.getZ()<0
                            && SectionPos.asLong(f.center)!=SectionPos.asLong(f.center.east())
                            && SectionPos.asLong(f.center)!=SectionPos.asLong(f.center.above()),
                    "The fixture crosses negative horizontal and vertical section boundaries");
            f.mob.discard();
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_section_water")
    public static void waterPaletteSupersetKeepsFluidsSnowAndExactCauldronEyeChecks(GameTestHelper h) {
        for(boolean chicken:new boolean[]{false,true}) {
            Fixture f=new Fixture(h,chicken);
            BlockPos low=f.center.below(2),high=f.center.above();
            f.set(f.center,Blocks.WATER.defaultBlockState());
            f.set(f.center.east(),Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true));
            f.set(f.center.south(),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,1));
            f.set(f.center.west(),Blocks.SNOW_BLOCK.defaultBlockState());
            f.set(f.center.north(),Blocks.CAULDRON.defaultBlockState());
            f.set(low,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));
            f.set(high,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,1));
            List<BlockPos> expected=originalCandidates(f,true),actual=filteredCandidates(f,true);
            h.assertValueEqual(actual,expected,"Filtered water candidates exactly match original per-position source decisions");
            h.assertTrue(actual.contains(f.center) && actual.contains(f.center.east()),
                    "Water fluids include waterlogged blocks");
            h.assertTrue(actual.contains(f.center.south()) && actual.contains(f.center.west()),
                    "Snow layers and full snow blocks remain drinking sources");
            h.assertTrue(actual.contains(low) && !actual.contains(high) && !actual.contains(f.center.north()),
                    "Only an actual filled cauldron below the animal's eye height is accepted");
            f.mob.discard();
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_section_counts")
    public static void emptySectionNeedsOnePaletteQueryAndNoIndividualSourceReads(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        BlockPos middle=new BlockPos((f.center.getX()&~15)+8,120,(f.center.getZ()&~15)+8);
        var sources=new LivestockSourceScan(f.mob,false,pos -> originalSource(f.mob,false,pos));
        List<BlockPos> result=new ArrayList<>();
        LivestockSeeking032.scanCandidates(middle,7,7,24,sources,pos -> { result.add(pos); return null; });
        h.assertTrue(result.isEmpty(),"An actual source-free section has no candidates throughout the full scan volume");
        h.assertValueEqual(sources.paletteQueries(),1,"All 3375 positions share one conservative section palette proof");
        h.assertValueEqual(sources.sourceQueries(),0,"A proven source-free section requires no individual block source reads");
        f.mob.discard();
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_section_hook")
    public static void failedNavigationHookCanAddFoodToAPreviouslyAbsentSection(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        BlockPos first=f.center.east(),added=f.center.north();
        f.set(first.below(),Blocks.DIRT.defaultBlockState());
        f.set(added.below(),Blocks.DIRT.defaultBlockState());
        f.set(first,Blocks.DANDELION.defaultBlockState());
        h.assertFalse(f.level.getChunkAt(added).getSection(f.level.getSectionIndex(added.getY()))
                        .maybeHas(state -> Livestock032.foodBlock(f.mob,state)),
                "Before navigation, the origin section has a real negative palette proof");
        var navigation=(RecordingNavigation)f.mob.getNavigation();
        navigation.firstAttempt=() -> f.set(added,Blocks.SHORT_GRASS.defaultBlockState());
        Livestock032.needs(f.mob,new LivestockRules032.Needs(.4F,1F,1F));
        Path path=new LivestockSeeking032(f.mob,false).movementPath();
        h.assertTrue(path!=null && path.getTarget().equals(added),
                "The actual goal observes food introduced by a synchronous failed-route hook");
        h.assertValueEqual(navigation.attempts,List.of(first,added),
                "Clearing section proofs after navigation keeps the new source's original scan order");
        f.mob.discard();
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_section_lifecycle")
    public static void newCandidateSearchSeesFoodPlacedAfterAnEarlierNegativeScan(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        var goal=new LivestockSeeking032(f.mob,false);
        h.assertTrue(goal.candidates(2,2,24).isEmpty(),"The initial full candidate scan has no food");
        f.set(f.center.below(),Blocks.DIRT.defaultBlockState());
        f.set(f.center,Blocks.DANDELION.defaultBlockState());
        h.assertValueEqual(goal.candidates(2,2,24),List.of(f.center),
                "The next search creates fresh palette proofs and immediately sees newly placed food");
        f.mob.discard();
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_section_missing")
    public static void missingChunksAndBuildBoundariesNeverLoadTerrain(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        BlockPos missing=f.center.offset(100_000,0,100_000);
        int x=SectionPos.blockToSectionCoord(missing.getX()),z=SectionPos.blockToSectionCoord(missing.getZ());
        h.assertTrue(f.level.getChunkSource().getChunkNow(x,z)==null,"The remote chunk starts absent");
        var sources=new LivestockSourceScan(f.mob,false,pos -> originalSource(f.mob,false,pos));
        for(int repeat=0;repeat<32;repeat++) h.assertFalse(sources.test(missing),"Missing terrain cannot contain a loaded food source");
        h.assertFalse(sources.test(new BlockPos(missing.getX(),f.level.getMinBuildHeight()-1,missing.getZ())),
                "Below-world terrain cannot become a source");
        h.assertFalse(sources.test(new BlockPos(missing.getX(),f.level.getMaxBuildHeight(),missing.getZ())),
                "Above-world terrain cannot become a source");
        h.assertValueEqual(sources.paletteQueries(),0,"No palette is queried by loading missing or out-of-bounds terrain");
        h.assertTrue(f.level.getChunkSource().getChunkNow(x,z)==null,"The entire source scan leaves the remote chunk unloaded");
        f.mob.discard();
        h.succeed();
    }

    private static List<BlockPos> originalCandidates(Fixture f,boolean water) {
        List<BlockPos> result=new ArrayList<>();
        LivestockSeeking032.scanCandidates(f.center,2,2,64,pos -> originalSource(f.mob,water,pos),
                pos -> { result.add(pos); return null; });
        return result;
    }

    private static List<BlockPos> filteredCandidates(Fixture f,boolean water) {
        List<BlockPos> result=new ArrayList<>();
        var sources=new LivestockSourceScan(f.mob,water,pos -> originalSource(f.mob,water,pos));
        LivestockSeeking032.scanCandidates(f.center,2,2,64,sources,pos -> { result.add(pos); return null; });
        return result;
    }

    private static boolean originalSource(PathfinderMob mob,boolean water,BlockPos pos) {
        if(pos.getY()<mob.level().getMinBuildHeight() || pos.getY()>=mob.level().getMaxBuildHeight()
                || !mob.level().hasChunkAt(pos)) return false;
        return water?Livestock032.waterBlock(mob,pos):Livestock032.foodBlock(mob,mob.level().getBlockState(pos));
    }

    private static final class Fixture {
        final ServerLevel level;
        final PathfinderMob mob;
        final BlockPos center;
        Fixture(GameTestHelper h,boolean chicken) {
            level=h.getLevel();
            center=new BlockPos(-3_400_001+IDS.getAndIncrement()*64,111,-3_400_001);
            for(int x=SectionPos.blockToSectionCoord(center.getX()-2);x<=SectionPos.blockToSectionCoord(center.getX()+2);x++)
                for(int z=SectionPos.blockToSectionCoord(center.getZ()-2);z<=SectionPos.blockToSectionCoord(center.getZ()+2);z++) {
                    var chunk=level.getChunk(x,z);
                    // Re-running this suite reuses its remote test chunks. Setting old food to
                    // air leaves unused food entries in a palette, which is a valid maybeHas
                    // false positive. Reset these isolated test sections to fresh all-air
                    // palettes so the strict negative-proof preconditions remain reproducible.
                    for(int sectionY=SectionPos.blockToSectionCoord(center.getY()-2);
                            sectionY<=SectionPos.blockToSectionCoord(center.getY()+2);sectionY++) {
                        int index=chunk.getSectionIndex(sectionY*16);
                        var previous=chunk.getSection(index);
                        chunk.getSections()[index]=new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                                Blocks.AIR.defaultBlockState(),PalettedContainer.Strategy.SECTION_STATES),previous.getBiomes());
                    }
                    chunk.setUnsaved(true);
                }
            mob=chicken?EntityType.CHICKEN.create(level):new TestCow(level);
            mob.moveTo(center.getX()+.5D,center.getY()-1D,center.getZ()+.5D,0F,0F);
            mob.setNoAi(true);
        }
        void set(BlockPos pos,BlockState state) {
            // Keep intended test states, including upper vegetation, without neighbour-shape side effects.
            level.setBlock(pos,state,18);
        }
    }

    private static final class TestCow extends Cow {
        TestCow(Level level) { super(EntityType.COW,level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this,level); }
    }

    private static final class RecordingNavigation extends GroundPathNavigation {
        final List<BlockPos> attempts=new ArrayList<>();
        @Nullable Runnable firstAttempt;
        RecordingNavigation(Mob mob,Level level) { super(mob,level); }
        @Override public Path createPath(BlockPos target,int accuracy,int range) {
            attempts.add(target.immutable());
            if(firstAttempt!=null) {
                Runnable hook=firstAttempt;
                firstAttempt=null;
                hook.run();
                return null;
            }
            return new Path(List.of(new Node(mob.getBlockX(),mob.getBlockY(),mob.getBlockZ()),
                    new Node(target.getX(),target.getY(),target.getZ())),target,true);
        }
    }
}
