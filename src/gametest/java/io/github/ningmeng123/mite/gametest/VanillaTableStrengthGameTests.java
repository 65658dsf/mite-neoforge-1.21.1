package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source acquisition budgets and material ceilings remain separate from native offers and saved ranks. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class VanillaTableStrengthGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);

    private VanillaTableStrengthGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void tableUsesConfiguredCeilingsWithoutRewritingSavedHighRanks(GameTestHelper h) {
        try (var settings = new Settings()) {
            var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
            var unbreaking = registry.getHolderOrThrow(Enchantments.UNBREAKING);
            var protection = registry.getHolderOrThrow(Enchantments.PROTECTION);
            h.assertValueEqual(sharpness.value().getMaxLevel(), 7, "Registered source Sharpness still supports existing VII and anvil operations");
            h.assertValueEqual(unbreaking.value().getMaxLevel(), 5, "Registered source Unbreaking remains V");
            h.assertValueEqual(protection.value().getMaxLevel(), 5, "Registered source Protection remains V");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(sharpness), 7, "The global Sharpness cap remains available to existing items and anvils");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(unbreaking), 5, "New table acquisition uses the configured Unbreaking V ceiling");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(protection), 5, "The global Protection cap remains V");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(new ItemStack(Items.IRON_SWORD), sharpness), 5,
                    "New iron table results retain the R196 Sharpness V ceiling");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(new ItemStack(Items.IRON_CHESTPLATE), protection), 4,
                    "New iron table results retain the R196 Protection IV ceiling");
            h.assertValueEqual(VanillaTableEnchanting.maximumLevel(new ItemStack(MiteTools.get("vibranium_sword")), sharpness), 7,
                    "The ITE Sharpness VII exception belongs to actual vibranium");
            ItemStack saved = new ItemStack(Items.IRON_SWORD);
            saved.enchant(sharpness, 7);
            h.assertValueEqual(saved.getEnchantmentLevel(sharpness), 7, "Acquisition profiles never downgrade saved enchantments");
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void actualBookshelvesRatherThanCompressedFeeControlVanillaOfferStrength(GameTestHelper h) {
        h.assertValueEqual(VanillaTableEnchanting.mappedBookshelves(0), 0, "No shelves means vanilla zero shelves");
        h.assertValueEqual(VanillaTableEnchanting.mappedBookshelves(14), 14, "Every native shelf contributes one point up to fifteen");
        h.assertValueEqual(VanillaTableEnchanting.mappedBookshelves(15), 15, "Fifteen shelves already provide full vanilla power");
        h.assertValueEqual(VanillaTableEnchanting.mappedBookshelves(100), 15, "Extra shelves cannot exceed full vanilla power");
        for (ItemStack stack : List.of(new ItemStack(Items.BOOK), new ItemStack(Items.IRON_SWORD),
                new ItemStack(MiteTools.get("vibranium_sword")))) {
            for (int seed = 0; seed < 32; seed++) {
                h.assertValueEqual(VanillaTableEnchanting.offerStrength(stack, 15, 2, seed), 30,
                        "The fully surrounded third offer is level thirty for every material");
                h.assertTrue(VanillaTableEnchanting.offerStrength(stack, 15, 0, seed) < 30,
                        "The first slot does not inherit the maximum offer's strength");
                h.assertTrue(VanillaTableEnchanting.offerStrength(stack, 0, 2, seed) <= 8,
                        "An empty table cannot reach full strength through source material compression");
            }
        }
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(new ItemStack(Items.BOOK)), 1, "Books use vanilla enchantability one");
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(new ItemStack(Items.IRON_SWORD)), 14, "Vanilla iron sword uses its native enchantability");
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(new ItemStack(Items.GOLDEN_PICKAXE)), 22, "Retained gold tools use original tier enchantability for table results");
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(new ItemStack(Items.IRON_CHESTPLATE)), 9, "Retained iron armor uses original material enchantability for table results");
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(new ItemStack(Items.FISHING_ROD)), 1, "The retained fishing rod keeps native result power despite source casting adaptation");
        ItemStack vibranium = new ItemStack(MiteTools.get("vibranium_sword"));
        h.assertValueEqual(R196EnchantmentTable.enchantability(vibranium), 120, "Source material enchantability is retained for fees");
        h.assertValueEqual(VanillaTableEnchanting.selectionEnchantability(vibranium), 120, "The native algorithm uses the custom material enchantability without an artificial cap");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void configurationCapsCannotBypassMaterialOrPaidEnergyCeilings(GameTestHelper h) {
        try (var settings = new Settings()) {
            var sharpness = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            ItemStack sword = new ItemStack(MiteTools.get("vibranium_sword"));
            boolean foundRaisedRank = false, foundBelowMaximum = false;
            for (int cap : new int[]{2, 5, 7, 9}) {
                settings.set(Map.of("minecraft:sharpness", cap), Set.of());
                h.assertValueEqual(VanillaTableEnchanting.maximumLevel(sharpness), cap, "The global configured cap remains unchanged");
                int materialCap = Math.min(cap, 7);
                h.assertValueEqual(VanillaTableEnchanting.maximumLevel(sword, sharpness), materialCap,
                        "New vibranium results combine the configured cap with the ITE VII ceiling");
                var random = RandomSource.create(196L);
                for (int seed = 0; seed < 256; seed++) {
                    var low = VanillaTableEnchanting.select(sword, 20, random, List.of(sharpness));
                    var high = VanillaTableEnchanting.select(sword, 100, random, List.of(sharpness));
                    h.assertValueEqual(low.size(), 1, "An eligible single candidate produces one result at low energy");
                    h.assertValueEqual(high.size(), 1, "An eligible single candidate produces one result at high energy");
                    h.assertTrue(low.getFirst().level <= Math.min(materialCap, 3),
                            "Twenty energy cannot buy Sharpness IV even when a high cap is configured");
                    h.assertValueEqual(high.getFirst().level, materialCap,
                            "A sufficiently funded isolated candidate reaches its material/configured ceiling");
                    foundRaisedRank |= high.getFirst().level > 5;
                    foundBelowMaximum |= low.getFirst().level < materialCap;
                }
            }
            h.assertTrue(foundRaisedRank, "Vibranium can still acquire the reserved ranks above V");
            h.assertTrue(foundBelowMaximum, "A configured maximum does not replace the paid energy budget");
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void loweredConfigurationCapsStillRestrictEverySelectedAndStoredRank(GameTestHelper h) {
        try (var settings = new Settings()) {
            Map<String, Integer> caps = Map.of("minecraft:sharpness", 2, "minecraft:unbreaking", 1, "minecraft:protection", 1);
            settings.set(caps, Set.of());
            for (int seed = 0; seed < 512; seed++) {
                ItemStack output = R196EnchantmentTable.output(h.getLevel().registryAccess(), new ItemStack(Items.BOOK), 158, RandomSource.create(seed));
                for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(output).entrySet()) {
                    String id = entry.getKey().unwrapKey().orElseThrow().location().toString();
                    h.assertTrue(entry.getIntValue() <= caps.getOrDefault(id, 255), "Stored table rank respects the lowered configuration: " + id);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void singleSourceCandidateHasRealEnergyThresholdsAndVariedFullOfferRanks(GameTestHelper h) {
        try (var settings = new Settings()) {
            var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            String id = "minecraft:fire_aspect";
            settings.set(Map.of(), registry.holders().map(holder -> holder.key().location().toString())
                    .filter(key -> !key.equals(id)).collect(Collectors.toSet()));
            Holder<Enchantment> fire = registry.getHolderOrThrow(Enchantments.FIRE_ASPECT);
            boolean firstRank = false, secondRank = false;
            var random = RandomSource.create(196L);
            for (int seed = 0; seed < 256; seed++) {
                var input = new ItemStack(Items.IRON_SWORD);
                h.assertTrue(R196EnchantmentTable.select(h.getLevel().registryAccess(), input, 8, random).isEmpty(),
                        "Even the strongest eight-energy roll cannot pay Fire Aspect I's source minimum");
                var modest = R196EnchantmentTable.select(h.getLevel().registryAccess(), input, 16, random);
                var funded = R196EnchantmentTable.select(h.getLevel().registryAccess(), input, 48, random);
                h.assertValueEqual(modest.getFirst().level, 1, "Sixteen energy consistently pays I but cannot pay II");
                h.assertValueEqual(funded.getFirst().level, 2, "Forty-eight energy consistently funds the source II ceiling");
                var selected = R196EnchantmentTable.select(h.getLevel().registryAccess(), input, 30, random);
                h.assertValueEqual(selected.size(), 1, "The isolated source candidate is acquired once");
                h.assertValueEqual(selected.getFirst().enchantment, fire, "Only Fire Aspect is eligible");
                h.assertTrue(selected.getFirst().level >= 1 && selected.getFirst().level <= 2,
                        "Every thirty-energy result respects the source material ceiling");
                firstRank |= selected.getFirst().level == 1;
                secondRank |= selected.getFirst().level == 2;
            }
            h.assertTrue(firstRank && secondRank, "Even the full offer can roll different eligible ranks");
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void realFourLayerTransactionsRetainFeesAndRefreshSeedOnlyAfterSuccess(GameTestHelper h) {
        try (var settings = new Settings()) {
            for (Block block : List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get())) {
                ServerPlayer player = owner(h, 0);
                EnchantmentMenu menu = table(h, player, block, 4);
                menu.getSlot(0).set(new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate")));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                int expected = block == Blocks.ENCHANTING_TABLE ? 15800 : 9800;
                h.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), expected, "The original table-specific source point price is retained");
                int seed = player.getEnchantmentSeed();
                h.assertFalse(menu.clickMenuButton(player, 2), "An unfunded offer is rejected");
                h.assertValueEqual(player.getEnchantmentSeed(), seed, "Rejected offers do not consume the enchantment seed");
                PlayerExperience.setTotal(player, expected + 17);
                h.assertTrue(menu.clickMenuButton(player, 2), "The funded full-table transaction succeeds");
                h.assertValueEqual(player.totalExperience, 17, "Refreshing the seed adds no level charge or rounding loss");
                h.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The third offer still consumes exactly three lapis");
                h.assertTrue(player.getEnchantmentSeed() != seed, "A completed enchantment refreshes its native seed");
                var result = EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
                h.assertTrue(!result.isEmpty(), "Actual native container publishes the selected enchantments");
                for (var entry : result.entrySet())
                    h.assertTrue(entry.getIntValue() <= VanillaTableEnchanting.maximumLevel(entry.getKey()), "Actual stored results use acquisition ceilings");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void realEmptyBookshelfTableCannotEnchantOrdinaryBooks(GameTestHelper h) {
        try (var settings = new Settings()) {
            ServerPlayer player = owner(h, 20000);
            EnchantmentMenu menu = table(h, player, Blocks.ENCHANTING_TABLE, 0);
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            h.assertFalse(menu.clickMenuButton(player, 2), "A table without shelves cannot create an enchanted book");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.BOOK), "The rejected table operation preserves the ordinary book");
            h.assertValueEqual(menu.costs[2], 0, "The rejected book has no native offer");
            h.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), 0, "The rejected book has no point price");
            h.assertValueEqual(player.totalExperience, 20000, "The rejected book consumes no points");
            h.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The rejected book consumes no lapis");
        }
        h.succeed();
    }

    private static ServerPlayer owner(GameTestHelper h, int points) {
        ServerPlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "VanillaTable"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, points);
        return player;
    }

    private static EnchantmentMenu table(GameTestHelper h, ServerPlayer player, Block block, int layers) {
        BlockPos pos = h.absolutePos(SITE);
        for (int dy = 0; dy < 5; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        h.getLevel().setBlock(pos, block.defaultBlockState(), 2);
        for (int dy = 0; dy < layers; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && !(Math.abs(dx) == 2 && Math.abs(dz) == 2))
                h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
    }

    private static final class Settings implements AutoCloseable {
        private final MiteConfig.Snapshot base = MiteConfig.localSnapshot();
        private final MiteConfig.Snapshot original = MiteConfig.effectiveSnapshot() == base ? null : MiteConfig.effectiveSnapshot();
        private Settings() { set(Map.of(), Set.of()); }
        private void set(Map<String, Integer> caps, Set<String> disabled) {
            MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(base.ores(), base.growth(), base.healthGrowth(), base.damageGrowth(),
                    base.reachMultiplier(), base.reachReduction(), base.heightPenalty(), base.underworldTrials(), base.overworldTrials(),
                    caps, disabled, base.monsterSettings(), base.oreCandidates(), base.oreChances(), base.overworldOreCandidates(),
                    base.overworldOreChances(), base.overworldOres(), Map.of()));
        }
        @Override public void close() {
            MiteConfig.clearServerSnapshot();
            if (original != null) MiteConfig.receiveServerSnapshot(original);
        }
    }
}
