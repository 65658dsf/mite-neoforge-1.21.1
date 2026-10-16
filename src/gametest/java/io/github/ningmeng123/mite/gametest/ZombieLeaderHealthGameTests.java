package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native leader finalization and NBT lifecycle, without healing ticks or synthetic leader modifiers. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ZombieLeaderHealthGameTests {
    private static final List<EntityType<? extends Zombie>> SOURCE_TYPES = List.of(
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN);
    private static final List<MobSpawnType> REASONS = List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER,
            MobSpawnType.SPAWN_EGG, MobSpawnType.REINFORCEMENT, MobSpawnType.COMMAND);
    private static final DifficultyInstance MAXIMUM_LOCAL_DIFFICULTY = new DifficultyInstance(
            Difficulty.HARD, Long.MAX_VALUE, Long.MAX_VALUE, 1F);

    private ZombieLeaderHealthGameTests() {}

    private static List<EntityType<? extends Zombie>> allTypes() {
        return List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN,
                EntityType.HUSK, EntityType.DROWNED, revenant());
    }

    @SuppressWarnings("unchecked")
    private static EntityType<? extends Zombie> revenant() {
        return (EntityType<? extends Zombie>)(EntityType<?>) MiteMobs.type(MobKind.REVENANT);
    }

    private static Zombie create(GameTestHelper helper, EntityType<? extends Zombie> type, long seed) {
        Zombie zombie = type.create(helper.getLevel());
        if (zombie == null) throw new AssertionError("Unable to construct " + type);
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        zombie.setNoAi(true); zombie.setNoGravity(true);
        // Exclude the wall-clock Halloween helmet branch from this deterministic spawn fixture.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.getRandom().setSeed(seed);
        return zombie;
    }

    private static void finalizeNative(Zombie zombie, MobSpawnType reason) {
        EventHooks.finalizeMobSpawn(zombie, (ServerLevel) zombie.level(), MAXIMUM_LOCAL_DIFFICULTY,
                reason, new Zombie.ZombieGroupData(false, false));
    }

    /** The independent oracle is a max-health increase produced by actual spawn rolls, not a test-injected modifier. */
    private static Zombie leader(GameTestHelper helper, EntityType<? extends Zombie> type, MobSpawnType reason,
            boolean alreadyWounded) {
        for (long seed = 0; seed < 512; seed++) {
            Zombie zombie = create(helper, type, seed);
            float initialMaximum = zombie.getMaxHealth();
            helper.assertTrue(zombie.getHealth() == initialMaximum, "Fresh constructor starts full: " + type);
            if (alreadyWounded) zombie.setHealth(7F);
            finalizeNative(zombie, reason);
            if (zombie.getMaxHealth() > initialMaximum) return zombie;
        }
        throw new AssertionError("No real leader roll found in 512 deterministic spawn transactions for " + type + "/" + reason);
    }

    private static void full(GameTestHelper helper, Zombie zombie, String description) {
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), description + ": "
                + zombie.getHealth() + "/" + zombie.getMaxHealth());
    }

    /** Every test restores the world clock and native inhabited-time value before another fixture can observe it. */
    private static final class SourceTension implements AutoCloseable {
        private final ServerLevel level;
        private final LevelChunk chunk;
        private final long time;
        private final long inhabited;
        private final BloodMoonInvasionData progress;
        private final long highestDay;
        private final boolean dirty;

        SourceTension(GameTestHelper helper) {
            level = helper.getLevel(); time = level.getDayTime();
            progress = BloodMoonInvasionData.get(level); highestDay = progress.highestDay(); dirty = progress.isDirty();
            chunk = level.getChunkAt(helper.absolutePos(new BlockPos(2, 3, 2)));
            inhabited = chunk.getInhabitedTime();
            level.setDayTime(168_000L); chunk.setInhabitedTime(3_600_000L);
        }

        @Override public void close() {
            level.setDayTime(time); chunk.setInhabitedTime(inhabited);
            progress.restoreProgress(highestDay); progress.setDirty(dirty);
        }
    }

    @GameTest(template = "empty")
    public static void sourceZombieLeadersStartFullForEveryNativeSpawnReason(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var type : SOURCE_TYPES) for (var reason : REASONS) {
                Zombie zombie = leader(helper, type, reason, false);
                helper.assertTrue(zombie.getMaxHealth() > 20F, "Leader really raised the native source maximum");
                full(helper, zombie, "Source leader starts full after native finalization: " + type + "/" + reason);
                helper.assertValueEqual(zombie.getSpawnType(), reason, "NeoForge spawn reason is preserved");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernHuskAndDrownedLeadersStartFullAfterInheritedZombieFinalization(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var type : List.of(EntityType.HUSK, EntityType.DROWNED)) for (var reason : REASONS) {
                Zombie zombie = leader(helper, type, reason, false);
                helper.assertTrue(zombie.getMaxHealth() > 20F, "Modern variant really rolled a native leader modifier");
                full(helper, zombie, "Modern leader starts full: " + type + "/" + reason);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void revenantLeadersStartFullInTheirIndependentSourceOverride(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var reason : REASONS) {
                Zombie zombie = leader(helper, revenant(), reason, false);
                helper.assertTrue(zombie.getMaxHealth() > zombie.getAttributeBaseValue(Attributes.MAX_HEALTH),
                        "Custom revenant really rolled its independent leader bonus");
                full(helper, zombie, "Independent revenant finalization starts its leader full: " + reason);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woundedLeaderHealthSurvivesNativeNbtReloadWithoutAnotherLeaderRoll(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var type : allTypes()) {
                Zombie zombie = leader(helper, type, MobSpawnType.NATURAL, false);
                zombie.setHealth(7F);
                CompoundTag tag = new CompoundTag(); zombie.saveWithoutId(tag);
                tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                Zombie restored = (Zombie) EntityType.loadEntityRecursive(tag, helper.getLevel(), Function.identity());
                helper.assertTrue(restored != null, "Native recursive entity load returned the saved leader");
                helper.assertTrue(restored.getHealth() == 7F, "Reload preserves actual injuries instead of topping up: " + type);
                helper.assertTrue(restored.getMaxHealth() == zombie.getMaxHealth(), "Reload preserves the leader maximum: " + type);
                helper.assertValueEqual(restored.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(),
                        zombie.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(), "Reload never rerolls or duplicates leader attributes");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void injuredEntitiesAreNotHealedWhenFinalizationRaisesTheirMaximumOrRunsAgain(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var type : allTypes()) {
                Zombie zombie = leader(helper, type, MobSpawnType.COMMAND, true);
                helper.assertTrue(zombie.getHealth() == 7F, "A wounded entity is not healed even on an actual leader roll: " + type);
                zombie.getRandom().setSeed(196L);
                finalizeNative(zombie, MobSpawnType.REINFORCEMENT);
                helper.assertTrue(zombie.getHealth() == 7F, "Repeated finalization is not a general healing hook: " + type);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitHealthNbtOverridesSpawnInitializationAndOrdinarySpawnsStayOrdinary(GameTestHelper helper) {
        try (var ignored = new SourceTension(helper)) {
            for (var type : allTypes()) {
                Zombie leader = leader(helper, type, MobSpawnType.SPAWN_EGG, false);
                CompoundTag tag = new CompoundTag(); leader.saveWithoutId(tag); tag.putFloat("Health", 3F);
                Zombie restored = create(helper, type, 196L); restored.load(tag);
                helper.assertTrue(restored.getHealth() == 3F, "Explicit Health NBT applied after initialization remains authoritative: " + type);

                boolean ordinaryFound = false;
                for (long seed = 0; seed < 512 && !ordinaryFound; seed++) {
                    Zombie ordinary = create(helper, type, seed); float maximum = ordinary.getMaxHealth();
                    finalizeNative(ordinary, MobSpawnType.NATURAL);
                    if (ordinary.getMaxHealth() != maximum) continue;
                    full(helper, ordinary, "A nonleader remains at its original full health: " + type);
                    helper.assertTrue(ordinary.getMaxHealth() == maximum, "No leader maximum is manufactured for a normal spawn");
                    ordinaryFound = true;
                }
                helper.assertTrue(ordinaryFound, "Independent native oracle also produced an ordinary spawn: " + type);
            }
        }
        helper.succeed();
    }
}
