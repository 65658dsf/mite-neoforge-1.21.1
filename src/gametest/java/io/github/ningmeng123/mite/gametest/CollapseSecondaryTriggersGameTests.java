package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196Silverfish;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native removal paths are exercised separately from the core collapse entry points. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CollapseSecondaryTriggersGameTests {
    private static BlockPos support(GameTestHelper h) { return h.absolutePos(new BlockPos(2, 4, 2)); }
    private static void put(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, 2); }
    private static void column(ServerLevel level, BlockPos pos, BlockState support) {
        put(level, pos.below(3), Blocks.STONE.defaultBlockState());
        put(level, pos.below(2), Blocks.AIR.defaultBlockState());
        put(level, pos.below(), Blocks.AIR.defaultBlockState());
        put(level, pos, support);
        put(level, pos.above(), Blocks.DIRT.defaultBlockState());
    }
    private static Goal goal(net.minecraft.world.entity.Mob mob, String simpleName) {
        return mob.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                .filter(g -> g.getClass().getSimpleName().equals(simpleName)).findFirst().orElseThrow();
    }
    private static void assertOneFall(GameTestHelper h, SpawnedEntityCapture capture, String cause) {
        h.assertValueEqual(capture.live(FallingBlockEntity.class).size(), 1,
                cause + " releases exactly the directly overlying source dirt block");
    }

    @GameTest(template = "empty")
    public static void realExplosionSchedulesAboveWithoutImmediateOrHorizontalFall(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = support(h);
        column(level, p, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos separate = p.east(2).above();
        put(level, separate.below(), Blocks.AIR.defaultBlockState());
        put(level, separate, Blocks.DIRT.defaultBlockState());
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity)) {
            var explosion = new Explosion(level, null, p.getX()+.5, p.getY()+.5, p.getZ()+.5,
                    1F, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(p); explosion.finalizeExplosion(false);
            h.assertTrue(level.getBlockState(p).isAir(), "The native finalization actually removes the selected support");
            h.assertTrue(capture.live(FallingBlockEntity.class).isEmpty(), "Explosion undermining is delayed, not an immediate tryFall");
            h.assertTrue(level.getBlockTicks().hasScheduledTick(p.above(), Blocks.DIRT), "The exact source-overhead cell receives its scheduled tick");
            h.assertFalse(level.getBlockTicks().hasScheduledTick(separate, Blocks.DIRT), "A separate unsupported neighbor is not scheduled by the blast");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nondestructiveExplosionDoesNotUnderminePreservedSupport(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = support(h);
        for (var mode : new Explosion.BlockInteraction[]{Explosion.BlockInteraction.KEEP, Explosion.BlockInteraction.TRIGGER_BLOCK}) {
            column(level, p, Blocks.OAK_PLANKS.defaultBlockState());
            var explosion = new Explosion(level, null, p.getX(), p.getY(), p.getZ(), 1F, false, mode);
            explosion.getToBlow().add(p); explosion.finalizeExplosion(false);
            h.assertTrue(level.getBlockState(p).is(Blocks.OAK_PLANKS), "Nondestructive native modes preserve the support");
            h.assertFalse(level.getBlockTicks().hasScheduledTick(p.above(), Blocks.DIRT), "A block effect without removal cannot undermine the dirt");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void decayingNativeLeavesReleaseOverlyingDirt(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = support(h);
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 7);
        column(level, p, leaf);
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity)) {
            leaf.randomTick(level, p, level.random);
            h.assertTrue(!level.getBlockState(p).is(Blocks.OAK_LEAVES), "Actual native leaf decay removes the support");
            assertOneFall(h, capture, "Natural leaf decay");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void grassCapsSurviveActualExplosionAndLeafDecaySupportRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = support(helper);
        column(level, position, Blocks.OAK_PLANKS.defaultBlockState());
        put(level, position.above(), Blocks.GRASS_BLOCK.defaultBlockState());
        try (var capture = new SpawnedEntityCapture(level, entity -> entity instanceof FallingBlockEntity)) {
            var explosion = new Explosion(level, null, position.getX() + .5, position.getY() + .5, position.getZ() + .5,
                    1F, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(position);
            explosion.finalizeExplosion(false);
            helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockState(position.above()).is(Blocks.GRASS_BLOCK),
                    "Native explosion support removal leaves its grass cap suspended");
            helper.assertFalse(level.getBlockTicks().hasScheduledTick(position.above(), Blocks.GRASS_BLOCK),
                    "Explosion does not schedule the excluded grass block");
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 7);
            column(level, position, leaves);
            put(level, position.above(), Blocks.GRASS_BLOCK.defaultBlockState());
            leaves.randomTick(level, position, level.random);
            helper.assertTrue(!level.getBlockState(position).is(Blocks.OAK_LEAVES)
                            && level.getBlockState(position.above()).is(Blocks.GRASS_BLOCK),
                    "Native leaf decay also leaves grass suspended rather than starting an immediate collapse");
            helper.assertFalse(level.getBlockTicks().hasScheduledTick(position.above(), Blocks.GRASS_BLOCK),
                    "Leaf decay cannot leave a delayed grass collapse behind");
            helper.assertTrue(capture.live(FallingBlockEntity.class).isEmpty(), "Neither secondary support-removal path creates a grass entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistentLeavesAndOrdinaryWorldEditsDoNotCauseCollapse(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = support(h);
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        column(level, p, leaf);
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity)) {
            leaf.randomTick(level, p, level.random);
            h.assertTrue(level.getBlockState(p).is(Blocks.OAK_LEAVES), "Persistent leaves do not decay");
            level.removeBlock(p, false);
            h.assertTrue(capture.live(FallingBlockEntity.class).isEmpty(), "An arbitrary Level.removeBlock is not a collapse trigger");
            h.assertFalse(level.getBlockTicks().hasScheduledTick(p.above(), Blocks.DIRT), "Ordinary neighbor notification does not turn source dirt into sand");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeEndermanPickupReleasesOnlyOverlyingDirt(GameTestHelper h) {
        var level = h.getLevel(); BlockPos origin = support(h);
        EnderMan mob = EntityType.ENDERMAN.create(level);
        mob.moveTo(origin.getX()+.5, origin.getY(), origin.getZ()+.5, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true);
        // The actual goal uses precisely these three random draws. Locate a deterministic unobstructed lateral pickup.
        long seed = 0; BlockPos p;
        for (;;) {
            mob.getRandom().setSeed(seed);
            p = BlockPos.containing(mob.getX()-2+mob.getRandom().nextDouble()*4,
                    mob.getY()+mob.getRandom().nextDouble()*3, mob.getZ()-2+mob.getRandom().nextDouble()*4);
            if (p.equals(origin.east())) break;
            seed++;
        }
        column(level, p, Blocks.DIRT.defaultBlockState());
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity)) {
            mob.getRandom().setSeed(seed); goal(mob, "EndermanTakeBlockGoal").tick();
            h.assertTrue(mob.getCarriedBlock()!=null && mob.getCarriedBlock().is(Blocks.DIRT), "The real enderman goal takes the selected dirt support");
            assertOneFall(h, capture, "Enderman pickup");
        } finally { mob.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceSilverfishRecruitmentReleasesDirtButHonorsMobGriefing(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = support(h);
        boolean old = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        R196Silverfish fish = (R196Silverfish) MiteMobs.type(MobKind.HOARY_SILVERFISH).create(level);
        fish.moveTo(p.getX()+.5, p.getY(), p.getZ()+.5, 0, 0); fish.setNoAi(true); fish.setNoGravity(true);
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity || e instanceof Silverfish)) {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
            column(level, p, Blocks.INFESTED_STONE.defaultBlockState()); fish.recruit();
            h.assertTrue(level.getBlockState(p).is(Blocks.STONE), "No-grief recruitment replaces the host without removing support");
            h.assertTrue(capture.live(FallingBlockEntity.class).isEmpty(), "Preserved host prevents collapse");
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
            column(level, p, Blocks.INFESTED_STONE.defaultBlockState()); fish.recruit();
            assertOneFall(h, capture, "Source silverfish recruitment");
        } finally {
            fish.discard(); level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(old, level.getServer());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSilverfishWakeupTriggersAfterActualHostRemoval(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel(); BlockPos p = support(h);
        boolean old = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        Silverfish fish = EntityType.SILVERFISH.create(level);
        fish.moveTo(p.getX()+.5, p.getY(), p.getZ()+.5, 0, 0); fish.setNoAi(true); fish.setNoGravity(true);
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity || e instanceof Silverfish)) {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
            column(level, p, Blocks.INFESTED_STONE.defaultBlockState());
            Goal wake = goal(fish, "SilverfishWakeUpFriendsGoal");
            Method notify = wake.getClass().getDeclaredMethod("notifyHurt"); notify.setAccessible(true); notify.invoke(wake);
            for (int i=0; i<40 && !level.getBlockState(p).isAir(); i++) wake.tick();
            h.assertTrue(level.getBlockState(p).isAir(), "The native wake-up task really destroys the host block");
            assertOneFall(h, capture, "Native silverfish wake-up");
        } finally {
            fish.discard(); level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(old, level.getServer());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realDiggerRecognizesDirtHazardPausesAndStartsCollapse(GameTestHelper h) {
        var level = h.getLevel(); BlockPos origin = support(h), p = origin.east();
        boolean old = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        Zombie mob = EntityType.ZOMBIE.create(level);
        mob.moveTo(origin.getX()+.5, origin.getY(), origin.getZ()+.5, 0, 0); mob.setNoAi(true); mob.setNoGravity(true);
        var prey = h.makeMockPlayer(GameType.SURVIVAL); prey.moveTo(mob.getX()+3, mob.getY(), mob.getZ(), 0, 0); mob.setTarget(prey);
        var digger = new R196DiggerGoal(mob);
        try (var capture = new SpawnedEntityCapture(level, e -> e instanceof FallingBlockEntity)) {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
            column(level, p, Blocks.DIRT.defaultBlockState());
            h.assertTrue(digger.blockWillFall(p.above()), "Managed source dirt is a falling overhead hazard despite not extending modern FallingBlock");
            h.assertTrue(digger.setBlockToDig(p, false), "A real source digger accepts the dirt support");
            digger.start();
            for (int i=0; i<10000 && !level.getBlockState(p).isAir(); i++) digger.tick();
            h.assertTrue(level.getBlockState(p).isAir(), "The actual digging progress completes a support removal");
            h.assertValueEqual(digger.pauseTicks(), 10, "The monster keeps its source ten-tick overhead-fall pause");
            assertOneFall(h, capture, "Monster digging");
        } finally {
            digger.stop(); mob.discard(); level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(old, level.getServer());
        }
        h.succeed();
    }
}
