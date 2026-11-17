package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real acquired-target memory follows owned overhead mining and ends when that work becomes invalid. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterOverheadTargetMemoryGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty")
    public static void startedOverheadMiningRetainsAcquiredPlayerThroughEntireDirtBreak(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                f.startDigging(h);
                h.assertValueEqual(f.digger.stageDelay(f.roof), 150, "Ordinary empty-hand dirt mining keeps its source stage delay");
                int total = 40 + 10 * f.digger.stageDelay(f.roof);
                h.assertTrue(total > 600, "The real block needs more than both ordinary unseen-memory limits");
                for (int tick = 0; tick < total; tick++) {
                    h.assertTrue(f.selector.canContinueToUse(), "The real source selector retains its acquired player while overhead mining is owned and valid");
                    h.assertTrue(f.digger.canContinueToUse(), "The higher-priority digging goal continues the same overhead work");
                    f.digger.tick();
                    if (tick == 600) {
                        h.assertTrue(h.getLevel().getBlockState(f.roof).is(Blocks.DIRT), "More than six hundred updates do not bypass actual staged block breaking");
                        h.assertTrue(f.digger.isBreakingOverheadFor(f.player), "The actual started goal justifies this extra unseen memory");
                    }
                }
                h.assertTrue(h.getLevel().isEmptyBlock(f.roof), "Retaining the real player allows all eleven mining stages to finish");
                h.assertFalse(f.digger.isBreakingOverheadFor(f.player), "A removed obstruction ends the overhead-memory exemption");
                h.assertTrue(SourceMonsterTargeting.canSee(f.mob, f.player, false), "Breaking the player's actual support restores sight through the opening");
                h.assertTrue(f.selector.canContinueToUse(), "Renewed physical sight now keeps the target under the original policy");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void overheadCanUsePreviewDoesNotRetainUnseenPlayerWithoutStarting(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                h.assertTrue(f.digger.canUse(), "The selector may preview a valid overhead mining candidate");
                h.assertFalse(f.digger.isBreakingOverheadFor(f.player), "A canUse preview has not acquired ownership through start");
                for (int update = 0; update < 60; update++)
                    h.assertTrue(f.selector.canContinueToUse(), "The original sixty unseen updates remain available");
                h.assertFalse(f.selector.canContinueToUse(), "An unstarted mining preview cannot extend target memory");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void playerMovingOffTheMinedCeilingEndsExtendedMemoryWhileStillHidden(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                f.startDigging(h);
                f.advanceHidden(h, 61);
                f.player.moveTo(Vec3.atBottomCenterOf(f.origin.offset(2, 3, 0)));
                h.assertFalse(SourceMonsterTargeting.canSee(f.mob, f.player, false), "The neighboring roof cell still hides the moving player");
                h.assertFalse(f.digger.isBreakingOverheadFor(f.player), "The player no longer stands on the mob's current overhead obstruction");
                h.assertFalse(f.selector.canContinueToUse(), "An unrelated roof cell does not preserve extended unseen memory");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void realProtectionVetoEndsOverheadMemoryAndDoesNotRetryRejectedBlock(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                f.startDigging(h);
                int[] calls = {0};
                Consumer<LivingDestroyBlockEvent> cancel = event -> {
                    if (event.getEntity() == f.mob && event.getPos().equals(f.roof)) {
                        calls[0]++;
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, cancel);
                try {
                    f.advanceHidden(h, 40 + 10 * f.digger.stageDelay(f.roof));
                } finally {
                    NeoForge.EVENT_BUS.unregister(cancel);
                }
                h.assertValueEqual(calls[0], 1, "The final real mining action observes the actual cancellable protection event");
                h.assertTrue(h.getLevel().getBlockState(f.roof).is(Blocks.DIRT), "Protection leaves the opaque overhead support intact");
                h.assertFalse(f.digger.isBreakingOverheadFor(f.player), "A veto stops the actual goal's overhead-memory ownership");
                h.assertFalse(f.digger.canUse(), "The protected overhead cell remains rejected instead of starting a fresh attempt");
                h.assertFalse(f.selector.canContinueToUse(), "Protected blocks cannot preserve the acquired unseen player indefinitely");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void disablingMobGriefingImmediatelyEndsExtendedOverheadMemory(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                f.startDigging(h);
                f.advanceHidden(h, 61);
                boolean previous = h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, h.getLevel().getServer());
                    h.assertFalse(f.digger.isBreakingOverheadFor(f.player), "Current grief permission is rechecked while remembering the target");
                    h.assertFalse(f.selector.canContinueToUse(), "Disallowed overhead mining cannot extend unseen memory");
                } finally {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previous, h.getLevel().getServer());
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void restoredOverheadProgressWaitsForPendingPlayerTargetBeforeResuming(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h)) {
                f.startDigging(h);
                f.advanceHidden(h, 47);
                var saved = f.mob.getPersistentData().getCompound("mite_digging_031").copy();
                int progress = saved.getInt("progress"), cooldown = saved.getInt("cooldown");
                h.assertTrue(saved.getBoolean("overhead") && progress == 0 && cooldown < 150,
                        "The real started goal saves its overhead mode, completed first stage and remaining stage timer");
                f.digger.stop();
                f.mob.goalSelector.removeGoal(f.digger);
                var restored = new R196DiggerGoal(f.mob);
                f.mob.goalSelector.addGoal(1, restored);
                f.mob.getPersistentData().put("mite_digging_031", saved.copy());
                f.mob.setTarget(null);
                try {
                    h.assertFalse(restored.canUse(), "A freshly loaded goal waits while the player's pending UUID has not resolved");
                    h.assertTrue(f.mob.getPersistentData().getCompound("mite_digging_031").equals(saved),
                            "The target restoration gap preserves the complete saved overhead state without clearing or restarting it");
                    h.assertValueEqual(restored.progress(), progress, "The loaded crack stage survives the unresolved player");
                    h.assertValueEqual(restored.cooldown(), cooldown, "The loaded stage countdown survives the unresolved player");
                    h.assertFalse(restored.isBreakingOverheadFor(f.player), "Waiting for a player does not claim running ownership");
                    f.mob.setTarget(f.player);
                    h.assertTrue(restored.canUse(), "Resolving the same acquired player makes the saved overhead work eligible again");
                    restored.start();
                    h.assertValueEqual(restored.progress(), progress, "Resuming the actual goal retains its exact saved mining stage");
                    h.assertValueEqual(restored.cooldown(), cooldown, "Resuming the actual goal retains its exact saved countdown");
                    h.assertTrue(restored.isBreakingOverheadFor(f.player), "Only the resumed started goal now preserves this player's unseen memory");
                    restored.tick();
                    h.assertValueEqual(restored.progress(), progress, "The next action continues inside the restored stage");
                    h.assertValueEqual(restored.cooldown(), cooldown - 1, "The next action decrements the saved timer instead of restarting forty ticks");
                } finally {
                    restored.stop();
                    f.mob.goalSelector.removeGoal(restored);
                }
            }
            h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin, roof;
        final Zombie mob;
        final Player player;
        final R196DiggerGoal digger;
        final SourceMonsterTargeting.Nearest selector;
        final long previousTime;

        Fixture(GameTestHelper h) {
            helper = h;
            previousTime = h.getLevel().getDayTime();
            h.getLevel().setDayTime(18_000);
            origin = new BlockPos(660_000 + IDS.getAndIncrement() * 32, h.absolutePos(BlockPos.ZERO).getY() + 10, 660_000);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -1; y <= 5; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            mob = EntityType.ZOMBIE.create(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.getPersistentData().putBoolean(R196MonsterAi.SMART, false);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            h.getLevel().addFreshEntity(mob);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(origin.above(3)));
            selector = new SourceMonsterTargeting.Nearest(mob, Player.class, 0, true);
            ((SourceMonsterTargetMemory)mob).mite$retarget(player);
            h.assertTrue(selector.canUse(), "An accepted queued player starts the actual source target selector before the roof hides it");
            selector.start();
            var installed = R196DiggerGoal.first(mob);
            if (installed == null) {
                installed = new R196DiggerGoal(mob);
                mob.goalSelector.addGoal(1, installed);
            }
            digger = installed;
            mob.goalSelector.removeAllGoals(goal -> goal != digger);
            roof = origin.above(2);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                h.getLevel().setBlock(roof.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 2);
            h.assertFalse(R196MonsterAi.diggingEnabled(mob), "The ordinary unarmed zombie retains its original smart-digging gate");
            h.assertFalse(SourceMonsterTargeting.canSee(mob, player, false), "The real opaque roof removes the acquired player's line of sight");
        }

        void startDigging(GameTestHelper h) {
            h.assertTrue(digger.canUse(), "The actual registered digging goal accepts the nearby overhead support");
            digger.start();
            h.assertTrue(digger.isBreakingOverheadFor(player), "Starting the real goal establishes ownership of this overhead work");
        }

        void advanceHidden(GameTestHelper h, int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                h.assertTrue(selector.canContinueToUse(), "The source target selector retains its player only while the actual overhead attempt remains valid");
                h.assertTrue(digger.canContinueToUse(), "The real digging goal remains active before this mining action");
                digger.tick();
            }
        }

        @Override public void close() {
            digger.stop();
            selector.stop();
            mob.discard();
            player.discard();
            helper.getLevel().setDayTime(previousTime);
        }
    }
}
