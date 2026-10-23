package io.github.ningmeng123.mite.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real side-head acquisition, pending targets and skull emission obey the hunting calendar. @author NingMeng123 */
@GameTestHolder("mite_hunting")
@PrefixGameTestTemplate(false)
public final class WitherHuntingGameTests {
    private WitherHuntingGameTests() {}

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BloodMoonHuntingTestClock clock;
        final List<Mob> entities = new ArrayList<>();
        final WitherBoss wither;
        final AABB area;
        Fixture(GameTestHelper helper, ServerLevel level) {
            this.level = level;
            clock = new BloodMoonHuntingTestClock(helper.getLevel());
            var origin = helper.absolutePos(new BlockPos(1, 10, 1));
            area = new AABB(origin).inflate(24D);
            level.getChunkAt(origin);
            wither = mob(EntityType.WITHER, origin, 0D);
            wither.setInvulnerableTicks(0);
        }
        <T extends Mob> T mob(EntityType<T> type, BlockPos origin, double dx) {
            T entity = type.create(level);
            if (entity == null) throw new AssertionError("Cannot construct wither hunting fixture " + type);
            entity.moveTo(origin.getX() + .5D + dx, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            entity.setNoAi(true); entity.setNoGravity(true);
            level.addFreshEntity(entity); entities.add(entity); return entity;
        }
        Mob cow() { return mob(EntityType.COW, wither.blockPosition(), 4D); }
        void time(long value) { level.setDayTime(value); }
        void ai() throws ReflectiveOperationException {
            Method tick = WitherBoss.class.getDeclaredMethod("customServerAiStep"); tick.setAccessible(true);
            // Make the native side-head timer eligible without running unrelated living movement ticks.
            wither.tickCount += 1_000; tick.invoke(wither);
        }
        void shoot(int head, LivingEntity target) throws ReflectiveOperationException {
            Method attack = WitherBoss.class.getDeclaredMethod("performRangedAttack", int.class, LivingEntity.class);
            attack.setAccessible(true); attack.invoke(wither, head, target);
        }
        List<WitherSkull> skulls() {
            return level.getEntitiesOfClass(WitherSkull.class, area, skull -> skull.getOwner() == wither);
        }
        void clearSkulls() { skulls().forEach(WitherSkull::discard); }
        @Override public void close() { clearSkulls(); entities.forEach(Mob::discard); clock.close(); }
    }

    @GameTest(template = "empty")
    public static void sideHeadsOnlyAcquirePassivePreyOnAnActualBloodNight(GameTestHelper helper) throws ReflectiveOperationException {
        try (var f = new Fixture(helper, helper.getLevel())) {
            Mob cow = f.cow();
            for (long time : new long[]{18_000L, 31L * 24_000L + 6_000L, 127L * 24_000L + 18_000L}) {
                f.time(time); f.ai();
                helper.assertValueEqual(f.wither.getAlternativeTarget(1), 0, "The first side head rejects passive prey outside a blood night");
                helper.assertValueEqual(f.wither.getAlternativeTarget(2), 0, "The second side head rejects passive prey outside a blood night");
            }
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); f.ai();
            helper.assertTrue(f.wither.getAlternativeTarget(1) == cow.getId() || f.wither.getAlternativeTarget(2) == cow.getId(),
                    "Native side-head scanning can select the nearby animal on a blood night");
            f.clearSkulls();
            f.time(32L * 24_000L + 1L); f.ai();
            helper.assertValueEqual(f.wither.getAlternativeTarget(1), 0, "Dawn clears the first side head's pending prey");
            helper.assertValueEqual(f.wither.getAlternativeTarget(2), 0, "Dawn clears the second side head's pending prey");
            helper.assertTrue(f.skulls().isEmpty(), "Expired side-head prey is cleared before any aimed skull is emitted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sideHeadSkullBoundaryPreservesPlayersAndRetaliation(GameTestHelper helper) throws ReflectiveOperationException {
        try (var f = new Fixture(helper, helper.getLevel())) {
            Mob cow = f.cow(); f.time(18_000L);
            f.shoot(2, cow);
            helper.assertTrue(f.skulls().isEmpty(), "The actual side-head attack method cannot shoot passive prey on an ordinary night");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(cow.position());
            f.shoot(2, player);
            helper.assertValueEqual(f.skulls().size(), 1, "Side heads can still shoot players outside the blood moon");
            f.clearSkulls();
            f.wither.setLastHurtByMob(cow); f.shoot(3, cow);
            helper.assertValueEqual(f.skulls().size(), 1, "A side head can still retaliate against the mob that attacked the wither");
            f.clearSkulls(); f.wither.setLastHurtByMob(null);
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); f.shoot(2, cow);
            helper.assertValueEqual(f.skulls().size(), 1, "A blood night permits the original side-head skull attack against passive prey");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherSideHeadsRejectAnimalsEvenOnTheOverworldBloodDate(GameTestHelper helper) throws ReflectiveOperationException {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The normal Nether dimension is available to the hunting fixture");
        try (var f = new Fixture(helper, nether)) {
            Mob cow = f.cow(); f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT);
            f.wither.setAlternativeTarget(1, cow.getId()); f.wither.setAlternativeTarget(2, cow.getId());
            f.ai(); f.shoot(2, cow);
            helper.assertValueEqual(f.wither.getAlternativeTarget(1), 0, "A pending Nether side-head target is rejected without an actual blood night");
            helper.assertValueEqual(f.wither.getAlternativeTarget(2), 0, "The other Nether side head cannot reacquire passive prey");
            helper.assertTrue(f.skulls().isEmpty(), "The Nether never inherits overworld blood-night prey attacks");
        }
        helper.succeed();
    }
}
