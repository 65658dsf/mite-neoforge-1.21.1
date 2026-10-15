package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual items, native spawn hooks, persisted empty results and independently agreed progression fixtures. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BloodMoonEquipmentGameTests {
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final String[] SUFFIXES = {"helmet", "chestplate", "leggings", "boots"};
    private BloodMoonEquipmentGameTests() {}

    private static Mob create(GameTestHelper h, EntityType<?> type) {
        Mob mob = (Mob)type.create(h.getLevel());
        mob.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter()); mob.setNoAi(true); mob.setNoGravity(true);
        return mob;
    }
    private static long seed(double chance, boolean success) {
        for (long value = 0; value < 100_000; value++) if ((RandomSource.create(value).nextDouble() < chance) == success) return value;
        throw new AssertionError("No independent probability fixture");
    }
    private static List<ItemStack> equipment(Mob mob) { return SLOTS.stream().map(slot -> mob.getItemBySlot(slot).copy()).toList(); }
    private static void unchanged(GameTestHelper h, Mob mob, List<ItemStack> before) {
        for (int index = 0; index < SLOTS.size(); index++) h.assertTrue(ItemStack.matches(before.get(index), mob.getItemBySlot(SLOTS.get(index))),
                "The recorded spawn result does not reroll " + SLOTS.get(index));
    }
    private static void set(GameTestHelper h, long highest, long time) {
        h.getLevel().getServer().overworld().setDayTime(time);
        BloodMoonInvasionData.get(h.getLevel()).restoreProgress(highest);
    }

    @GameTest(template = "empty")
    public static void progressiveArmorIsOneWornSetAndNeverOverwritesExplicitEquipment(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 64, 6000);
            Mob mob = create(h, EntityType.ZOMBIE);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(1);
            ItemStack weapon = new ItemStack(Items.DIAMOND_SWORD);
            mob.setItemSlot(EquipmentSlot.HEAD, helmet); mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            mob.getRandom().setSeed(seed(.2, true));
            h.assertTrue(BloodMoonEquipment.populate(mob), "The shared source armor hook owns the progressive roll");
            int tier = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("tier");
            h.assertTrue(tier >= 0 && tier <= 8, "An accepted material draw is recorded");
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD) == helmet && mob.getMainHandItem() == weapon,
                    "Explicit helmet and weapon retain their exact stacks");
            for (int index = 1; index < SLOTS.size(); index++) {
                ItemStack stack = mob.getItemBySlot(SLOTS.get(index));
                h.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[index], "Every empty slot receives the one selected material");
                h.assertTrue(stack.isDamageableItem() && stack.getDamageValue() > 0 && stack.getDamageValue() < stack.getMaxDamage(),
                        "Source generated quality/wear is present on actual armor");
            }
            var before = equipment(mob); mob.getRandom().setSeed(196L);
            BloodMoonEquipment.populate(mob); unchanged(h, mob, before);
            h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "A repeated roll consumes no owner RNG");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptedAndEmptyRollsSurviveNativeNbtAndCannotUpgradeAfterTimeAdvances(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            for (boolean success : new boolean[]{false, true}) {
                set(h, 64, 6000); Mob mob = create(h, EntityType.ZOMBIE); mob.getRandom().setSeed(seed(.2, success));
                BloodMoonEquipment.populate(mob); var before = equipment(mob);
                h.assertTrue(SLOTS.stream().anyMatch(slot -> !mob.getItemBySlot(slot).isEmpty()) == success, "The independent acceptance fixture matches actual armor");
                CompoundTag tag = new CompoundTag(); mob.saveWithoutId(tag);
                Mob restored = create(h, EntityType.ZOMBIE); restored.load(tag);
                set(h, 736, 735L * 24000L + 6000L);
                restored.getRandom().setSeed(seed(.94, true)); BloodMoonEquipment.populate(restored);
                unchanged(h, restored, before);
                h.assertValueEqual(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("stage"), 0,
                        "Saved empty and nonempty results retain their original progression stage");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realSpawnAndJoinPathsCoverModernAndSourceHumanoidsAtTheHighestTier(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 736, 735L * 24000L + 6000L);
            List<EntityType<?>> types = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN,
                    EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.STRAY, EntityType.BOGGED));
            for (MobKind kind : List.of(MobKind.GHOUL, MobKind.WIGHT, MobKind.SHADOW, MobKind.INVISIBLE_STALKER)) types.add(MiteMobs.type(kind));
            for (var type : types) {
                boolean found = false;
                for (int attempt = 0; attempt < 32 && !found; attempt++) {
                    Mob mob = create(h, type);
                    try {
                        EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
                        h.getLevel().addFreshEntity(mob);
                        var decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                        h.assertValueEqual(decision.getInt("stage"), 8, "Every eligible actual spawn path shares the maximum historical stage: " + type);
                        if (decision.getInt("tier") < 0) continue;
                        for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                                BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS.get(index)).getItem()).toString(),
                                "mite:adamantium_" + SUFFIXES[index], "Highest-tier successful spawn is a complete actual adamantium set: " + type);
                        found = true;
                    } finally { mob.discard(); }
                }
                h.assertTrue(found, "A94-percent successful armor result is reachable through native spawn and join: " + type);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinarySkeletonNativeFinalizationRetainsAnExplicitHelmetBeforeTheProgressiveRoll(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 736, 6000); Mob skeleton = create(h, EntityType.SKELETON);
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); skeleton.setItemSlot(EquipmentSlot.HEAD, helmet);
            EventHooks.finalizeMobSpawn(skeleton, h.getLevel(), h.getLevel().getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.COMMAND, null);
            h.assertTrue(skeleton.getItemBySlot(EquipmentSlot.HEAD) == helmet, "Source initializer must not clear an explicit helmet before the empty-slot policy runs");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldLoadedEntitiesAndFixedSetsNeverReceiveProgressiveOrInvisibleArmor(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 736, 6000);
            Mob old = create(h, EntityType.ZOMBIE);
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(old, h.getLevel(), true));
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(old, h.getLevel(), false));
            h.assertTrue(SLOTS.stream().allMatch(slot -> old.getItemBySlot(slot).isEmpty()), "Loading an old naked mob then crossing dimensions never grants new armor");
            for (var type : List.of(EntityType.SPIDER, EntityType.CREEPER, MiteMobs.type(MobKind.REVENANT),
                    MiteMobs.type(MobKind.LONGDEAD), MiteMobs.type(MobKind.LONGDEAD_GUARDIAN),
                    MiteMobs.type(MobKind.BONE_LORD), MiteMobs.type(MobKind.ANCIENT_BONE_LORD))) {
                Mob excluded = create(h, type); ItemStack sentinel = new ItemStack(Items.IRON_CHESTPLATE);
                excluded.setItemSlot(EquipmentSlot.CHEST, sentinel);
                h.assertTrue(!BloodMoonEquipment.populate(excluded), "Non-humanoids and fixed source sets are excluded: " + type);
                h.assertTrue(excluded.getItemBySlot(EquipmentSlot.CHEST) == sentinel, "Existing fixed armor cannot be downgraded");
                h.assertTrue(!excluded.getPersistentData().contains(BloodMoonEquipment.DATA_KEY), "Exclusion has no progression metadata side effect");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void firstBloodUsesTheLegacyPartialArmorPathAndOrdinaryTroopReinitializationStillWorks(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L); Mob zombie = create(h, EntityType.ZOMBIE);
            h.assertTrue(Math.abs(BloodMoonEquipment.legacyChance(zombie, .01D) - .6D) < 1E-12, "First blood increases only the legacy acceptance roll to60 percent");
            h.assertTrue(!BloodMoonEquipment.populate(zombie) && !zombie.getPersistentData().contains(BloodMoonEquipment.DATA_KEY),
                    "Before blood2 the source initialization is not prematurely marked as complete");
            set(h, 32, 7L * 24000L + 18000L);
            var chunk = h.getLevel().getChunkAt(zombie.blockPosition()); long inhabited = chunk.getInhabitedTime();
            try {
                chunk.setInhabitedTime(3_600_000L);
                AbstractSkeleton troop = (AbstractSkeleton)create(h, EntityType.SKELETON);
                EventHooks.finalizeMobSpawn(troop, h.getLevel(), h.getLevel().getCurrentDifficultyAt(troop.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                h.assertTrue(!troop.getPersistentData().contains(BloodMoonEquipment.DATA_KEY), "First native finalize does not suppress the source troop's second legal initializer");
                boolean armor = false;
                for (long seed = 0; seed < 1000 && !armor; seed++) {
                    troop.getRandom().setSeed(seed); R196Skeleton.initializeOrdinaryTroop(troop, 0);
                    armor = !troop.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
                }
                h.assertTrue(armor, "The real finalize then initializeOrdinaryTroop sequence can still produce legacy armor before blood2");
                var before = equipment(troop);
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(troop, h.getLevel(), false));
                set(h, 736, 6000); BloodMoonEquipment.populate(troop); unchanged(h, troop, before);
            } finally { chunk.setInhabitedTime(inhabited); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodBoostIsMainWorldOnlyAndGeneratedArmorIsNotEnchantedTwice(GameTestHelper h) throws Exception {
        try (Progress saved = new Progress(h)) {
            set(h, 64, 31L * 24000L + 18000L);
            Mob mob = create(h, EntityType.ZOMBIE); h.assertTrue(BloodMoonEquipment.bloodDate(mob), "Actual main-world blood date enables the boost");
            Mob other = EntityType.ZOMBIE.create(h.getLevel().getServer().getLevel(Level.NETHER));
            h.assertTrue(!BloodMoonEquipment.bloodDate(other), "A shared historical stage does not create a Nether blood-date boost");
            mob.getRandom().setSeed(seed(.7, true)); BloodMoonEquipment.populate(mob);
            var before = equipment(mob);
            Method armorEnchant = Mob.class.getDeclaredMethod("enchantSpawnedArmor", net.minecraft.world.level.ServerLevelAccessor.class,
                    RandomSource.class, EquipmentSlot.class, net.minecraft.world.DifficultyInstance.class);
            armorEnchant.setAccessible(true);
            for (int repeat = 0; repeat < 8; repeat++) for (var slot : SLOTS) armorEnchant.invoke(mob, h.getLevel(), RandomSource.create(repeat),
                    slot, new net.minecraft.world.DifficultyInstance(net.minecraft.world.Difficulty.HARD, Long.MAX_VALUE, Long.MAX_VALUE, 1F));
            unchanged(h, mob, before);
        }
        h.succeed();
    }

    private static final class Progress implements AutoCloseable {
        private final net.minecraft.server.level.ServerLevel level;
        private final long time, highest;
        Progress(GameTestHelper h) {
            level = h.getLevel().getServer().overworld(); time = level.getDayTime(); highest = BloodMoonInvasionData.get(level).highestDay();
        }
        @Override public void close() { level.setDayTime(time); BloodMoonInvasionData.get(level).restoreProgress(highest); }
    }
}
