package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
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
    private static long fractionalSeed(double acceptanceMinimum, double acceptanceMaximum, double materialMinimum, double materialMaximum) {
        for (long value = 0; value < 100_000; value++) {
            RandomSource random = RandomSource.create(value);
            double acceptance = random.nextDouble(), material = random.nextDouble();
            if (acceptance >= acceptanceMinimum && acceptance < acceptanceMaximum && material >= materialMinimum && material < materialMaximum) return value;
        }
        throw new AssertionError("No independent fractional progression fixture");
    }
    private record BloodFixture(long seed, long nextRandom) {}
    /** Independent first-stage blood probabilities and the documented unenchanted source-item RNG path. */
    private static BloodFixture bloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {78125L, 31250L, 12500L, 2500L, 2500L, 2000L, 800L, 320L, 128L};
        return bloodFixture(tension, .8D, materialWeights, 130123D, expectedTiers);
    }
    /** Independent eight-grade rational weights, with the copper/silver grade divided in half. */
    private static BloodFixture highestBloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {823543L, 2352980L, 6722800L, 9604000L, 9604000L,
                54880000L, 156800000L, 448000000L, 1280000000L};
        return bloodFixture(tension, .96D, materialWeights, 1968787323D, expectedTiers);
    }
    private static BloodFixture copperSilverBloodFixture(float tension, int... expectedTiers) {
        long[] materialWeights = {1715L, 4900L, 14000L, 20000L, 20000L, 16000L, 6400L, 2560L, 1024L};
        return bloodFixture(tension, .86D, materialWeights, 86599D, expectedTiers);
    }
    private static BloodFixture bloodFixture(float tension, double acceptance, long[] materialWeights, double denominator, int... expectedTiers) {
        candidates: for (long value = 0; value < 100_000; value++) {
            RandomSource random = RandomSource.create(value);
            for (int expected : expectedTiers) {
                if (expected == -2) continue; // An explicitly occupied slot must skip both acceptance and material draws.
                boolean accepted = random.nextDouble() < acceptance;
                if (accepted != (expected >= 0)) continue candidates;
                if (!accepted) continue;
                double material = random.nextDouble();
                long cumulative = 0L; int actual = 8;
                for (int tier = 0; tier < materialWeights.length; tier++) {
                    cumulative += materialWeights[tier];
                    if (material < cumulative / denominator) { actual = tier; break; }
                }
                if (actual != expected) continue candidates;
                if (random.nextFloat() < .1F * tension) continue candidates; // Select a fixture without extra enchantment draws.
                random.nextFloat(); // One source wear draw follows quality assignment, which consumes no RNG.
            }
            return new BloodFixture(value, random.nextLong());
        }
        throw new AssertionError("No independent per-slot blood fixture");
    }
    private static CompoundTag held(Mob mob) { return mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getCompound("held"); }
    private static void emptyArmorRoll(Mob mob, double chance) {
        mob.getRandom().setSeed(seed(chance, false)); BloodMoonEquipment.populate(mob);
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
            ItemStack weapon = new ItemStack(Items.NETHERITE_SWORD);
            mob.setItemSlot(EquipmentSlot.HEAD, helmet); mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            mob.getRandom().setSeed(seed(.5, true));
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
                set(h, 64, 6000); Mob mob = create(h, EntityType.ZOMBIE); mob.getRandom().setSeed(seed(.5, success));
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
            set(h, 320, 319L * 24000L + 6000L);
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
                        h.assertValueEqual(decision.getInt("stage"), 8, "Every eligible actual spawn path reaches the maximum stage on the eighth blood date: " + type);
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
                h.assertTrue(found, "Independent96-percent-per-slot armor is reachable through native spawn and join: " + type);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinarySkeletonNativeFinalizationRetainsAnExplicitHelmetBeforeTheProgressiveRoll(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000); Mob skeleton = create(h, EntityType.SKELETON);
            ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET); skeleton.setItemSlot(EquipmentSlot.HEAD, helmet);
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
                set(h, 1152, 6000); BloodMoonEquipment.populate(troop); unchanged(h, troop, before);
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
            mob.getRandom().setSeed(seed(.8, true)); BloodMoonEquipment.populate(mob);
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
                    "mite:silver_", "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_"};
            String[] tools = {"sword", "pickaxe", "shovel", "axe"};
            for (int tier = 0; tier < expected.length; tier++) for (int tool = 0; tool < tools.length; tool++) {
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
                ItemStack helmet = new ItemStack(R196MobEquipment.item(BloodMoonEquipmentRules.PREFIXES.get(tier) + "helmet"));
                mob.setItemSlot(EquipmentSlot.HEAD, helmet); emptyArmorRoll(mob, .9D);
                mob.getRandom().setSeed(heldSeed(.8D, true, tool));
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
            Mob mob = create(h, EntityType.ZOMBIE); mob.getRandom().setSeed(seed(.9D, true));
            BloodMoonEquipment.populate(mob);
            int tier = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("tier");
            mob.getRandom().setSeed(heldSeed(.8D, true, 1));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertValueEqual(held(mob).getInt("tier"), tier, "A successfully generated set determines the independent held roll's material");
            h.assertValueEqual(held(mob).getInt("tool"), 1, "Only the independent acceptance and family draws precede source item generation");
            h.assertTrue(mob.getMainHandItem().getItem().toString().contains("pickaxe"), "The held family is an actual pickaxe");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nakedHumanoidsCanIndependentlyReceiveToolsAtTheSameProgressionStage(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 1152, 6000);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .9D);
            long selected = -1L;
            for (long value = 0; value < 100_000 && selected < 0; value++) {
                RandomSource random = RandomSource.create(value);
                if (random.nextDouble() < .8D && random.nextDouble() > .8D) selected = value;
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
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .9D);
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.getRandom().setSeed(heldSeed(.8D, success, 0)); BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
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
                Mob mob = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(mob, .9D);
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
            for (var item : List.of(Items.NETHERITE_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.IRON_PICKAXE)) {
                Mob mob = create(h, EntityType.ZOMBIE); emptyArmorRoll(mob, .9D);
                ItemStack explicit = new ItemStack(item); explicit.setDamageValue(1); mob.setItemSlot(EquipmentSlot.MAINHAND, explicit);
                mob.getRandom().setSeed(196L); BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
                h.assertTrue(mob.getMainHandItem() == explicit && held(mob).getBoolean("preserved"), "An existing weapon or tool retains its exact supplied stack");
                h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Preserving an existing weapon skips all held RNG");
            }
            for (var type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON)) {
                Mob skeleton = create(h, type); emptyArmorRoll(skeleton, .9D);
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
    public static void ordinaryCalendarProgressReachesRealNativeSpawnAndJoinWithoutBloodStages(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 200, 199L * 24000L + 6000L);
            for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, MiteMobs.type(MobKind.GHOUL))) {
                Mob mob = create(h, type);
                try {
                    EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
                    h.assertTrue(h.getLevel().addFreshEntity(mob), "An ordinary-day actual native spawn joins the world");
                    CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                    h.assertValueEqual(decision.getInt("version"), 3, "New spawns use versioned daily progression");
                    h.assertValueEqual(decision.getLong("highest_day"), 200L, "The spawn records the shared calendar maximum");
                    h.assertValueEqual(decision.getInt("stage"), 0, "The compatibility stage is the floor of daily progress, rather than the historical blood stage");
                    h.assertTrue(!decision.getBoolean("blood_date") && decision.getDouble("progress") == 9D / 16D,
                            "Day200 uses exactly9/16 daily progress despite five historical blood moons");
                    h.assertTrue(decision.contains("held"), "The real join finalizes a held decision after native weapons have been supplied");
                    if (mob instanceof AbstractSkeleton) h.assertTrue(!mob.getMainHandItem().isEmpty(), "Native skeleton weapons survive the daily armor hook");
                } finally { mob.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryFractionalArmorAndHeldRollsUseDailyAcceptanceAndMaterialInterpolation(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 200, 199L * 24000L + 6000L);
            // Independently specified day200: p=9/16, armor52.8125%, held42.8125%.
            // The independent eight-grade day200 mixture puts0.55..0.64 in chainmail (0.48155..0.87041), while basis0 yields leather.
            Mob armored = create(h, MiteMobs.type(MobKind.GHOUL));
            armored.getRandom().setSeed(fractionalSeed(.5D, .528125D, .55D, .64D));
            BloodMoonEquipment.populate(armored);
            h.assertValueEqual(armored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getInt("tier"), 1,
                    "Both fractional armor acceptance and the independently bounded material interval affect real registered items");
            for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                    BuiltInRegistries.ITEM.getKey(armored.getItemBySlot(SLOTS.get(index)).getItem()).toString(), "minecraft:chainmail_" + SUFFIXES[index],
                    "The daily mixture creates one matching complete set");
            armored.getRandom().setSeed(fractionalSeed(.4D, .428125D, 0D, 1D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(armored, h.getLevel(), false));
            h.assertTrue(held(armored).getBoolean("generated") && !armored.getMainHandItem().isEmpty(),
                    "A hand acceptance draw above the integer stage's40 percent succeeds at the daily42.8125 percent");
            h.assertValueEqual(held(armored).getInt("tier"), 1, "The held tool matches the generated daily armor material");

            Mob naked = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(naked, .528125D);
            naked.getRandom().setSeed(fractionalSeed(.4D, .428125D, .55D, .64D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(naked, h.getLevel(), false));
            h.assertTrue(SLOTS.stream().allMatch(slot -> naked.getItemBySlot(slot).isEmpty()), "A daily armor rejection stays empty after a successful hand roll");
            h.assertValueEqual(held(naked).getInt("tier"), 1, "An unarmored hand independently uses the same fractional material mixture");
            h.assertTrue(BuiltInRegistries.ITEM.getKey(naked.getMainHandItem().getItem()).toString().startsWith("mite:rusted_iron_"),
                    "The chain tier resolves to its existing registered rusted-iron tool fallback");
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
            h.assertTrue(later.getDouble("progress") == 7D && original.getDouble("progress") == 7D,
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
    public static void ordinaryAndBloodSpawnsUseDifferentProgressAndARecordedHandNeverChangesItsClock(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 160, 6000L);
            Mob ordinary = create(h, MiteMobs.type(MobKind.GHOUL)); emptyArmorRoll(ordinary, .5125D);
            CompoundTag ordinaryDecision = ordinary.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(ordinaryDecision.getDouble("progress") == .25D && !ordinaryDecision.getBoolean("blood_date"),
                    "A late ordinary spawn uses slower daily progress rather than the highest unlocked blood tier");
            h.getLevel().getServer().overworld().setDayTime(159L * 24000L + 6000L);
            Mob blood = create(h, EntityType.ZOMBIE); BloodMoonEquipment.populate(blood);
            CompoundTag bloodDecision = blood.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(bloodDecision.getDouble("progress") == 2D && bloodDecision.getBoolean("blood_date"),
                    "The same historical day uses the fourth blood moon's gold stage on a blood date");
            ordinary.getRandom().setSeed(fractionalSeed(.4125D, .76D, 0D, 1D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(ordinary, h.getLevel(), false));
            h.assertTrue(ordinary.getMainHandItem().isEmpty() && !held(ordinary).getBoolean("generated"),
                    "A pending ordinary hand uses its saved41.25 percent chance even after the current clock enters a76-percent blood date");
            h.assertTrue(ordinaryDecision.getDouble("progress") == .25D && !ordinaryDecision.getBoolean("blood_date"),
                    "Crossing into a blood date never rewrites the previously recorded ordinary decision");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBloodDatesKeepEarlierStagesAndReachAdamantiumOnTheEighth(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            long[] days = {64L, 96L, 160L, 192L, 224L, 288L, 320L, 352L, 416L};
            int[] stages = {0, 1, 2, 3, 5, 6, 8, 8, 8};
            for (int index = 0; index < days.length; index++) {
                int stage = stages[index];
                set(h, days[index], (days[index] - 1L) * 24000L + 6000L);
                Mob mob = create(h, EntityType.ZOMBIE);
                h.assertTrue(BloodMoonEquipment.populate(mob), "Every blood moon from the second onward owns a progressive spawn roll");
                CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                h.assertValueEqual(decision.getInt("stage"), stage, "Actual blood dates share copper/silver, then iron and ancient metal before Adamantium at count eight");
                h.assertTrue(decision.getBoolean("blood_date") && decision.getDouble("progress") == stage,
                        "The stored blood progress agrees with the eighth-blood threshold");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodSlotsIndependentlyAcceptRejectAndMixActualArmorMaterials(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 64, 63L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {0, 1, -1, 2};
            BloodFixture fixture = bloodFixture(R196MobEquipment.tension(mob), expected);
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
    public static void eighthBloodDateActuallyGeneratesMixedAdamantiumAndStillAllowsAnEmptySlot(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 320, 319L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {8, 7, -1, 8};
            BloodFixture fixture = highestBloodFixture(R196MobEquipment.tension(mob), expected);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getInt("stage") == 8 && decision.getLong("highest_day") == 320L && decision.getBoolean("blood_date"),
                    "The actual eighth blood date records its advanced progression");
            h.assertTrue(Arrays.equals(expected, decision.getIntArray("armor_tiers")) && decision.getInt("filled_slots") == 11,
                    "Independent96-percent slots produce Adamantium, Mithril, empty leggings and Adamantium boots");
            String[] ids = {"mite:adamantium_helmet", "mite:mithril_chestplate", "minecraft:air", "mite:adamantium_boots"};
            for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                    BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS.get(index)).getItem()).toString(), ids[index],
                    "Eighth-blood per-slot decisions resolve actual registered mixed armor");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(),
                    "The earlier threshold preserves independent acceptance/material draws and source equipment wear");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fifthBloodDateCanMixCopperAndSilverWithinOneMaterialGrade(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 192, 191L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            int[] expected = {3, 4, -1, 5};
            BloodFixture fixture = copperSilverBloodFixture(R196MobEquipment.tension(mob), expected);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(decision.getInt("stage") == 3 && Arrays.equals(expected, decision.getIntArray("armor_tiers")),
                    "The fifth blood moon shares one copper/silver grade but persists their distinct material IDs");
            String[] ids = {"mite:copper_helmet", "mite:silver_chestplate", "minecraft:air", "minecraft:iron_boots"};
            for (int index = 0; index < SLOTS.size(); index++) h.assertValueEqual(
                    BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS.get(index)).getItem()).toString(), ids[index],
                    "One copper/silver grade allows actual mixed equipment and independent empty slots");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Grouped weights do not add a hidden second random draw for copper versus silver");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodExplicitArmorSkipsItsDrawAndTheHandMatchesTheFirstNewMixedSlot(GameTestHelper h) {
        try (Progress saved = new Progress(h)) {
            set(h, 64, 63L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET); helmet.setDamageValue(1); mob.setItemSlot(EquipmentSlot.HEAD, helmet);
            BloodFixture fixture = bloodFixture(R196MobEquipment.tension(mob), -2, 1, -1, 0);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD) == helmet, "An explicit blood-date helmet retains its exact stack");
            h.assertTrue(Arrays.equals(new int[]{-1, 1, -1, 0}, decision.getIntArray("armor_tiers")), "Per-slot metadata records generated armor separately from explicit armor");
            h.assertValueEqual(decision.getInt("filled_slots"), 10, "The explicit helmet is excluded from generated-slot ownership");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "An occupied slot consumes neither an acceptance nor a material draw");
            mob.getRandom().setSeed(heldSeed(.7D, true, 3));
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
            set(h, 64, 63L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            BloodFixture fixture = bloodFixture(R196MobEquipment.tension(mob), 0, 1, -1, 2);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).copy();
            var armor = equipment(mob); ItemStack hand = mob.getMainHandItem().copy();
            CompoundTag serialized = new CompoundTag(); mob.saveWithoutId(serialized);
            set(h, 320, 319L * 24000L + 6000L);
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
            set(h, 64, 63L * 24000L + 6000L);
            Mob mob = create(h, MiteMobs.type(MobKind.GHOUL));
            BloodFixture fixture = bloodFixture(R196MobEquipment.tension(mob), -1, -1, -1, -1);
            mob.getRandom().setSeed(fixture.seed()); BloodMoonEquipment.populate(mob);
            CompoundTag decision = mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
            h.assertTrue(Arrays.equals(new int[]{-1, -1, -1, -1}, decision.getIntArray("armor_tiers"))
                    && decision.getInt("filled_slots") == 0, "All four rejected blood slots are explicitly persisted");
            h.assertValueEqual(mob.getRandom().nextLong(), fixture.nextRandom(), "Four armor rejections still consume four independent acceptance draws");
            mob.getRandom().setSeed(fractionalSeed(0D, .7D, .85D, .93D));
            BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertTrue(SLOTS.stream().allMatch(slot -> mob.getItemBySlot(slot).isEmpty()), "An accepted hand never changes four rejected armor slots");
            h.assertValueEqual(held(mob).getInt("tier"), 2, "A naked blood-date humanoid independently selects gold in the eight-grade interval15625/18589..121875/130123");
            h.assertTrue(BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString().startsWith("minecraft:golden_"),
                    "The naked blood-date hand contains an actual matching gold tool");
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
