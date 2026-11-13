package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual table payment and independent source-budget/material regressions. @author NingMeng123 */
@GameTestHolder("mite_enchanting")
@PrefixGameTestTemplate(false)
public final class MaterialEnchantingGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);

    private MaterialEnchantingGameTests() {}

    @GameTest(template = "empty")
    public static void physicalFortyEightShelvesOnlyExtendVibraniumPaymentBeyondSourceTwentyFour(GameTestHelper helper) {
        try (var settings = new Settings()) {
            String[] materials = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
            int[] ordinary = {5300, 5300, 7500, 5300, 6500, 10000, 6500, 15800};
            int[] emerald = {4000, 4000, 5000, 4000, 4500, 5000, 4500, 9800};
            var player = owner(helper, 20000);
            for (int material = 0; material < materials.length; material++) {
                ItemStack input = new ItemStack(switch (materials[material]) {
                    case "gold" -> Items.GOLDEN_SWORD;
                    case "iron" -> Items.IRON_SWORD;
                    default -> MiteTools.get(materials[material] + "_sword");
                });
                helper.assertFalse(input.isEmpty(), "Every price vector uses a real registered item: " + materials[material]);
                var definition = MiteTools.definition(input);
                helper.assertTrue(definition != null && definition.material().equals(materials[material]),
                        "Every real item has the intended source material, including retained native gold and iron");
                for (boolean cheaper : new boolean[]{false, true}) {
                    int expected = (cheaper ? emerald : ordinary)[material];
                    int energy = R196EnchantmentTable.energy(input, 48, cheaper, 2, .5F);
                    helper.assertValueEqual(energy * 100, expected, "The source material compression and shelf limit set the full price: " + materials[material]);
                    if (!materials[material].equals("vibranium")) {
                        helper.assertValueEqual(R196EnchantmentTable.energy(input, 24, cheaper, 2, .5F), energy,
                                "Extra physical bookshelf layers cannot inflate an ordinary material's paid selection budget");
                        helper.assertTrue(energy <= 100, "Every non-vibranium material stays within 10000 XP");
                    }
                    EnchantmentMenu menu = menu(helper, player, cheaper ? MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get() : Blocks.ENCHANTING_TABLE);
                    menu.getSlot(0).set(input.copy());
                    helper.assertValueEqual(menu.costs[2], 30, "The native displayed level requirement remains thirty");
                    helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), expected,
                            "The real menu quotes the same material-specific budget");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mithrilActualMenuRejects9999ThenChargesExactly10000AndKeepsSourceCaps(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
            var protection = registry.getHolderOrThrow(Enchantments.PROTECTION);
            var mithril = new ItemStack(MiteTools.get("mithril_sword"));
            var vibranium = new ItemStack(MiteTools.get("vibranium_sword"));
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(mithril, sharpness), 5, "The immutable non-vibranium source Sharpness ceiling is V");
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(mithril, protection), 4, "The immutable non-vibranium source Protection ceiling is IV");
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(vibranium, sharpness), 7, "Vibranium keeps the old ITE Sharpness VII exception");
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(vibranium, protection), 5, "Vibranium keeps the old ITE Protection V exception");
            for (String id : List.of("mithril_sword", "mithril_chestplate")) {
                var player = owner(helper, 9999);
                var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
                var input = new ItemStack(MiteTools.get(id));
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
                helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), 10000,
                        "Four physical layers charge mithril the source maximum, not 14800 XP");
                helper.assertFalse(menu.clickMenuButton(player, 2), "9999 points cannot fund a 10000-point offer");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Rejected payment preserves the exact input");
                helper.assertValueEqual(player.totalExperience, 9999, "Rejected payment deducts no points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected payment deducts no lapis");
                PlayerExperience.setTotal(player, 10000);
                int clue = menu.enchantClue[2], clueRank = menu.levelClue[2];
                helper.assertTrue(menu.clickMenuButton(player, 2), "Exactly 10000 XP fund the highest mithril offer");
                var result = EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
                helper.assertTrue(result.size() >= 1 && result.size() <= 3, "The real transaction produces one to three source-budget selections");
                helper.assertValueEqual(result.getLevel(registry.asHolderIdMap().byId(clue)), clueRank,
                        "The paid result contains its exact synchronized preview");
                helper.assertTrue(result.getLevel(sharpness) <= 5, "Mithril never receives vibranium Sharpness VII");
                helper.assertTrue(result.getLevel(protection) <= 4, "Mithril never receives vibranium Protection V");
                helper.assertValueEqual(player.totalExperience, 0, "Only the quoted 10000 points are charged");
                helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "The third offer consumes exactly three lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remainingBudgetRebuildsRanksInsteadOfReusingFullPowerCandidates(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            List<Holder<Enchantment>> allowed = List.of(registry.getHolderOrThrow(Enchantments.SHARPNESS), registry.getHolderOrThrow(Enchantments.UNBREAKING));
            var input = new ItemStack(MiteTools.get("mithril_sword"));
            // Independent R196 arithmetic: rank V costs41, then overhead5; 55 leaves9, which only funds rank I.
            var selected = VanillaTableEnchanting.select(input, 30, new MiddleBudgetRandom(), allowed, 55);
            helper.assertValueEqual(selected.size(), 2, "Remaining nine energy funds a second compatible low-rank enchantment");
            helper.assertTrue(selected.stream().map(entry -> entry.level).sorted().toList().equals(List.of(1, 5)),
                    "The second draw rebuilds its candidate rank from nine energy; it cannot reuse rank V");
            int total = selected.stream().mapToInt(entry -> R196EnchantmentCatalog.definition(entry.enchantment.unwrapKey().orElseThrow().location().toString())
                    .minCost(entry.level) + 5).sum();
            helper.assertValueEqual(total, 52, "Exact source debits are41+5 then1+5");
            var shortBudget = VanillaTableEnchanting.select(input, 30, new MiddleBudgetRandom(), allowed, 50);
            helper.assertValueEqual(shortBudget.size(), 1, "After41+5 only four energy remains, below the source continuation threshold five");
            helper.assertValueEqual(shortBudget.getFirst().level, 5, "The first draw still uses the full initial budget");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedSourceRewardsSpendTheirBudgetWithoutContinuationRollsAndStopAtThree(GameTestHelper helper) {
        try (var settings = new Settings()) {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:sharpness=1", "minecraft:unbreaking=1",
                    "minecraft:looting=1", "minecraft:fortune=1"));
            MiteConfig.refreshEnchantmentRules();
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            List<Holder<Enchantment>> allowed = List.of(registry.getHolderOrThrow(Enchantments.SHARPNESS), registry.getHolderOrThrow(Enchantments.UNBREAKING),
                    registry.getHolderOrThrow(Enchantments.LOOTING), registry.getHolderOrThrow(Enchantments.FORTUNE));
            var random = new SelectionRandom(.5F);
            var selected = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.BOOK), 1000, allowed.stream());
            helper.assertValueEqual(selected.size(), 3, "The real generated-reward entry spends enough remaining energy on exactly three entries");
            helper.assertValueEqual(selected.stream().map(entry -> entry.enchantment).distinct().count(), 3L,
                    "The three-entry ceiling retains unique compatible source enchantments");
            helper.assertValueEqual(random.continuationRolls, 0, "Continuation depends only on remaining budget and never consumes an extra probability roll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerAndGeneratedSelectionShareTheExtensionAdjustedEnergyRoll(GameTestHelper helper) throws Exception {
        var policy = MiteExtensions.class.getDeclaredField("policy");
        policy.setAccessible(true);
        Object original = policy.get(null);
        try (var settings = new Settings()) {
            policy.set(null, new MiteExtensions.Policy() {});
            float[] rolls = {0F, .5F, Math.nextDown(1F)};
            int[] sourceEnergy = {75, 100, 125};
            for (int index = 0; index < rolls.length; index++)
                helper.assertValueEqual(R196EnchantmentTable.randomizedEnergy(100, new SelectionRandom(rolls[index])), sourceEnergy[index],
                        "R196 float variation retains its independent boundary vector");
            int[] adjustments = {0};
            policy.set(null, new MiteExtensions.Policy() {
                @Override public float enchantmentPowerMultiplier(float roll, float originalMultiplier) {
                    adjustments[0]++;
                    return 1F + (roll - .75F) * .5F;
                }
            });
            int[] extensionEnergy = {62, 87, 112};
            for (int index = 0; index < rolls.length; index++)
                helper.assertValueEqual(R196EnchantmentTable.randomizedEnergy(100, new SelectionRandom(rolls[index])), extensionEnergy[index],
                        "An installed extension can restore ITE's lower centered energy interval");
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            List<Holder<Enchantment>> allowed = List.of(registry.getHolderOrThrow(Enchantments.SHARPNESS));
            adjustments[0] = 0;
            var input = new ItemStack(Items.IRON_SWORD);
            var playerResult = VanillaTableEnchanting.select(input, 30, new SelectionRandom(.5F), allowed, 100);
            var generatedResult = EnchantmentHelper.selectEnchantment(new SelectionRandom(.5F), input, 1000, allowed.stream());
            helper.assertTrue(!playerResult.isEmpty() && !generatedResult.isEmpty(), "Both real selection routes produce an eligible source enchantment");
            helper.assertValueEqual(adjustments[0], 2, "Player and generated selection each apply the same extension adjustment exactly once");
        } finally {
            policy.set(null, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tableMaterialCompatibilityAndApplicabilityReachBothSelectionRoutes(GameTestHelper helper) throws Exception {
        var policy = MiteExtensions.class.getDeclaredField("policy");
        policy.setAccessible(true);
        Object original = policy.get(null);
        try (var settings = new Settings()) {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:sharpness=1", "minecraft:smite=1"));
            MiteConfig.refreshEnchantmentRules();
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
            var smite = registry.getHolderOrThrow(Enchantments.SMITE);
            List<Holder<Enchantment>> allowed = List.of(sharpness, smite);
            policy.set(null, new MiteExtensions.Policy() {
                @Override public boolean tableEnchantmentsCompatible(ItemStack stack, Holder<Enchantment> left,
                        Holder<Enchantment> right, boolean originalCompatibility) {
                    return R196EnchantmentTable.vibranium(stack) && !left.equals(right)
                            && allowed.contains(left) && allowed.contains(right) || originalCompatibility;
                }
                @Override public boolean enchantmentApplicable(String id, ItemStack stack, boolean originalApplicability) {
                    return R196EnchantmentTable.vibranium(stack) && (id.equals("smite") || id.equals("minecraft:smite"))
                            || originalApplicability;
                }
            });
            for (boolean vibranium : new boolean[]{false, true}) {
                var input = new ItemStack(vibranium ? MiteTools.get("vibranium_sword") : Items.IRON_SWORD);
                int expected = vibranium ? 2 : 1;
                helper.assertValueEqual(VanillaTableEnchanting.select(input, 30, new SelectionRandom(.5F), allowed, 100).size(), expected,
                        "Player tables apply material-specific damage compatibility while retaining lower-material conflicts");
                helper.assertValueEqual(EnchantmentHelper.selectEnchantment(new SelectionRandom(.5F), input, 1000, allowed.stream()).size(), expected,
                        "Generated rewards apply the same material compatibility and candidate applicability policy");
            }
            policy.set(null, new MiteExtensions.Policy() {
                @Override public boolean enchantmentApplicable(String id, ItemStack stack, boolean originalApplicability) {
                    return !id.equals("sharpness") && !id.equals("minecraft:sharpness") && originalApplicability;
                }
            });
            var input = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(VanillaTableEnchanting.select(input, 30, new SelectionRandom(.5F), List.of(sharpness), 100).isEmpty(),
                    "An extension's applicability restriction also overrides a matching native primary-item tag");
        } finally {
            policy.set(null, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullMithrilOffersUsuallyHaveMultipleCompatibleEntriesAndBooksRetainOnlyOne(GameTestHelper helper) {
        try (var settings = new Settings()) {
            int multiple = 0, triples = 0;
            var input = new ItemStack(MiteTools.get("mithril_sword"));
            var access = helper.getLevel().registryAccess();
            for (int seed = 0; seed < 512; seed++) {
                var selected = R196EnchantmentTable.select(access, input, 100, RandomSource.create(seed));
                helper.assertTrue(selected.size() >= 1 && selected.size() <= 3, "Source selection never returns an empty or overfull equipment result");
                if (selected.size() > 1) multiple++;
                if (selected.size() == 3) triples++;
                for (int first = 0; first < selected.size(); first++) for (int second = first + 1; second < selected.size(); second++) {
                    helper.assertTrue(VanillaTableEnchanting.compatible(selected.get(first).enchantment, selected.get(second).enchantment),
                            "Every selected pair remains compatible after repeated budget rebuilding");
                }
                for (var entry : selected) {
                    if (entry.enchantment.is(Enchantments.SHARPNESS)) helper.assertTrue(entry.level <= 5, "All512 mithril draws respect source Sharpness V");
                }
                var book = R196EnchantmentTable.output(access, new ItemStack(Items.BOOK), 53, RandomSource.create(seed));
                helper.assertTrue(book.is(Items.ENCHANTED_BOOK), "The source book conversion remains valid");
                helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(book).size(), 1,
                        "R196 keeps one randomly chosen selection on the final book");
            }
            helper.assertTrue(multiple > 384, "At least three quarters of the fixed full-power sequence produces multiple attributes");
            helper.assertTrue(triples > 0, "The same actual source sequence can spend its budget on three compatible attributes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realHighestMenuTransactionsUsuallyGiveMultipleAttributesForMithrilAndVibranium(GameTestHelper helper) throws java.io.IOException {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("sampling", "128 actual highest-option menu clicks per material; creative payment bypass; real source prices, previews and output components");
        report.addProperty("configured_chances_and_caps", "isolated defaults, restored after sampling");
        var materialReports = new JsonArray();
        report.add("materials", materialReports);
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (String material : List.of("mithril", "vibranium")) {
                var player = owner(helper, 20000);
                player.setGameMode(GameType.CREATIVE);
                var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
                var input = new ItemStack(MiteTools.get(material + "_chestplate"));
                var materialReport = new JsonObject();
                materialReport.addProperty("material", material);
                materialReport.addProperty("quoted_experience_points", material.equals("vibranium") ? 15800 : 10000);
                var samples = new JsonArray();
                materialReport.add("samples", samples);
                materialReports.add(materialReport);
                int multiple = 0, triples = 0;
                for (int seed = 0; seed < 128; seed++) {
                    menu.setData(3, seed);
                    menu.getSlot(0).set(input.copy());
                    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
                    helper.assertValueEqual(menu.costs[2], 30, "The sampled transaction buys the actual highest menu option");
                    helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), material.equals("vibranium") ? 15800 : 10000,
                            "Each real highest option quotes its material-specific source price");
                    int clue = menu.enchantClue[2], rank = menu.levelClue[2];
                    helper.assertTrue(menu.clickMenuButton(player, 2), "A real highest-option click succeeds for seed " + seed + " and " + material);
                    ItemStack output = menu.getSlot(0).getItem();
                    helper.assertTrue(output.is(input.getItem()) && output.isEnchanted(), "The menu publishes real enchanted armor");
                    var entries = EnchantmentHelper.getEnchantmentsForCrafting(output);
                    helper.assertTrue(entries.size() >= 1 && entries.size() <= 3, "Every actual armor result has one to three attributes");
                    helper.assertValueEqual(entries.getLevel(registry.asHolderIdMap().byId(clue)), rank,
                            "The actual output contains the exact highest-option preview");
                    if (entries.size() > 1) multiple++;
                    if (entries.size() == 3) triples++;
                    var sample = new JsonObject();
                    sample.addProperty("seed", seed);
                    sample.addProperty("count", entries.size());
                    var ranks = new JsonObject();
                    for (var entry : entries.entrySet())
                        ranks.addProperty(entry.getKey().unwrapKey().orElseThrow().location().toString(), entry.getIntValue());
                    sample.add("enchantments", ranks);
                    samples.add(sample);
                    helper.assertTrue(entries.getLevel(registry.getHolderOrThrow(Enchantments.PROTECTION)) <= (material.equals("vibranium") ? 5 : 4),
                            "Real menu results respect the material's Protection ceiling");
                }
                helper.assertTrue(multiple > 96, "More than75% of128 real highest-option " + material + " transactions produce multiple attributes; actual=" + multiple);
                helper.assertTrue(triples > 0, "Real highest-option " + material + " transactions can produce three attributes");
                materialReport.addProperty("sample_count", 128);
                materialReport.addProperty("single_count", 128 - multiple);
                materialReport.addProperty("double_count", multiple - triples);
                materialReport.addProperty("triple_count", triples);
                materialReport.addProperty("multiple_count", multiple);
                materialReport.addProperty("multiple_fraction", multiple / 128D);
            }
        }
        report.addProperty("status", "passed_real_menu_transactions_and_exact_output_components");
        Path output = highestMenuReport();
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        helper.succeed();
    }

    private static Path highestMenuReport() {
        String configured = System.getProperty("mite.enchantingHighestMenuSamplesOutput");
        if (configured != null) return Path.of(configured).toAbsolutePath().normalize();
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path workspace = start; workspace != null; workspace = workspace.getParent())
            if (Files.isRegularFile(workspace.resolve("build.gradle")) && Files.isDirectory(workspace.resolve("src/main")))
                return workspace.resolve("build/verification/enchanting-highest-menu-samples.json");
        return start.resolve("build/verification/enchanting-highest-menu-samples.json");
    }

    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block) {
        BlockPos table = helper.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy < 4; dy++)
            helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table, block.defaultBlockState(), 2);
        for (int dy = 0; dy < 4; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && !(Math.abs(dx) == 2 && Math.abs(dz) == 2))
                helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }

    private static ServerPlayer owner(GameTestHelper helper, int points) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MaterialEnchant"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, points);
        return player;
    }

    /** No initial variation and no optional first-two rank downgrades; weighted order stays deterministic. */
    private static final class MiddleBudgetRandom extends LegacyRandomSource {
        private MiddleBudgetRandom() { super(196L); }
        @Override public float nextFloat() { return .5F; }
        @Override public int nextInt(int bound) { return bound == 2 ? 1 : 0; }
    }

    private static final class SelectionRandom extends LegacyRandomSource {
        private final float roll;
        private int continuationRolls;
        private SelectionRandom(float roll) { super(196L); this.roll = roll; }
        @Override public float nextFloat() { return roll; }
        @Override public int nextInt(int bound) {
            if (bound == 50) {
                continuationRolls++;
                return 49;
            }
            return bound == 2 ? 1 : 0;
        }
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
        @Override public void close() {
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
