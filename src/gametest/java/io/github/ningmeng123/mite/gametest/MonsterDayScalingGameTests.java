package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.combat.SourcePlayerCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.MonsterMeleeScaling;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
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
            Mob brute = mob(helper, EntityType.PIGLIN_BRUTE);
            MonsterDayScaling.refresh(brute);
            health(helper, brute, 150F, 150F);
            close(helper, brute.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 20D, "Piglin brute innate attack");
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
            MonsterDayScaling.refresh(monster); health(helper, monster, 61.712F, 30.856F);
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
            set(helper, 1L);
            for (boolean loaded : new boolean[]{true, false, true}) {
                MonsterDayScaling.join(new EntityJoinLevelEvent(restored, helper.getLevel(), loaded));
                health(helper, restored, 26.912F, 14F); modifier(helper, restored, .3456D);
                close(helper, MonsterDayScaling.attackMultiplier(restored), 1.3456D,
                        "Repeated joins use the saved day for attack growth after a clock rollback");
            }
            set(helper, 65L);
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
                float grown = (maximum + 3.2F) * 1.16F;
                health(helper, bat, grown, grown);
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
            set(helper, 1L);
            Mob attacker = mob(helper, EntityType.ZOMBIE), target = cow(helper);
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "Native arrow source exists"); arrow.setOwner(attacker);
            hit(helper, target, target.damageSources().arrow(arrow, attacker), 20F, 20F);
            set(helper, 2L);
            hit(helper, target, target.damageSources().arrow(arrow, attacker), 20F, 20.1F);
            set(helper, 65L);
            for (DamageSource source : List.of(target.damageSources().mobAttack(attacker), target.damageSources().arrow(arrow, attacker),
                    target.damageSources().explosion(attacker, attacker), target.damageSources().indirectMagic(arrow, attacker)))
                hit(helper, target, source, 20F, 26.912F);
            set(helper, 97L);
            hit(helper, target, target.damageSources().arrow(arrow, attacker), 20F, 31.21792F);
            helper.assertTrue(!attacker.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(HEALTH), "No health multiplier is installed on attack damage");
            arrow.discard(); target.discard(); attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void incomingGrowthRunsBeforeNativeNpcArmor(GameTestHelper helper) throws ReflectiveOperationException {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 65L);
            Mob attacker = mob(helper, EntityType.ZOMBIE), target = cow(helper);
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            target.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            equipment(target);
            close(helper, target.getAttributeValue(Attributes.ARMOR), 18D, "The real NPC wears balanced iron armor");
            close(helper, target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D, "The real NPC gains one half toughness from each iron piece");
            // Day 65 grows raw 20 to 26.912 before armor 18 and toughness 2 reduce it.
            hit(helper, target, target.damageSources().mobAttack(attacker), 20F, 19.123451904F);
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
            Mob hostile = mob(helper, EntityType.ZOMBIE);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "Native unowned arrow source exists");
            for (DamageSource source : List.of(target.damageSources().generic(), target.damageSources().fall(),
                    target.damageSources().arrow(arrow, null), target.damageSources().explosion(null, null),
                    target.damageSources().thorns(hostile), new DamageSource(helper.getLevel().registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SourcePlayerCombat.HAND_DAMAGE), hostile),
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
            hit(helper, target, target.damageSources().mobAttack(attacker), 20F, 26.912F);
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
    public static void actualZombieBareAttackGrowsBeforeWeaponDamageIsAdded(GameTestHelper helper) throws ReflectiveOperationException {
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
                    double expected = 6D * 1.3456D + weaponDelta;
                    close(helper, raw[0], expected, "Two blood-moon settlements grow innate damage before the weapon bonus");
                    close(helper, incoming[0], expected, "A real melee transaction is not multiplied again at the common incoming boundary");
                    close(helper, 200F - target.getHealth(), expected, "Native melee health loss includes grown innate attack plus weapon damage");
                    close(helper, MonsterDayScaling.attackMultiplier(attacker), 1.3456D, "Day 65 uses two compounded 16-percent increments");
                    if (!armed) bareStrike = raw[0];
                    else close(helper, raw[0] - bareStrike, weaponDelta, "Equipping a sword adds exactly its original attack modifier, without date multiplication");
                }
                attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(-1D);
                double weaponAdjusted = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
                target.invulnerableTime = 0;
                helper.assertTrue(attacker.doHurtTarget(target), "A sword can offset a negative command-supplied attack base");
                close(helper, incoming[0], weaponAdjusted, "Native combat still combines the negative base with the weapon");
            } finally { NeoForge.EVENT_BUS.unregister(capture); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bossesKeepNativeHealthAndDamageAtFarFutureDays(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 100_001L);
            for (EntityType<?> type : List.of(EntityType.ENDER_DRAGON, EntityType.WITHER)) {
                Mob boss = mob(helper, type);
                float maximum = boss.getMaxHealth(); boss.setHealth(maximum / 2F);
                helper.assertTrue(!MonsterDayScaling.handles(boss), "Native bosses are excluded from calendar growth: " + type);
                for (int refresh = 0; refresh < 3; refresh++) MonsterDayScaling.refresh(boss);
                health(helper, boss, maximum, maximum / 2F);
                helper.assertTrue(!boss.getAttribute(Attributes.MAX_HEALTH).hasModifier(HEALTH),
                        "A far-future date installs no boss health multiplier: " + type);
                helper.assertTrue(!boss.getPersistentData().contains(MonsterDayScaling.DATA_KEY),
                        "Excluded bosses receive no date snapshot: " + type);
                close(helper, MonsterDayScaling.attackBase(boss, 7.5D), 7.5D, "Boss innate melee damage retains its native base");
                close(helper, MonsterDayScaling.attackMultiplier(boss), 1D, "Boss attributed damage retains a neutral multiplier");
                try (MonsterMeleeScaling.Scope scope = MonsterMeleeScaling.enter(boss)) {
                    close(helper, MonsterMeleeScaling.attackValue(boss, 9D), 9D, "The native melee scope cannot grow an excluded boss");
                }
            }
            Mob ordinary = mob(helper, EntityType.ZOMBIE);
            MonsterDayScaling.refresh(ordinary);
            helper.assertTrue(ordinary.getMaxHealth() > 20F && MonsterDayScaling.attackMultiplier(ordinary) > 1D,
                    "The same far-future date grows ordinary monster health and attack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bossOwnedMeleeProjectilesExplosionsAndSpellsKeepIncomingDamage(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 100_001L);
            Mob target = cow(helper);
            for (EntityType<?> type : List.of(EntityType.ENDER_DRAGON, EntityType.WITHER)) {
                Mob boss = mob(helper, type);
                Arrow arrow = EntityType.ARROW.create(helper.getLevel());
                helper.assertTrue(arrow != null, "A native projectile can carry the boss owner"); arrow.setOwner(boss);
                try {
                    for (DamageSource source : List.of(target.damageSources().mobAttack(boss), target.damageSources().arrow(arrow, boss),
                            target.damageSources().explosion(boss, boss), target.damageSources().indirectMagic(arrow, boss))) {
                        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(target, new DamageContainer(source, 20F));
                        MonsterDayScaling.damage(event);
                        close(helper, event.getAmount(), 20F, "Boss-owned incoming damage receives no calendar multiplier: " + source);
                    }
                } finally { arrow.discard(); boss.discard(); }
            }
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyBossHealthModifiersAreRemovedOnJoinAndTickWhilePreservingWoundsAndOtherData(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 100_001L);
            for (EntityType<?> type : List.of(EntityType.ENDER_DRAGON, EntityType.WITHER)) {
                for (boolean join : new boolean[]{true, false}) {
                    Mob original = mob(helper, type);
                    float nativeMaximum = original.getMaxHealth();
                    var attribute = original.getAttribute(Attributes.MAX_HEALTH);
                    attribute.addPermanentModifier(new AttributeModifier(LEADER, .5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    attribute.addPermanentModifier(new AttributeModifier(HEALTH, 1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    original.setHealth(original.getMaxHealth() / 4F);
                    CompoundTag progress = new CompoundTag(); progress.putInt("version", 1); progress.putLong("day", 65L);
                    original.getPersistentData().put(MonsterDayScaling.DATA_KEY, progress);
                    original.getPersistentData().putInt("mite_test:boss_marker", 42);
                    CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
                    saved.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                    Mob restored = (Mob)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
                    helper.assertTrue(restored != null, "The legacy boss reconstructs through native entity loading");
                    helper.assertTrue(restored.getAttribute(Attributes.MAX_HEALTH).hasModifier(HEALTH),
                            "The fixture really loads an old permanent calendar modifier");
                    health(helper, restored, nativeMaximum * 3F, nativeMaximum * .75F);
                    if (join) MonsterDayScaling.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    else for (int tick = 1; tick <= 20; tick++) {
                        restored.tickCount = tick;
                        MonsterDayScaling.tick(new EntityTickEvent.Post(restored));
                    }
                    health(helper, restored, nativeMaximum * 1.5F, nativeMaximum * .375F);
                    helper.assertTrue(!restored.getAttribute(Attributes.MAX_HEALTH).hasModifier(HEALTH),
                            "Boss migration removes only the calendar health modifier");
                    helper.assertTrue(restored.getAttribute(Attributes.MAX_HEALTH).hasModifier(LEADER),
                            "An independent permanent boss health modifier survives migration");
                    helper.assertValueEqual(restored.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(), 1,
                            "Only the independent health modifier remains");
                    helper.assertTrue(!restored.getPersistentData().contains(MonsterDayScaling.DATA_KEY),
                            "Legacy boss date metadata is removed");
                    helper.assertValueEqual(restored.getPersistentData().getInt("mite_test:boss_marker"), 42,
                            "Unrelated persistent boss data survives migration");
                    MonsterDayScaling.refresh(restored);
                    health(helper, restored, nativeMaximum * 1.5F, nativeMaximum * .375F);
                    CompoundTag cleaned = new CompoundTag(); restored.saveWithoutId(cleaned);
                    cleaned.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                    Mob reloaded = (Mob)EntityType.loadEntityRecursive(cleaned, helper.getLevel(), Function.identity());
                    helper.assertTrue(reloaded != null, "The cleaned boss saves and loads natively");
                    MonsterDayScaling.join(new EntityJoinLevelEvent(reloaded, helper.getLevel(), true));
                    health(helper, reloaded, nativeMaximum * 1.5F, nativeMaximum * .375F);
                    helper.assertTrue(!reloaded.getAttribute(Attributes.MAX_HEALTH).hasModifier(HEALTH),
                            "A subsequent save and reload cannot restore boss calendar growth");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void taggedLordsGainCalendarHealthAndDamageOnLoadingAnOldSave(GameTestHelper helper) throws ReflectiveOperationException {
        try (Progress ignored = new Progress(helper)) {
            checkTaggedLordGrowth(helper, MiteMobs.type(MobKind.BONE_LORD));
            if (ModList.get().isLoaded("modern_ite"))
                checkTaggedLordGrowth(helper, BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse("modern_ite:zombie_lord")).orElseThrow());
        }
        helper.succeed();
    }

    private static void checkTaggedLordGrowth(GameTestHelper helper, EntityType<?> type) throws ReflectiveOperationException {
        var holder = type.builtInRegistryHolder();
        var originalTags = holder.tags().toList();
        var bossTags = new ArrayList<>(originalTags);
        if (!bossTags.contains(Tags.EntityTypes.BOSSES)) bossTags.add(Tags.EntityTypes.BOSSES);
        Method bind = holder.getClass().getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        try {
            bind.invoke(holder, bossTags);
            helper.assertTrue(type.is(Tags.EntityTypes.BOSSES), "The lord is tagged as a boss: " + type);
            set(helper, 1L);
            Mob original = mob(helper, type);
            float nativeMaximum = original.getMaxHealth();
            original.setHealth(nativeMaximum / 2F);
            CompoundTag saved = new CompoundTag();
            original.saveWithoutId(saved);
            saved.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            set(helper, 65L);
            Mob lord = (Mob)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
            helper.assertTrue(lord != null && MonsterDayScaling.handles(lord), "The wounded lord loads as a growth participant: " + type);
            for (int repeat = 0; repeat < 2; repeat++) MonsterDayScaling.join(new EntityJoinLevelEvent(lord, helper.getLevel(), true));
            float grownMaximum = (nativeMaximum + 3.2F) * 1.16F;
            health(helper, lord, grownMaximum, grownMaximum / 2F);
            helper.assertValueEqual(lord.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY).getLong("day"), 65L,
                    "The lord adopts the world's historical day: " + type);
            double nativeAttack = lord.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            Mob target = cow(helper);
            helper.assertTrue(lord.doHurtTarget(target), "The lord completes a native melee strike: " + type);
            close(helper, target.getMaxHealth() - target.getHealth(), nativeAttack * 1.3456D, "Day 65 lord melee health loss: " + type);
            hit(helper, target, target.damageSources().explosion(lord, lord), 20F, 26.912F);
        } finally {
            bind.invoke(holder, originalTags);
        }
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
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 10.672F, 5.336F); modifier(helper, skeleton, 4.672D / 6D);
            for (int repeat = 0; repeat < 20; repeat++) MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 10.672F, 5.336F);
            CompoundTag saved = new CompoundTag(); skeleton.saveWithoutId(saved);
            saved.putString("id", "minecraft:skeleton");
            set(helper, 1L);
            Mob restored = (Mob)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
            helper.assertTrue(restored != null, "A real native skeleton reloads with its date and ordinary max-health attributes");
            for (boolean loaded : new boolean[]{true, false, true}) {
                MonsterDayScaling.join(new EntityJoinLevelEvent(restored, helper.getLevel(), loaded));
                health(helper, restored, 10.672F, 5.336F); modifier(helper, restored, 4.672D / 6D);
            }
            close(helper, restored.getAttributeBaseValue(Attributes.MAX_HEALTH), 6D,
                    "The detached growth calculation preserves the original six-health base across save and read");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skeletonsAndOtherMonstersShareTwentyReferenceAcrossDifferentNativeHealth(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (double basis : new double[]{20D, 30D}) {
                set(helper, 1L);
                Mob skeleton = mob(helper, EntityType.SKELETON);
                skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(basis); skeleton.setHealth((float)basis);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, (float)basis, (float)basis);
                set(helper, 33L);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, basis == 20D ? 23.2F : 33.2F, basis == 20D ? 23.2F : 33.2F);
                set(helper, 65L);
                MonsterDayScaling.refresh(skeleton); health(helper, skeleton, basis == 20D ? 26.912F : 38.512F, basis == 20D ? 26.912F : 38.512F);
                close(helper, skeleton.getAttributeBaseValue(Attributes.MAX_HEALTH), basis,
                        "The greater skeleton basis is not replaced by an unconditional twenty-health value");
            }
            set(helper, 1L);
            Mob smallZombie = mob(helper, EntityType.ZOMBIE);
            smallZombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D); smallZombie.setHealth(3F);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 6F, 3F);
            set(helper, 33L);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 9.2F, 4.6F);
            set(helper, 65L);
            MonsterDayScaling.refresh(smallZombie); health(helper, smallZombie, 10.672F, 5.336F);
            modifier(helper, smallZombie, 4.672D / 6D);
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
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 33.2F, 16.6F); modifier(helper, skeleton, 3.2D / 30D);
            for (int repeat = 0; repeat < 20; repeat++) MonsterDayScaling.refresh(skeleton);
            health(helper, skeleton, 33.2F, 16.6F);
            set(helper, 65L);
            MonsterDayScaling.refresh(skeleton); health(helper, skeleton, 38.512F, 19.256F); modifier(helper, skeleton, 8.512D / 30D);
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
