package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.item.WoodenClubBehavior;
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

/** Native item eligibility and source-budget acquisition through the actual table selector. @author NingMeng123 */
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
    public static void seededSwordSelectionRespectsSourceBudgetsWeightsAndMaterialCeilings(GameTestHelper helper) {
        try (var settings = new Settings()) {
            // Deliberately permissive configuration cannot override ordinary materials' source ceilings.
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(SOURCE_SWORD_CAPS.stream().map(entry -> entry.key().location() + "=99").toList());
            MiteConfig.refreshEnchantmentRules();
            List<Holder<Enchantment>> allowed = SOURCE_SWORD_CAPS.stream().map(entry -> holder(helper, entry.key())).toList();
            int firstRankDraws = 0, firstRankSharpness = 0;
            boolean multiple = false, maximumRank = false;
            for (var input : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLDEN_SWORD))) {
                for (int energy : new int[]{1, 5, 15, 30, 53, 100}) for (int sample = 0; sample < 256; sample++) {
                    long seed = 196L + sample * 0x9E3779B97F4A7C15L;
                    RandomSource actualRandom = RandomSource.create(seed), repeatedRandom = RandomSource.create(seed);
                    var actual = VanillaTableEnchanting.select(input, energy, actualRandom, allowed);
                    var repeated = VanillaTableEnchanting.select(input, energy, repeatedRandom, allowed);
                    helper.assertTrue(actual.stream().map(NativeTableSelectionGameTests::signature).toList()
                                    .equals(repeated.stream().map(NativeTableSelectionGameTests::signature).toList())
                                    && actualRandom.nextLong() == repeatedRandom.nextLong(),
                            "The same material, budget and seed preserve the selected ranks, order and following RNG state");
                    helper.assertTrue(!actual.isEmpty() && actual.size() <= 3,
                            "An eligible source sword pool produces one to three entries: energy=" + energy + ", seed=" + seed);
                    var identities = new java.util.HashSet<ResourceKey<Enchantment>>();
                    int damageKinds = 0, rankCosts = 0;
                    for (var selected : actual) {
                        var key = selected.enchantment.unwrapKey().orElseThrow();
                        var source = SOURCE_SWORD_CAPS.stream().filter(entry -> entry.key().equals(key)).findFirst().orElseThrow();
                        helper.assertTrue(identities.add(key), "A selected identity never occurs twice");
                        helper.assertTrue(selected.level >= 1 && selected.level <= source.maximum(),
                                "Neither iron nor gold inherits configured99 or the vibranium rank exception: " + signature(selected));
                        if (key.equals(Enchantments.SHARPNESS) || key.equals(Enchantments.SMITE)
                                || key.equals(Enchantments.BANE_OF_ARTHROPODS)) damageKinds++;
                        // These six R196 identities all have difficulty10, hence rank costs1,11,21,31,41.
                        rankCosts += 1 + 10 * (selected.level - 1);
                        maximumRank |= selected.level == 5;
                    }
                    helper.assertTrue(damageKinds <= 1, "Different damage enchantments remain mutually exclusive");
                    helper.assertTrue(rankCosts + 5 * (actual.size() - 1) <= energy * 5 / 4,
                            "All ranks and intermediate five-energy debits fit the strongest possible paid budget: energy="
                                    + energy + ", result=" + actual.stream().map(NativeTableSelectionGameTests::signature).toList());
                    multiple |= actual.size() > 1;
                    if (energy == 1) {
                        helper.assertValueEqual(actual.size(), 1, "One energy cannot fund any second draw");
                        helper.assertValueEqual(actual.getFirst().level, 1, "One energy cannot buy a second rank");
                        firstRankDraws++;
                        if (actual.getFirst().enchantment.is(Enchantments.SHARPNESS)) firstRankSharpness++;
                    }
                }
            }
            helper.assertTrue(multiple && maximumRank, "Funded samples exercise multi-entry results and ordinary rankV");
            double sharpnessFraction = firstRankSharpness / (double)firstRankDraws;
            helper.assertTrue(sharpnessFraction > .37D && sharpnessFraction < .52D,
                    "R196 common weight100 versus five uncommon weights25 is observable in actual single-rank draws: "
                            + firstRankSharpness + "/" + firstRankDraws);
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

    // Immutable ceilings from R196 aau/aar, abe and abd, independent of editable configuration.
    private static final List<SourceCap> SOURCE_SWORD_CAPS = List.of(
            new SourceCap(Enchantments.SHARPNESS, 5), new SourceCap(Enchantments.SMITE, 5),
            new SourceCap(Enchantments.BANE_OF_ARTHROPODS, 5), new SourceCap(Enchantments.UNBREAKING, 5),
            new SourceCap(Enchantments.KNOCKBACK, 2), new SourceCap(Enchantments.LOOTING, 3));

    private record SourceCap(ResourceKey<Enchantment> key, int maximum) {}

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
