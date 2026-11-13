package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.SpecialMobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual fixed-set spawn transactions, independent material floors and native equipment persistence. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SpecialMobEquipmentGameTests {
    private static final List<MobKind> KINDS = List.of(MobKind.BONE_LORD, MobKind.LONGDEAD_GUARDIAN,
            MobKind.ANCIENT_BONE_LORD, MobKind.REVENANT, MobKind.LONGDEAD);
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<EquipmentSlot> EQUIPMENT = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final String[] PARTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final Map<String, Integer> MATERIALS = Map.of("rusted_iron", 0, "gold", 1, "copper", 2,
            "silver", 2, "iron", 3, "ancient_metal", 4, "mithril", 5, "adamantium", 6, "vibranium", 7);

    private SpecialMobEquipmentGameTests() {}

    private static Mob create(GameTestHelper helper, MobKind kind, long seed) {
        return create(helper, kind, helper.getLevel(), seed);
    }

    private static Mob create(GameTestHelper helper, MobKind kind, ServerLevel level, long seed) {
        Mob mob = MiteMobs.type(kind).create(level);
        if (mob == null) throw new AssertionError("Registered fixed-set entity cannot be created: " + kind);
        mob.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        mob.setNoAi(true); mob.setNoGravity(true); mob.getRandom().setSeed(seed);
        return mob;
    }

    private static void finalizeNative(Mob mob, MobSpawnType reason, int forcedSkeletonType) {
        if (mob instanceof R196Skeleton skeleton) skeleton.forceSkeletonType(forcedSkeletonType);
        ServerLevel level = (ServerLevel)mob.level();
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null);
    }

    private static void set(GameTestHelper helper, long highestDay, long dayTime) {
        helper.getLevel().getServer().overworld().setDayTime(dayTime);
        BloodMoonInvasionData.get(helper.getLevel()).restoreProgress(highestDay);
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String armorMaterial(ItemStack piece) {
        if (piece.is(Items.IRON_HELMET) || piece.is(Items.IRON_CHESTPLATE)
                || piece.is(Items.IRON_LEGGINGS) || piece.is(Items.IRON_BOOTS)) return "iron";
        var definition = MiteArmorContent.definition(piece.getItem());
        return definition == null ? "unrecognized" : definition.material();
    }

    private static boolean ancient(MobKind kind) {
        return kind == MobKind.LONGDEAD || kind == MobKind.LONGDEAD_GUARDIAN || kind == MobKind.ANCIENT_BONE_LORD;
    }

    private static boolean chain(MobKind kind) {
        return kind == MobKind.LONGDEAD || kind == MobKind.LONGDEAD_GUARDIAN;
    }

    private static String baselineArmor(MobKind kind) {
        return chain(kind) ? "mite:ancient_metal_chainmail_" : ancient(kind) ? "mite:ancient_metal_" : "mite:rusted_iron_";
    }

    private static long firstDraw(double lower, double upper) {
        for (long seed = 0; seed < 100_000; seed++) {
            double draw = RandomSource.create(seed).nextDouble();
            if (draw >= lower && draw < upper) return seed;
        }
        throw new AssertionError("No independent first-double fixture in the requested interval");
    }

    private static List<ItemStack> equipment(Mob mob) {
        return EQUIPMENT.stream().map(slot -> mob.getItemBySlot(slot).copy()).toList();
    }

    private static void unchanged(GameTestHelper helper, Mob mob, List<ItemStack> expected) {
        for (int index = 0; index < EQUIPMENT.size(); index++) helper.assertTrue(
                ItemStack.matches(expected.get(index), mob.getItemBySlot(EQUIPMENT.get(index))),
                "Exact stack, components and wear survive for " + EQUIPMENT.get(index));
    }

    private static void floor(GameTestHelper helper, Mob mob, MobKind kind) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = mob.getItemBySlot(slot);
            var definition = MiteArmorContent.definition(piece.getItem());
            helper.assertTrue(!piece.isEmpty() && !armorMaterial(piece).equals("unrecognized"),
                    "A fixed-set piece cannot be lost: " + kind + "/" + slot);
            helper.assertTrue((definition != null && definition.chainmail()) == chain(kind), "The source plate/chain family remains intact: " + kind);
            helper.assertTrue(MATERIALS.getOrDefault(armorMaterial(piece), -1) >= (ancient(kind) ? 4 : 0),
                    "The material never falls below its independently recorded source floor: " + kind + "/" + id(piece));
            helper.assertTrue(piece.isDamageableItem() && piece.getDamageValue() > 0 && piece.getDamageValue() < piece.getMaxDamage(),
                    "Source generated wear remains on the final actual item");
        }
    }

    @GameTest(template = "empty")
    public static void earlyRealSpawnsKeepAllFiveSourceSetsAndTheirEffectiveQualities(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1L, 6_000L);
            for (MobKind kind : KINDS) {
                Mob mob = create(helper, kind, 196L);
                finalizeNative(mob, MobSpawnType.NATURAL, 2);
                floor(helper, mob, kind);
                for (int index = 0; index < ARMOR.size(); index++) {
                    ItemStack piece = mob.getItemBySlot(ARMOR.get(index));
                    helper.assertValueEqual(id(piece), baselineArmor(kind) + PARTS[index], "Pre-progression source armor identity");
                    helper.assertValueEqual(EquipmentDurability.quality(piece), ancient(kind) ? Quality.AVERAGE : Quality.POOR,
                            "Material growth does not invent a random fine-item quality roll");
                }
                String weapon = id(mob.getMainHandItem());
                helper.assertTrue(weapon.startsWith(ancient(kind) ? "mite:ancient_metal_" : "mite:rusted_iron_"),
                        "The original source weapon material is retained before growth: " + kind);
                helper.assertTrue(weapon.endsWith("sword") || weapon.endsWith("battle_axe") || weapon.endsWith("war_hammer"),
                        "Source melee weapon families are not replaced by generic random tools");
                if (chain(kind)) helper.assertValueEqual(weapon, "mite:ancient_metal_sword", "Forced source melee longdead retains its sword");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lowRollsNeverReplaceTheSourceFloorAndInactiveProgressConsumesNoMaterialRandom(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 32L, 63L}) {
                set(helper, day, (day - 1L) * 24_000L + 6_000L);
                Mob mob = create(helper, MobKind.ANCIENT_BONE_LORD, 196L);
                helper.assertValueEqual(SpecialMobEquipment.itemId(mob, "mite:ancient_metal_helmet"), "mite:ancient_metal_helmet",
                        "The first blood moon and pre-day64 ordinary dates retain the source material");
                helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                        "Inactive progression introduces no material RNG draw");
            }
            set(helper, 64L, 6_000L);
            for (String source : List.of("mite:rusted_iron_helmet", "mite:ancient_metal_helmet",
                    "mite:ancient_metal_chainmail_helmet", "mite:ancient_metal_sword", "mite:ancient_metal_dagger", "mite:ancient_metal_bow")) {
                Mob mob = create(helper, MobKind.ANCIENT_BONE_LORD, firstDraw(0D, .05D));
                helper.assertValueEqual(SpecialMobEquipment.itemId(mob, source), source,
                        "A real low material draw retains the exact source identity instead of downgrading " + source);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lateRealSpawnsGrowMaterialsAndActuallyEquipTheirWornNpcArmorAttributes(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            for (MobKind kind : KINDS) {
                boolean advancedArmor = false, advancedWeapon = false;
                for (long seed = 0; seed < 24; seed++) {
                    Mob mob = create(helper, kind, seed);
                    finalizeNative(mob, MobSpawnType.NATURAL, 2);
                    floor(helper, mob, kind);
                    for (EquipmentSlot slot : ARMOR) {
                        String material = armorMaterial(mob.getItemBySlot(slot));
                        advancedArmor |= material.equals("mithril") || material.equals("adamantium") || material.equals("vibranium");
                    }
                    String held = id(mob.getMainHandItem());
                    advancedWeapon |= held.startsWith("mite:mithril_") || held.startsWith("mite:adamantium_") || held.startsWith("mite:vibranium_");
                    if (chain(kind)) helper.assertTrue(held.endsWith("_sword"), "A source melee longdead never loses its weapon family");
                    mob.tick();
                    helper.assertTrue(mob.getAttributeValue(Attributes.ARMOR) >= (chain(kind) ? 16D : ancient(kind) ? 18D : 16D),
                            "Native NPC equipment reconciliation applies at least the full source-set armor despite generated wear");
                    helper.assertTrue(mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= (chain(kind) ? 1.5D : ancient(kind) ? 2D : 1.5D),
                            "Native NPC equipment reconciliation retains the source toughness floor");
                    mob.discard();
                }
                helper.assertTrue(advancedArmor && advancedWeapon, "Actual late-game finalization produces high material armor and melee equipment: " + kind);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eighthBloodMoonAndOrdinaryDatesShareMaterialDistributionsWithoutChangingFloors(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            long seed = firstDraw(.5D, .55D);
            set(helper, 320L, 6_000L);
            Mob ordinary = create(helper, MobKind.ANCIENT_BONE_LORD, seed);
            helper.assertValueEqual(SpecialMobEquipment.itemId(ordinary, "mite:ancient_metal_helmet"), "mite:adamantium_helmet",
                    "An ordinary day320 middle draw uses the same adamantium distribution as blood");
            CompoundTag normal = ordinary.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY);
            helper.assertTrue(!normal.getBoolean("blood_date") && normal.getDouble("progress") == 8D,
                    "Ordinary growth reaches the same eighth blood-moon stage on day320");

            set(helper, 320L, 319L * 24_000L + 6_000L);
            Mob blood = create(helper, MobKind.ANCIENT_BONE_LORD, seed);
            helper.assertValueEqual(SpecialMobEquipment.itemId(blood, "mite:ancient_metal_helmet"), "mite:adamantium_helmet",
                    "The same middle draw is adamantium at the eighth blood moon");
            CompoundTag crimson = blood.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY);
            helper.assertTrue(crimson.getBoolean("blood_date") && crimson.getDouble("progress") == 8D,
                    "Blood day320 and ordinary highest day320 share the eighth blood-moon stage");
            helper.assertTrue(crimson.getLong("highest_day") == 320L && crimson.getInt("version") > 0,
                    "The authoritative highest day and schema version accompany the spawn snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredChainWeaponsAndBowCeilingsResolveWithoutInventingItems(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            Map<String, String> expected = Map.of(
                    "mite:ancient_metal_chainmail_helmet", "mite:adamantium_chain_helmet",
                    "mite:ancient_metal_chainmail_chestplate", "mite:adamantium_chain_chestplate",
                    "mite:ancient_metal_dagger", "mite:vibranium_dagger",
                    "mite:ancient_metal_battle_axe", "mite:vibranium_battle_axe",
                    "mite:ancient_metal_war_hammer", "mite:vibranium_war_hammer",
                    "mite:ancient_metal_bow", "mite:vibranium_bow");
            for (var mapping : expected.entrySet()) {
                Mob mob = create(helper, MobKind.LONGDEAD_GUARDIAN, firstDraw(.999D, 1D));
                String actual = SpecialMobEquipment.itemId(mob, mapping.getKey());
                helper.assertValueEqual(actual, mapping.getValue(), "A high draw preserves equipment family and uses actual registered material ceilings");
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(actual)),
                        "Progression cannot resolve to an absent or air item: " + actual);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void intermediateRollsResolveNativeIronPlateAndGoldenAndIronBattleAxes(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            // Shared stage9: gold [22235661,69295261)/39381511261; iron [203751261,587911261)/39381511261.
            long gold = firstDraw(.001D, .0015D), iron = firstDraw(.008D, .01D);
            Mob plate = create(helper, MobKind.BONE_LORD, iron);
            helper.assertValueEqual(SpecialMobEquipment.itemId(plate, "mite:rusted_iron_helmet"), "minecraft:iron_helmet",
                    "Iron plate uses the retained native item ID rather than a nonexistent MITE registration");
            Mob goldenAxe = create(helper, MobKind.REVENANT, gold);
            helper.assertValueEqual(SpecialMobEquipment.itemId(goldenAxe, "mite:rusted_iron_battle_axe"), "mite:golden_battle_axe",
                    "A golden source-family weapon uses its actual golden_ item spelling");
            Mob ironAxe = create(helper, MobKind.REVENANT, iron);
            helper.assertValueEqual(SpecialMobEquipment.itemId(ironAxe, "mite:rusted_iron_battle_axe"), "mite:iron_battle_axe",
                    "A source battle axe remains a battle axe when its material becomes iron");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalRegisteredItemReceivesExactlyOneSourceWearGeneration(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            long seed = -1L;
            for (long candidate = 0; candidate < 1_000_000 && seed < 0L; candidate++) {
                RandomSource random = RandomSource.create(candidate);
                if (random.nextDouble() >= .999D && random.nextFloat() >= .75F) seed = candidate;
            }
            helper.assertTrue(seed >= 0L, "An independent late-world fixture selects the highest material and rejects its75-percent enchantment roll");
            Mob mob = create(helper, MobKind.ANCIENT_BONE_LORD, seed);
            RandomSource oracle = RandomSource.create(seed);
            oracle.nextDouble(); // One material selection, then the single calendar enchantment decision.
            oracle.nextFloat();
            float fraction = .7F + .3F * oracle.nextFloat();
            ItemStack item = SpecialMobEquipment.generated(mob, "mite:ancient_metal_helmet", false);
            helper.assertValueEqual(id(item), "mite:vibranium_helmet", "The material decision occurs before source item generation");
            helper.assertTrue(!item.isEnchanted() && EquipmentDurability.quality(item) == Quality.AVERAGE,
                    "The ordinary metal item quality remains the source default without a fabricated quality lottery");
            helper.assertValueEqual(item.getDamageValue(), Math.min(item.getMaxDamage() - 1, (int)(item.getMaxDamage() * fraction)),
                    "Only the final actual item receives its independently transcribed source wear fraction");
            helper.assertValueEqual(mob.getRandom().nextLong(), oracle.nextLong(),
                    "No discarded first equipment stack consumes a second set of source wear or enchantment draws");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSpawnKeepsOneDateSnapshotWhenAnotherPieceIsGeneratedAfterTheClockChanges(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 64L, 6_000L);
            Mob mob = create(helper, MobKind.ANCIENT_BONE_LORD, firstDraw(0D, .05D));
            SpecialMobEquipment.itemId(mob, "mite:ancient_metal_helmet");
            CompoundTag original = mob.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY).copy();
            set(helper, 320L, 319L * 24_000L + 6_000L);
            mob.getRandom().setSeed(firstDraw(.5D, .55D));
            helper.assertValueEqual(SpecialMobEquipment.itemId(mob, "mite:ancient_metal_chestplate"), "mite:ancient_metal_chestplate",
                    "Another piece on this existing transaction cannot use a later blood-date material distribution");
            helper.assertTrue(original.equals(mob.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY)),
                    "Highest day, ordinary/blood mode and progress are captured once for the whole spawn");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardianBowAndStowedDaggerSwitchAndPersistAlongsideTheFrozenSpawnSnapshot(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            R196Skeleton guardian = null;
            for (long seed = 0; seed < 48 && guardian == null; seed++) {
                R196Skeleton candidate = (R196Skeleton)create(helper, MobKind.LONGDEAD_GUARDIAN, seed);
                finalizeNative(candidate, MobSpawnType.SPAWN_EGG, 0);
                if ((id(candidate.getMainHandItem()).equals("mite:mithril_bow") || id(candidate.getMainHandItem()).equals("mite:vibranium_bow"))
                        && (id(candidate.stowedItem()).equals("mite:mithril_dagger") || id(candidate.stowedItem()).equals("mite:adamantium_dagger") || id(candidate.stowedItem()).equals("mite:vibranium_dagger"))) guardian = candidate;
            }
            helper.assertTrue(guardian != null, "Real guardian finalization upgrades its registered bow and hidden melee weapon");
            floor(helper, guardian, MobKind.LONGDEAD_GUARDIAN);
            ItemStack bow = guardian.getMainHandItem().copy(), dagger = guardian.stowedItem().copy();
            guardian.swapHeldItemWithStowed();
            helper.assertTrue(ItemStack.matches(guardian.getMainHandItem(), dagger) && ItemStack.matches(guardian.stowedItem(), bow),
                    "Switching to close combat uses the already generated upgraded dagger without recreating it");
            CompoundTag snapshot = guardian.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY).copy();
            CompoundTag saved = new CompoundTag(); guardian.saveWithoutId(saved);
            set(helper, 64L, 6_000L);
            R196Skeleton restored = (R196Skeleton)create(helper, MobKind.LONGDEAD_GUARDIAN, 196L); restored.load(saved);
            helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), dagger) && ItemStack.matches(restored.stowedItem(), bow),
                    "Native save/read preserves both equipped and stowed stacks including quality, enchantments and wear");
            helper.assertTrue(snapshot.equals(restored.getPersistentData().getCompound(SpecialMobEquipment.DATA_KEY)),
                    "The original late-date progression snapshot survives an earlier current clock");
            restored.swapHeldItemWithStowed();
            helper.assertTrue(restored.getMainHandItem().getItem() instanceof BowItem && ItemStack.matches(restored.getMainHandItem(), bow),
                    "The reloaded guardian can switch back to its exact registered ranged weapon");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitNativeEquipmentNbtAndRepeatedJoinsDoNotGrantAnotherUpgrade(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            for (MobKind kind : KINDS) {
                Mob original = create(helper, kind, 196L);
                ItemStack helmet = new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(1);
                helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Explicit helmet"));
                ItemStack weapon = new ItemStack(Items.IRON_AXE); weapon.setDamageValue(2);
                original.setItemSlot(EquipmentSlot.HEAD, helmet); original.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                CompoundTag tag = new CompoundTag(); original.saveWithoutId(tag);
                tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(original.getType()).toString());
                Mob restored = (Mob)EntityType.loadEntityRecursive(tag, helper.getLevel(), Function.identity());
                helper.assertTrue(restored != null, "Explicit native entity NBT loads the selected fixed-set species");
                List<ItemStack> before = equipment(restored);
                for (boolean loaded : new boolean[]{true, false, false}) {
                    restored.getRandom().setSeed(196L);
                    NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(restored, helper.getLevel(), loaded));
                    unchanged(helper, restored, before);
                    helper.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "Loading or joining an explicitly equipped entity performs no new material roll");
                }
                helper.assertTrue(ItemStack.matches(helmet, restored.getItemBySlot(EquipmentSlot.HEAD))
                        && ItemStack.matches(weapon, restored.getMainHandItem()), "Explicit names, item identities and wear remain authoritative");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void summonedLongdeadAndNetherSwordsGrowWithoutChangingSpecialSkeletonStateOrOtherSpecies(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            set(helper, 1152L, 6_000L);
            boolean summonedAdvanced = false;
            for (long seed = 0; seed < 24; seed++) {
                Mob troop = create(helper, MobKind.LONGDEAD, seed);
                finalizeNative(troop, MobSpawnType.MOB_SUMMONED, 2);
                floor(helper, troop, MobKind.LONGDEAD);
                summonedAdvanced |= id(troop.getMainHandItem()).startsWith("mite:mithril_")
                        || id(troop.getMainHandItem()).startsWith("mite:adamantium_") || id(troop.getMainHandItem()).startsWith("mite:vibranium_");
                helper.assertValueEqual(troop.getSpawnType(), MobSpawnType.MOB_SUMMONED, "Actual reinforcement reason remains unchanged");
            }
            helper.assertTrue(summonedAdvanced, "The real ancient troop finalize path participates in material progression");
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Native Nether level is available for its preserved special skeleton branch");
            for (MobKind kind : List.of(MobKind.BONE_LORD, MobKind.LONGDEAD_GUARDIAN, MobKind.ANCIENT_BONE_LORD, MobKind.LONGDEAD)) {
                for (long day : new long[]{1L, 63L}) {
                    set(helper, day, 6_000L);
                    Mob mob = create(helper, kind, nether, 196L);
                    finalizeNative(mob, MobSpawnType.SPAWN_EGG, 1);
                    helper.assertTrue(mob.getMainHandItem().is(Items.IRON_SWORD)
                            && EquipmentDurability.quality(mob.getMainHandItem()) == Quality.POOR,
                            "Pre-progression Nether type1 retains its exact explicit poor iron sword: " + kind);
                    helper.assertTrue(ARMOR.stream().allMatch(slot -> mob.getItemBySlot(slot).isEmpty()),
                            "The source type1 branch does not gain an invented fixed armor set");
                    helper.assertValueEqual(mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 4D,
                            "The original Nether type1 base attack remains four before equipment attributes");
                }
                set(helper, 1152L, 6_000L);
                boolean advancedSword = false, enchantedSword = false;
                for (long seed = 0; seed < 24L; seed++) {
                    Mob mob = create(helper, kind, nether, seed);
                    finalizeNative(mob, MobSpawnType.SPAWN_EGG, 1);
                    String sword = id(mob.getMainHandItem());
                    helper.assertTrue(List.of("minecraft:iron_sword", "mite:ancient_metal_sword", "mite:mithril_sword",
                            "mite:adamantium_sword", "mite:vibranium_sword").contains(sword), "Nether type1 swords cannot fall below their iron material floor");
                    advancedSword |= sword.equals("mite:mithril_sword") || sword.equals("mite:adamantium_sword") || sword.equals("mite:vibranium_sword");
                    helper.assertValueEqual(EquipmentDurability.quality(mob.getMainHandItem()), Quality.POOR,
                            "A higher material does not remove the explicit source poor-item quality");
                    enchantedSword |= mob.getMainHandItem().isEnchanted();
                    helper.assertTrue(ARMOR.stream().allMatch(slot -> mob.getItemBySlot(slot).isEmpty()),
                            "Late material progression upgrades the existing sword without adding armor");
                    helper.assertValueEqual(mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 4D,
                            "Late growth does not replace the source type1 base-attack override");
                    helper.assertValueEqual(((R196Skeleton)mob).skeletonType(), 1,
                            "Material progression preserves the actual Nether skeleton subtype");
                }
                helper.assertTrue(advancedSword, "Every selected Nether skeleton species can receive a high-material sword: " + kind);
                helper.assertTrue(enchantedSword, "Late calendar growth can enchant generated Nether type1 swords while retaining source poor quality and subtype: " + kind);
            }
            Mob ordinary = EntityType.ZOMBIE.create(helper.getLevel());
            ordinary.getRandom().setSeed(196L);
            helper.assertValueEqual(SpecialMobEquipment.itemId(ordinary, "mite:ancient_metal_sword"), "mite:ancient_metal_sword",
                    "Ordinary zombie equipment remains under the existing separate progression policy");
            helper.assertValueEqual(ordinary.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                    "An unrelated species does not consume the special-set material RNG");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void specialSourceFamiliesResolveVibraniumTailAtBothLateCalendarAndNinthBlood(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long[] date : new long[][]{{1280L, 6000L}, {352L, 351L * 24000L + 6000L}}) {
                set(helper, date[0], date[1]);
                Map<String,String> expected = Map.of(
                        "mite:ancient_metal_helmet", "mite:vibranium_helmet",
                        "mite:ancient_metal_sword", "mite:vibranium_sword",
                        "mite:ancient_metal_dagger", "mite:vibranium_dagger",
                        "mite:ancient_metal_battle_axe", "mite:vibranium_battle_axe",
                        "mite:ancient_metal_war_hammer", "mite:vibranium_war_hammer",
                        "mite:ancient_metal_bow", "mite:vibranium_bow",
                        "mite:ancient_metal_chainmail_helmet", "mite:adamantium_chain_helmet");
                for (var entry : expected.entrySet()) {
                    Mob mob = create(helper, MobKind.LONGDEAD_GUARDIAN, firstDraw(.999D, 1D));
                    helper.assertValueEqual(SpecialMobEquipment.itemId(mob,entry.getKey()),entry.getValue(),
                            "A deterministic Vibranium tail preserves each registered source family, with an Adamantium ceiling for chainmail");
                }
            }
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
