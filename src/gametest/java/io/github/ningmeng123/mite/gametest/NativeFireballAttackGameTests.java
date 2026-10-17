package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.NativeFireballAttacks;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Installed goals create actual native projectiles; independent seed arithmetic checks their launch vectors. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NativeFireballAttackGameTests {
    private NativeFireballAttackGameTests() {}
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, int x, int y) {
        T mob = type.create(h.getLevel());
        h.assertTrue(mob != null, "Native fixture creates its entity");
        mob.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(x, y, 5))));
        mob.setNoAi(true); mob.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(mob), "Native join installs source attack");
        return mob;
    }
    private static <T extends Goal> T goal(Mob owner, Class<T> type) {
        return owner.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(type::isInstance)
                .map(type::cast).findFirst().orElseThrow();
    }
    private static final class Shots implements AutoCloseable {
        private final Consumer<EntityJoinLevelEvent> listener;
        final List<AbstractHurtingProjectile> values = new ArrayList<>();
        Shots(Mob owner) {
            listener = event -> { if (event.getEntity() instanceof AbstractHurtingProjectile shot && shot.getOwner() == owner) values.add(shot); };
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, listener);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(listener); for (var shot : values) shot.discard(); }
    }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String message) {
        h.assertTrue(actual.distanceTo(expected) < 1E-8, message + ": " + actual + " expected " + expected);
    }
    private static void wall(GameTestHelper h, int x, boolean present) {
        for (int y = 1; y <= 14; y++) for (int z = 2; z <= 8; z++)
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x, y, z)), (present ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }

    @GameTest(template = "fireball_r196") public static void nativeJoinReplacesOnlyAttacksAndDoesNotDuplicateOnReload(GameTestHelper h) {
        Blaze blaze = mob(h, EntityType.BLAZE, 4, 4); Ghast ghast = mob(h, EntityType.GHAST, 16, 4);
        int blazeGoals = blaze.goalSelector.getAvailableGoals().size(), ghastGoals = ghast.goalSelector.getAvailableGoals().size();
        NativeFireballAttacks.install(blaze); NativeFireballAttacks.install(ghast);
        h.assertTrue(blaze.goalSelector.getAvailableGoals().size() == blazeGoals && ghast.goalSelector.getAvailableGoals().size() == ghastGoals,
                "Repeated installation does not add attacks");
        h.assertTrue(blazeGoals >= 5 && ghastGoals == 3, "Native stroll/look and ghast float/look goals remain");
        h.assertTrue(ghast.targetSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof NativeFireballAttacks.GhastTarget),
                "Source spherical 100-block target search replaces the four-block vertical band");
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void actualBlazeCreatesThreeShotsAtSourceTicksAndChargesNativeFlag(GameTestHelper h) {
        Blaze blaze = mob(h, EntityType.BLAZE, 4, 4); Mob target = mob(h, EntityType.COW, 24, 4); blaze.setTarget(target);
        var goal = goal(blaze, NativeFireballAttacks.BlazeAttack.class);
        List<Integer> fired = new ArrayList<>();
        try (Shots shots = new Shots(blaze)) {
            for (int tick = 0; tick <= 78; tick++) {
                blaze.tickCount = tick; int before = shots.values.size(); goal.tick();
                if (shots.values.size() > before) fired.add(tick);
                if (tick == 0) h.assertTrue(blaze.isOnFire(), "Native synchronized charged flag begins at source charge step");
                if (tick == 38) h.assertTrue(!blaze.isOnFire(), "Burst end clears native flag");
            }
            h.assertValueEqual(fired, List.of(20, 26, 32, 78), "Real native small fireballs follow the source burst, recovery and next charge");
            h.assertTrue(shots.values.stream().allMatch(s -> s instanceof SmallFireball), "Blaze retains native small fireball identity");
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void blazeThirtyBlockBoundaryAndOpaqueObstructionPreventShooting(GameTestHelper h) {
        Blaze blaze = mob(h, EntityType.BLAZE, 4, 4); Mob target = mob(h, EntityType.COW, 34, 4); blaze.setTarget(target);
        var goal = goal(blaze, NativeFireballAttacks.BlazeAttack.class);
        try (Shots shots = new Shots(blaze)) {
            for (int tick = 0; tick <= 30; tick++) { blaze.tickCount = tick; goal.tick(); }
            h.assertTrue(shots.values.isEmpty() && goal.attackStep() == 0, "Exactly30 is outside source range even though modern follow range is48");
            target.setPos(blaze.position().add(20, 0, 0)); wall(h, 14, true);
            for (int tick = 31; tick <= 60; tick++) { blaze.tickCount = tick; goal.tick(); }
            h.assertTrue(shots.values.isEmpty() && goal.attackStep() == 0, "Opaque wall prevents source attack, including charge");
            wall(h, 14, false);
            for (int tick = 61; tick <= 81; tick++) { blaze.tickCount = tick; goal.tick(); }
            h.assertTrue(shots.values.size() == 1, "Visible target inside30 begins source charge and fires");
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void blazeMovingTargetUsesBothSourceScatterStagesAndCentreHeight(GameTestHelper h) {
        Blaze blaze = mob(h, EntityType.BLAZE, 4, 4); Mob target = mob(h, EntityType.COW, 24, 4); blaze.setTarget(target);
        target.setDeltaMovement(0, .8, .25);
        var goal = goal(blaze, NativeFireballAttacks.BlazeAttack.class);
        blaze.tickCount = 0; goal.tick(); blaze.tickCount = 20;
        long seed = 731; blaze.getRandom().setSeed(seed); RandomSource random = RandomSource.create(seed);
        Vec3 delta = target.position().add(0, target.getBbHeight() * .5F, 0).subtract(blaze.position().add(0, blaze.getBbHeight() * .5F, 0));
        float lead = (float)Math.pow(delta.lengthSqr(), .42D) * 2F * Math.min(.5F + random.nextFloat(), 1F);
        float spread = (float)Math.sqrt(blaze.distanceTo(target)) * .25F;
        double dx = delta.x + random.nextGaussian() * spread, dz = delta.z + .25D * lead + random.nextGaussian() * spread;
        Vec3 expected = new Vec3(dx + random.nextGaussian() * .4D, delta.y + random.nextGaussian() * .4D,
                dz + random.nextGaussian() * .4D).normalize().scale(.1D);
        try (Shots shots = new Shots(blaze)) {
            goal.tick(); h.assertTrue(shots.values.size() == 1, "Actual moving-target blaze shot exists");
            var shot = shots.values.getFirst();
            vector(h, shot.getDeltaMovement(), expected, "Both source Gaussian stages and horizontal lead reach native launch");
            h.assertTrue(shot.getZ() == blaze.getZ() && Math.abs(shot.getY() - blaze.getY(.5D) - .5D) < 1E-8,
                    "Source blaze muzzle is above centre; target vertical velocity is not predicted");
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void blazeMeleeNeedsVerticalOverlapAndSharesTwentyTickCooldown(GameTestHelper h) {
        Blaze blaze = mob(h, EntityType.BLAZE, 4, 4); Mob target = mob(h, EntityType.COW, 5, 4); blaze.setTarget(target);
        var goal = goal(blaze, NativeFireballAttacks.BlazeAttack.class); float health = target.getHealth();
        blaze.tickCount = 0; goal.tick();
        h.assertTrue(target.getHealth() < health && goal.attackStep() == 0, "Close vertically overlapping target receives native melee instead of fireball");
        target.setPos(blaze.position().add(0, 1.9, 0)); blaze.tickCount = 20; goal.tick();
        h.assertTrue(goal.attackStep() == 1, "Close target without vertical overlap begins ranged charge");
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void actualGhastUsesSourcePredictiveWanderAndFourBlockMuzzle(GameTestHelper h) {
        Ghast ghast = mob(h, EntityType.GHAST, 4, 4); Mob target = mob(h, EntityType.COW, 34, 9); ghast.setTarget(target);
        target.setDeltaMovement(-.1D, .75D, .2D);
        var goal = goal(ghast, NativeFireballAttacks.GhastAttack.class);
        for (int tick = 0; tick < 19; tick++) goal.tick();
        h.assertTrue(ghast.isCharging(), "Source charging is synchronized before release");
        Vec3 origin = ghast.position().add(0, ghast.getBbHeight() * .5F, 0), targetCenter = target.position().add(0, target.getBbHeight() * .5F, 0);
        long seed = 912; ghast.getRandom().setSeed(seed); RandomSource random = RandomSource.create(seed);
        float lead = (float)Math.pow(origin.distanceToSqr(targetCenter), .44D) * (.5F + random.nextFloat());
        Vec3 aim = targetCenter.add(-.1D * lead, 0, .2D * lead).subtract(origin).add(0, -.5D, 0);
        Vec3 direction = new Vec3(aim.x * (1F + (random.nextFloat() - random.nextFloat()) * .1F),
                aim.y * (1F + (random.nextFloat() - random.nextFloat()) * .1F),
                aim.z * (1F + (random.nextFloat() - random.nextFloat()) * .1F)).normalize();
        try (Shots shots = new Shots(ghast)) {
            goal.tick(); h.assertTrue(shots.values.size() == 1 && shots.values.getFirst() instanceof LargeFireball, "Real native large fireball released");
            var shot = shots.values.getFirst();
            vector(h, shot.position(), origin.add(direction.scale(4D)), "Source launch advances4 along actual scattered trajectory");
            vector(h, shot.getDeltaMovement(), direction.scale(.1D), "Source lead and multiplicative wander determine modern native initial motion");
            h.assertTrue(goal.chargeTime() == -40 && !ghast.isCharging(), "Source recovery clears native charging");
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void ghastSixtyFourBoundaryAndLostSightHaveSourceChargeDecay(GameTestHelper h) {
        Ghast ghast = mob(h, EntityType.GHAST, 4, 4); Mob target = mob(h, EntityType.COW, 24, 4); ghast.setTarget(target);
        var goal = goal(ghast, NativeFireballAttacks.GhastAttack.class);
        for (int tick = 0; tick < 12; tick++) goal.tick();
        wall(h, 14, true); goal.tick(); goal.tick();
        h.assertTrue(goal.chargeTime() == 10 && !ghast.isCharging(), "Blocked positive charge decays one per owner tick");
        wall(h, 14, false); target.setPos(ghast.position().add(64, 0, 0));
        goal.tick(); h.assertTrue(goal.chargeTime() == 9, "Exactly64 is outside source firing range");
        target.setPos(ghast.position().add(63.99D, 0, 0));
        try (Shots shots = new Shots(ghast)) {
            for (int tick = 0; tick < 11; tick++) goal.tick();
            h.assertTrue(shots.values.size() == 1 && goal.chargeTime() == -40, "Inside64 resumes and fires without full restart");
            target.setPos(ghast.position().add(64, 0, 0)); goal.tick();
            h.assertTrue(goal.chargeTime() == -40, "Lost sight/range pauses negative recovery");
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void installedGhastTargetSearchUsesSourceSphereAndVulnerablePlayers(GameTestHelper h) {
        Ghast ghast = mob(h, EntityType.GHAST, 4, 4);
        var targetGoal = ghast.targetSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                .filter(NativeFireballAttacks.GhastTarget.class::isInstance).map(NativeFireballAttacks.GhastTarget.class::cast).findFirst().orElseThrow();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            try {
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                player.moveTo(ghast.position().add(30, 12, 0)); targetGoal.tick();
                h.assertTrue(ghast.getTarget() == player, "Visible player above modern four-block vertical band is acquired");
                Vec3 center = ghast.position().add(0, ghast.getBbHeight() * .5F, 0);
                player.moveTo(center.add(100, 0, 0));
                for (int tick = 0; tick < 21; tick++) targetGoal.tick();
                h.assertTrue(ghast.getTarget() == null, "Exactly100 from source centre is outside target range");
                player.moveTo(center.add(99.9D, 0, 0)); targetGoal.tick();
                h.assertTrue(ghast.getTarget() == player, "Inside100 can be tracked despite being outside64 firing range");
                player.setGameMode(GameType.CREATIVE);
                for (int tick = 0; tick < 21; tick++) targetGoal.tick();
                h.assertTrue(ghast.getTarget() == null, "Creative player is not a vulnerable source target");
            } finally { player.server.getPlayerList().remove(player); }
        } finally { channel.finishAndReleaseAll(); ghast.discard(); }
        h.succeed();
    }
}
