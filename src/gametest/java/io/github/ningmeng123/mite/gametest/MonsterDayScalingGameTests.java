package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Calendar modifiers through native attributes, actual damage transactions and persisted entity lifecycles. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterDayScalingGameTests {
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath("mite", "day_health_scaling");
    private static final ResourceLocation LEADER = ResourceLocation.fromNamespaceAndPath("mite_test", "leader_health");
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private MonsterDayScalingGameTests() {}

    private static Mob mob(GameTestHelper helper, EntityType<?> type) {
        Mob mob = (Mob)type.create(helper.getLevel());
        if (mob == null) throw new AssertionError("Cannot construct native scaling fixture " + type);
        mob.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        mob.setNoAi(true); mob.setNoGravity(true);
        return mob;
    }

    private static Mob cow(GameTestHelper helper) {
        Mob cow = mob(helper, EntityType.COW);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); cow.setHealth(200F);
        return cow;
    }

    private static void set(GameTestHelper helper, long highestDay) {
        helper.getLevel().getServer().overworld().setDayTime(6_000L);
        BloodMoonInvasionData.get(helper.getLevel()).restoreProgress(highestDay);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-4, description + ": " + actual + " vs " + expected);
    }

    private static void health(GameTestHelper helper, Mob mob, float maximum, float current) {
        close(helper, mob.getMaxHealth(), maximum, "Native maximum health");
        close(helper, mob.getHealth(), current, "Current health retains its intended proportion");
    }

    private static void modifier(GameTestHelper helper, Mob mob, double amount) {
        var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        var modifier = attribute.getModifier(HEALTH);
        helper.assertTrue(modifier != null, "The unique calendar health modifier exists");
        close(helper, modifier.amount(), amount, "Calendar multiplier amount");
        helper.assertValueEqual(modifier.operation(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                "Calendar health composes with native leader multipliers");
        helper.assertValueEqual(attribute.getModifiers().stream().filter(entry -> entry.id().equals(HEALTH)).count(), 1L,
                "Repeated updates cannot stack duplicate calendar modifiers");
    }

    private static void hit(GameTestHelper helper, Mob target, DamageSource source, float raw, float expected) {
        target.setHealth(target.getMaxHealth()); target.invulnerableTime = 0; target.setAbsorptionAmount(0F);
        float before = target.getHealth();
        helper.assertTrue(target.hurt(source, raw), "The actual native damage transaction is accepted: " + source);
        close(helper, before - target.getHealth(), expected, "Actual health loss for " + source);
    }

    /** Reconcile both native modifiers and last-equipment snapshots, without adding a fixture to the live world. */
    private static void equipment(LivingEntity entity) throws ReflectiveOperationException {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        method.setAccessible(true); method.invoke(entity);
    }

    @GameTest(template = "empty")
    public static void freshMonstersStayFullAndDayRefreshPreservesWoundsWithoutStacking(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1L);
            Mob monster = mob(helper, EntityType.ZOMBIE);
            MonsterDayScaling.refresh(monster); health(helper, monster, 20F, 20F);
            double attack = monster.getAttributeValue(Attributes.ATTACK_DAMAGE);
            set(helper, 33L);
            MonsterDayScaling.refresh(monster); health(helper, monster, 23.2F, 23.2F); modifier(helper, monster, .16D);
            monster.setHealth(11.6F);
            set(helper, 65L);
            MonsterDayScaling.refresh(monster); health(helper, monster, 26.912F, 13.456F); modifier(helper, monster, .3456D);
            for (int count = 0; count < 20; count++) MonsterDayScaling.refresh(monster);
            health(helper, monster, 26.912F, 13.456F); modifier(helper, monster, .3456D);
            close(helper, monster.getAttributeValue(Attributes.ATTACK_DAMAGE), attack, "Calendar damage is not also multiplied in ATTACK_DAMAGE");
            set(helper, 1L);
            MonsterDayScaling.refresh(monster); health(helper, monster, 26.912F, 13.456F);
            helper.assertValueEqual(monster.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY).getLong("day"), 65L,
                    "An earlier world clock or migration value cannot downgrade an already recorded monster");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void calendarModifierComposesWithLeaderAttributesAndNativeHealthCap(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob monster = mob(helper, EntityType.ZOMBIE);
            monster.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(LEADER, 1.5D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            monster.setHealth(25F);
            MonsterDayScaling.refresh(monster); health(helper, monster, 67.28F, 33.64F);
            helper.assertTrue(monster.getAttribute(Attributes.MAX_HEALTH).hasModifier(LEADER), "The independently installed leader modifier remains intact");
            helper.assertValueEqual(monster.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(), 2,
                    "Leader and calendar growth remain distinct modifiers");
            set(helper, 100_001L);
            MonsterDayScaling.refresh(monster); health(helper, monster, 1024F, 512F);
            set(helper, Long.MAX_VALUE);
            MonsterDayScaling.refresh(monster); health(helper, monster, 1024F, 512F);
            helper.assertTrue(Double.isFinite(monster.getAttributeValue(Attributes.MAX_HEALTH)), "Far-future dates respect the native maximum-health cap");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSaveReloadAndLegacyFirstJoinKeepActualInjuriesAndOnePermanentModifier(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob original = mob(helper, EntityType.ZOMBIE); MonsterDayScaling.refresh(original); original.setHealth(14F);
            CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
            saved.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(original.getType()).toString());
            Mob restored = (Mob)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
            helper.assertTrue(restored != null, "Native recursive loading reconstructs the saved monster");
            for (boolean loaded : new boolean[]{true, false, true}) {
                MonsterDayScaling.join(new EntityJoinLevelEvent(restored, helper.getLevel(), loaded));
                health(helper, restored, 26.912F, 14F); modifier(helper, restored, .3456D);
            }
            Mob legacy = mob(helper, EntityType.ZOMBIE); legacy.setHealth(5F);
            CompoundTag unscaled = new CompoundTag(); legacy.saveWithoutId(unscaled);
            Mob old = mob(helper, EntityType.ZOMBIE); old.load(unscaled);
            MonsterDayScaling.join(new EntityJoinLevelEvent(old, helper.getLevel(), true));
            health(helper, old, 26.912F, 6.728F);
            helper.assertTrue(old.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY).getInt("version") > 0,
                    "First adoption of an old save writes a versioned day snapshot without healing it full");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneFullEntityTickWindowUpdatesExistingMonstersAfterTheHighestDayChanges(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1L);
            Mob monster = mob(helper, EntityType.ZOMBIE); MonsterDayScaling.refresh(monster); monster.setHealth(10F);
            set(helper, 65L);
            int transitions = 0; float previous = monster.getMaxHealth();
            for (int tick = 1; tick <= 20; tick++) {
                monster.tickCount = tick;
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(monster));
                if (monster.getMaxHealth() != previous) transitions++;
                previous = monster.getMaxHealth();
            }
            health(helper, monster, 26.912F, 13.456F);
            helper.assertValueEqual(transitions, 1, "A full native entity-post-tick window updates the existing mob once");
            modifier(helper, monster, .3456D);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hostileSourceBatsShareGrowthWhileAnimalsNativeBatsAndPlayersKeepTheirAttributes(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            for (MobKind kind : List.of(MobKind.VAMPIRE_BAT, MobKind.GIANT_VAMPIRE_BAT, MobKind.NIGHTWING)) {
                Mob bat = mob(helper, MiteMobs.type(kind)); float maximum = bat.getMaxHealth();
                helper.assertTrue(MonsterDayScaling.handles(bat), "MITE attacking ambient bat is part of hostile growth: " + kind);
                MonsterDayScaling.refresh(bat);
                health(helper, bat, maximum * 1.3456F, maximum * 1.3456F);
            }
            for (EntityType<?> type : List.of(EntityType.COW, EntityType.WOLF, EntityType.BAT, MiteMobs.type(MobKind.DIRE_WOLF))) {
                Mob animal = mob(helper, type); float maximum = animal.getMaxHealth(); animal.setHealth(maximum / 2F);
                helper.assertTrue(!MonsterDayScaling.handles(animal), "Friendly/animal-category identities remain outside hostile growth: " + type);
                MonsterDayScaling.refresh(animal); health(helper, animal, maximum, maximum / 2F);
                helper.assertTrue(!animal.getPersistentData().contains(MonsterDayScaling.DATA_KEY), "Excluded animals receive no growth metadata");
            }
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            float maximum = player.getMaxHealth(); player.setHealth(maximum / 2F);
            MonsterDayScaling.join(new EntityJoinLevelEvent(player, helper.getLevel(), false));
            close(helper, player.getMaxHealth(), maximum, "Players do not acquire monster maximum health");
            close(helper, player.getHealth(), maximum / 2F, "Player health is untouched by the monster join hook");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attributedMeleeArrowsExplosionsAndSpellsScaleActualHealthLossExactlyOnce(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob attacker = mob(helper, EntityType.ZOMBIE), target = cow(helper);
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "Native arrow source exists"); arrow.setOwner(attacker);
            for (DamageSource source : List.of(target.damageSources().mobAttack(attacker), target.damageSources().arrow(arrow, attacker),
                    target.damageSources().explosion(attacker, attacker), target.damageSources().indirectMagic(arrow, attacker)))
                hit(helper, target, source, 20F, 23.328F);
            set(helper, 97L);
            hit(helper, target, target.damageSources().arrow(arrow, attacker), 20F, 25.19424F);
            helper.assertTrue(!attacker.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(HEALTH), "No health multiplier is installed on attack damage");
            arrow.discard(); target.discard(); attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void incomingGrowthRunsBeforeNativeNpcArmorRatherThanAfterItsReduction(GameTestHelper helper) throws ReflectiveOperationException {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob attacker = mob(helper, EntityType.ZOMBIE), target = cow(helper);
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            target.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            equipment(target);
            close(helper, target.getAttributeValue(Attributes.ARMOR), 15D, "The real NPC wears native iron armor");
            // At day65 raw20 becomes23.328 before armor: effective armor15-23.328/2=3.336.
            // The independent native formula gives23.328*(1-3.336/25)=20.21511168.
            hit(helper, target, target.damageSources().mobAttack(attacker), 20F, 20.215112F);
            for (EquipmentSlot slot : ARMOR) helper.assertValueEqual(target.getItemBySlot(slot).getDamageValue(), 0,
                    "Calendar growth does not change the established NPC equipment wear policy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void environmentUnownedProjectilesPlayerAndAnimalSourcesNeverGainMonsterDamage(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob target = cow(helper), animal = mob(helper, EntityType.WOLF);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "Native unowned arrow source exists");
            for (DamageSource source : List.of(target.damageSources().generic(), target.damageSources().fall(),
                    target.damageSources().arrow(arrow, null), target.damageSources().explosion(null, null),
                    target.damageSources().mobAttack(animal), target.damageSources().playerAttack(player), target.damageSources().arrow(arrow, player)))
                hit(helper, target, source, 20F, 20F);
            arrow.discard(); target.discard(); animal.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recordedAttackerDaySurvivesClockRollbackAndCanceledIncomingDamageRemainsCanceled(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob attacker = mob(helper, EntityType.ZOMBIE), target = cow(helper);
            MonsterDayScaling.refresh(attacker); set(helper, 1L);
            hit(helper, target, target.damageSources().mobAttack(attacker), 20F, 23.328F);
            LivingIncomingDamageEvent canceled = new LivingIncomingDamageEvent(target,
                    new DamageContainer(target.damageSources().mobAttack(attacker), 20F));
            canceled.setCanceled(true); MonsterDayScaling.damage(canceled);
            close(helper, canceled.getAmount(), 20F, "An already canceled damage event is not altered");
            LivingIncomingDamageEvent huge = new LivingIncomingDamageEvent(target,
                    new DamageContainer(target.damageSources().mobAttack(attacker), Float.MAX_VALUE));
            MonsterDayScaling.damage(huge);
            helper.assertTrue(Float.isFinite(huge.getAmount()), "Scaled damage remains finite at the float storage limit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualZombieBareAttackGrowsBeforeUnscaledWeaponDamageIsAdded(GameTestHelper helper) throws ReflectiveOperationException {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Zombie attacker = (Zombie)mob(helper, EntityType.ZOMBIE);
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6D);
            Mob target = cow(helper);
            float[] raw = {Float.NaN}, incoming = {Float.NaN}; int[] calls = {0};
            Consumer<LivingIncomingDamageEvent> capture = event -> {
                if (event.getEntity() == target && event.getSource().getEntity() == attacker) {
                    raw[0] = event.getOriginalAmount(); incoming[0] = event.getAmount(); calls[0]++;
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, capture);
            try {
                double bareDamage = 0D, bareStrike = 0D;
                for (boolean armed : new boolean[]{false, true}) {
                    attacker.setItemSlot(EquipmentSlot.MAINHAND, armed ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);
                    equipment(attacker);
                    double combined = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    if (!armed) bareDamage = combined;
                    else helper.assertTrue(combined > bareDamage, "Native iron-sword attributes add to the zombie's own attack strength");
                    MonsterDayScaling.refresh(attacker);
                    close(helper, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE), combined,
                            "Refreshing calendar growth does not pre-multiply base or equipped attack attributes");
                    target.setHealth(200F); target.invulnerableTime = 0; calls[0] = 0;
                    helper.assertTrue(attacker.doHurtTarget(target), "Actual zombie melee with the current held stack succeeds");
                    helper.assertValueEqual(calls[0], 1, "A native melee strike enters the common incoming-damage pipeline once");
                    double weaponDelta = combined - bareDamage;
                    double expected = 6D * 1.1664D + weaponDelta;
                    close(helper, raw[0], expected, "Only the naked base attack grows; current weapon modifiers are then added unchanged");
                    close(helper, incoming[0], expected, "A real melee transaction is not multiplied again at the common incoming boundary");
                    close(helper, 200F - target.getHealth(), expected, "Native melee health loss includes grown bare attack plus unscaled weapon damage");
                    close(helper, MonsterDayScaling.attackMultiplier(attacker), 1.1664D, "The current attacker uses the second thirty-two-day reset basis");
                    if (!armed) bareStrike = raw[0];
                    else close(helper, raw[0] - bareStrike, weaponDelta, "Equipping a sword adds exactly its original attack modifier, without date multiplication");
                }
            } finally { NeoForge.EVENT_BUS.unregister(capture); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSixHealthSkeletonKeepsItsInitialSixAndUsesResetPeriodIncrementsWithoutCompoundingOnReload(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1L);
            Mob skeleton = mob(helper, EntityType.SKELETON);
            health(helper, skeleton, 6F, 6F);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 6F, 6F);
            helper.assertTrue(!skeleton.getAttribute(Attributes.MAX_HEALTH).hasModifier(HEALTH),
                    "Initial six-health skeletons are not raised to twenty or assigned a nonneutral date modifier");
            skeleton.setHealth(3F);
            set(helper, 33L);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 9.2F, 4.6F); modifier(helper, skeleton, 3.2D / 6D);
            set(helper, 65L);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 12.912F, 6.456F); modifier(helper, skeleton, 6.912D / 6D);
            for (int repeat = 0; repeat < 20; repeat++) MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 12.912F, 6.456F);
            CompoundTag saved = new CompoundTag(); skeleton.saveWithoutId(saved);
            saved.putString("id", "minecraft:skeleton");
            set(helper, 1L);
            Mob restored = (Mob)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
            helper.assertTrue(restored != null, "A real native skeleton reloads with its date and ordinary max-health attributes");
            for (boolean loaded : new boolean[]{true, false, true}) {
                MonsterDayScaling.join(new EntityJoinLevelEvent(restored, helper.getLevel(), loaded));
                health(helper, restored, 12.912F, 6.456F); modifier(helper, restored, 6.912D / 6D);
            }
            close(helper, restored.getAttributeBaseValue(Attributes.MAX_HEALTH), 6D,
                    "The detached growth calculation preserves the original six-health base across save and read");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twentyAndThirtyHealthSkeletonsUseTheirOwnBasisWhileOtherSixHealthMonstersStayOnOrdinaryGrowth(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (double basis : new double[]{20D, 30D}) {
                set(helper, 1L);
                Mob skeleton = mob(helper, EntityType.SKELETON);
                skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(basis); skeleton.setHealth((float)basis);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, (float)basis, (float)basis);
                set(helper, 33L);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, basis == 20D ? 23.2F : 34.8F, basis == 20D ? 23.2F : 34.8F);
                set(helper, 65L);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, basis == 20D ? 26.912F : 40.368F, basis == 20D ? 26.912F : 40.368F);
                close(helper, skeleton.getAttributeBaseValue(Attributes.MAX_HEALTH), basis,
                        "The greater skeleton basis is not replaced by an unconditional twenty-health value");
            }
            set(helper, 1L);
            Mob smallZombie = mob(helper, EntityType.ZOMBIE);
            smallZombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D); smallZombie.setHealth(3F);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 6F, 3F);
            set(helper, 33L);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 6.96F, 3.48F);
            set(helper, 65L);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 8.0736F, 4.0368F);
            modifier(helper, smallZombie, .3456D);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSkeletonsOtherNativeModifiersAreIncludedInItsBasisButThePreviousDateModifierIsExcluded(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1L);
            Mob skeleton = mob(helper, EntityType.SKELETON);
            skeleton.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(LEADER, 4D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            skeleton.setHealth(15F);
            health(helper, skeleton, 30F, 15F);
            set(helper, 33L);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 34.8F, 17.4F); modifier(helper, skeleton, .16D);
            for (int repeat = 0; repeat < 20; repeat++) MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 34.8F, 17.4F);
            set(helper, 65L);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 40.368F, 20.184F); modifier(helper, skeleton, .3456D);
            helper.assertValueEqual(skeleton.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(), 2,
                    "The actual thirty-health basis retains one independent native modifier plus one date modifier");
            close(helper, skeleton.getAttribute(Attributes.MAX_HEALTH).getModifier(LEADER).amount(), 4D,
                    "Reading the detached health basis does not remove or alter native leader modifiers");
        }
        helper.succeed();
    }

    private static final class Progress implements AutoCloseable {
        private final ServerLevel level;
        private final long time, highest;
        private Progress(GameTestHelper helper) {
            level = helper.getLevel().getServer().overworld(); time = level.getDayTime();
            highest = BloodMoonInvasionData.get(level).highestDay();
        }
        @Override public void close() {
            level.setDayTime(time); BloodMoonInvasionData.get(level).restoreProgress(highest);
        }
    }
}
