package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.SourceZombieDifficulty;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native initialization/attribute/save hooks against independently transcribed tw/tn literals. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceZombieDifficultyGameTests {
    private static final List<EntityType<? extends Zombie>> SOURCE_TYPES = List.of(
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN);

    private SourceZombieDifficultyGameTests() {}

    private static Zombie create(GameTestHelper helper, EntityType<? extends Zombie> type) {
        Zombie zombie = type.create(helper.getLevel());
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        zombie.setNoAi(true); zombie.setNoGravity(true);
        return zombie;
    }

    private static void attributes(Zombie zombie, float misleadingModernMultiplier) throws Exception {
        Method method = Zombie.class.getDeclaredMethod("handleAttributes", float.class);
        method.setAccessible(true); method.invoke(zombie, misleadingModernMultiplier);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-10, description + ": " + actual + " vs " + expected);
    }

    private static long seed(Predicate<RandomSource> condition) {
        for (long seed = 0; seed < 100_000; seed++) if (condition.test(RandomSource.create(seed))) return seed;
        throw new AssertionError("No independent source RNG fixture found");
    }

    @GameTest(template = "empty")
    public static void constructorInitializesSourceReinforcementsBeforeAnySpawnFinalization(GameTestHelper helper) {
        for (var type : SOURCE_TYPES) {
            Zombie zombie = create(helper, type);
            double chance = zombie.getAttributeBaseValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
            if (type == EntityType.ZOMBIFIED_PIGLIN) close(helper, chance, 0D, "tn.az fixes base reinforcements at zero");
            else helper.assertTrue(chance > 0D && chance < (double) .1F,
                    "tw.az draws base reinforcements during construction rather than rerolling during finalize");
            helper.assertTrue(zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).getModifiers().isEmpty(),
                    "Constructor base chance is not a leader modifier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSourceZombieSpawnReasonsAlwaysPickUpLootEvenAtZeroTension(GameTestHelper helper) {
        var level = helper.getLevel(); long oldTime = level.getDayTime();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(2, 3, 2))); long oldInhabited = chunk.getInhabitedTime();
        // Real equipment initialization observes this temporary date in the shared persistent calendar.
        try (ProgressSnapshot progress = new ProgressSnapshot(level)) {
            level.setDayTime(72_000L); chunk.setInhabitedTime(0L);
            for (var type : SOURCE_TYPES) for (var reason : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER,
                    MobSpawnType.SPAWN_EGG, MobSpawnType.REINFORCEMENT, MobSpawnType.COMMAND)) {
                Zombie zombie = create(helper, type); zombie.setCanPickUpLoot(false);
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                double base = zombie.getAttributeBaseValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                var group = new Zombie.ZombieGroupData(true, true);
                EventHooks.finalizeMobSpawn(zombie, level, new DifficultyInstance(Difficulty.PEACEFUL, 0, 0, 0F), reason, group);
                helper.assertTrue(zombie.canPickUpLoot(), "tw.h(true) is independent of local multiplier and source tension: " + type);
                helper.assertTrue(!zombie.isBaby() && !zombie.isPassenger(), "Existing R196 adult/no-jockey behavior is preserved");
                helper.assertValueEqual(zombie.getSpawnType(), reason, "NeoForge spawn metadata survives the native finalize path");
                close(helper, zombie.getAttributeBaseValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), base,
                        "Finalization does not reroll source constructor reinforcement chance");
                helper.assertTrue(!zombie.getAttribute(Attributes.MAX_HEALTH).hasModifier(SourceZombieDifficulty.LEADER_BONUS),
                        "Zero source tension produces no leader even when a group was supplied");
            }
        } finally { level.setDayTime(oldTime); chunk.setInhabitedTime(oldInhabited); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceKnockbackDrawDoesNotAddModernFollowRangeOrRerollBaseChance(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); long oldTime = level.getDayTime();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(2, 3, 2))); long oldInhabited = chunk.getInhabitedTime();
        try {
            level.setDayTime(72_000L); chunk.setInhabitedTime(0L);
            for (var type : SOURCE_TYPES) {
                Zombie zombie = create(helper, type); zombie.getRandom().setSeed(196L);
                double base = zombie.getAttributeBaseValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                RandomSource oracle = RandomSource.create(196L); double expectedKnockback = oracle.nextDouble() * (double) .05F;
                oracle.nextFloat();
                attributes(zombie, 100F);
                var modifier = zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(SourceZombieDifficulty.SPAWN_BONUS);
                helper.assertTrue(modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_VALUE,
                        "tw.a uses an additive knockback modifier, not armor");
                close(helper, modifier.amount(), expectedKnockback, "First draw is source knockback, not a modern reinforcement reroll");
                close(helper, zombie.getAttributeBaseValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), base, "Constructor reinforcement base is preserved");
                helper.assertTrue(zombie.getAttribute(Attributes.FOLLOW_RANGE).getModifiers().isEmpty(),
                        "Modern extra nextDouble()*1.5*multiplier follow-range modifier is absent");
                helper.assertValueEqual(zombie.getRandom().nextLong(), oracle.nextLong(),
                        "No hidden modern follow-range draw or base-chance reroll consumes the owner RNG");
            }
        } finally { level.setDayTime(oldTime); chunk.setInhabitedTime(oldInhabited); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactLeaderModifiersUseSourceOnePointTwoFiveTensionRatherThanPassedModernZero(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); long oldTime = level.getDayTime();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(2, 3, 2))); long oldInhabited = chunk.getInhabitedTime();
        try {
            level.setDayTime(168_000L); chunk.setInhabitedTime(3_600_000L);
            long seed = seed(random -> { random.nextDouble(); float draw = random.nextFloat(); return draw >= .05F && draw < .0625F; });
            for (var type : SOURCE_TYPES) {
                Zombie zombie = create(helper, type); zombie.getRandom().setSeed(seed);
                RandomSource oracle = RandomSource.create(seed); oracle.nextDouble(); oracle.nextFloat();
                double reinforcement = oracle.nextDouble() * .25D + .5D, health = oracle.nextDouble() * 3D + 1D;
                attributes(zombie, 0F);
                var reinforcementModifier = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).getModifier(SourceZombieDifficulty.LEADER_BONUS);
                var healthModifier = zombie.getAttribute(Attributes.MAX_HEALTH).getModifier(SourceZombieDifficulty.LEADER_BONUS);
                helper.assertTrue(reinforcementModifier != null && healthModifier != null,
                        "Full phase and maximum inhabitation yield .0625 leader chance for all source identities, including tn");
                close(helper, reinforcementModifier.amount(), reinforcement, "Source leader reinforcement draw");
                close(helper, healthModifier.amount(), health, "Source leader health draw");
                helper.assertTrue(reinforcementModifier.operation() == AttributeModifier.Operation.ADD_VALUE
                        && healthModifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        "Source leader modifier operations remain separate");
                helper.assertValueEqual(zombie.getRandom().nextLong(), oracle.nextLong(), "Successful leader consumes exactly the two source double draws");
            }
            level.setDayTime(72_000L);
            Zombie control = create(helper, EntityType.ZOMBIE); control.getRandom().setSeed(seed); attributes(control, 100F);
            helper.assertTrue(!control.getAttribute(Attributes.MAX_HEALTH).hasModifier(SourceZombieDifficulty.LEADER_BONUS),
                    "Same roll is rejected at source new-moon tension1 despite a huge passed modern multiplier");
        } finally { level.setDayTime(oldTime); chunk.setInhabitedTime(oldInhabited); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFinalizeKeepsOnlyTheSourceGaussianFollowRangeBonus(GameTestHelper helper) {
        for (var type : SOURCE_TYPES) {
            Zombie zombie = create(helper, type); zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.getRandom().setSeed(196L);
            double expected = RandomSource.create(196L).nextGaussian() * .05D;
            EventHooks.finalizeMobSpawn(zombie, helper.getLevel(), new DifficultyInstance(Difficulty.HARD,
                    Long.MAX_VALUE, Long.MAX_VALUE, 1F), MobSpawnType.SPAWN_EGG, new Zombie.ZombieGroupData(false, false));
            var modifiers = zombie.getAttribute(Attributes.FOLLOW_RANGE).getModifiers();
            helper.assertTrue(modifiers.size() == 1 && modifiers.iterator().next().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                    "og.a Gaussian base multiplier remains; modern multiplied-total local-difficulty bonus does not");
            close(helper, modifiers.iterator().next().amount(), expected,
                    "Real native finalize uses owner-RNG source Gaussian, not modern world-RNG triangle");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceZombieSpawnAttributesAndPickupSurviveSavingWithoutRerolls(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); long oldTime = level.getDayTime();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(2, 3, 2))); long oldInhabited = chunk.getInhabitedTime();
        try {
            level.setDayTime(168_000L); chunk.setInhabitedTime(3_600_000L);
            long seed = seed(random -> { random.nextDouble(); return random.nextFloat() < .0625F; });
            for (var type : SOURCE_TYPES) {
                Zombie zombie = create(helper, type); zombie.getRandom().setSeed(seed); zombie.setCanPickUpLoot(true); attributes(zombie, 0F);
                CompoundTag tag = new CompoundTag(); zombie.saveWithoutId(tag); Zombie restored = create(helper, type); restored.load(tag);
                helper.assertTrue(restored.canPickUpLoot(), "Fixed source pickup flag persists");
                for (var attribute : List.of(Attributes.KNOCKBACK_RESISTANCE, Attributes.SPAWN_REINFORCEMENTS_CHANCE, Attributes.MAX_HEALTH)) {
                    close(helper, restored.getAttributeValue(attribute), zombie.getAttributeValue(attribute), "Source spawn/leader attributes survive load");
                    helper.assertValueEqual(restored.getAttribute(attribute).getModifiers().size(), zombie.getAttribute(attribute).getModifiers().size(),
                            "Reload does not add a second source or modern modifier");
                }
            }
        } finally { level.setDayTime(oldTime); chunk.setInhabitedTime(oldInhabited); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernZombieVariantsAndRevenantDoNotReceiveDuplicateSourceInitialization(GameTestHelper helper) throws Exception {
        for (var type : List.of(EntityType.HUSK, EntityType.DROWNED)) {
            Zombie zombie = create(helper, type);
            helper.assertTrue(!SourceZombieDifficulty.handles(type) && !SourceZombieDifficulty.finishAttributes(zombie),
                    "Modern identity is excluded from the source helper");
            zombie.getRandom().setSeed(196L); attributes(zombie, 100F);
            helper.assertTrue(zombie.getAttribute(Attributes.FOLLOW_RANGE).getModifiers().stream()
                    .anyMatch(modifier -> modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    "Actual modern attribute path still responds to its own supplied local multiplier");
            helper.assertTrue(!zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).hasModifier(SourceZombieDifficulty.SPAWN_BONUS),
                    "Modern path has no source duplicate modifier");
        }
        Zombie revenant = create(helper, (EntityType<? extends Zombie>)(EntityType<?>) MiteMobs.type(MobKind.REVENANT));
        helper.assertTrue(!SourceZombieDifficulty.finishAttributes(revenant), "Custom revenant keeps its existing source override");
        attributes(revenant, 0F);
        helper.assertTrue(!revenant.getAttribute(Attributes.KNOCKBACK_RESISTANCE).hasModifier(SourceZombieDifficulty.SPAWN_BONUS),
                "Revenant attributes are not initialized twice through the native mixin");
        helper.succeed();
    }
}
