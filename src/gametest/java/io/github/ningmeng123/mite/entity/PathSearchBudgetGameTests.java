package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native fallback admission, fair deferral and unchanged decision semantics. @author NingMeng123 */
@GameTestHolder("mite_ai_budget")
@PrefixGameTestTemplate(false)
public final class PathSearchBudgetGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_path_budget_test", Integer::compareTo);

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_fair", timeoutTicks = 200)
    public static void twentyMovingNativePursuitsShareTwoDecisionSlotsAndEveryWaiterMakesProgress(GameTestHelper h) {
        Fixture f = new Fixture(h, 20);
        f.ready(() -> {
            boolean[] reached = new boolean[f.mobs.size()];
            int[] total = {0};
            h.startSequence().thenWaitUntil(() -> {
                int before = total[0];
                for (int i = 0; i < f.mobs.size(); i++) {
                    Zombie mob = f.mobs.get(i);
                    Path path = optimized(() -> MeleePursuitPaths.find(mob, f.player, null));
                    if (MeleePursuitPaths.reaches(mob, path, f.player)) {
                        if (!reached[i]) { reached[i] = true; total[0]++; }
                    } else {
                        h.assertTrue(optimized(() -> MeleePursuitPaths.pending(mob)), "Unadmitted moving fallbacks remain pending");
                        var stats = MeleePursuitPaths.searchStats(mob);
                        h.assertTrue(stats.failures() == 0 && stats.retryAt() == Long.MIN_VALUE,
                                "Budget waiting is never a failed route or failed retry deadline");
                    }
                }
                h.assertTrue(total[0] - before <= 2, "At most two real full native pursuit decisions start in one server tick");
                h.assertTrue(PathSearchBudget.stats(h.getLevel().getServer()).tickGranted() <= 2, "The shared grant counter obeys its limit");
                h.assertTrue(total[0] == f.mobs.size(), "Every FIFO waiter eventually receives a complete real route: " + total[0]);
            }).thenExecute(() -> {
                var before = PathSearchBudget.stats(h.getLevel().getServer());
                Zombie mob = f.mobs.getFirst();
                // A freshly verified successful route is reusable without consuming a third permit.
                MeleePursuitPaths.invalidate(mob);
                Path path = unbounded(() -> MeleePursuitPaths.find(mob, f.player, null));
                Path reused = optimized(() -> MeleePursuitPaths.find(mob, f.player, path));
                h.assertTrue(MeleePursuitPaths.reaches(mob, reused, f.player), "The hot complete route is still usable");
                h.assertValueEqual(PathSearchBudget.stats(h.getLevel().getServer()).granted(), before.granted(),
                        "Real cache hits do not spend another decision permit");
                h.assertTrue(before.deferred() > 0 && before.decisionNanos() > 0 && before.peakDecisionNanos() > 0,
                        "Counters distinguish actual native work, waiting and the largest atomic decision");
                f.close();
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_pending", timeoutTicks = 160)
    public static void deniedWalkingAndOpeningQueriesNeverStartMiningPortalsOrFailedBackoff(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        f.ready(() -> {
            occupyTick(f.mobs.get(0), f.mobs.get(1));
            Zombie waiting = f.mobs.get(2);
            var digger = R196DiggerGoal.first(waiting);
            var plan = optimized(digger::pursuitPlan);
            h.assertTrue(optimized(() -> MeleePursuitPaths.pending(waiting)) && !plan.choosesBreaking(),
                    "The real planner waits without selecting a destructive shortcut");
            h.assertFalse(optimized(digger::canUse), "The denied query cannot fall through into native mining");
            h.assertFalse(optimized(() -> new SourceBreakPortalGoal(waiting).canUse()), "Pending cannot start portal pounding");
            h.assertTrue(optimized(() -> SourceMeleeGoal.first(waiting).canUse()), "The waiting real melee goal retains its movement reservation");
            h.assertTrue(waiting.getTarget() == f.player, "Waiting keeps the existing target");
            Zombie opening = f.mobs.get(3);
            Path path = optimized(() -> MeleePursuitPaths.find(opening, Set.of(f.origin.east(2)), 0, false));
            h.assertTrue(path == null && optimized(() -> MeleePursuitPaths.pending(opening)),
                    "The separate native opening query propagates pending instead of a real failure");
            for (Zombie mob : List.of(waiting, opening)) {
                var stats = MeleePursuitPaths.searchStats(mob);
                h.assertTrue(stats.searches() == 0 && stats.failures() == 0 && stats.retryAt() == Long.MIN_VALUE,
                        "Rejected native calls are neither counted nor remembered as failures");
            }
            PathSearchBudget.cancel(waiting); PathSearchBudget.cancel(opening);
            h.assertValueEqual(PathSearchBudget.stats(h.getLevel().getServer()).backlog(), 0, "Explicit cancellation removes all waiting tickets");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_active_digger", timeoutTicks = 160)
    public static void activeValidatedDiggingFinishesWhileAnotherRouteDecisionWaitsAndStillHonorsProtection(GameTestHelper h) {
        Fixture f = new Fixture(h, 3);
        f.ready(() -> {
            Zombie mob = f.mobs.get(2);
            var digger = R196DiggerGoal.first(mob);
            BlockPos stone = f.origin.east().above();
            h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("vibranium_pickaxe")));
            occupyTick(f.mobs.get(0), f.mobs.get(1));
            h.assertFalse(attempt(mob, () -> {}), "The next actual synchronous decision is denied by the full budget");
            h.assertFalse(optimized(digger::canUse), "An initial unconfirmed route still cannot start digging while pending");
            PathSearchBudget.cancel(mob);
            h.assertTrue(digger.setBlockToDig(stone, true), "The prior decision selects a physically reachable valid stone block");
            digger.start();
            h.assertFalse(attempt(mob, () -> {}), "A later route recheck waits while the established block remains valid");
            h.assertTrue(optimized(digger::canContinueToUse), "The pending recheck retains the valid active digging goal");
            int workTicks = 40 + 10 * Math.max(1, digger.stageDelay(stone));
            optimized(() -> {
                h.assertTrue(MeleePursuitPaths.pending(mob), "The real admission ticket remains pending before mining advances");
                digger.tick();
                h.assertValueEqual(digger.cooldown(), 39,
                        "An already validated active block counts down while an independent route recheck is pending");
                for (int tick = 1; tick < workTicks; tick++) digger.tick();
                h.assertTrue(h.getLevel().getBlockState(stone).isAir(),
                        "Established valid digging completes within its tool-derived work ticks despite sustained pending");
                h.assertTrue(MeleePursuitPaths.pending(mob), "Mining does not manufacture a completed route decision");
                return null;
            });

            digger.stop();
            h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(digger.setBlockToDig(stone, true), "The next validated target can start before the final protection callback");
            digger.start();
            int[] callbacks = {0};
            Consumer<LivingDestroyBlockEvent> protect = event -> {
                if (event.getEntity() == mob && event.getPos().equals(stone)) {
                    callbacks[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, protect);
            try {
                optimized(() -> {
                    for (int tick = 0; tick < workTicks; tick++) digger.tick();
                    return null;
                });
            } finally {
                NeoForge.EVENT_BUS.unregister(protect);
            }
            h.assertValueEqual(callbacks[0], 1, "The final protected mutation still dispatches the real cancellable event");
            h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE), "Final protection preserves the selected stone block");
            h.assertTrue(digger.destructionRejected(stone), "A protected target is rejected after the completed work");
            digger.stop();
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_idle_head", timeoutTicks = 160)
    public static void aHeadWhoseGoalStopsRequestingCannotBlockTheServerForMoreThanTwoTicks(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        f.ready(() -> {
            occupyTick(f.mobs.get(0), f.mobs.get(1));
            h.assertFalse(attempt(f.mobs.get(2), () -> {}), "The first waiter becomes the FIFO head");
            h.assertFalse(attempt(f.mobs.get(3), () -> {}), "The following waiter initially queues behind it");
            int began = h.getLevel().getServer().getTickCount();
            h.startSequence().thenWaitUntil(() -> {
                boolean granted = attempt(f.mobs.get(3), () -> {});
                h.assertTrue(granted, "The stopped head's live target cannot prevent another ready goal progressing");
                h.assertTrue(h.getLevel().getServer().getTickCount() - began <= 2,
                        "The ready waiter progresses within the two-tick active request lease");
            }).thenExecute(f::close).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_soft_time", timeoutTicks = 160)
    public static void oneSlowAtomicDecisionStopsFurtherAdmissionButItsNestedStagesFinish(GameTestHelper h) {
        Fixture f = new Fixture(h, 2);
        f.ready(() -> {
            AtomicInteger stages = new AtomicInteger();
            optimized(() -> {
                try (var ignored = PathSearchBudget.open(f.mobs.get(0))) {
                    PathSearchBudget.synchronous(f.mobs.get(0), () -> {
                        long until = System.nanoTime() + 4_500_000L;
                        while (System.nanoTime() < until) LockSupport.parkNanos(Math.min(100_000L, until - System.nanoTime()));
                        return stages.incrementAndGet();
                    });
                    // Crossing the soft time threshold cannot starve later direct/exact/detour stages.
                    for (int i = 0; i < 2; i++) PathSearchBudget.synchronous(f.mobs.get(0), stages::incrementAndGet);
                }
                return null;
            });
            h.assertValueEqual(stages.get(), 3, "All stages belonging to the admitted decision complete");
            h.assertFalse(attempt(f.mobs.get(1), () -> {}), "A second decision is not started after the four-millisecond allowance");
            var stats = PathSearchBudget.stats(h.getLevel().getServer());
            h.assertTrue(stats.granted() == 1 && stats.tickNanos() >= 4_000_000L && stats.peakDecisionNanos() >= 4_000_000L,
                    "Counters expose an atomic overrun as a soft budget rather than promising a hard cap");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_disabled", timeoutTicks = 160)
    public static void disablingTheBudgetRestoresUnrestrictedSynchronousCalls(GameTestHelper h) {
        Fixture f = new Fixture(h, 6);
        f.ready(() -> {
            for (Zombie mob : f.mobs) {
                Path path = unbounded(() -> MeleePursuitPaths.find(mob, f.player, null));
                h.assertTrue(MeleePursuitPaths.reaches(mob, path, f.player), "Disabled mode retains the original immediate native response");
                h.assertFalse(unbounded(() -> MeleePursuitPaths.pending(mob)), "Disabled synchronous decisions never become budget pending");
            }
            h.assertValueEqual(PathSearchBudget.stats(h.getLevel().getServer()).granted(), 0L, "Disabled mode does not use the budget at all");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_async_full", timeoutTicks = 180)
    public static void fullWorkerCapacityWaitsWithoutAnUnbudgetedNativeFallback(GameTestHelper h) {
        Fixture f = new Fixture(h, 2);
        f.ready(() -> {
            var server = h.getLevel().getServer();
            AsyncPathSearchEngine.close(server);
            List<AsyncPathSearchEngine.Task> tasks = new ArrayList<>();
            try {
                for (int i = 0; i < 64; i++) {
                    var region = new LoadedPathNavigationRegion(h.getLevel(), f.origin.offset(-8, -8, -8), f.origin.offset(16, 8, 8));
                    var task = AsyncPathSearchEngine.submit(h.getLevel(), region, f.mobs.get(0), new WalkNodeEvaluator(),
                            Set.of(f.origin.east(8)), 1024, 48F, 1, 1F, () -> true);
                    h.assertTrue(task != null, "All sixty-four bounded slots are filled before any main callbacks are pumped");
                    tasks.add(task);
                }
                Zombie waiting = f.mobs.get(1); waiting.setDeltaMovement(Vec3.ZERO);
                optimized(() -> {
                    System.setProperty("mite.asyncPathfinding", "true");
                    h.assertTrue(AsyncPathSearchEngine.saturated(server), "The actual native worker pool reports capacity saturation");
                    Path result = MeleePursuitPaths.find(waiting, f.player, null);
                    h.assertTrue(result == null && MeleePursuitPaths.pending(waiting), "The sixty-fifth real pursuit waits rather than becoming failed");
                    h.assertTrue(MeleePursuitPaths.searchStats(waiting).searches() == 0
                                    && MeleePursuitPaths.searchStats(waiting).failures() == 0,
                            "Capacity rejection executes no fallback native query and creates no failure");
                    h.assertValueEqual(PathSearchBudget.stats(server).granted(), 0L, "Worker overload does not spend a synchronous decision permit");
                    return null;
                });
            } finally {
                for (var task : tasks) task.cancel();
                AsyncPathSearchEngine.close(server); f.close();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_unsupported", timeoutTicks = 160)
    public static void anUnsupportedWorkerRequestUsesTheBudgetInsteadOfWaitingForNonexistentCapacity(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.ready(() -> {
            Zombie mob = f.mobs.getFirst(); mob.setDeltaMovement(Vec3.ZERO);
            AtomicInteger calls = new AtomicInteger();
            optimized(() -> {
                System.setProperty("mite.asyncPathfinding", "true");
                AsyncMeleePursuit.begin(mob, f.player, false);
                var region = new LoadedPathNavigationRegion(h.getLevel(), f.origin, f.origin.east(8));
                Path path = AsyncMeleePursuit.query(mob, region, new WalkNodeEvaluator(), Set.of(f.origin.east(8)),
                        8193, 48F, 1, 1F, () -> {}, () -> PathSearchBudget.synchronous(mob, () -> {
                            calls.incrementAndGet(); return null;
                        }));
                h.assertTrue(path == null && !MeleePursuitPaths.pending(mob),
                        "An unsupported node limit falls back through admission instead of waiting two hundred ticks");
                return null;
            });
            h.assertTrue(calls.get() == 1 && PathSearchBudget.stats(h.getLevel().getServer()).granted() == 1,
                    "The unsupported request executes its fallback exactly once using a real budget permit");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_detour", timeoutTicks = 240)
    public static void reducedNodeBudgetStillFindsTheLongWallDetourWithinOneDecision(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); f.longWall();
        f.ready(() -> {
            Zombie mob = f.mobs.getFirst();
            Path path = optimized(() -> MeleePursuitPaths.find(mob, f.player, null));
            h.assertTrue(MeleePursuitPaths.completeRouteClear(mob, path, f.player),
                    "The optimized 1024/4096-node policy still finds the proven long-wall route: " + path);
            h.assertTrue(MeleePursuitPaths.searchStats(mob).searches() >= 2, "The fixture really needs direct and detour native stages");
            h.assertValueEqual(PathSearchBudget.stats(h.getLevel().getServer()).granted(), 1L,
                    "The multi-stage real decision spends one permit and cannot be starved between stages");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "mite", batch = "path_budget_retry", timeoutTicks = 240)
    public static void optimizedFailureCooldownExpiresAndAChangedOpeningCanBeSearchedAgain(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); f.divider();
        f.ready(() -> {
            Zombie mob = f.mobs.getFirst();
            optimized(() -> MeleePursuitPaths.find(mob, f.player, null));
            var failed = MeleePursuitPaths.searchStats(mob);
            h.assertTrue(failed.failures() == 1 && failed.retryAt() - h.getLevel().getGameTime() >= 40,
                    "A real failed optimized decision gets the documented forty-tick first cooldown");
            optimized(() -> MeleePursuitPaths.find(mob, f.player, null));
            h.assertValueEqual(MeleePursuitPaths.searchStats(mob).searches(), failed.searches(), "Unchanged cooldown probes reuse the failure");
            h.getLevel().setBlock(f.origin.east(3), Blocks.AIR.defaultBlockState(), 2);
            h.getLevel().setBlock(f.origin.east(3).above(), Blocks.AIR.defaultBlockState(), 2);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().getGameTime() >= failed.retryAt(), "Wait for the complete real cooldown"))
                    .thenExecute(() -> {
                        Path path = optimized(() -> MeleePursuitPaths.find(mob, f.player, null));
                        h.assertTrue(MeleePursuitPaths.completeRouteClear(mob, path, f.player), "The next admitted retry uses the newly open real route");
                        h.assertTrue(MeleePursuitPaths.searchStats(mob).searches() > failed.searches(), "A real new native query happens after cooldown");
                        f.close();
                    }).thenSucceed();
        });
    }

    private static void occupyTick(Zombie first, Zombie second) {
        attempt(first, () -> {}); attempt(second, () -> {});
    }

    private static boolean attempt(Zombie mob, Runnable work) {
        return optimized(() -> {
            try (var ignored = PathSearchBudget.open(mob)) {
                PathSearchBudget.synchronous(mob, () -> { work.run(); return null; });
                return true;
            } catch (AsyncMeleePursuit.Pending pending) { return false; }
        });
    }

    private static <T> T optimized(Supplier<T> action) { return properties(true, action); }
    private static <T> T unbounded(Supplier<T> action) { return properties(false, action); }
    private static <T> T properties(boolean enabled, Supplier<T> action) {
        String budget = System.getProperty("mite.pursuitSearchBudget"), async = System.getProperty("mite.asyncPathfinding");
        System.setProperty("mite.pursuitSearchBudget", Boolean.toString(enabled)); System.setProperty("mite.asyncPathfinding", "false");
        try { return action.get(); }
        finally { restore("mite.pursuitSearchBudget", budget); restore("mite.asyncPathfinding", async); }
    }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int id = IDS.getAndIncrement();
        final BlockPos origin;
        final List<Zombie> mobs = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<ChunkPos> tickingChunks = new ArrayList<>();
        final Player player;
        boolean closed;
        Fixture(GameTestHelper h, int count) {
            this.h = h;
            PathSearchBudget.clear(h.getLevel().getServer());
            origin = new BlockPos(6_100_000 + id * 192, h.absolutePos(BlockPos.ZERO).getY() + 12, 6_100_000);
            room(16);
            player = h.makeMockPlayer(GameType.SURVIVAL); player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            for (int i = 0; i < count; i++) {
                Zombie mob = EntityType.ZOMBIE.create(h.getLevel());
                mob.moveTo(Vec3.atBottomCenterOf(origin)); mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true);
                mob.setPersistenceRequired(); mob.setCanPickUpLoot(false); mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                R196NpcNeeds.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
                R196MonsterAi.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
                mob.setTarget(player); mob.setDeltaMovement(.2D, 0D, 0D); // Real moving-ineligible synchronous fallback.
                mobs.add(mob);
            }
            ChunkPos low = new ChunkPos(origin.offset(-56, 0, -56)), high = new ChunkPos(origin.offset(56, 0, 56));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 0, id); h.getLevel().getChunk(x, z);
            }
            ChunkPos roomLow = new ChunkPos(origin.offset(-6, 0, -30)), roomHigh = new ChunkPos(origin.offset(14, 0, 30));
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
        void ready(Runnable action) {
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(chunks.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "All bounded native search region chunks have been published");
                h.assertTrue(tickingChunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "The real room's ticking chunks invalidate native path-type caches on terrain changes");
            }).thenExecute(() -> {
                PathSearchBudget.clear(h.getLevel().getServer()); action.run();
            });
        }
        void room(int radius) {
            for (int x = -6; x <= 14; x++) for (int z = -radius; z <= radius; z++) for (int y = -1; y <= 7; y++) {
                boolean solid = y == -1 || y == 7 || x == -6 || x == 14 || z == -radius || z == radius;
                h.getLevel().setBlock(origin.offset(x, y, z), (solid ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
        }
        void divider() { for (int z = -16; z <= 16; z++) for (int y = 0; y < 7; y++) h.getLevel().setBlock(origin.offset(3, y, z), Blocks.BEDROCK.defaultBlockState(), 2); }
        void longWall() {
            room(30);
            for (int z = -27; z <= 27; z++) for (int y = 0; y <= 5; y++) h.getLevel().setBlock(origin.offset(2, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        @Override public void close() {
            if (closed) return; closed = true;
            for (Zombie mob : mobs) { MeleePursuitPaths.invalidate(mob); mob.discard(); }
            player.discard();
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 0, id);
            for (ChunkPos chunk : tickingChunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, id);
            PathSearchBudget.clear(h.getLevel().getServer());
        }
    }
}
