package io.github.ningmeng123.mite.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual async pursuit consumption must observe vehicles changed after worker completion. @author NingMeng123 */
@GameTestHolder("mite_async_paths")
@PrefixGameTestTemplate(false)
public final class VehicleAsyncPursuitGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_vehicle_async_pursuit", Integer::compareTo);

    private VehicleAsyncPursuitGameTests() {}

    @GameTest(template="empty", templateNamespace="mite_path_perf", batch="vehicle_async_removed", timeoutTicks=600)
    public static void removingABoatAfterWorkerCompletionRechecksInsteadOfCachingTheOldFailure(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.addBoat();
        f.begin().thenExecute(f::submit).thenWaitUntil(f::awaitWorker).thenExecute(() -> {
            h.assertFalse(MeleePursuitPaths.reaches(f.mob, f.task.result(), f.player),
                    "The original completed native query really cannot cross the boat in this low narrow corridor");
            f.boat.discard();
            Path current = f.find();
            h.assertTrue(!MeleePursuitPaths.pending(f.mob)
                            && MeleePursuitPaths.completeRouteClear(f.mob, current, f.player),
                    "Consumption synchronously rechecks the newly clear route, rather than reusing a stale failure: " + f.describe(current));
            h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).failures(), 0,
                    "A boat removed before consumption cannot establish false failure backoff");
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).retryAt() == Long.MIN_VALUE,
                    "Successful fresh consumption has no stale retry deadline");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template="empty", templateNamespace="mite_path_perf", batch="vehicle_async_added", timeoutTicks=600)
    public static void addingABoatAfterWorkerCompletionCannotPublishAPathThroughItsCurrentBody(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.begin().thenExecute(f::submit).thenWaitUntil(f::awaitWorker).thenExecute(() -> {
            h.assertTrue(MeleePursuitPaths.completeRouteClear(f.mob, f.task.result(), f.player),
                    "The original completed native query really reaches the target before the boat appears");
            f.addBoat();
            h.assertFalse(MeleePursuitPaths.completeRouteClear(f.mob, f.task.result(), f.player),
                    "The live vehicle physically invalidates the former complete route before it is consumed");
            Path current = f.find();
            h.assertTrue(!MeleePursuitPaths.pending(f.mob)
                            && !MeleePursuitPaths.completeRouteClear(f.mob, current, f.player),
                    "Consumption cannot publish the stale clear path through the now obstructed corridor: " + f.describe(current));
            h.assertFalse(f.mob.isPassenger(), "The query never boards the actual boat to evade the obstruction");
            f.close();
        }).thenSucceed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int id = IDS.getAndIncrement();
        final BlockPos origin;
        final Zombie mob;
        final Player player;
        final List<ChunkPos> chunks = new ArrayList<>(), tickingChunks = new ArrayList<>();
        final String previousAsync = System.getProperty("mite.asyncPathfinding");
        final String previousBudget = System.getProperty("mite.pursuitSearchBudget");
        AsyncPathSearchEngine.Task task;
        Boat boat;
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            System.setProperty("mite.asyncPathfinding", "true");
            System.setProperty("mite.pursuitSearchBudget", "false");
            origin = new BlockPos(4_900_000 + id * 192, h.absolutePos(BlockPos.ZERO).getY() + 12, 4_900_000);
            for (int x = -2; x <= 14; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 3; y++) {
                boolean solid = y == -1 || y >= 2 || z != 0 || x == -2 || x == 14;
                h.getLevel().setBlock(origin.offset(x, y, z), (solid ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            mob = EntityType.ZOMBIE.create(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true); mob.setDeltaMovement(Vec3.ZERO);
            mob.setPersistenceRequired(); mob.setCanPickUpLoot(false);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            R196NpcNeeds.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            R196MonsterAi.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(10))); mob.setTarget(player);

            ChunkPos low = new ChunkPos(origin.offset(-56, 0, -56)), high = new ChunkPos(origin.offset(56, 0, 56));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 0, id);
                h.getLevel().getChunk(x, z);
            }
            ChunkPos roomLow = new ChunkPos(origin.offset(-2, 0, -1)), roomHigh = new ChunkPos(origin.offset(14, 0, 1));
            for (int x = roomLow.x; x <= roomHigh.x; x++) for (int z = roomLow.z; z <= roomHigh.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); tickingChunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, id);
            }
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        void addBoat() {
            boat = EntityType.BOAT.create(h.getLevel());
            boat.moveTo(Vec3.atBottomCenterOf(origin.east(4)));
            boat.setNoGravity(true); boat.setDeltaMovement(Vec3.ZERO); boat.setInvulnerable(true);
            h.assertTrue(h.getLevel().addFreshEntity(boat), "The actual boat enters the real level entity index");
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int i = 0; i < 64 && h.getLevel().getChunkSource().pollTask(); i++) {}
                h.assertTrue(chunks.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "Every chunk captured by the native region is already published");
                h.assertTrue(tickingChunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "The real low corridor and its vehicle have native ticking terrain");
            }).thenExecute(() -> h.assertTrue(SourceMeleeGoal.first(mob) != null && MeleePursuitPaths.applies(mob)
                            && mob.getTarget() == player && player.isAlive() && !player.isCreative() && !player.isSpectator(),
                    "The stationary source zombie has a legitimate survival player pursuit"));
        }

        Path find() { return MeleePursuitPaths.find(mob, player, null); }

        void submit() {
            h.assertTrue(find() == null && MeleePursuitPaths.pending(mob), "The first pursuit actually submits an asynchronous native query");
            var state = AsyncMeleePursuit.state(mob);
            h.assertTrue(state != null && state.task != null, "A real engine task is held by the actual pursuit state");
            task = state.task;
        }

        void awaitWorker() {
            AsyncPathSearchEngine.pump(h.getLevel().getServer(), TimeUnit.MILLISECONDS.toNanos(4));
            h.assertTrue(task.done(), "The real engine completes before any mutation or result consumption");
            h.assertTrue(task.failure() == null && !task.cancelled(), "The native query finishes normally");
            h.assertTrue(MeleePursuitPaths.pending(mob) && AsyncMeleePursuit.state(mob).task == task,
                    "Only the worker finished; the high-level pursuit has not consumed its result");
        }

        String describe(Path path) {
            return "path=" + path + ", reaches=" + MeleePursuitPaths.reaches(mob, path, player)
                    + ", pending=" + MeleePursuitPaths.pending(mob) + ", stats=" + MeleePursuitPaths.searchStats(mob);
        }

        @Override public void close() {
            if (closed) return;
            closed = true; MeleePursuitPaths.invalidate(mob); mob.discard(); player.discard();
            if (boat != null) boat.discard();
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 0, id);
            for (ChunkPos chunk : tickingChunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, id);
            restore("mite.asyncPathfinding", previousAsync); restore("mite.pursuitSearchBudget", previousBudget);
        }

        private static void restore(String key, String value) {
            if (value == null) System.clearProperty(key); else System.setProperty(key, value);
        }
    }
}
