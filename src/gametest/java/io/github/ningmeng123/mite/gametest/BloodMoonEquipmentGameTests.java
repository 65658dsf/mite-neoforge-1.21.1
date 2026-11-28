package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MonsterArmorSettings;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobEquipmentEnchantments;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.rules.MonsterArmorRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static long heldSeed(double chance, boolean success, int tool) {
        for (long value = 0; value < 100_000; value++) {
            RandomSource random = RandomSource.create(value);
            if ((random.nextDouble() < chance) == success && (!success || random.nextInt(4) == tool)) return value;
        }
        throw new AssertionError("No independent held-tool fixture");
    }
    private static long legacyArmorSeed(double lower, double upper, boolean partial, float enchantChance) {
        for (long seed = 0; seed < 1_000_000; seed++) {
            RandomSource random = RandomSource.create(seed);
            float acceptance = random.nextFloat();
            if (acceptance < lower || acceptance >= upper) continue;
            if (!partial) return seed;
            random.nextInt(2);
            for (int index = 0; index < 3; index++) random.nextFloat();
            if (random.nextFloat() < enchantChance) continue;
            random.nextFloat(); // The accepted helmet receives one source wear draw.
            if (random.nextFloat() < .1F) return seed; // Stop before the chest even on hard difficulty.
        }
        throw new AssertionError("No independent legacy acceptance/partial-armor fixture");
    }
    private static long fractionalSeed(double acceptanceMinimum, double acceptanceMaximum, double materialMinimum, double materialMaximum) {
        for (long value = 0; value < 100_000; value++) {
            RandomSource random = RandomSource.create(value);
            double acceptance = random.nextDouble(), material = random.nextDouble();
            if (acceptance >= acceptanceMinimum && acceptance < acceptanceMaximum && material >= materialMinimum && material < materialMaximum) return value;
        }
        throw new AssertionError("No independent fractional progression fixture");
    }
    private record SlotFixture(long seed, long nextRandom) {}
    /** Independent first blood moon weights for chainmail and the unenchanted source-item RNG path. */
    private static SlotFixture firstBloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {109375L, 312500L, 125000L, 25000L, 25000L, 20000L, 8000L, 3200L, 1280L, 512L};
        return slotFixture(tension, 32L, .82D, materialWeights, 629867D, expectedTiers);
    }
    /** Independent nine-grade rational weights, with the copper/silver grade divided in half. */
    private static SlotFixture adamantiumBloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {823543L, 2352980L, 6722800L, 9604000L, 9604000L,
                54880000L, 156800000L, 448000000L, 1280000000L, 512000000L};
        return slotFixture(tension, 128L, .96D, materialWeights, 2480787323D, expectedTiers);
    }
    private static SlotFixture thirdBloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {16807L, 48020L, 137200L, 196000L, 196000L, 1120000L, 3200000L, 1280000L, 512000L, 204800L};
        return slotFixture(tension, 96L, .92D, materialWeights, 6910827D, expectedTiers);
    }
    private static SlotFixture slotFixture(float tension, long highestDay, double acceptance, long[] materialWeights, double denominator, int... expectedTiers) {
        candidates: for (long value = 0; value < 1_000_000; value++) {
            RandomSource random = RandomSource.create(value);
            for (int expected : expectedTiers) {
                if (expected == -2) continue; // An explicitly occupied slot must skip both acceptance and material draws.
                boolean accepted = random.nextDouble() < acceptance;
                if (accepted != (expected >= 0)) continue candidates;
                if (!accepted) continue;
                double material = random.nextDouble();
                long cumulative = 0L; int actual = 9;
                for (int tier = 0; tier < materialWeights.length; tier++) {
                    cumulative += materialWeights[tier];
                    if (material < cumulative / denominator) { actual = tier; break; }
                }
                if (actual != expected) continue candidates;
                if (random.nextFloat() < Math.max(.1F * tension, Math.min(.75F, (highestDay - 1L) / 400F))) continue candidates; // Select a fixture without extra enchantment draws.
                random.nextFloat(); // One source wear draw follows quality assignment, which consumes no RNG.
            }
            return new SlotFixture(value, random.nextLong());
        }
        throw new AssertionError("No independent per-slot armor fixture");
    }
    private static CompoundTag held(Mob mob) { return mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getCompound("held"); }
    private static long emptyArmorSeed(double chance) {
        candidates: for (long value = 0; value < 100_000; value++) {
            RandomSource random = RandomSource.create(value);
            for (int slot = 0; slot < SLOTS.size(); slot++) if (random.nextDouble() < chance) continue candidates;
            return value;
        }
        throw new AssertionError("No independent fixture rejecting every armor slot");
    }
    private static void emptyArmorRoll(Mob mob, double chance) {
        mob.getRandom().setSeed(emptyArmorSeed(chance)); BloodMoonEquipment.populate(mob);
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
    public static void ordinaryArmorUsesIndependentWornPiecesAndPreservesExplicitEquipment(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 16, 6000);
            Mob mob = create(h, EntityType.ZOMBIE);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(1);
            ItemStack weapon = new ItemStack(Items.IRON_SWORD);
            mob.setItemSlot(EquipmentSlot.HEAD, helmet); mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            long[] weights = {390625L, 156250L, 62500L, 12500L, 12500L, 10000L, 4000L, 1600L, 640L, 256L};
            SlotFixture fixture = slotFixture(R196MobEquipment.tension(mob), 16L, .4D, weights, 650871D, -2, 0, -1, 1);
            mob.getRandom().setSeed(fixture.seed());
            h.assertTrue(BloodMoonEquipment.populate(mob), "The shared source armor hook owns the progressive roll");
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(Arrays.equals(new int[]{-1, 0, -1, 1}, decision.getIntArray("armor_tiers")),
                    "Ordinary slots independently retain the helmet, generate leather, reject leggings and generate chainmail");
            h.assertValueEqual(decision.getInt("filled_slots"), 10, "Only the two generated slots are marked for ownership");
            h.assertValueEqual(decision.getInt("tier"), 0, "The compatibility tier describes the first generated slot");
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD) == helmet && mob.getMainHandItem() == weapon,
                    "Explicit helmet and weapon retain their exact stacks");
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                    && mob.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && mob.getItemBySlot(EquipmentSlot.FEET).is(Items.CHAINMAIL_BOOTS),
                    "Real ordinary armor can mix materials and leave an interior slot empty");
            for (var slot : List.of(EquipmentSlot.CHEST, EquipmentSlot.FEET)) {
                ItemStack stack = mob.getItemBySlot(slot);
                h.assertTrue(stack.isDamageableItem() && stack.getDamageValue() > 0 && stack.getDamageValue() < stack.getMaxDamage(),
                        "Source generated quality/wear is present on actual armor");
            }
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "The explicit helmet consumes no RNG and the three empty slots roll independently");
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
                set(h, 16, 6000); Mob mob = create(h, EntityType.ZOMBIE);
                mob.getRandom().setSeed(success ? seed(.4, true) : emptyArmorSeed(.4));
                BloodMoonEquipment.populate(mob); var before = equipment(mob);
                h.assertTrue(SLOTS.stream().anyMatch(slot -> !mob.getItemBySlot(slot).isEmpty()) == success, "The independent acceptance fixture matches actual armor");
                CompoundTag tag = new CompoundTag(); mob.saveWithoutId(tag);
                Mob restored = create(h, EntityType.ZOMBIE); restored.load(tag);
                set(h, 736, 735L * 24000L + 6000L);
                restored.getRandom().setSeed(seed(.96, true)); BloodMoonEquipment.populate(restored);
                unchanged(h, restored, before);
                h.assertValueEqual(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("stage"), 0,
                        "Saved empty and nonempty results retain their original progression stage");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realSpawnAndJoinPathsCoverModernAndSourceHumanoidsAtTheHighestStage(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 160, 159L * 24000L + 6000L);
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
                        h.assertValueEqual(decision.getInt("stage"), 9, "Every eligible actual spawn path uses the Vibranium stage at the first blood moon after day 144: " + type);
                        int[] tiers = decision.getIntArray("armor_tiers");
                        h.assertValueEqual(tiers.length, 4, "A blood-date native spawn records all four independent slots");
                        if (decision.getInt("filled_slots") == 0) continue;
                        for (int index = 0; index < SLOTS.size(); index++) {
                            ItemStack stack = mob.getItemBySlot(SLOTS.get(index));
                            if (tiers[index] < 0) h.assertTrue(stack.isEmpty(), "Rejected blood slots remain empty: " + type);
                            else h.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                                    BloodMoonEquipmentRules.PREFIXES.get(tiers[index]) + SUFFIXES[index],
                                    "Each successful blood slot keeps its own selected material: " + type);
                        }
                        found = true;
                    } finally { mob.discard(); }
                }
                h.assertTrue(found, "Independent blood-date armor is reachable through native spawn and join: " + type);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinarySkeletonNativeFinalizationRetainsAnExplicitHelmetBeforeTheProgressiveRoll(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000); Mob skeleton = create(h, EntityType.SKELETON);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET); skeleton.setItemSlot(EquipmentSlot.HEAD, helmet);
            EventHooks.finalizeMobSpawn(skeleton, h.getLevel(), h.getLevel().getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.COMMAND, null);
            h.assertTrue(skeleton.getItemBySlot(EquipmentSlot.HEAD) == helmet, "Source initializer must not clear an explicit helmet before the empty-slot policy runs");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldLoadedEntitiesAndFixedSetsNeverReceiveProgressiveOrInvisibleArmor(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
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
    public static void earlyOrdinaryDaysUseThirtyPercentLegacyArmorAndKeepTheOriginalPartialMaterials(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            for (long day : new long[]{1L, 8L, 14L, 15L}) {
                set(h, day, 6000L);
                Mob accepted = create(h, EntityType.ZOMBIE);
                float enchantChance = Math.max(.1F * R196MobEquipment.tension(accepted), (day - 1L) / 400F);
                accepted.getRandom().setSeed(legacyArmorSeed(.2D, .3D, true, enchantChance));
                h.assertTrue(BloodMoonEquipment.legacyChance(accepted, .01D) == .3D,
                        "Ordinary dates before day 16 use a 30-percent legacy entrance");
                R196MobEquipment.randomArmor(accepted);
                String helmet = BuiltInRegistries.ITEM.getKey(accepted.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString();
                h.assertTrue(List.of("minecraft:leather_helmet", "mite:rusted_iron_chain_helmet", "mite:copper_chain_helmet",
                        "mite:rusted_iron_helmet", "mite:copper_helmet").contains(helmet), "Early armor keeps the original material pool");
                h.assertTrue(accepted.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                        && accepted.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && accepted.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                        "The source partial-set stop remains effective after an accepted entrance roll");
                h.assertTrue(!accepted.getPersistentData().contains(BloodMoonEquipment.DATA_KEY),
                        "Early source initialization remains available until the normal join finalizes it");
                Mob rejected = create(h, EntityType.ZOMBIE);
                rejected.getRandom().setSeed(legacyArmorSeed(.3D, .4D, false, 0F));
                R196MobEquipment.randomArmor(rejected);
                h.assertTrue(SLOTS.stream().allMatch(slot -> rejected.getItemBySlot(slot).isEmpty()),
                        "An entrance draw above 30 percent remains rejected before day 16");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryMaterialThresholdsFollowTheHighestCalendarDate(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            long[] days = {15, 16, 31, 32, 47, 48, 63, 64, 79, 80, 95, 96, 111, 112, 127, 128, 129, 143, 144, 159, 255, 256};
            int[] expected = {-1, 0, 0, 1, 1, 3, 3, 5, 5, 5, 5, 6, 6, 7, 7, 8, 8, 8, 9, 9, 9, 9};
            for (int index = 0; index < days.length; index++) {
                long day = days[index];
                // Rewind the clock for blood dates while retaining the unlocked calendar stage.
                long clockDay = day == 32L || day == 64L || day == 96L ? day - 1L : day;
                set(h, day, (clockDay - 1L) * 24000L + 6000L);
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
                boolean handled = BloodMoonEquipment.populate(mob);
                h.assertTrue(handled == (expected[index] >= 0), "The ordinary modern-material roll starts on day 16");
                if (!handled) continue;
                CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                h.assertValueEqual(decision.getInt("stage"), expected[index], "The actual ordinary spawn follows its material stage at highest day " + day);
                h.assertTrue(!decision.getBoolean("blood_date") && decision.getDouble("progress") == expected[index],
                        "Ordinary and blue dates use the ordinary equipment mode");
                int[] recorded = decision.getIntArray("armor_tiers");
                h.assertValueEqual(recorded.length, SLOTS.size(), "An ordinary spawn records every independent slot");
                int firstTier = -1;
                int filledSlots = 0;
                for (int slot = 0; slot < recorded.length; slot++) {
                    int tier = recorded[slot];
                    ItemStack stack = mob.getItemBySlot(SLOTS.get(slot));
                    if (tier < 0) h.assertTrue(stack.isEmpty(), "A rejected ordinary slot remains empty");
                    else {
                        if (firstTier < 0) firstTier = tier;
                        filledSlots |= 1 << slot;
                        h.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                                BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[slot],
                                "Each accepted ordinary slot equips its own recorded material");
                    }
                }
                h.assertValueEqual(decision.getInt("filled_slots"), filledSlots, "Only successfully generated armor is marked");
                h.assertValueEqual(decision.getInt("tier"), firstTier, "The compatibility tier follows the first generated slot");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void firstBloodUsesChainmailAndEarlyTroopReinitializationStillWorks(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L); Mob zombie = create(h, EntityType.ZOMBIE);
            zombie.getRandom().setSeed(fractionalSeed(.8D, .82D, .2D, .65D));
            h.assertTrue(BloodMoonEquipment.populate(zombie), "The first blood moon uses the progressive armor roll");
            CompoundTag decision = zombie.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getBoolean("blood_date") && decision.getInt("stage") == 1,
                    "The first blood moon starts the chainmail stage on day 32");
            h.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),
                    "A roll between 80 and 82 percent can generate real chainmail on the first blood moon");
            set(h, 8, 7L * 24000L + 18000L);
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
                h.assertTrue(armor, "The real finalize then initializeOrdinaryTroop sequence can still produce legacy armor before day 16");
                MobEquipmentEnchantments.populate(troop);
                var before = equipment(troop);
                NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(troop, h.getLevel(), false));
                unchanged(h, troop, before);
                h.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(troop),
                        "An early troop keeps the completed legacy equipment");
                set(h, 1152, 6000); BloodMoonEquipment.populate(troop); unchanged(h, troop, before);
            } finally { chunk.setInhabitedTime(inhabited); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodBoostIsMainWorldOnlyAndGeneratedArmorIsNotEnchantedTwice(GameTestHelper h) throws Exception {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 18000L);
            Mob mob = create(h, EntityType.ZOMBIE); h.assertTrue(BloodMoonEquipment.bloodDate(mob), "Actual main-world blood date enables the boost");
            Mob other = EntityType.ZOMBIE.create(h.getLevel().getServer().getLevel(Level.NETHER));
            h.assertTrue(!BloodMoonEquipment.bloodDate(other), "A shared historical stage does not create a Nether blood-date boost");
            mob.getRandom().setSeed(seed(.82, true)); BloodMoonEquipment.populate(mob);
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

    @GameTest(template = "empty")
    public static void realJoinGeneratesEveryToolWithMatchingArmorMaterialAndSourceWear(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            String[] expected = {"mite:rusted_iron_", "mite:rusted_iron_", "minecraft:golden_", "mite:copper_",
                    "mite:silver_", "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_"};
            String[] tools = {"sword", "pickaxe", "shovel", "axe"};
            for (int tier = 0; tier < expected.length; tier++) for (int tool = 0; tool < tools.length; tool++) {
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
                ItemStack helmet = new ItemStack(R196MobEquipment.item(BloodMoonEquipmentRules.PREFIXES.get(tier) + "helmet"));
                mob.setItemSlot(EquipmentSlot.HEAD, helmet); emptyArmorRoll(mob, .49D);
                mob.getRandom().setSeed(heldSeed(.485D, true, tool));
                try {
                    h.assertTrue(h.getLevel().addFreshEntity(mob), "The actual entity join path accepts the test humanoid");
                    ItemStack stack = mob.getMainHandItem();
                    h.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), expected[tier] + tools[tool],
                            "Each sword/pickaxe/shovel/axe follows existing armor, with rusted tools for leather and chainmail");
                    h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD) == helmet, "The existing armor stack is preserved");
                    h.assertTrue(EquipmentDurability.isManaged(stack) && EquipmentDurability.toolProfile(stack) != null,
                            "The held stack participates in the source quality and durability system");
                    h.assertTrue(stack.isDamageableItem() && stack.getDamageValue() >= (int)(stack.getMaxDamage() * (stack.isEnchanted() ? .5F : .7F))
                            && stack.getDamageValue() < stack.getMaxDamage(), "Actual tools receive source generated wear");
                    h.assertValueEqual(held(mob).getInt("tier"), tier, "The matched material is persisted separately from the rejected armor roll");
                    h.assertValueEqual(held(mob).getInt("tool"), tool, "The selected tool family is persisted");
                } finally { mob.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedArmorAndHeldToolShareTheMaterialWithoutASecondTierDraw(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            Mob mob = create(h, EntityType.ZOMBIE); mob.getRandom().setSeed(seed(.49D, true));
            BloodMoonEquipment.populate(mob);
            int tier = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("tier");
            mob.getRandom().setSeed(heldSeed(.485D, true, 1));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertValueEqual(held(mob).getInt("tier"), tier, "The first generated piece determines the independent held roll's material");
            h.assertValueEqual(held(mob).getInt("tool"), 1, "Only the independent acceptance and family draws precede source item generation");
            h.assertTrue(mob.getMainHandItem().getItem().toString().contains("pickaxe"), "The held family is an actual pickaxe");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nakedHumanoidsCanIndependentlyReceiveToolsAtTheSameProgressionStage(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .49D);
            long selected = -1L;
            for (long value = 0; value < 100_000 && selected < 0; value++) {
                RandomSource random = RandomSource.create(value);
                double acceptance = random.nextDouble(), material = random.nextDouble();
                if (acceptance < .485D && material > .15D && material < .3D) selected = value;
            }
            h.assertTrue(selected >= 0L, "A fixture independently succeeds at holding while selecting the upper material interval");
            mob.getRandom().setSeed(selected);
            try {
                h.getLevel().addFreshEntity(mob);
                h.assertTrue(SLOTS.stream().allMatch(slot -> mob.getItemBySlot(slot).isEmpty()), "A successful held draw does not grant rejected armor");
                h.assertTrue(!mob.getMainHandItem().isEmpty() && held(mob).getBoolean("generated"), "A naked newly joined humanoid can hold a tool");
                h.assertValueEqual(held(mob).getInt("tier"), 8, "The naked mob uses the same advanced-stage material distribution");
                h.assertTrue(BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString().startsWith("mite:adamantium_"),
                        "The recorded high material resolves to a registered actual tool");
            } finally { mob.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heldSuccessAndEmptyDecisionsPersistAndNeverConsumeRandomOrEnchantAgain(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            for (boolean success : new boolean[]{false, true}) {
                set(h, 1152, 6000);
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .49D);
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.getRandom().setSeed(heldSeed(.485D, success, 0)); BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
                h.assertTrue(mob.getMainHandItem().isEmpty() != success, "Held acceptance and empty outcomes use the independent hand fixture");
                ItemStack before = mob.getMainHandItem().copy(); CompoundTag decision = held(mob).copy();
                CompoundTag serialized = new CompoundTag(); mob.saveWithoutId(serialized);
                Mob restored = create(h, MiteMobs.type(MobKind.GHOUL)); restored.load(serialized);
                for (boolean loaded : new boolean[]{true, false, false}) {
                    restored.getRandom().setSeed(196L); BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, h.getLevel(), loaded));
                    h.assertTrue(ItemStack.matches(before, restored.getMainHandItem()), "Save/load and repeated joins preserve all wear, quality and enchantments");
                    h.assertTrue(decision.equals(held(restored)), "The persisted accepted/empty held decision is immutable");
                    h.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "An already decided hand consumes no RNG");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldArmorVersionAndLoadedIncompleteVersionNeverReceiveNewWeapons(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            for (int version : new int[]{1, 2, 3}) for (boolean loaded : new boolean[]{false, true}) {
                if (version == 3 && !loaded) continue; // A fresh, pending v3 spawn is the only legal completion path.
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .49D);
                CompoundTag legacy = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                legacy.putInt("version", version);
                if (version < 3) { legacy.remove("highest_day"); legacy.remove("progress"); }
                CompoundTag tag = new CompoundTag(); mob.saveWithoutId(tag);
                Mob restored = create(h, MiteMobs.type(MobKind.GHOUL)); restored.load(tag);
                restored.getRandom().setSeed(196L);
                if (loaded) BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, h.getLevel(), true));
                BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, h.getLevel(), false));
                h.assertTrue(restored.getMainHandItem().isEmpty(), "Old v1/v2 and loaded pending v3 entities are never retroactively upgraded");
                h.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Excluded legacy held decisions consume no random values");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitWeaponsAndAllRangedSkeletonsRetainTheirExactMainHand(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            for (var item : List.of(Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.IRON_PICKAXE)) {
                Mob mob = create(h, EntityType.ZOMBIE); emptyArmorRoll(mob, .49D);
                ItemStack explicit = new ItemStack(item); explicit.setDamageValue(1); mob.setItemSlot(EquipmentSlot.MAINHAND, explicit);
                mob.getRandom().setSeed(196L); BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
                h.assertTrue(mob.getMainHandItem() == explicit && held(mob).getBoolean("preserved"), "An existing weapon or tool retains its exact supplied stack");
                h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Preserving an existing weapon skips all held RNG");
            }
            for (var type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON)) {
                Mob skeleton = create(h, type); emptyArmorRoll(skeleton, .49D);
                ItemStack bow = new ItemStack(Items.BOW); skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
                skeleton.getRandom().setSeed(196L); BloodMoonEquipment.join(new EntityJoinLevelEvent(skeleton, h.getLevel(), false));
                h.assertTrue(skeleton.getMainHandItem() == bow, "Ranged skeleton variants keep their supplied bow");
                h.assertValueEqual(skeleton.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Skeleton exclusion consumes no held RNG");
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                BloodMoonEquipment.join(new EntityJoinLevelEvent(skeleton, h.getLevel(), false));
                h.assertTrue(skeleton.getMainHandItem().isEmpty(), "Even an empty ranged-skeleton main hand is not changed into a random melee tool");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCalendarProgressReachesRealNativeSpawnWithTheSameBloodStage(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 97, 96L * 24000L + 6000L);
            for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, MiteMobs.type(MobKind.GHOUL))) {
                Mob mob = create(h, type);
                try {
                    EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
                    h.assertTrue(h.getLevel().addFreshEntity(mob), "An ordinary-day actual native spawn joins the world");
                    CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                    h.assertValueEqual(decision.getInt("version"), 3, "New spawns use versioned daily progression");
                    h.assertValueEqual(decision.getLong("highest_day"), 97L, "The spawn records the shared calendar maximum");
                    h.assertValueEqual(decision.getInt("stage"), 6, "An ordinary spawn uses the same highest-date Ancient Metal stage");
                    h.assertTrue(!decision.getBoolean("blood_date") && decision.getDouble("progress") == 6D,
                            "The first ordinary date after the third blood moon uses the Ancient Metal stage unlocked on day 96");
                    h.assertTrue(decision.contains("held"), "The real join finalizes a held decision after native weapons have been supplied");
                    if (mob instanceof AbstractSkeleton) h.assertTrue(!mob.getMainHandItem().isEmpty(), "Native skeleton weapons survive the daily armor hook");
                } finally { mob.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryArmorAndHeldRollsUseTheirDefaultAcceptanceAndSharedMaterials(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 65, 64L * 24000L + 6000L);
            // The iron stage has 45% armor acceptance per slot and 42.5% held acceptance on ordinary dates.
            Mob armored = create(h, MiteMobs.type(MobKind.GHOUL));
            long[] weights = {2401L, 6860L, 19600L, 28000L, 28000L, 160000L, 64000L, 25600L, 10240L, 4096L};
            SlotFixture fixture = slotFixture(R196MobEquipment.tension(armored), 65L, .45D, weights, 348797D, 5, -1, 6, -1);
            armored.getRandom().setSeed(fixture.seed());
            BloodMoonEquipment.populate(armored);
            CompoundTag decision = armored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(Arrays.equals(new int[]{5, -1, 6, -1}, decision.getIntArray("armor_tiers")),
                    "The default acceptance and material distribution roll independently in each ordinary slot");
            h.assertTrue(armored.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                            && armored.getItemBySlot(EquipmentSlot.LEGS).is(R196MobEquipment.item("mite:ancient_metal_leggings")),
                    "The ordinary roll equips iron and ancient metal together");
            h.assertValueEqual(armored.getRandom().nextLong(), fixture.nextRandom(), "Each accepted slot draws its own material and wear");
            armored.getRandom().setSeed(fractionalSeed(.395D, .425D, 0D, 1D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(armored, h.getLevel(), false));
            h.assertTrue(held(armored).getBoolean("generated") && !armored.getMainHandItem().isEmpty(),
                    "An empty hand accepts at 42.5 percent, half the same-stage blood chance of 85 percent");
            h.assertValueEqual(held(armored).getInt("tier"), 5, "The held tool matches the first generated ordinary armor material");

            Mob naked = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(naked, .45D);
            naked.getRandom().setSeed(fractionalSeed(.395D, .425D, .35D, .6D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(naked, h.getLevel(), false));
            h.assertTrue(SLOTS.stream().allMatch(slot -> naked.getItemBySlot(slot).isEmpty()), "A daily armor rejection stays empty after a successful hand roll");
            h.assertValueEqual(held(naked).getInt("tier"), 5, "An unarmored hand independently uses the same blood-stage material distribution");
            h.assertTrue(BuiltInRegistries.ITEM.getKey(naked.getMainHandItem().getItem()).toString().startsWith("minecraft:iron_"),
                    "The shared iron tier resolves to an actual registered tool");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void day48CanMixActualGoldCopperAndSilverInOneSpawn(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 48, 47L * 24000L + 6000L);
            Mob mob = create(h, EntityType.ZOMBIE);
            long[] weights = {8575L, 24500L, 90000L, 90000L, 90000L, 80000L, 32000L, 12800L, 5120L, 2048L};
            int[] expected = {2, 3, -1, 4};
            SlotFixture fixture = slotFixture(R196MobEquipment.tension(mob), 48L, .43D, weights, 435043D, expected);
            mob.getRandom().setSeed(fixture.seed());
            BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getInt("stage") == 3 && Arrays.equals(expected, decision.getIntArray("armor_tiers")),
                    "Day 48's shared stage rolls three different metals and leaves a slot empty");
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)
                            && mob.getItemBySlot(EquipmentSlot.CHEST).is(R196MobEquipment.item("mite:copper_chestplate"))
                            && mob.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                            && mob.getItemBySlot(EquipmentSlot.FEET).is(R196MobEquipment.item("mite:silver_boots")),
                    "The three material draws generate the actual mixed armor items");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Mixing the three metals consumes one material draw per accepted slot");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedArmorSettingsControlDatesAndBothSlotProbabilities(GameTestHelper h) {
        var previous = MonsterArmorSettings.localSettings();
        try (Progress saved = new Progress(h)) {
            var rows = new ArrayList<>(MonsterArmorRules.DEFAULTS.rows());
            rows.set(0, new MonsterArmorRules.Row(16L, 19L, 40D, 80D));
            rows.set(1, new MonsterArmorRules.Row(20L, 47L, 0D, 100D));
            var settings = new MonsterArmorRules.Settings(rows);
            try {
                h.assertTrue(MonsterArmorSettings.save(settings), "The live armor settings accept the edited ranges and chances");
                h.assertTrue(MonsterArmorSettings.localSettings().equals(settings), "The saved settings immediately become authoritative");
                set(h, 20L, 19L * 24000L + 6000L);
                Mob ordinary = create(h, EntityType.ZOMBIE);
                h.assertTrue(BloodMoonEquipment.populate(ordinary), "The edited chainmail range governs day 20");
                CompoundTag ordinaryDecision = ordinary.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                h.assertValueEqual(ordinaryDecision.getInt("stage"), 1, "The chainmail stage starts on the configured day");
                h.assertTrue(!ordinaryDecision.getBoolean("blood_date") && SLOTS.stream().allMatch(slot -> ordinary.getItemBySlot(slot).isEmpty()),
                        "The configured 0-percent ordinary chance rejects each empty slot");

                set(h, 32L, 31L * 24000L + 6000L);
                Mob blood = create(h, EntityType.ZOMBIE);
                h.assertTrue(BloodMoonEquipment.populate(blood), "The edited range also governs real blood dates");
                CompoundTag bloodDecision = blood.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                h.assertTrue(bloodDecision.getInt("stage") == 1 && bloodDecision.getBoolean("blood_date"),
                        "Blood dates use the same configured material range");
                h.assertTrue(SLOTS.stream().allMatch(slot -> !blood.getItemBySlot(slot).isEmpty()),
                        "The configured 100-percent blood chance accepts each empty slot");
            } finally {
                h.assertTrue(MonsterArmorSettings.save(previous), "Restore the original armor settings after the test");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryHighestCalendarDayDoesNotRegressAfterClockRollbackOrNbtReload(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1024, 1023L * 24000L + 6000L);
            Mob before = create(h, EntityType.ZOMBIE); BloodMoonEquipment.populate(before);
            CompoundTag original = before.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).copy();
            h.getLevel().getServer().overworld().setDayTime(6000L);
            Mob after = create(h, EntityType.ZOMBIE); BloodMoonEquipment.populate(after);
            CompoundTag later = after.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertValueEqual(later.getLong("highest_day"), 1024L, "Rolling the actual clock back does not lower the saved daily progression");
            h.assertTrue(later.getDouble("progress") == 9D && original.getDouble("progress") == 9D,
                    "New ordinary mobs before and after rollback share the maximum calendar progress");
            CompoundTag tag = new CompoundTag(); before.saveWithoutId(tag);
            Mob restored = create(h, EntityType.ZOMBIE); restored.load(tag);
            var armor = equipment(restored); restored.getRandom().setSeed(196L);
            BloodMoonEquipment.populate(restored);
            unchanged(h, restored, armor);
            h.assertTrue(original.equals(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY)),
                    "Actual entity NBT retains the exact fractional/day/version decision without recalculation");
            h.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "An existing saved daily decision consumes no random values");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAndBloodSpawnsShareAStageButTheRecordedHandRetainsHalfAcceptance(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 96, 6000L);
            Mob ordinary = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(ordinary, .46D);
            CompoundTag ordinaryDecision = ordinary.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(ordinaryDecision.getDouble("progress") == 6D && !ordinaryDecision.getBoolean("blood_date"),
                    "An ordinary spawn uses the same highest-date Ancient Metal stage as blood");
            h.getLevel().getServer().overworld().setDayTime(95L * 24000L + 6000L);
            Mob blood = create(h, EntityType.ZOMBIE); BloodMoonEquipment.populate(blood);
            CompoundTag bloodDecision = blood.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(bloodDecision.getDouble("progress") == 6D && bloodDecision.getBoolean("blood_date"),
                    "The third blood moon uses the same Ancient Metal stage at the same highest date");
            ordinary.getRandom().setSeed(fractionalSeed(.44D, .88D, 0D, 1D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(ordinary, h.getLevel(), false));
            h.assertTrue(ordinary.getMainHandItem().isEmpty() && !held(ordinary).getBoolean("generated"),
                    "A pending ordinary hand keeps 44 percent acceptance when the current clock enters an 88-percent blood date");
            h.assertTrue(ordinaryDecision.getDouble("progress") == 6D && !ordinaryDecision.getBoolean("blood_date"),
                    "Crossing into a blood date never rewrites the previously recorded ordinary decision");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBloodDatesUseTheMaterialStageUnlockedByTheirCalendarDate(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            long[] days = {32L, 64L, 96L, 160L, 192L, 224L, 288L};
            int[] stages = {1, 5, 6, 9, 9, 9, 9};
            for (int index = 0; index < days.length; index++) {
                int stage = stages[index];
                set(h, days[index], (days[index] - 1L) * 24000L + 6000L);
                Mob mob = create(h, EntityType.ZOMBIE);
                h.assertTrue(BloodMoonEquipment.populate(mob), "Every blood moon uses the material progression roll");
                CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                h.assertValueEqual(decision.getInt("stage"), stage, "A blood-date spawn uses the stage at highest day " + days[index]);
                h.assertTrue(decision.getBoolean("blood_date") && decision.getDouble("progress") == stage,
                        "The stored blood progress agrees with the calendar stage");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodSlotsIndependentlyAcceptRejectAndMixActualArmorMaterials(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {0, 1, -1, 2};
            SlotFixture fixture = firstBloodFixture(R196MobEquipment.tension(mob), expected);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(Arrays.equals(expected, decision.getIntArray("armor_tiers")), "One real blood spawn independently gets leather, chain, no leggings and gold");
            h.assertValueEqual(decision.getInt("tier"), -1, "A mixed blood result never claims one global suit material");
            h.assertValueEqual(decision.getInt("filled_slots"), 11, "Only accepted independent slots enter the reenchantment guard");
            for (int index = 0; index < expected.length; index++) {
                ItemStack stack = mob.getItemBySlot(SLOTS.get(index));
                if (expected[index] < 0) h.assertTrue(stack.isEmpty(), "A rejected interior slot stays empty while later slots still roll");
                else {
                    h.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            BloodMoonEquipmentRules.PREFIXES.get(expected[index]) + SUFFIXES[index], "Every accepted slot resolves its own registered material");
                    h.assertTrue(!stack.isEnchanted() && stack.getDamageValue() >= (int)(stack.getMaxDamage() * .7F)
                            && stack.getDamageValue() < stack.getMaxDamage(), "Independent armor keeps the documented source quality/wear path");
                }
            }
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Real RNG matches four independent acceptance/material paths and source wear draws");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void day128UnlocksAdamantiumForBlueMoonSpawnsAndRewoundBloodDates(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 128, 127L * 24000L + 6000L);
            Mob ordinary = create(h, EntityType.ZOMBIE);
            ordinary.getRandom().setSeed(fractionalSeed(0D, .48D, .5D, .55D));
            BloodMoonEquipment.populate(ordinary);
            CompoundTag ordinaryDecision = ordinary.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(ordinaryDecision.getInt("stage") == 8 && ordinaryDecision.getInt("tier") == 8 && !ordinaryDecision.getBoolean("blood_date"),
                    "The blue moon on day 128 equips a spawn using the Adamantium stage and ordinary chances");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(ordinary.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString(),
                    "mite:adamantium_helmet", "An actual Adamantium helmet can generate on day 128");

            h.getLevel().getServer().overworld().setDayTime(95L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {8, 7, -1, 8};
            SlotFixture fixture = adamantiumBloodFixture(R196MobEquipment.tension(mob), expected);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getInt("stage") == 8 && decision.getLong("highest_day") == 128L && decision.getBoolean("blood_date"),
                    "Rewinding to the third blood moon retains the Adamantium stage unlocked on day 128");
            h.assertTrue(Arrays.equals(expected, decision.getIntArray("armor_tiers")) && decision.getInt("filled_slots") == 11,
                    "Independent 96-percent slots produce Adamantium, Mithril, empty leggings and Adamantium boots");
            String[] ids = {"mite:adamantium_helmet", "mite:mithril_chestplate", "minecraft:air", "mite:adamantium_boots"};
            for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                    BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS.get(index)).getItem()).toString(), ids[index],
                    "The per-slot decisions resolve actual registered mixed armor");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(),
                    "The earlier threshold preserves independent acceptance/material draws and source equipment wear");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thirdBloodDateCanGenerateAncientMetalAlongsideCopperAndSilver(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 96, 95L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {6, 3, 4, 6};
            SlotFixture fixture = thirdBloodFixture(R196MobEquipment.tension(mob), expected);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getInt("stage") == 6 && decision.getLong("highest_day") == 96L && decision.getBoolean("blood_date"),
                    "The actual third blood moon records the Ancient Metal stage");
            h.assertTrue(Arrays.equals(expected, decision.getIntArray("armor_tiers")) && decision.getInt("filled_slots") == 15,
                    "The Ancient Metal stage can generate Ancient Metal in two slots alongside both shared-grade materials");
            String[] ids = {"mite:ancient_metal_helmet", "mite:copper_chestplate", "mite:silver_leggings", "mite:ancient_metal_boots"};
            for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                    BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS.get(index)).getItem()).toString(), ids[index],
                    "The third-blood material draws resolve actual registered mixed armor");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Grouped weights do not add a hidden second random draw for copper versus silver");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodExplicitArmorSkipsItsDrawAndTheHandMatchesTheFirstNewMixedSlot(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            ItemStack helmet = new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(1); mob.setItemSlot(EquipmentSlot.HEAD, helmet);
            SlotFixture fixture = firstBloodFixture(R196MobEquipment.tension(mob), -2, 1, -1, 0);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD) == helmet, "An explicit blood-date helmet retains its exact stack");
            h.assertTrue(Arrays.equals(new int[]{-1, 1, -1, 0}, decision.getIntArray("armor_tiers")), "Per-slot metadata records generated armor separately from explicit armor");
            h.assertValueEqual(decision.getInt("filled_slots"), 10, "The explicit helmet is excluded from generated-slot ownership");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "An occupied slot consumes neither an acceptance nor a material draw");
            mob.getRandom().setSeed(heldSeed(.73D, true, 3));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertValueEqual(held(mob).getInt("tier"), 1, "The hand prefers the first newly generated armor over the explicit helmet and later leather boots");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString(), "mite:rusted_iron_axe",
                    "The independent hand still uses the matching chain-tier tool fallback");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodMixedSlotsAndEmptyDecisionsPersistThroughNativeSaveAndRepeatedJoins(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            SlotFixture fixture = firstBloodFixture(R196MobEquipment.tension(mob), 0, 1, -1, 2);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).copy();
            var armor = equipment(mob); ItemStack hand = mob.getMainHandItem().copy();
            CompoundTag serialized = new CompoundTag(); mob.saveWithoutId(serialized);
            set(h, 160, 159L * 24000L + 6000L);
            Mob restored = create(h, MiteMobs.type(MobKind.GHOUL)); restored.load(serialized);
            for (boolean loaded : new boolean[]{true, false, false}) {
                restored.getRandom().setSeed(196L); BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, h.getLevel(), loaded));
                unchanged(h, restored, armor);
                h.assertTrue(ItemStack.matches(hand, restored.getMainHandItem()), "Mixed armor and its optional matching hand keep exact saved stacks");
                h.assertTrue(decision.equals(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY)),
                        "Accepted, rejected and mixed per-slot decisions retain the original blood progression and materials");
                h.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Saved mixed blood armor never rerolls on reload or dimension-style rejoin");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fourRejectedBloodSlotsStillAllowAnIndependentUnarmoredMaterialTool(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 32, 31L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            SlotFixture fixture = firstBloodFixture(R196MobEquipment.tension(mob), -1, -1, -1, -1);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(Arrays.equals(new int[]{-1, -1, -1, -1}, decision.getIntArray("armor_tiers"))
                    && decision.getInt("filled_slots") == 0, "All four rejected blood slots are explicitly persisted");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Four armor rejections still consume four independent acceptance draws");
            mob.getRandom().setSeed(fractionalSeed(0D, .73D, .7D, .85D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertTrue(SLOTS.stream().allMatch(slot -> mob.getItemBySlot(slot).isEmpty()), "An accepted hand never changes four rejected armor slots");
            h.assertValueEqual(held(mob).getInt("tier"), 2, "A naked blood-date humanoid independently selects gold in the independent nine-grade interval");
            h.assertTrue(BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString().startsWith("minecraft:golden_"),
                    "The naked blood-date hand contains an actual matching gold tool");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void day144AndTheNextBloodDateGenerateActualVibraniumAndPreserveOldIndices(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            long[] weights = {5764801L, 16470860L, 47059600L, 67228000L, 67228000L,
                    384160000L, 1097600000L, 3136000000L, 8960000000L, 25600000000L};
            set(h, 160L, 159L * 24000L + 6000L);
            Mob blood = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {9, 8, -1, 9};
            SlotFixture fixture = slotFixture(R196MobEquipment.tension(blood), 160L, .98D, weights, 39381511261D, expected);
            blood.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(blood);
            CompoundTag decision = blood.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertValueEqual(decision.getInt("stage"), 9, "The fourth blood moon uses the Vibranium stage unlocked on day 144");
            h.assertTrue(Arrays.equals(expected, decision.getIntArray("armor_tiers")), "Vibranium is appended at9; Adamantium stays8; rejected slots remain empty");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(blood.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString(),
                    "mite:vibranium_helmet", "Highest material resolves to actual registered Vibranium armor");
            set(h, 144L, 143L * 24000L + 6000L);
            Mob ordinary = create(h, EntityType.ZOMBIE);
            ordinary.getRandom().setSeed(fractionalSeed(0D, .49D, .75D, .95D));
            BloodMoonEquipment.populate(ordinary);
            CompoundTag ordinaryDecision = ordinary.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(ordinaryDecision.getDouble("progress") == 9D && !ordinaryDecision.getBoolean("blood_date"),
                    "On day 144 an ordinary spawn reaches the Vibranium stage");
            h.assertValueEqual(ordinaryDecision.getInt("tier"), 9, "Independent upper interval chooses Vibranium at the ordinary cap");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(ordinary.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString(),
                    "mite:vibranium_helmet", "An actual Vibranium helmet can generate on day 144");
            var before = equipment(ordinary); CompoundTag tag = new CompoundTag(); ordinary.saveWithoutId(tag);
            Mob restored = create(h, EntityType.ZOMBIE); restored.load(tag);
            restored.getRandom().setSeed(196L); BloodMoonEquipment.populate(restored); unchanged(h, restored, before);
            h.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Saved Vibranium never rerolls");
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
