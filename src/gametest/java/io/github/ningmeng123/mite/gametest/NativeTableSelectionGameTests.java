package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.item.WoodenClubBehavior;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Compare table acquisition with official 1.21.1 item rules and a small independent seeded oracle. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class NativeTableSelectionGameTests {
    private NativeTableSelectionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void booksIncludeModernTableEnchantmentsButExcludeTreasure(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (var key : List.of(Enchantments.INFINITY, Enchantments.LURE, Enchantments.LUCK_OF_THE_SEA,
                    Enchantments.DEPTH_STRIDER, Enchantments.SWEEPING_EDGE, Enchantments.QUICK_CHARGE, Enchantments.BREACH)) {
                settings.only(helper, key);
                for (int seed = 0; seed < 16; seed++) {
                    var selected = VanillaTableEnchanting.select(helper.getLevel().registryAccess(),
                            new ItemStack(Items.BOOK), 30, RandomSource.create(seed));
                    helper.assertTrue(selected.size() == 1 && selected.getFirst().enchantment.is(key),
                            "The official table tag admits modern book enchantment " + key.location());
                }
            }
            for (var key : List.of(Enchantments.MENDING, Enchantments.FROST_WALKER, Enchantments.SOUL_SPEED,
                    Enchantments.SWIFT_SNEAK, Enchantments.WIND_BURST, Enchantments.BINDING_CURSE, Enchantments.VANISHING_CURSE)) {
                settings.only(helper, key);
                for (int seed = 0; seed < 16; seed++) {
                    helper.assertTrue(VanillaTableEnchanting.select(helper.getLevel().registryAccess(),
                            new ItemStack(Items.BOOK), 30, RandomSource.create(seed)).isEmpty(),
                            "Treasure enchantment cannot enter the table pool: " + key.location());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void nativeAndAddedMaterialsUseNativePrimaryEquipmentCategories(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (var sword : List.of(new ItemStack(Items.IRON_SWORD), item("mite:copper_sword"))) {
                for (var key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
                        Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.UNBREAKING, Enchantments.SWEEPING_EDGE)) {
                    helper.assertTrue(VanillaTableEnchanting.primaryItem(sword, holder(helper, key)),
                            "Sword materials use the native table category for " + key.location());
                }
            }
            for (var axe : List.of(new ItemStack(Items.IRON_AXE), item("mite:copper_axe"))) {
                helper.assertFalse(VanillaTableEnchanting.primaryItem(axe, holder(helper, Enchantments.SHARPNESS)),
                        "Sharpness supports axes on anvils but is not a primary axe table enchantment");
                helper.assertTrue(VanillaTableEnchanting.primaryItem(axe, holder(helper, Enchantments.EFFICIENCY)),
                        "Axes retain native mining enchantments");
            }
            for (var helmet : List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.LEATHER_HELMET), item("mite:copper_helmet"))) {
                helper.assertFalse(VanillaTableEnchanting.primaryItem(helmet, holder(helper, Enchantments.THORNS)),
                        "Helmet material cannot bypass Thorns' native chestplate-only table rule");
                for (var key : List.of(Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.UNBREAKING)) {
                    helper.assertTrue(VanillaTableEnchanting.primaryItem(helmet, holder(helper, key)),
                            "All helmet materials retain native armor enchantment " + key.location());
                }
            }
            helper.assertTrue(VanillaTableEnchanting.primaryItem(item("mite:copper_chestplate"), holder(helper, Enchantments.THORNS)),
                    "Added chestplate materials can receive Thorns at the table");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void nativeExclusiveSetsReplaceLegacyTableCompatibility(GameTestHelper helper) {
        helper.assertFalse(compatible(helper, Enchantments.SHARPNESS, Enchantments.SMITE), "Damage enchantments remain exclusive");
        helper.assertFalse(compatible(helper, Enchantments.SILK_TOUCH, Enchantments.FORTUNE), "Mining enchantments remain exclusive");
        helper.assertFalse(compatible(helper, Enchantments.INFINITY, Enchantments.MENDING), "Modern exclusive sets apply in both directions");
        helper.assertFalse(compatible(helper, Enchantments.MENDING, Enchantments.INFINITY), "Reverse modern exclusivity is preserved");
        helper.assertFalse(compatible(helper, Enchantments.DEPTH_STRIDER, Enchantments.FROST_WALKER), "Boot movement enchantments remain exclusive");
        helper.assertFalse(compatible(helper, Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION), "Protection enchantments remain exclusive");
        helper.assertTrue(compatible(helper, Enchantments.SILK_TOUCH, Enchantments.LOOTING),
                "The source-only Silk Touch/Looting exclusion must not affect native table books");
        helper.assertFalse(compatible(helper, Enchantments.UNBREAKING, Enchantments.UNBREAKING), "Duplicate selections are excluded");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void retainedWoodenSwordIdKeepsItsWoodenClubIdentity(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var club = new ItemStack(Items.WOODEN_SWORD);
            helper.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(club), 10,
                    "The repurposed wooden sword ID keeps the wooden club's enchantability");
            for (var key : List.of(Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.UNBREAKING)) {
                helper.assertTrue(VanillaTableEnchanting.primaryItem(club, holder(helper, key)),
                        "The wooden club keeps its supported table enchantments");
            }
            for (var key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
                    Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE)) {
                helper.assertFalse(VanillaTableEnchanting.primaryItem(club, holder(helper, key)),
                        "The wooden club must not regain sword enchantments through the retained registry ID");
            }
            for (int seed = 0; seed < 64; seed++) {
                var selected = VanillaTableEnchanting.select(helper.getLevel().registryAccess(), club, 30, RandomSource.create(seed));
                helper.assertFalse(selected.isEmpty(), "The club still receives its permitted table enchantments");
                helper.assertTrue(selected.stream().allMatch(entry -> WoodenClubBehavior.allowsEnchantment(entry.enchantment)
                                && !entry.enchantment.is(Enchantments.MENDING) && !entry.enchantment.is(Enchantments.VANISHING_CURSE)),
                        "Actual table selection respects the club identity and treasure exclusions");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void seededSwordSelectionMatchesOfficialWindowsWeightsAndRandomSequence(GameTestHelper helper) {
        try (var settings = new Settings()) {
            // Match the oracle's vanilla limits explicitly; normal table limits follow configuration.
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(ORACLE.stream().map(entry -> entry.key().location() + "=" + entry.maximum()).toList());
            MiteConfig.refreshEnchantmentRules();
            List<Holder<Enchantment>> allowed = ORACLE.stream().map(entry -> holder(helper, entry.key())).toList();
            for (var input : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLDEN_SWORD))) {
                int enchantability = input.is(Items.IRON_SWORD) ? 14 : 22;
                for (int level : new int[]{1, 5, 15, 30}) for (int seed = 0; seed < 128; seed++) {
                    RandomSource expectedRandom = RandomSource.create(seed), actualRandom = RandomSource.create(seed);
                    var expected = oracle(enchantability, level, expectedRandom);
                    var actual = VanillaTableEnchanting.select(input, level, actualRandom, allowed).stream()
                            .map(NativeTableSelectionGameTests::signature).toList();
                    helper.assertTrue(actual.equals(expected),
                            "Native windows, weights, conflicts and extra draws match for " + input + ", level=" + level + ", seed=" + seed);
                    helper.assertTrue(actualRandom.nextLong() == expectedRandom.nextLong(),
                            "The next RNG state matches native selection for seed " + seed);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void addedMaterialEnchantabilityIsNotClampedToVanillaMaterials(GameTestHelper helper) {
        var mithril = item("mite:mithril_sword");
        helper.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(mithril), 100,
                "Mithril retains its registered material enchantability in the vanilla algorithm");
        for (int seed = 0; seed < 64; seed++) {
            var expected = RandomSource.create(seed);
            int power = 31 + expected.nextInt(26) + expected.nextInt(26);
            float variation = (expected.nextFloat() + expected.nextFloat() - 1F) * .15F;
            helper.assertValueEqual(VanillaTableEnchanting.modifiedPower(mithril, 30, RandomSource.create(seed)),
                    Math.max(1, Math.round(power + power * variation)), "The native bonus uses the complete material value");
        }
        helper.succeed();
    }

    // Values independently transcribed from official 1.21.1 data/minecraft/enchantment/*.json.
    private static final List<NativeEntry> ORACLE = List.of(
            new NativeEntry(Enchantments.SHARPNESS, 5, 10, 1, 21, 11, true),
            new NativeEntry(Enchantments.SMITE, 5, 5, 5, 25, 8, true),
            new NativeEntry(Enchantments.BANE_OF_ARTHROPODS, 5, 5, 5, 25, 8, true),
            new NativeEntry(Enchantments.UNBREAKING, 3, 5, 5, 55, 8, false),
            new NativeEntry(Enchantments.KNOCKBACK, 2, 5, 5, 55, 20, false),
            new NativeEntry(Enchantments.LOOTING, 3, 2, 15, 65, 9, false));

    private record NativeEntry(ResourceKey<Enchantment> key, int maximum, int weight, int minimumCost,
            int maximumCost, int perLevel, boolean damage) {}
    private record NativeChoice(NativeEntry entry, int level) {}

    private static List<String> oracle(int enchantability, int level, RandomSource random) {
        int power = level + 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float variance = (random.nextFloat() + random.nextFloat() - 1F) * .15F;
        power = Math.max(1, Math.round(power + power * variance));
        List<NativeChoice> available = new ArrayList<>();
        for (var entry : ORACLE) for (int rank = entry.maximum(); rank >= 1; rank--) {
            int increment = (rank - 1) * entry.perLevel();
            if (power >= entry.minimumCost() + increment && power <= entry.maximumCost() + increment) {
                available.add(new NativeChoice(entry, rank));
                break;
            }
        }
        List<NativeChoice> selected = new ArrayList<>();
        if (!available.isEmpty()) {
            selected.add(weighted(available, random));
            while (random.nextInt(50) <= power) {
                var previous = selected.getLast().entry();
                available.removeIf(candidate -> candidate.entry().key().equals(previous.key())
                        || candidate.entry().damage() && previous.damage());
                if (available.isEmpty()) break;
                selected.add(weighted(available, random));
                power /= 2;
            }
        }
        return selected.stream().map(choice -> choice.entry().key().location() + "=" + choice.level()).toList();
    }

    private static NativeChoice weighted(List<NativeChoice> available, RandomSource random) {
        int roll = random.nextInt(available.stream().mapToInt(choice -> choice.entry().weight()).sum());
        for (var choice : available) {
            roll -= choice.entry().weight();
            if (roll < 0) return choice;
        }
        throw new AssertionError("Oracle weight sum did not select a candidate");
    }

    private static String signature(EnchantmentInstance instance) {
        return instance.enchantment.unwrapKey().orElseThrow().location() + "=" + instance.level;
    }

    private static boolean compatible(GameTestHelper helper, ResourceKey<Enchantment> first, ResourceKey<Enchantment> second) {
        return VanillaTableEnchanting.compatible(holder(helper, first), holder(helper, second));
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();

        private Settings() {
            MiteConfig.DISABLED_ENCHANTMENTS.set(List.of());
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of());
            MiteConfig.ENCHANTMENT_CHANCES.set(List.of());
            MiteConfig.refreshEnchantmentRules();
        }

        private void only(GameTestHelper helper, ResourceKey<Enchantment> key) {
            MiteConfig.DISABLED_ENCHANTMENTS.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .holders().map(holder -> holder.key().location().toString())
                    .filter(id -> !id.equals(key.location().toString())).toList());
            MiteConfig.refreshEnchantmentRules();
        }

        @Override public void close() {
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
