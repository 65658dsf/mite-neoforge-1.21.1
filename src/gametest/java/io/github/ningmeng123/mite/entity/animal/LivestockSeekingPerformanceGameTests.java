package io.github.ningmeng123.mite.entity.animal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Candidate order, actual goal selection and query counts, without timing assertions. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class LivestockSeekingPerformanceGameTests {
    @GameTest(template="empty",batch="livestock_seeking_scan")
    public static void spiralKeepsExactColumnsAndBottomToTopOrder(GameTestHelper h) {
        BlockPos center=new BlockPos(-17,64,-33);
        List<BlockPos> expected=new ArrayList<>(),actual=new ArrayList<>();
        int[][] columns={{0,0},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
        for(int[] column:columns) for(int dy=-1;dy<=1;dy++) expected.add(center.offset(column[0],dy,column[1]));
        LivestockSeeking032.scanCandidates(center,1,1,100,pos -> true,pos -> { actual.add(pos); return null; });
        h.assertValueEqual(actual,expected,"The complete negative-coordinate spiral keeps its exact boundary and vertical order");
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_seeking_scan")
    public static void rejectedRoutesStillSpendOriginalCandidateBudget(GameTestHelper h) {
        BlockPos center=new BlockPos(0,64,0);
        List<BlockPos> attempts=new ArrayList<>();
        AtomicInteger reads=new AtomicInteger();
        BlockPos result=LivestockSeeking032.scanCandidates(center,16,0,3,pos -> { reads.incrementAndGet(); return true; },
                pos -> { attempts.add(pos); return null; });
        h.assertTrue(result==null,"Exhausting the candidate budget does not invent a selected route");
        h.assertValueEqual(attempts,List.of(center,center.east(),center.east().south()),
                "Rejected paths count toward the original source budget, in original spiral order");
        h.assertValueEqual(reads.get(),3,"The budget stops further terrain queries even when all routes fail");
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_seeking_scan")
    public static void acceptedFirstRouteSkipsUnusedTerrainQueries(GameTestHelper h) {
        BlockPos center=new BlockPos(0,64,0),first=center.below(6);
        AtomicInteger lazyReads=new AtomicInteger(),eagerReads=new AtomicInteger();
        BlockPos selected=LivestockSeeking032.scanCandidates(center,48,6,24,
                pos -> { lazyReads.incrementAndGet(); return pos.equals(first); },pos -> pos);
        List<BlockPos> eager=new ArrayList<>();
        LivestockSeeking032.scanCandidates(center,48,6,24,
                pos -> { eagerReads.incrementAndGet(); return pos.equals(first); },pos -> { eager.add(pos); return null; });
        h.assertValueEqual(selected,eager.getFirst(),"Early exit chooses the same first candidate as eager collection");
        h.assertValueEqual(lazyReads.get(),1,"A successful first route needs one source terrain query");
        h.assertValueEqual(eagerReads.get(),97*97*13,"Eager collection needs the entire unchanged source search volume");
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_seeking_scan")
    public static void missingSourcesStillSearchFullRange(GameTestHelper h) {
        AtomicInteger reads=new AtomicInteger(),attempts=new AtomicInteger();
        BlockPos result=LivestockSeeking032.scanCandidates(new BlockPos(0,64,0),48,6,24,
                pos -> { reads.incrementAndGet(); return false; },pos -> { attempts.incrementAndGet(); return pos; });
        h.assertTrue(result==null,"No source still leaves the goal eligible for its original random fallback");
        h.assertValueEqual(reads.get(),97*97*13,"Lazy scanning does not shorten the no-source search range");
        h.assertValueEqual(attempts.get(),0,"Unmatched terrain never consumes a path attempt");
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_seeking_route")
    public static void actualGoalKeepsFirstAcceptedEagerRouteAndAttempts(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        BlockPos first=f.center.below(2),second=f.center.east().below(2);
        // Flag 2 still propagates shape updates. Adjacent unsupported flowers remove each
        // other during setup, so provide real soil before comparing the two source routes.
        h.getLevel().setBlock(first.below(),Blocks.DIRT.defaultBlockState(),2);
        h.getLevel().setBlock(second.below(),Blocks.DIRT.defaultBlockState(),2);
        h.getLevel().setBlock(first,Blocks.DANDELION.defaultBlockState(),2);
        h.getLevel().setBlock(second,Blocks.DANDELION.defaultBlockState(),2);
        h.assertValueEqual(f.goal.candidates(16,2,8),List.of(first,second),
                "Both supported flowers survive setup in the intended original candidate order");
        f.navigation.rejected=first;
        Path expected=eagerPath(f.goal,f.cow,false);
        List<BlockPos> expectedAttempts=List.copyOf(f.navigation.attempts);
        f.navigation.attempts.clear();
        Path actual=f.goal.movementPath();
        h.assertTrue(expected!=null && actual!=null && actual.sameAs(expected),
                "The real goal chooses the original accepted route after the earlier source path fails: eager="
                        + describe(expected)+", lazy="+describe(actual)+", attempts="+f.navigation.attempts);
        h.assertValueEqual(expectedAttempts,List.of(first,second),"The fixture exercises one failed then one accepted source");
        h.assertValueEqual(f.navigation.attempts,expectedAttempts,"Early scanning keeps the exact path attempt sequence");
        f.cow.discard();
        h.succeed();
    }

    @GameTest(template="empty",batch="livestock_seeking_fallback")
    public static void noSourceGoalKeepsFallbackRouteAndRandomSequence(GameTestHelper h) {
        Fixture f=new Fixture(h,true);
        f.cow.getRandom().setSeed(987654321L);
        Path expected=eagerPath(f.goal,f.cow,true);
        List<BlockPos> expectedAttempts=List.copyOf(f.navigation.attempts);
        long expectedNext=f.cow.getRandom().nextLong();
        f.navigation.attempts.clear();
        f.cow.getRandom().setSeed(987654321L);
        Path actual=f.goal.movementPath();
        h.assertTrue(expected==null?actual==null:actual!=null && actual.sameAs(expected),
                "A complete unsuccessful source scan keeps the original random fallback route");
        h.assertValueEqual(f.navigation.attempts,expectedAttempts,"The fallback performs the same navigation requests");
        h.assertValueEqual(f.cow.getRandom().nextLong(),expectedNext,"Source scanning and fallback preserve the random sequence");
        f.cow.discard();
        h.succeed();
    }

    private static String describe(@Nullable Path path) {
        return path==null?"null":path.toString()+" target="+path.getTarget()+" end="+path.getEndNode();
    }

    /** The original eager goal, retained only as a behavioral comparison. */
    @Nullable private static Path eagerPath(LivestockSeeking032 goal,Cow cow,boolean water) {
        float resource=water?Livestock032.needs(cow).water():Livestock032.needs(cow).food();
        int distance=LivestockRules032.searchDistance(resource);
        for(BlockPos candidate:goal.candidates(distance,distance/8,LivestockRules032.searchCandidates(resource))) {
            Path path=SourceMovement032Goal.pathTo(cow,candidate,distance);
            if(path==null || path.getEndNode()==null) continue;
            var last=path.getEndNode();
            BlockPos end=new BlockPos(last.x,last.y,last.z);
            if(water?Livestock032.isNearWater(cow,end):Livestock032.isNearFood(cow,end)) return path;
        }
        Vec3 away=DefaultRandomPos.getPosAway(cow,16,7,cow.position());
        return away==null?null:SourceMovement032Goal.pathTo(cow,BlockPos.containing(away),32);
    }

    private static final class Fixture {
        final TestCow cow;
        final RecordingNavigation navigation;
        final LivestockSeeking032 goal;
        final BlockPos center;
        Fixture(GameTestHelper h,boolean water) {
            cow=new TestCow(h.getLevel());
            BlockPos feet=h.absolutePos(new BlockPos(2,20,2));
            cow.moveTo(feet.getX()+.5D,feet.getY(),feet.getZ()+.5D,0F,0F);
            cow.setNoAi(true);
            center=new BlockPos(cow.getBlockX(),(int)Math.floor(cow.getY()+cow.getBbHeight()*.75F),cow.getBlockZ());
            for(BlockPos pos:BlockPos.betweenClosed(center.offset(-16,-2,-16),center.offset(16,2,16)))
                h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            Livestock032.needs(cow,new LivestockRules032.Needs(.4F,.4F,1F));
            navigation=(RecordingNavigation)cow.getNavigation();
            goal=new LivestockSeeking032(cow,water);
        }
    }

    private static final class TestCow extends Cow {
        TestCow(Level level) { super(EntityType.COW,level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this,level); }
    }

    /** Control path success while exercising actual source matching and endpoint acceptance. */
    private static final class RecordingNavigation extends GroundPathNavigation {
        final List<BlockPos> attempts=new ArrayList<>();
        @Nullable BlockPos rejected;
        RecordingNavigation(Mob mob,Level level) { super(mob,level); }
        @Override public Path createPath(BlockPos target,int accuracy,int range) {
            attempts.add(target.immutable());
            if(target.equals(rejected)) return null;
            return new Path(List.of(new Node(mob.getBlockX(),mob.getBlockY(),mob.getBlockZ()),
                    new Node(target.getX(),target.getY(),target.getZ())),target,true);
        }
    }
}
