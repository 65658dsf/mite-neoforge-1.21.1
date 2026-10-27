package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Real failed native queries, shared reuse, staggered retries and recovery through a newly opened passage. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class MeleePathPerformanceGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_path_performance_test", Integer::compareTo);

    @GameTest(template = "empty", batch = "melee_path_performance_shared", timeoutTicks = 400)
    public static void failedQueriesAreSharedAcrossFreshDiggingAndMeleeRequests(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        f.whenReady(() -> {
            var first = new R196DiggerGoal(f.mob).pursuitPlan();
            h.assertFalse(first.completeWalkingRoute() || first.choosesBreaking(),
                    "The sealed bedrock divider has neither a real walking route nor a mining shortcut");
            var initial = MeleePursuitPaths.searchStats(f.mob);
            h.assertTrue(initial.searches() > 0 && initial.searches() <= 2,
                    "One failed pursuit performs native queries without redundantly repeating the unreachable exact target");
            for (int request = 0; request < 30; ++request) {
                Path path = MeleePursuitPaths.find(f.mob, f.player, null);
                h.assertFalse(MeleePursuitPaths.reaches(f.mob, path, f.player), "A cached partial path remains incomplete");
                h.assertFalse(new R196DiggerGoal(f.mob).pursuitPlan().completeWalkingRoute(),
                        "A fresh goal also sees the same physical obstruction");
                new SourceMeleeGoal(f.mob, Player.class, 1D, false).canUse();
            }
            var reused = MeleePursuitPaths.searchStats(f.mob);
            h.assertTrue(reused.searches() == initial.searches() && reused.cacheHits() >= initial.cacheHits() + 30,
                    "Independent pursuit entrypoints reuse the failed search instead of issuing more native queries");
            f.close();
            h.succeed();
        });
    }

    // The overall test also waits for remote chunks; route retry assertions use their own unchanged world-clock deadlines.
    @GameTest(template = "empty", batch = "melee_path_performance_backoff", timeoutTicks = 640)
    public static void stationaryFailureRetriesAtItsDeadlineAndBacksOffWithinTheBound(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        long[] searches = {0}, deadline = {0};
        int[] attempts = {1};
        f.whenReady(() -> {
            f.find();
            var initial = MeleePursuitPaths.searchStats(f.mob);
            searches[0] = initial.searches();
            deadline[0] = initial.retryAt();
            long initialDelay = initial.retryAt() - h.getLevel().getGameTime();
            h.assertTrue(initialDelay >= 20 && initialDelay < 20 + MeleePursuitPaths.RETRY_STAGGER_TICKS,
                    "The first genuine failure has a short, staggered retry deadline");
        }).thenWaitUntil(() -> {
            long now = h.getLevel().getGameTime();
            Path path = f.find();
            h.assertFalse(MeleePursuitPaths.reaches(f.mob, path, f.player), "The unchanged bedrock room remains unreachable");
            var current = MeleePursuitPaths.searchStats(f.mob);
            if (now < deadline[0]) {
                h.assertTrue(current.searches() == searches[0], "No native query runs before the failed route's retry deadline");
            } else {
                h.assertTrue(current.searches() > searches[0], "A genuine failed route retries when its deadline arrives");
                ++attempts[0];
                int base = attempts[0] == 2 ? 40 : MeleePursuitPaths.MAX_FAILURE_RETRY_TICKS;
                long delay = current.retryAt() - now;
                h.assertTrue(delay >= base && delay < base + MeleePursuitPaths.RETRY_STAGGER_TICKS,
                        "Repeated failures back off without exceeding the documented maximum plus staggering");
                searches[0] = current.searches();
                deadline[0] = current.retryAt();
            }
            h.assertTrue(attempts[0] >= 4, "Waiting for four genuine failed searches on the advancing world clock; attempts="
                    + attempts[0] + ", gameTime=" + now + ", retryAt=" + deadline[0] + ", stats=" + current);
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template = "empty", batch = "melee_path_performance_target", timeoutTicks = 400)
    public static void movingOrReplacingTheTargetAndMovingTheMobInvalidateFailure(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        f.whenReady(() -> {
            f.find();
            long searches = MeleePursuitPaths.searchStats(f.mob).searches();
            f.player.moveTo(Vec3.atBottomCenterOf(f.origin.offset(10, 0, 0)));
            f.find();
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                    "Moving the target to another block invalidates the shared failed result immediately");
            searches = MeleePursuitPaths.searchStats(f.mob).searches();
            Player replacement = h.makeMockPlayer(GameType.SURVIVAL);
            f.players.add(replacement);
            replacement.moveTo(f.player.position());
            f.mob.setTarget(replacement);
            f.player = replacement;
            f.find();
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                    "Another player at the same coordinates does not inherit the previous target's failed cache");
            searches = MeleePursuitPaths.searchStats(f.mob).searches();
            f.mob.moveTo(Vec3.atBottomCenterOf(f.origin.offset(0, 0, 2)));
            f.find();
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                    "Moving the pursuer to another approach also invalidates the failed result");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "melee_path_performance_invalidate", timeoutTicks = 400)
    public static void explicitInvalidationPreservesStatisticsAndUsesTheNewOpeningImmediately(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        f.whenReady(() -> {
            h.assertFalse(MeleePursuitPaths.reaches(f.mob, f.find(), f.player), "The closed divider first produces a real failed route");
            long searches = MeleePursuitPaths.searchStats(f.mob).searches();
            f.openPassage();
            MeleePursuitPaths.invalidate(f.mob);
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() == searches,
                    "Explicit cache invalidation retains the cumulative native-query measurement");
            Path path = f.find();
            f.assertOpenRoute(path);
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                    "An explicitly invalidated failure retries in the same tick and finds the physical opening");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "melee_path_performance_recovery", timeoutTicks = 400)
    public static void newlyOpenedPassageBecomesWalkableWithinTheFailureRetryBound(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        long[] searches = {0}, openedAt = {0};
        f.whenReady(() -> {
            h.assertFalse(MeleePursuitPaths.reaches(f.mob, f.find(), f.player), "The unbroken divider initially blocks pursuit");
            searches[0] = MeleePursuitPaths.searchStats(f.mob).searches();
            openedAt[0] = h.getLevel().getGameTime();
            f.openPassage();
        }).thenWaitUntil(() -> {
            Path path = f.find();
            long elapsed = h.getLevel().getGameTime() - openedAt[0];
            h.assertTrue(elapsed <= MeleePursuitPaths.MAX_FAILURE_RETRY_TICKS + MeleePursuitPaths.RETRY_STAGGER_TICKS,
                    "A changed world cannot leave a failed pursuit cached beyond its bounded retry window; " + f.diagnostic(path));
            h.assertTrue(MeleePursuitPaths.reaches(f.mob, path, f.player),
                    "Waiting for a genuine query to discover the opening; elapsed=" + elapsed + ", stats=" + MeleePursuitPaths.searchStats(f.mob));
            f.assertOpenRoute(path);
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches[0],
                    "A later genuine native query discovers the new passage without an explicit invalidation call");
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template = "empty", batch = "melee_path_performance_stagger", timeoutTicks = 400)
    public static void simultaneousUnreachableMobsReuseFailuresAndReceiveDifferentRetryTicks(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.closeWithTest();
        f.whenReady(() -> {
            Set<Long> deadlines = new HashSet<>();
            for (int index = 0; index < MeleePursuitPaths.RETRY_STAGGER_TICKS; ++index) {
                Zombie mob = index == 0 ? f.mob : f.spawn();
                mob.setTarget(f.player);
                Path path = MeleePursuitPaths.find(mob, f.player, null);
                h.assertFalse(MeleePursuitPaths.reaches(mob, path, f.player), "Each monster faces the same real unreachable target");
                var initial = MeleePursuitPaths.searchStats(mob);
                h.assertTrue(initial.searches() > 0 && initial.searches() <= 2, "Each monster makes at most two real initial native queries");
                deadlines.add(initial.retryAt());
                for (int repeated = 0; repeated < 20; ++repeated) MeleePursuitPaths.find(mob, f.player, null);
                var reused = MeleePursuitPaths.searchStats(mob);
                h.assertTrue(reused.searches() == initial.searches() && reused.cacheHits() >= initial.cacheHits() + 20,
                        "A crowd's repeated requests do not multiply native failed queries within the same tick");
            }
            h.assertTrue(deadlines.size() > 1, "Simultaneous failures are distributed across different retry ticks");
            f.close();
            h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final Zombie mob;
        final List<Zombie> mobs = new ArrayList<>();
        final List<Player> players = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final int ticketKey;
        Player player;
        boolean closed;

        Fixture(GameTestHelper h) {
            helper = h;
            ticketKey = IDS.getAndIncrement();
            origin = new BlockPos(520_000 + ticketKey * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 520_000);
            for (int x = -4; x <= 14; ++x) for (int z = -12; z <= 12; ++z) for (int y = -1; y <= 3; ++y) {
                boolean boundary = y == -1 || y == 3 || x == -4 || x == 14 || z == -12 || z == 12 || x == 3;
                h.getLevel().setBlock(origin.offset(x, y, z), (boundary ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            mob = spawn();
            player = h.makeMockPlayer(GameType.SURVIVAL);
            players.add(player);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            mob.setTarget(player);
            ChunkPos low = new ChunkPos(origin.offset(-4, 0, -12)), high = new ChunkPos(origin.offset(14, 0, 12));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            }
        }

        Zombie spawn() {
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "The real native zombie can be created");
            zombie.moveTo(Vec3.atBottomCenterOf(origin));
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setOnGround(true);
            zombie.setPersistenceRequired();
            zombie.setCanPickUpLoot(false);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            // Native pathfinding needs the real species and goals, but no ticking/indexed entity.
            // Keep global test batches and their entity cleanup from discarding this query fixture.
            R196NpcNeeds.join(new EntityJoinLevelEvent(zombie, helper.getLevel(), false));
            R196MonsterAi.join(new EntityJoinLevelEvent(zombie, helper.getLevel(), false));
            mobs.add(zombie);
            return zombie;
        }

        Path find() { return MeleePursuitPaths.find(mob, player, null); }

        void openPassage() {
            helper.getLevel().setBlock(origin.east(3), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(origin.east(3).above(), Blocks.AIR.defaultBlockState(), 2);
        }

        void assertOpenRoute(Path path) {
            helper.assertTrue(MeleePursuitPaths.reaches(mob, path, player),
                    "The recovered native path ends at a physically valid melee position; " + diagnostic(path));
            boolean crossesOpening = false;
            for (int index = 0; index < path.getNodeCount(); ++index) {
                BlockPos point = path.getNode(index).asBlockPos();
                crossesOpening |= point.equals(origin.east(3));
                helper.assertFalse(helper.getLevel().getBlockState(point).is(Blocks.BEDROCK), "The recovered route never passes through the divider");
            }
            helper.assertTrue(crossesOpening, "Recovery walks through the actual newly opened passage");
        }

        String diagnostic(Path path) {
            var level = helper.getLevel();
            StringBuilder info = new StringBuilder("mob=").append(mob.position())
                    .append(", size=").append(mob.getBbWidth()).append('x').append(mob.getBbHeight())
                    .append(", eye=").append(mob.getEyeY()).append(", onGround=").append(mob.onGround())
                    .append(", alive=").append(mob.isAlive()).append(", removed=").append(mob.isRemoved())
                    .append(", bodyFits=").append(level.noCollision(mob, mob.getBoundingBox().deflate(.001D)))
                    .append(", target=").append(player.position()).append(", targetEye=").append(player.getEyeY())
                    .append(", loaded=").append(level.hasChunkAt(mob.blockPosition())).append('/')
                    .append(level.hasChunkAt(player.blockPosition())).append(", gameTime=").append(level.getGameTime())
                    .append(", stats=").append(MeleePursuitPaths.searchStats(mob));
            for (int height = -1; height <= 2; ++height) {
                BlockPos pos = origin.east(3).above(height);
                info.append(", opening[").append(height).append("]=").append(level.getBlockState(pos))
                        .append(" rawType=").append(PathTypeProbe.raw(level, pos))
                        .append(" cachedType=").append(level.getPathTypeCache().getOrCompute(level, pos));
            }
            if (path == null) return info.append(", path=null").toString();
            info.append(", pathReach=").append(path.canReach()).append(", pathTarget=").append(path.getTarget())
                    .append(", pathNodes=").append(path.getNodeCount());
            if (path.getEndNode() != null) {
                Vec3 end = MeleePursuitPaths.standingPoint(mob, path, path.getNodeCount() - 1);
                info.append(", endpoint=").append(end).append(", endpointCanStrike=").append(SourceMeleeGoal.canStrikeFrom(mob, player, end));
            }
            for (int index = 0; index < Math.min(24, path.getNodeCount()); ++index) {
                var node = path.getNode(index);
                info.append(" node[").append(index).append("]=").append(node.asBlockPos().subtract(origin)).append('/').append(node.type);
            }
            return info.toString();
        }

        void guard(Runnable action) {
            if (closed) return;
            try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        GameTestSequence whenReady(Runnable action) {
            return helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(chunks.stream().allMatch(chunk -> helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "Every fixture chunk is published in the nonblocking loaded-chunk map before native queries start");
                // Level.setBlock only sends the update that invalidates native PathTypeCache once
                // the holder is BLOCK_TICKING. Merely being visible through getChunkNow is insufficient.
                helper.assertTrue(chunks.stream().allMatch(chunk -> helper.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "Every fixture chunk reaches native ticking status before a route can cache the old wall");
                helper.assertTrue(helper.getLevel().hasChunkAt(origin) && helper.getLevel().hasChunkAt(player.blockPosition()),
                        "Both sides of the divider are visible to loaded-only pursuit");
            }).thenExecute(() -> guard(action));
        }

        void closeWithTest() {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Zombie zombie : mobs) { zombie.getNavigation().stop(); zombie.discard(); }
            for (Player target : players) target.discard();
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }

    private static final class PathTypeProbe extends WalkNodeEvaluator {
        static PathType raw(BlockGetter level, BlockPos pos) { return getPathTypeFromState(level, pos); }
    }
}
