package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Pursuit reuse without delaying actual attack range and opening changes. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class ZombieRoutePerformanceGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_zombie_route_performance_test", Integer::compareTo);

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void attackPositionIsReusedButSmallTargetMovementImmediatelyResumesWalking(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            f.player.moveTo(f.origin.getX() + 1.75D, f.origin.getY(), f.origin.getZ() + .5D, 0F, 0F);
            h.assertTrue(SourceMeleeGoal.canStrike(f.mob, f.player), "The initial player is inside the real unarmed attack range");
            var ready = f.digger.pursuitPlan();
            h.assertTrue(ready.completeWalkingRoute() && ready.walkingTicks() == 0D
                            && !ready.choosesBreaking() && ready.path().getNodeCount() == 1,
                    "An actual attack position needs no route search or excavation");
            h.assertTrue(f.digger.pursuitPlan() == ready, "Repeated pursuit entry points share the zero-step plan");
            f.player.moveTo(f.origin.getX() + 2.25D, f.origin.getY(), f.origin.getZ() + .5D, 0F, 0F);
            h.assertFalse(SourceMeleeGoal.canStrike(f.mob, f.player), "Half a block of target movement leaves attack range");
            var walking = f.digger.pursuitPlan();
            h.assertTrue(walking != ready && walking.completeWalkingRoute() && walking.walkingTicks() > 0D,
                    "Leaving attack range invalidates the zero-step plan before the one-block movement threshold");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void openingCachedGateImmediatelyReplacesItsBreakingPlan(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            for (int z = -10; z <= 10; z++)
                h.getLevel().setBlock(f.origin.offset(1, 0, z), Blocks.OAK_FENCE_GATE.defaultBlockState(), 2);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            var closed = f.digger.pursuitPlan();
            h.assertTrue(closed.choosesBreaking() && closed.portal(), "The closed gate is a faster real pounding shortcut");
            BlockPos gate = closed.firstBlock();
            h.getLevel().setBlock(gate, h.getLevel().getBlockState(gate).setValue(FenceGateBlock.OPEN, true), 2);
            var opened = f.digger.pursuitPlan();
            h.assertTrue(opened != closed && opened.completeWalkingRoute() && !opened.choosesBreaking(),
                    "A gate that opens within the same tick immediately becomes a walking route");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void stationaryFailedPlanSurvivesTwentyTicksAndMovingTargetInvalidatesIt(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            f.player.moveTo(f.origin.getX() + 8.5D, f.origin.getY() + 12D, f.origin.getZ() + .5D, 0F, 0F);
            h.getLevel().setBlock(f.player.blockPosition().below(), Blocks.BEDROCK.defaultBlockState(), 2);
            var failed = f.digger.pursuitPlan();
            h.assertTrue(!failed.completeWalkingRoute() && !failed.choosesBreaking(), "The isolated high perch has no route or excavation shortcut");
            h.runAfterDelay(25, () -> {
                try {
                    h.assertTrue(f.digger.pursuitPlan() == failed, "A stationary impossible pursuit does not restart the expensive search after twenty ticks");
                    f.player.moveTo(f.player.getX() + 1.1D, f.player.getY(), f.player.getZ(), 0F, 0F);
                    h.assertTrue(f.digger.pursuitPlan() != failed, "Real target movement invalidates the failed plan immediately");
                    h.succeed();
                } finally {
                    f.close();
                }
            });
        });
    }

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void movingUnreachablePlayerCannotResetTheNativeQueryCooldown(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            f.player.moveTo(f.origin.getX() + 8.5D, f.origin.getY() + 12D, f.origin.getZ() + .5D, 0F, 0F);
            h.getLevel().setBlock(f.player.blockPosition().below(), Blocks.BEDROCK.defaultBlockState(), 2);
            var failed = f.digger.pursuitPlan();
            h.assertFalse(failed.completeWalkingRoute() || failed.choosesBreaking(),
                    "The elevated isolated player has no walking or excavation route");
            long searches = MeleePursuitPaths.searchStats(f.mob).searches();
            h.assertTrue(searches > 0, "The initial failure executes a real native search");
            var melee = new SourceMeleeGoal(f.mob, Player.class, 1D, false);
            // Both endpoints repeatedly cross the old one-block cache boundary. Different goal
            // entrypoints must still share one minimum retry interval for this unchanged target.
            for (int request = 0; request < 12; ++request) {
                f.player.moveTo(f.origin.getX() + 8.5D + (request % 2) * 2.2D,
                        f.origin.getY() + 12D, f.origin.getZ() + .5D, 0F, 0F);
                f.mob.moveTo(f.origin.getX() + .5D + (request % 2) * 1.1D,
                        f.origin.getY(), f.origin.getZ() + .5D, 0F, 0F);
                f.digger.canUse(); melee.canUse();
            }
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() == searches,
                    "Movement and repeated digging/melee requests cannot force another same-tick native query");
            h.runAfterDelay(MeleePursuitPaths.MIN_MOVING_RETRY_TICKS - 1, () -> {
                f.player.moveTo(f.origin.getX() + 8.5D, f.origin.getY() + 12D,
                        f.origin.getZ() + .5D, 0F, 0F);
                f.digger.canUse(); melee.canUse();
                h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() == searches,
                        "A moving unreachable target cannot bypass the final tick of the minimum retry interval");
                h.runAfterDelay(1, () -> {
                    try {
                        // Ensure both original search positions have changed when the deadline
                        // arrives, so this is moving-target recovery rather than stationary backoff.
                        f.player.moveTo(f.origin.getX() + 10.7D, f.origin.getY() + 12D,
                                f.origin.getZ() + .5D, 0F, 0F);
                        f.mob.moveTo(f.origin.getX() + 1.6D, f.origin.getY(),
                                f.origin.getZ() + .5D, 0F, 0F);
                        f.digger.canUse();
                        h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                                "Moving-target pursuit resumes a real query as soon as its short minimum cooldown expires");
                        h.succeed();
                    } finally { f.close(); }
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void playerStoppingAfterMovementStillRetriesAtTheShortDeadline(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            f.player.moveTo(f.origin.getX() + 8.5D, f.origin.getY() + 12D, f.origin.getZ() + .5D, 0F, 0F);
            h.getLevel().setBlock(f.player.blockPosition().below(), Blocks.BEDROCK.defaultBlockState(), 2);
            var failed = f.digger.pursuitPlan();
            h.assertFalse(failed.completeWalkingRoute() || failed.choosesBreaking(),
                    "The first query genuinely fails to reach the isolated high player");
            long searches = MeleePursuitPaths.searchStats(f.mob).searches();
            h.assertTrue(searches > 0, "A real native query establishes the original cooldown");
            f.player.moveTo(f.player.getX() + 2.2D, f.player.getY(), f.player.getZ(), 0F, 0F);
            f.digger.pursuitPlan();
            h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() == searches,
                    "The initial player movement is deferred instead of starting another search immediately");
            // The player and zombie now stay exactly still. Requiring another position change
            // at the deadline would hide a deferred plan being cached as a new 40-tick failure.
            h.runAfterDelay(MeleePursuitPaths.MIN_MOVING_RETRY_TICKS - 1, () -> {
                f.digger.pursuitPlan();
                h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() == searches,
                        "Stopping after the movement does not shorten the minimum query cooldown");
                h.runAfterDelay(1, () -> {
                    try {
                        f.digger.pursuitPlan();
                        h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches,
                                "The unchanged moved position is queried at tick ten rather than cached for forty more ticks");
                        h.succeed();
                    } finally { f.close(); }
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "zombie_route_cache", timeoutTicks = 400)
    public static void activeAndPausedDiggingChecksShareThePursuitQueryBudget(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            // No complete walking alternative exists, while both adjacent plank blocks are
            // genuinely reachable excavation work. Non-collapsing planks keep the second
            // block intact during the real follow-up pause. Keep the player outside strike range.
            for (int z = -16; z <= 16; z++) for (int y = 0; y <= 3; y++)
                h.getLevel().setBlock(f.origin.offset(1, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            h.assertTrue(f.digger.canUse(), "The actual digging goal selects a physically reachable opening");
            f.digger.start();
            while (MobCombat.ticksWithOffset(f.mob) % 10 != 0) ++f.mob.tickCount;
            int initial = f.mob.navigationQueries;
            for (int request = 0; request < 12; ++request)
                h.assertTrue(f.digger.canContinueToUse(), "Unfinished valid digging remains active at the route-check cadence");
            h.assertTrue(f.mob.navigationQueries == initial,
                    "Active digging reuses the shared pursuit result instead of issuing independent native queries");

            // Finish the first block through the real break path, then reach the one-tick
            // pause branch which checks whether walking now makes better progress.
            BlockPos opening = f.digger.targetBlock();
            f.digger.stop();
            CompoundTag saved = new CompoundTag();
            saved.putLong("position", opening.asLong()); saved.putInt("progress", 9); saved.putInt("cooldown", 1);
            f.mob.getPersistentData().put("mite_digging_031", saved);
            var resumed = new R196DiggerGoal(f.mob);
            h.assertTrue(resumed.canUse(), "Saved final-stage work is restored through the actual goal");
            resumed.start(); resumed.tick();
            h.assertTrue(h.getLevel().isEmptyBlock(opening) && resumed.pauseTicks() == 10,
                    "Finishing one plank block leaves genuine follow-up digging work and its normal pause");
            h.assertTrue(resumed.targetBlock() != null && !h.getLevel().isEmptyBlock(resumed.targetBlock()),
                    "The paused goal has a real remaining block rather than only waiting for collapsing terrain");
            for (int tick = 0; tick < 9; ++tick) resumed.tick();
            h.assertTrue(resumed.pauseTicks() == 1, "The real pause reaches its final route-check tick");
            for (int request = 0; request < 12; ++request)
                h.assertTrue(resumed.canContinueToUse(),
                        "The final pause checks its actual route while preserving valid follow-up digging");
            h.assertTrue(f.mob.navigationQueries == initial,
                    "Paused digging also respects the shared route budget after a real terrain change");
            resumed.stop();
            f.close();
            h.succeed();
        });
    }

    private static final class QueryCountingZombie extends Zombie {
        int navigationQueries;
        QueryCountingZombie(Level level) { super(EntityType.ZOMBIE, level); }
        @Override protected PathNavigation createNavigation(Level level) {
            return new GroundPathNavigation(this, level) {
                @Override public Path createPath(BlockPos target, int accuracy, int range) {
                    ++navigationQueries;
                    return super.createPath(target, accuracy, range);
                }
            };
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final int ticketKey;
        final List<ChunkPos> chunks = new ArrayList<>();
        boolean closed;
        final BlockPos origin;
        final QueryCountingZombie mob;
        final Player player;
        final R196DiggerGoal digger;

        Fixture(GameTestHelper h) {
            helper = h;
            ticketKey = IDS.getAndIncrement();
            origin = new BlockPos(1_560_000 + ticketKey * 128,
                    h.absolutePos(BlockPos.ZERO).getY() + 8, 1_560_000);
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                h.getLevel().setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = 0; y < 4; y++) h.getLevel().setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            mob = new QueryCountingZombie(h.getLevel());
            mob.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true);
            // Exercise the actual installed goals without depending on a ticking entity surviving
            // unrelated batches, difficulty branches or native entity-section cleanup.
            R196NpcNeeds.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            R196MonsterAi.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(origin.getX() + 8.5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            mob.setTarget(player);
            R196DiggerGoal installed = R196DiggerGoal.first(mob);
            digger = installed == null ? new R196DiggerGoal(mob) : installed;
            ChunkPos low = new ChunkPos(origin.offset(-16, 0, -16)), high = new ChunkPos(origin.offset(16, 0, 16));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            }
        }

        void whenReady(Runnable action) {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(chunks.stream().allMatch(chunk -> helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "The complete fixture is published in the loaded-only navigation map before planning");
                helper.assertTrue(chunks.stream().allMatch(chunk -> helper.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "Native ticking chunk status enables the block-update path that invalidates cached gate types");
            }).thenExecute(() -> {
                if (closed) return;
                try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
            });
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            digger.stop(); mob.discard(); player.discard();
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}
