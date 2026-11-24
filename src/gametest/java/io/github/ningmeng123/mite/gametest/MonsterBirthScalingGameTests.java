package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual birth, damage and dimension joins retain the intended hostile realm bonus. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterBirthScalingGameTests {
    private static final List<Holder<Attribute>> COMPARED_ATTRIBUTES = List.of(Attributes.MOVEMENT_SPEED, Attributes.FOLLOW_RANGE,
            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.ATTACK_SPEED,
            Attributes.ATTACK_KNOCKBACK);

    private MonsterBirthScalingGameTests() {}

    @GameTest(template = "empty")
    public static void hostileBirthInTheNetherAndUnderworldBoostsCombatStatsAndPreservesCaps(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(1L);
            Mob normal = fixture.spawnZombie(fixture.overworld);
            Mob nether = fixture.spawnZombie(fixture.nether);
            Mob underworld = fixture.spawnZombie(fixture.underworld);
            for (Mob mob : List.of(nether, underworld)) {
                health(helper, mob, normal.getMaxHealth() * 1.5F, normal.getMaxHealth() * 1.5F);
                for (var key : COMPARED_ATTRIBUTES) {
                    if (normal.getAttribute(key) == null) continue;
                    double expected = key.value().sanitizeValue(normal.getAttributeValue(key) * 1.5D);
                    close(helper, mob.getAttributeValue(key), expected, "One birth modifier for " + key);
                }
                close(helper, mob.getAttributeValue(Attributes.SCALE), normal.getAttributeValue(Attributes.SCALE), "Body scale remains native");
                mob.setHealth(mob.getMaxHealth() * .5F);
            }
            fixture.day(65L);
            MonsterDayScaling.refresh(normal);
            for (Mob mob : List.of(nether, underworld)) {
                for (int repeat = 0; repeat < 4; repeat++) MonsterDayScaling.refresh(mob);
                health(helper, mob, normal.getMaxHealth() * 1.5F, normal.getMaxHealth() * .75F);
            }
            fixture.day(1L);
            MonsterDayScaling.refresh(normal);
            for (Mob mob : List.of(nether, underworld)) {
                MonsterDayScaling.refresh(mob);
                health(helper, mob, normal.getMaxHealth() * 1.5F, normal.getMaxHealth() * .75F);
            }

            Mob capped = fixture.spawnZombie(fixture.nether);
            capped.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
            capped.setHealth(capped.getMaxHealth() * .5F);
            MonsterDayScaling.refresh(capped);
            health(helper, capped, 1024F, 512F);
            Mob passive = fixture.spawn(EntityType.COW, fixture.nether);
            Mob normalPassive = fixture.spawn(EntityType.COW, fixture.overworld);
            health(helper, passive, normalPassive.getMaxHealth(), normalPassive.getHealth());
            Mob boss = fixture.spawn(EntityType.WITHER, fixture.nether);
            Mob normalBoss = fixture.spawn(EntityType.WITHER, fixture.overworld);
            close(helper, boss.getMaxHealth(), normalBoss.getMaxHealth(), "Wither retains native boss health");
            close(helper, MonsterDayScaling.attackMultiplier(boss), 1D, "Wither damage remains exempt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trueBirthBonusSurvivesReloadTravelAndDoesNotApplyToOldMobs(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(1L);
            Mob normal = fixture.spawnZombie(fixture.overworld);
            Mob born = fixture.spawnZombie(fixture.nether);
            born.setHealth(born.getMaxHealth() * .5F);
            float normalMaximum = normal.getMaxHealth(), bornMaximum = born.getMaxHealth();
            double bornSpeed = born.getAttributeValue(Attributes.MOVEMENT_SPEED);
            CompoundTag saved = born.saveWithoutId(new CompoundTag());
            born.discard();
            Mob loaded = fixture.zombie(fixture.nether);
            loaded.load(saved);
            fixture.loadedJoin(loaded);
            health(helper, loaded, bornMaximum, bornMaximum * .5F);
            close(helper, loaded.getAttributeValue(Attributes.MOVEMENT_SPEED), bornSpeed, "Loading retains one speed modifier");
            Mob transferred = fixture.move(loaded, fixture.overworld);
            health(helper, transferred, bornMaximum, bornMaximum * .5F);
            close(helper, transferred.getAttributeValue(Attributes.MOVEMENT_SPEED), bornSpeed, "Travel retains the birth modifier");

            Mob tourist = fixture.move(normal, fixture.underworld);
            health(helper, tourist, normalMaximum, normalMaximum);
            close(helper, MonsterDayScaling.attackMultiplier(tourist), 1D, "An Overworld birth keeps neutral outgoing damage after travel");

            Mob preChange = fixture.zombie(fixture.nether);
            preChange.setHealth(preChange.getMaxHealth() * .5F);
            CompoundTag old = preChange.saveWithoutId(new CompoundTag());
            Mob legacy = fixture.zombie(fixture.nether);
            legacy.load(old);
            fixture.loadedJoin(legacy);
            health(helper, legacy, normalMaximum, normalMaximum * .5F);
            Mob migrated = fixture.move(legacy, fixture.underworld);
            health(helper, migrated, normalMaximum, normalMaximum * .5F);
            close(helper, MonsterDayScaling.attackMultiplier(migrated), 1D, "Existing mobs retain neutral damage on subsequent joins");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dimensionAndDateMultiplyTheCompletedMeleeAndOwnedArrowExactlyOnce(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.day(65L);
            Mob normal = fixture.spawnZombie(fixture.overworld);
            Mob nether = fixture.spawnZombie(fixture.nether);
            Mob underworld = fixture.spawnZombie(fixture.underworld);
            var meleeBonus = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite_test", "birth_melee_bonus"),
                    2D, AttributeModifier.Operation.ADD_VALUE);
            for (Mob mob : List.of(normal, nether, underworld))
                mob.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(meleeBonus);
            double normalMelee = melee(helper, fixture, normal);
            double normalArrow = arrow(helper, fixture, normal);
            for (Mob mob : List.of(nether, underworld)) {
                close(helper, melee(helper, fixture, mob), normalMelee * 1.5D, "Multiply completed native melee once after the independent bonus");
                close(helper, arrow(helper, fixture, mob), normalArrow * 1.5D, "Multiply the real owned-arrow hit only once");
            }
        }
        helper.succeed();
    }

    private static double melee(GameTestHelper helper, Fixture fixture, Mob attacker) {
        Mob target = fixture.target(attacker);
        helper.assertTrue(attacker.doHurtTarget(target), "The native melee transaction lands");
        return target.getMaxHealth() - target.getHealth();
    }

    private static double arrow(GameTestHelper helper, Fixture fixture, Mob attacker) {
        Mob target = fixture.target(attacker);
        var arrow = EntityType.ARROW.create(attacker.level());
        arrow.setOwner(attacker);
        helper.assertTrue(target.hurt(target.damageSources().arrow(arrow, attacker), 6F), "The owned arrow transaction lands");
        return target.getMaxHealth() - target.getHealth();
    }

    private static void health(GameTestHelper helper, Mob mob, float maximum, float current) {
        close(helper, mob.getMaxHealth(), maximum, "Maximum health");
        close(helper, mob.getHealth(), current, "Current health preserves its fraction");
    }

    private static void close(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-4D, description + ": " + actual + " vs " + expected);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel overworld, nether, underworld;
        private final long previousTime;
        private final List<Mob> mobs = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var server = helper.getLevel().getServer();
            overworld = server.overworld(); nether = server.getLevel(Level.NETHER); underworld = server.getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(nether != null && underworld != null, "Both hostile dimensions are present");
            previousTime = overworld.getDayTime();
        }

        void day(long value) { overworld.setDayTime((value - 1L) * 24_000L + 6_000L); }

        private <T extends Mob> T create(EntityType<T> type, ServerLevel level) {
            T mob = type.create(level);
            if (mob == null) throw new AssertionError("Fixture entity did not create: " + type);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
            level.getChunkAt(pos);
            mobs.add(mob);
            mob.moveTo(pos.getCenter());
            mob.setNoAi(true);
            mob.setNoGravity(true);
            return mob;
        }

        Mob zombie(ServerLevel level) {
            Mob mob = create(EntityType.ZOMBIE, level);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(28D);
            mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(6D);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.8D);
            return mob;
        }

        <T extends Mob> T spawn(EntityType<T> type, ServerLevel level) { return join(create(type, level)); }
        Mob spawnZombie(ServerLevel level) { return join(zombie(level)); }

        <T extends Mob> T join(T mob) {
            ((MobSpawnTypeAccessor)mob).mite$setSpawnType(MobSpawnType.SPAWN_EGG);
            helper.assertTrue(mob.level().addFreshEntity(mob), "The fresh fixture enters " + mob.level().dimension().location());
            return mob;
        }

        void loadedJoin(Mob mob) {
            var event = new EntityJoinLevelEvent(mob, mob.level(), true);
            NeoForge.EVENT_BUS.post(event);
            helper.assertFalse(event.isCanceled(), "The saved mob passes the loaded-entity event");
            join(mob);
        }

        Mob move(Mob mob, ServerLevel targetLevel) {
            targetLevel.getChunkAt(mob.blockPosition());
            var entity = mob.changeDimension(new DimensionTransition(targetLevel, mob.position(), Vec3.ZERO,
                    0F, 0F, DimensionTransition.DO_NOTHING));
            helper.assertTrue(entity instanceof Mob && entity != mob && entity.level() == targetLevel, "The actual dimension transfer succeeds");
            Mob moved = (Mob)entity;
            mobs.add(moved);
            return moved;
        }

        Mob target(Mob attacker) {
            Mob target = create(EntityType.COW, (ServerLevel)attacker.level());
            target.moveTo(attacker.position().add(0D, 0D, 1D));
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            target.setHealth(200F);
            return target;
        }

        @Override public void close() {
            mobs.forEach(Mob::discard);
            overworld.setDayTime(previousTime);
        }
    }
}
