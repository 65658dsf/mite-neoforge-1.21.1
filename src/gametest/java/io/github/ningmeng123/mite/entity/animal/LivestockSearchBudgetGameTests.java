package io.github.ningmeng123.mite.entity.animal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bounded live source searching, scheduler yielding and original resource semantics. @author NingMeng123 */
@GameTestHolder("mite_ai_budget")
@PrefixGameTestTemplate(false)
public final class LivestockSearchBudgetGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_livestock_budget_test", Integer::compareTo);

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_queries", timeoutTicks = 240)
    public static void competingFoodAndWaterSessionsShareFourQueriesAndOneQueryPerAnimalPerTick(GameTestHelper h) {
        Fixture f = new Fixture(h, 8);
        f.food(f.origin.east(3)); f.water(f.origin.offset(3, 0, 1));
        f.ready(() -> {
            List<LivestockSeeking032> goals = new ArrayList<>();
            for (var cow : f.cows) {
                goals.add(new LivestockSeeking032(cow, false));
                goals.add(new LivestockSeeking032(cow, true));
            }
            goals.forEach(Goal::start);
            h.startSequence().thenWaitUntil(() -> {
                goals.forEach(Goal::tick);
                for (int repeat = 0; repeat < 8; repeat++) LivestockSearchBudget.pump(h.getLevel().getServer());
                var stats = LivestockSearchBudget.stats(h.getLevel().getServer());
                h.assertTrue(stats.tickNativeQueries() <= 4, "Repeated pumping shares the four-query global tick quota");
                h.assertTrue(stats.tickScanCells() <= 32768, "Repeated pumping shares the 32768-cell global tick quota");
                for (TestCow cow : f.cows) h.assertTrue(cow.recording().tickAttempts(h.getLevel().getServer().getTickCount()) <= 1,
                        "Food and water goals for the same live animal share one native createPath admission per tick");
                h.assertTrue(f.cows.stream().allMatch(cow -> !cow.recording().attempts.isEmpty()),
                        "All later animals eventually receive native navigation work rather than being starved by earlier cows");
            }).thenExecute(() -> {
                h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).nativeQueries() >= f.cows.size(),
                        "Counters record actual navigation entry calls across multiple server ticks");
                goals.forEach(Goal::stop); f.close();
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_far_source", timeoutTicks = 300)
    public static void aSourceBeyondTheFirstSliceIsEventuallyFoundWithoutRestartingTheSpiral(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); BlockPos resource = f.origin.east(30); f.food(resource);
        f.ready(() -> {
            TestCow cow = f.cows.getFirst();
            Livestock032.needs(cow, new LivestockRules032.Needs(.01F, 1F, 1F));
            var goal = new LivestockSeeking032(cow, false); goal.start();
            boolean[] running = {true};
            long beganCells = LivestockSearchBudget.stats(h.getLevel().getServer()).scanCells();
            h.startSequence().thenWaitUntil(() -> {
                if (running[0]) {
                    goal.tick();
                    if (!goal.canContinueToUse()) { goal.stop(); running[0] = false; }
                }
                if (!running[0] && goal.canUse()) { goal.start(); running[0] = true; }
                LivestockSearchBudget.pump(h.getLevel().getServer());
                h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).tickScanCells() <= 2048,
                        "One animal advances by at most one 2048-cell spiral slice in a server tick");
                h.assertTrue(!cow.getNavigation().isDone()
                                && cow.recording().attempts.stream().anyMatch(attempt -> attempt.target().equals(resource)),
                        "The real later spiral source is eventually submitted to native navigation");
            }).thenExecute(() -> {
                h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).scanCells() - beganCells > 2048,
                        "The fixture demonstrably traversed more than its first work slice");
                goal.stop(); f.close();
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_move_yield", timeoutTicks = 360)
    public static void aLongSearchReleasesMoveToAnotherGoalAndLaterResumesItsFarProgress(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); BlockPos resource = f.origin.east(40); f.food(resource);
        f.ready(() -> {
            TestCow cow = f.cows.getFirst();
            Livestock032.needs(cow, new LivestockRules032.Needs(.01F, 1F, 1F));
            cow.goalSelector.removeAllGoals(goal -> true);
            var seek = new LivestockSeeking032(cow, false); var wander = new WaitingMove();
            cow.goalSelector.addGoal(2, seek); cow.goalSelector.addGoal(9, wander);
            cow.getRandom().setSeed(0L);
            int began = h.getLevel().getServer().getTickCount();
            h.startSequence().thenWaitUntil(() -> {
                cow.goalSelector.tick(); LivestockSearchBudget.pump(h.getLevel().getServer());
                h.assertTrue(wander.starts > 0, "A pending long resource scan releases MOVE to the ordinary lower-priority goal");
                h.assertTrue(h.getLevel().getServer().getTickCount() - began <= 26,
                        "The twenty-tick pending limit yields with at most one ordinary three-tick selector scheduling delay");
            }).thenWaitUntil(() -> {
                cow.goalSelector.tick(); LivestockSearchBudget.pump(h.getLevel().getServer());
                h.assertTrue(!cow.getNavigation().isDone()
                                && cow.recording().attempts.stream().anyMatch(attempt -> attempt.target().equals(resource)),
                        "Yielding MOVE preserves scan progress: the source beyond twenty full slices is eventually found");
            }).thenExecute(() -> { seek.stop(); f.close(); }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_nearby", timeoutTicks = 240)
    public static void newlyNearbyFoodBypassesFailedSearchBackoffWithoutAnotherNativeQuery(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.ready(() -> {
            TestCow cow = f.cows.getFirst(); cow.recording().alwaysNull = true;
            Livestock032.needs(cow, new LivestockRules032.Needs(.4F, 1F, 1F));
            var goal = new LivestockSeeking032(cow, false); goal.start();
            h.startSequence().thenWaitUntil(() -> {
                goal.tick(); LivestockSearchBudget.pump(h.getLevel().getServer());
                if (!goal.canContinueToUse()) { goal.stop(); goal.canUse(); }
                h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).backoff() > 0,
                        "A genuinely exhausted source search enters failed-search backoff");
            }).thenExecute(() -> {
                int queries = cow.recording().attempts.size();
                f.food(f.origin.east());
                h.assertTrue(goal.canUse(), "Actually nearby food is authoritative even during failed-search backoff");
                goal.start();
                h.assertTrue(goal.canContinueToUse() && cow.getNavigation().isDone(), "The original nearby-source behavior waits to replenish food");
                h.assertValueEqual(cow.recording().attempts.size(), queries, "Nearby replenishment never spends a new path query");
                goal.stop(); f.close();
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_water", timeoutTicks = 260)
    public static void dryAndSwimmingSearchSlicesRestoreTheExactOriginalWaterMalus(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); f.food(f.origin.east(3));
        f.ready(() -> {
            TestCow cow = f.cows.getFirst(); cow.setPathfindingMalus(PathType.WATER, 8F); cow.recording().onlySwimming = true;
            Livestock032.needs(cow, new LivestockRules032.Needs(.4F, 1F, 1F));
            var goal = new LivestockSeeking032(cow, false); goal.start();
            boolean[] running = {true};
            h.startSequence().thenWaitUntil(() -> {
                if (running[0]) {
                    goal.tick();
                    if (!goal.canContinueToUse()) { goal.stop(); running[0] = false; }
                }
                if (!running[0] && goal.canUse()) { goal.start(); running[0] = true; }
                LivestockSearchBudget.pump(h.getLevel().getServer());
                h.assertTrue(!cow.getNavigation().isDone()
                                && cow.recording().attempts.stream().anyMatch(attempt -> attempt.waterMalus() == 0F),
                        "A genuinely rejected dry phase eventually attempts the original swimming fallback");
            }).thenExecute(() -> {
                h.assertTrue(cow.recording().attempts.stream().anyMatch(attempt -> attempt.waterMalus() == 8F),
                        "The first native query used the exact original dry malus");
                h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 0F,
                        "The consumed wet route retains its temporary swimming allowance while it is installed");
                goal.stop();
                h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 8F, "Stopping restores the original malus after all delayed phases");
                f.close();
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_disabled", timeoutTicks = 160)
    public static void disabledModeKeepsImmediateLegacySourceSearchAndNeverQueues(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); f.food(f.origin.east(3));
        f.ready(() -> {
            System.setProperty("mite.animalSearchBudget", "false");
            TestCow cow = f.cows.getFirst();
            var goal = new LivestockSeeking032(cow, false); goal.start();
            h.assertTrue(!cow.recording().attempts.isEmpty() && !cow.getNavigation().isDone(),
                    "The old mode submits and installs the original source route directly in start");
            h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).scanCells() == 0
                            && LivestockSearchBudget.stats(h.getLevel().getServer()).queued() == 0,
                    "The legacy path does not create a sliced search session");
            goal.stop(); f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_toggle", timeoutTicks = 180)
    public static void turningOptimizationOffMidSearchLeavesNoWaitingSessionOrTemporaryWaterCost(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); f.food(f.origin.east(20));
        f.ready(() -> {
            TestCow cow = f.cows.getFirst(); cow.setPathfindingMalus(PathType.WATER, 8F);
            Livestock032.needs(cow, new LivestockRules032.Needs(.01F, 1F, 1F));
            var goal = new LivestockSeeking032(cow, false); goal.start(); LivestockSearchBudget.pump(h.getLevel().getServer());
            h.assertTrue(LivestockSearchBudget.stats(h.getLevel().getServer()).queued() > 0,
                    "A real bounded scan is queued before changing the live setting");
            System.setProperty("mite.animalSearchBudget", "false");
            goal.tick(); goal.stop(); LivestockSearchBudget.pump(h.getLevel().getServer());
            h.assertValueEqual(LivestockSearchBudget.stats(h.getLevel().getServer()).queued(), 0,
                    "Disabling and stopping releases every queued session");
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 8F,
                    "Disabling midway never leaves a temporary swimming malus installed");
            goal.start();
            h.assertTrue(!cow.recording().attempts.isEmpty(), "A later start executes the original synchronous algorithm");
            goal.stop(); f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_soft_time", timeoutTicks = 180)
    public static void anAtomicSlowNavigationHookStopsOtherQueriesAfterTheTwoMillisecondAllowance(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.food(f.origin.east(3));
        f.ready(() -> {
            List<LivestockSeeking032> goals = new ArrayList<>();
            for (TestCow cow : f.cows) {
                cow.recording().delayNanos = 3_000_000L;
                var goal = new LivestockSeeking032(cow, false); goals.add(goal); goal.start();
            }
            h.startSequence().thenWaitUntil(() -> {
                goals.forEach(Goal::tick);
                for (int repeat = 0; repeat < 4; repeat++) LivestockSearchBudget.pump(h.getLevel().getServer());
                var stats = LivestockSearchBudget.stats(h.getLevel().getServer());
                h.assertTrue(stats.tickNativeQueries() <= 1,
                        "A single three-millisecond hook may overrun the soft allowance but no second query starts in that tick");
                h.assertTrue(stats.nativeQueries() > 0, "Measure a real delayed navigation callback rather than an empty scan queue");
            }).thenExecute(() -> { goals.forEach(Goal::stop); f.close(); }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "livestock_budget_changed_water", timeoutTicks = 180)
    public static void resumingAPausedSearchAdoptsANewPermanentWaterPolicy(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.ready(() -> {
            TestCow cow = f.cows.getFirst(); cow.setPathfindingMalus(PathType.WATER, 8F);
            Livestock032.needs(cow, new LivestockRules032.Needs(.01F, 1F, 1F));
            var goal = new LivestockSeeking032(cow, false); goal.start();
            LivestockSearchBudget.pump(h.getLevel().getServer()); goal.stop();
            cow.setPathfindingMalus(PathType.WATER, 4F);
            // A fresh episode must read the real policy before restoring a previous temporary cost.
            goal.start();
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 4F,
                    "Restarting a paused session cannot overwrite the new permanent policy with its old captured value");
            goal.stop();
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 4F, "The replacement episode restores its own newly captured cost");
            goal.start(); cow.setPathfindingMalus(PathType.WATER, 3F); f.food(f.origin.east());
            h.assertTrue(goal.canContinueToUse(), "A newly nearby source keeps the real resource goal running");
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 3F,
                    "The nearby-source continuation branch adopts a changed policy before restoring water cost");
            goal.stop(); f.set(f.origin.east(), Blocks.AIR.defaultBlockState());
            goal.start(); goal.stop(); cow.setPathfindingMalus(PathType.WATER, 2F);
            System.setProperty("mite.animalSearchBudget", "false"); goal.start(); goal.stop();
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 2F,
                    "Switching a paused search to legacy mode cannot resurrect the old captured policy");
            f.close(); h.succeed();
        });
    }

    private static final class WaitingMove extends Goal {
        int starts;
        WaitingMove() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return true; }
        @Override public boolean canContinueToUse() { return true; }
        @Override public void start() { starts++; }
    }

    private record Attempt(BlockPos target, int tick, float waterMalus) {}

    private static final class TestCow extends Cow {
        TestCow(Level level) { super(EntityType.COW, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
        RecordingNavigation recording() { return (RecordingNavigation)getNavigation(); }
    }

    private static final class RecordingNavigation extends GroundPathNavigation {
        final List<Attempt> attempts = new ArrayList<>();
        boolean alwaysNull, onlySwimming;
        long delayNanos;
        RecordingNavigation(Mob mob, Level level) { super(mob, level); }
        int tickAttempts(int tick) { return (int)attempts.stream().filter(attempt -> attempt.tick() == tick).count(); }
        @Nullable @Override public Path createPath(BlockPos target, int accuracy, int range) {
            float water = mob.getPathfindingMalus(PathType.WATER);
            attempts.add(new Attempt(target.immutable(), mob.getServer().getTickCount(), water));
            if (delayNanos > 0L) {
                long until = System.nanoTime() + delayNanos;
                while (System.nanoTime() < until) LockSupport.parkNanos(Math.min(100_000L, until - System.nanoTime()));
            }
            if (alwaysNull || onlySwimming && water > 0F) return null;
            return new Path(List.of(new Node(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()),
                    new Node(target.getX(), target.getY(), target.getZ())), target, true);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int id = IDS.getAndIncrement();
        final BlockPos origin = new BlockPos(8_300_000 + id * 256, 112, 8_300_000);
        final List<TestCow> cows = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final String previous = System.getProperty("mite.animalSearchBudget");
        boolean closed;
        Fixture(GameTestHelper h, int count) {
            this.h = h;
            System.setProperty("mite.animalSearchBudget", "true"); LivestockSearchBudget.clear(h.getLevel().getServer());
            ChunkPos low = new ChunkPos(origin.offset(-48, 0, -48)), high = new ChunkPos(origin.offset(48, 0, 48));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos pos = new ChunkPos(x, z); chunks.add(pos);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, pos, 0, id);
                var chunk = h.getLevel().getChunk(x, z);
                for (int sectionY = SectionPos.blockToSectionCoord(origin.getY() - 7); sectionY <= SectionPos.blockToSectionCoord(origin.getY() + 7); sectionY++) {
                    int sectionIndex = chunk.getSectionIndex(sectionY * 16); var previousSection = chunk.getSection(sectionIndex);
                    chunk.getSections()[sectionIndex] = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                            Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES), previousSection.getBiomes());
                }
                chunk.setUnsaved(true);
            }
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) set(origin.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
            for (int i = 0; i < count; i++) {
                TestCow cow = new TestCow(h.getLevel()); cow.moveTo(Vec3.atBottomCenterOf(origin));
                cow.setNoAi(true); cow.setNoGravity(true); cow.setOnGround(true);
                cow.goalSelector.removeAllGoals(goal -> true); cow.targetSelector.removeAllGoals(goal -> true);
                Livestock032.needs(cow, new LivestockRules032.Needs(.4F, .4F, 1F)); cows.add(cow);
            }
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }
        void ready(Runnable action) {
            h.startSequence().thenWaitUntil(() -> h.assertTrue(chunks.stream().allMatch(pos -> h.getLevel().getChunkSource().getChunkNow(pos.x, pos.z) != null),
                    "All real source-scan chunk palettes are published before beginning the bounded scan"))
                    .thenExecute(() -> { LivestockSearchBudget.clear(h.getLevel().getServer()); action.run(); });
        }
        void set(BlockPos pos, BlockState state) { h.getLevel().setBlock(pos, state, 18); }
        void food(BlockPos pos) { set(pos, Blocks.DANDELION.defaultBlockState()); }
        void water(BlockPos pos) { set(pos, Blocks.WATER.defaultBlockState()); }
        @Override public void close() {
            if (closed) return; closed = true;
            for (TestCow cow : cows) { cow.goalSelector.removeAllGoals(goal -> true); cow.getNavigation().stop(); cow.discard(); }
            LivestockSearchBudget.clear(h.getLevel().getServer());
            for (ChunkPos pos : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, pos, 0, id);
            if (previous == null) System.clearProperty("mite.animalSearchBudget"); else System.setProperty("mite.animalSearchBudget", previous);
        }
    }
}
