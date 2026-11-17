package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native pursuit routes extend existing target memory only while usable and progressing. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MeleeTargetMemoryGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void movingAlongVerifiedDetourKeepsUnseenTargetPastSixtyTicks(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h);
            int[] updates = {0};
            h.onEachTick(() -> {
                PathfindingTestSettings.runSynchronous(() -> {
                    if (f.closed) return;
                    try {
                        // The fixture drives real native path nodes deterministically, without changing the planner's answer.
                        f.mob.getNavigation().tick();
                        Path path = f.mob.getNavigation().getPath();
                        h.assertTrue(path != null && !path.isDone(), "The long side route remains unfinished during hidden tracking");
                        Vec3 delta = path.getNextEntityPos(f.mob).subtract(f.mob.position());
                        if (delta.lengthSqr() > 0D) f.mob.setPos(f.mob.position().add(delta.normalize().scale(Math.min(.08D, delta.length()))));
                        f.mob.setOnGround(true);
                        f.melee.tick();
                        h.assertFalse(SourceMonsterTargeting.canSee(f.mob, f.player, false), "The wall still hides the target throughout this portion of the detour");
                        h.assertTrue(f.selector.canContinueToUse(), "A verified moving route retains an existing target beyond the old sixty-tick cutoff");
                        if (++updates[0] == 90) {
                            h.assertTrue(f.mob.position().distanceToSqr(f.initial) > 16D, "The mob has made real position progress along the native route");
                            h.assertTrue(f.melee.followsReachableRoute(f.player), "The current real attack route justifies extended memory");
                            f.mob.getNavigation().stop();
                            h.assertFalse(f.selector.canContinueToUse(), "Losing that route immediately ends extended unseen memory");
                            f.close();
                            h.succeed();
                        }
                    } catch (RuntimeException | Error failure) {
                        f.close();
                        throw failure;
                    }
                });
            });
            h.runAtTickTime(129, f::close);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stationaryCompletePathDoesNotKeepHiddenTargetForever(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h);
            try {
                for (int update = 0; update < 60; update++)
                    h.assertTrue(f.selector.canContinueToUse(), "The source's original sixty unseen updates remain available");
                int[] stationaryTicks = {0};
                h.onEachTick(() -> {
                    PathfindingTestSettings.runSynchronous(() -> {
                        if (f.closed) return;
                        try {
                            f.melee.tick();
                            if (++stationaryTicks[0] < 65) return;
                            h.assertFalse(f.melee.followsReachableRoute(f.player), "Old route progress cannot justify continued memory");
                            h.assertFalse(f.selector.canContinueToUse(), "The sixty-first unseen update rejects the stationary route");
                            f.close();
                            h.succeed();
                        } catch (RuntimeException | Error failure) {
                            f.close();
                            throw failure;
                        }
                    });
                });
                h.runAtTickTime(99, f::close);
            } catch (RuntimeException | Error failure) {
                f.close();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty")
    public static void verifiedRouteMemoryStillHasAFiniteSixHundredUpdateLimit(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                for (int update = 0; update < 600; update++)
                    h.assertTrue(f.selector.canContinueToUse(), "A recently progressing verified route permits up to six hundred unseen updates");
                h.assertTrue(f.melee.followsReachableRoute(f.player), "The finite memory cap is tested with the actual route still valid");
                h.assertFalse(f.selector.canContinueToUse(), "Even a verified route cannot retain a target indefinitely without renewed sight");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void distantNonZombieTargetRetainsItsOriginalNativePursuitRange(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            BlockPos origin = new BlockPos(450_000 + IDS.getAndIncrement() * 128,
                    h.absolutePos(BlockPos.ZERO).getY() + 10, 450_000);
            for (int x = -1; x <= 61; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 3; y++) {
                boolean boundary = y == -1 || y == 3 || z != 0 || x == -1 || x == 61;
                h.getLevel().setBlock(origin.offset(x, y, z), (boundary ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            var mob = EntityType.WITHER_SKELETON.create(h.getLevel());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            boolean[] closed = {false};
            Runnable cleanup = () -> {
                if (closed[0]) return;
                closed[0] = true;
                mob.getNavigation().stop();
                mob.discard();
                player.discard();
            };
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            });
            try {
                mob.moveTo(Vec3.atBottomCenterOf(origin));
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setOnGround(true);
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                h.getLevel().addFreshEntity(mob);
                mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(80D);
                player.moveTo(Vec3.atBottomCenterOf(origin.east(60)));
                mob.setTarget(player);
                ChunkPos low = new ChunkPos(origin.offset(-1, 0, -1)), high = new ChunkPos(origin.offset(61, 0, 1));
                h.startSequence().thenWaitUntil(() -> {
                    PathfindingTestSettings.runSynchronous(() -> {
                        // PathNavigationRegion reads getChunkNow, whose visible map can lag synchronous fixture writes.
                        for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++)
                            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x, z) != null,
                                    "All prepared corridor chunks are visible to the native path region");
                        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4)
                                        .getBlockState(origin.below()).is(Blocks.BEDROCK)
                                        && h.getLevel().getChunkSource().getChunkNow((origin.getX() + 60) >> 4, origin.getZ() >> 4)
                                        .getBlockState(origin.east(60).below()).is(Blocks.BEDROCK),
                                "Native path-region chunks expose solid support at both ends of the corridor");
                    });
                }).thenExecute(() -> {
                    PathfindingTestSettings.runSynchronous(() -> {
                        try {
                            h.assertTrue(SourceMonsterTargeting.effectiveRange(mob) >= 80D && mob.distanceToSqr(player) == 3_600D,
                                    "The real non-zombie target is sixty blocks away within the source's extended follow range");
                            SourceMeleeGoal melee = SourceMeleeGoal.first(mob);
                            h.assertTrue(melee != null && melee.canUse(), "The installed melee goal accepts a target beyond the bounded enhanced search");
                            melee.start();
                            Path path = mob.getNavigation().getPath();
                            h.assertTrue(path != null && path.canReach() && path.getEndNode() != null,
                                    "Original native navigation still supplies a complete sixty-block chase route");
                            h.assertTrue(path.getEndNode().x >= origin.getX() + 59 && SourceMeleeGoal.canStrikeFrom(mob, player,
                                            path.getEntityPosAtNode(mob, path.getNodeCount() - 1)),
                                    "The actual distant route reaches the player rather than stopping at the enhanced search's forty-eight-block boundary");
                            melee.stop();
                        } finally { cleanup.run(); }
                    });
                }).thenSucceed();
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final BlockPos origin;
        final Zombie mob;
        final Player player;
        final SourceMeleeGoal melee;
        final SourceMonsterTargeting.Nearest selector;
        final Vec3 initial;
        boolean closed;

        Fixture(GameTestHelper h) {
            origin = new BlockPos(210_000 + IDS.getAndIncrement() * 96, h.absolutePos(BlockPos.ZERO).getY() + 10, 210_000);
            for (int x = -4; x <= 12; x++) for (int z = -18; z <= 18; z++)
                for (int y = -1; y <= 4; y++) h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == -1 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            mob = EntityType.ZOMBIE.create(h.getLevel());
            initial = Vec3.atBottomCenterOf(origin);
            mob.moveTo(initial);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.getPersistentData().putBoolean(R196MonsterAi.SMART, false);
            // A sword restores the source sight policy even for a smart zombie or blood-moon fixture.
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.getLevel().addFreshEntity(mob);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            selector = new SourceMonsterTargeting.Nearest(mob, Player.class, 0, true);
            // Exercise accepted prior targeting without depending on player-list registration in remote fixture chunks.
            ((SourceMonsterTargetMemory)mob).mite$retarget(player);
            h.assertTrue(selector.canUse(), "An accepted queued target starts the real source target selector");
            selector.start();
            for (int z = -14; z <= 14; z++) for (int y = 0; y <= 4; y++)
                h.getLevel().setBlock(origin.offset(2, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            mob.goalSelector.removeAllGoals(goal -> goal instanceof SourceMeleeGoal);
            melee = new SourceMeleeGoal(mob, Player.class, 1D, false);
            mob.goalSelector.addGoal(2, melee);
            h.assertTrue(melee.canUse(), "A real complete attack route goes around the finite wall");
            melee.start();
            h.assertTrue(SourceMeleeGoal.first(mob) == melee && melee.followsReachableRoute(player), "Extended memory reads the actual current melee goal and navigation route");
            h.assertFalse(R196MonsterAi.diggingEnabled(mob), "The fixture cannot bypass sight through the source digging exemption");
            h.assertFalse(SourceMonsterTargeting.canSee(mob, player, false), "The target is hidden behind solid bedrock");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            melee.stop();
            selector.stop();
            mob.discard();
            player.discard();
        }
    }
}
