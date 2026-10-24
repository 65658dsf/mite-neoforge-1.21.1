package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
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

/** Four-layer table geometry, vibranium payment and configured enchantment ceilings. @author NingMeng123 */
@GameTestHolder("mite_enchanting")
@PrefixGameTestTemplate(false)
public final class FourLayerEnchantingGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);

    private FourLayerEnchantingGameTests() {}

    @GameTest(template = "empty")
    public static void fourBookshelfLayersCountFortyEightAndIgnoreCornersAndFifthLayer(GameTestHelper helper) {
        BlockPos table = place(helper, Blocks.ENCHANTING_TABLE, 0);
        for (int layer = 0; layer < 4; layer++) {
            addLayer(helper, table, layer);
            helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), table),
                    (layer + 1) * 12, "Each supported height contributes twelve accessible bookshelves");
        }
        addLayer(helper, table, 4);
        for (int dy = 0; dy <= 4; dy++) for (int dx : new int[] {-2, 2}) for (int dz : new int[] {-2, 2})
            helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), table), 48,
                "A fifth layer and diagonal corners add no table power");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedVerticalCenterExcludesItsLayerAndEveryHigherLayer(GameTestHelper helper) {
        BlockPos table = place(helper, Blocks.ENCHANTING_TABLE, 4);
        helper.getLevel().setBlock(table.above(2), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), table), 24,
                "A blocked second center height leaves only the bottom two bookshelf layers");
        helper.getLevel().setBlock(table.above(2), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table.above(3), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), table), 36,
                "A blocked third center height leaves the bottom three bookshelf layers");
        helper.getLevel().setBlock(table.above(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), table), 12,
                "A blocked first center height excludes all three upper layers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourLayersOfferVibraniumOneHundredFiftyEightEnergyAndEmeraldNinetyEight(GameTestHelper helper) {
        var player = owner(helper, 15800);
        for (ItemStack equipment : List.of(armor(), new ItemStack(MiteTools.get("vibranium_pickaxe")),
                new ItemStack(MiteShields.get("vibranium")), new ItemStack(MiteWeaponContent.VIBRANIUM_BOW.get()))) {
            helper.assertValueEqual(R196EnchantmentTable.enchantability(equipment), 120,
                    "Vibranium equipment retains material enchantability 120");
            var ordinary = menu(helper, player, Blocks.ENCHANTING_TABLE);
            ordinary.getSlot(0).set(equipment.copy());
            helper.assertValueEqual(ordinary.costs[2], 30, "Native offers stop at thirty independently of point prices");
            helper.assertValueEqual(((R196EnchantmentMenuAccess)ordinary).mite$experienceCost(2), 15800,
                    "The maximum ordinary-table offer costs 15800 experience points");
            helper.assertValueEqual(VanillaTableEnchanting.offerStrength(equipment, 48, 2, player.getEnchantmentSeed()), 30,
                    "The same forty-eight physical shelves provide vanilla thirty-strength selection");
            var emerald = menu(helper, player, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
            emerald.getSlot(0).set(equipment.copy());
            helper.assertValueEqual(emerald.costs[2], 30, "Emerald tables use the same native offer strength");
            helper.assertValueEqual(((R196EnchantmentMenuAccess)emerald).mite$experienceCost(2), 9800,
                    "Emerald tables retain their separate lower point price");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualVibraniumOfferRejectsInsufficientPointsThenChargesExactly15800AndThreeLapis(GameTestHelper helper) {
        var player = owner(helper, 15799);
        var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
        ItemStack input = armor();
        menu.getSlot(0).set(input.copy());
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
        helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), 15800, "The actual menu quotes the unchanged four-layer price");
        helper.assertFalse(menu.clickMenuButton(player, 2), "One missing experience point rejects the maximum offer");
        helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Rejected payment retains the armor");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "Rejected payment retains every lapis");
        helper.assertValueEqual(player.totalExperience, 15799, "Rejected payment deducts no experience points");
        PlayerExperience.setTotal(player, 15800);
        helper.assertTrue(menu.clickMenuButton(player, 2), "Exactly 15800 points fund the maximum offer");
        helper.assertTrue(menu.getSlot(0).getItem().is(input.getItem()) && menu.getSlot(0).getItem().isEnchanted(),
                "The successful transaction publishes enchanted vibranium armor");
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
        helper.assertTrue(enchantments.getLevel(holder(helper, Enchantments.PROTECTION)) <= holder(helper, Enchantments.PROTECTION).value().getMaxLevel(),
                "The actual fully paid third option respects the configured Protection ceiling");
        helper.assertTrue(enchantments.getLevel(holder(helper, Enchantments.UNBREAKING)) <= holder(helper, Enchantments.UNBREAKING).value().getMaxLevel(),
                "The actual fully paid third option respects the configured Unbreaking ceiling");
        helper.assertValueEqual(player.totalExperience, 0, "Successful enchanting deducts exactly 15800 points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "The third option consumes exactly three lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullBookshelvesRespectBothLoweredAndRaisedConfigurationCaps(GameTestHelper helper) {
        try (var settings = new Settings()) {
            settings.set(List.of(), List.of());
            assertOutputCaps(helper, 5, 5, 7);
            settings.set(List.of(), List.of("minecraft:unbreaking=2", "minecraft:protection=3", "minecraft:sharpness=3"));
            assertOutputCaps(helper, 3, 2, 3);
            settings.set(List.of(), List.of("minecraft:unbreaking=6", "minecraft:protection=6", "minecraft:sharpness=9"));
            assertOutputCaps(helper, 5, 5, 7);
            settings.set(List.of("minecraft:protection"), List.of("minecraft:unbreaking=2"));
            var protection = holder(helper, Enchantments.PROTECTION);
            var unbreaking = holder(helper, Enchantments.UNBREAKING);
            var random = RandomSource.create(196L);
            for (ItemStack input : List.of(new ItemStack(Items.BOOK), armor())) {
                int energy = R196EnchantmentTable.energy(input, 48, false, 2, .5F);
                for (int draw = 0; draw < 256; draw++) {
                    var output = R196EnchantmentTable.output(helper.getLevel().registryAccess(), input, energy, random);
                    var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(output);
                    helper.assertValueEqual(enchantments.getLevel(protection), 0,
                            "Additional bookshelf power cannot restore a disabled enchantment");
                    helper.assertTrue(enchantments.getLevel(unbreaking) <= 2,
                            "Disabled-entry selection still observes the lowered Unbreaking II ceiling");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vibraniumRetainsItsSourceExceptionsWithoutRaisingOtherMaterials(GameTestHelper helper) {
        try (var settings = new Settings()) {
            helper.assertValueEqual(R196EnchantmentCatalog.definition("minecraft:unbreaking").maxLevel(), 5,
                    "Default Unbreaking ceiling is V");
            helper.assertValueEqual(R196EnchantmentCatalog.definition("minecraft:sharpness").maxLevel(), 7,
                    "Default Sharpness ceiling is VII");
            var sharpness = holder(helper, Enchantments.SHARPNESS);
            var unbreaking = holder(helper, Enchantments.UNBREAKING);
            var protection = holder(helper, Enchantments.PROTECTION);
            helper.assertValueEqual(sharpness.value().getMaxLevel(), 7, "Native Sharpness metadata exposes VII");
            helper.assertValueEqual(unbreaking.value().getMaxLevel(), 5, "Native Unbreaking metadata exposes V");
            helper.assertValueEqual(protection.value().getMaxLevel(), 5, "Protection metadata retains V for existing items and anvil upgrades");
            boolean foundSharpnessSeven = false;
            boolean foundUnbreakingFive = false;
            boolean foundProtectionFive = false;
            var random = RandomSource.create(196L);
            for (ItemStack input : List.of(new ItemStack(MiteTools.get("vibranium_sword")), armor())) {
                int energy = R196EnchantmentTable.energy(input, 48, false, 2, .5F);
                helper.assertValueEqual(energy, 158, "Both source routes use reachable four-layer vibranium power");
                for (int draw = 0; draw < 4096; draw++) {
                    var output = R196EnchantmentTable.output(helper.getLevel().registryAccess(), input, energy, random);
                    var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(output);
                    helper.assertTrue(enchantments.getLevel(sharpness) <= 7, "The source budget respects vibranium's Sharpness VII limit");
                    helper.assertTrue(enchantments.getLevel(unbreaking) <= 5, "The source budget respects Unbreaking V");
                    helper.assertTrue(enchantments.getLevel(protection) <= 5, "The source budget respects vibranium's Protection V limit");
                    helper.assertTrue(enchantments.size() <= 3, "Even full vibranium energy adds at most three source enchantments");
                    foundSharpnessSeven |= enchantments.getLevel(sharpness) == 7;
                    foundUnbreakingFive |= enchantments.getLevel(unbreaking) == 5;
                    foundProtectionFive |= enchantments.getLevel(protection) == 5;
                }
            }
            helper.assertTrue(foundSharpnessSeven, "A fixed full-bookshelf sword sequence can reach the configured Sharpness VII maximum");
            helper.assertTrue(foundUnbreakingFive, "A fixed full-bookshelf sequence can reach configured Unbreaking V");
            helper.assertTrue(foundProtectionFive, "A fixed full-bookshelf armor sequence can reach configured Protection V");
        }
        helper.succeed();
    }

    private static void assertOutputCaps(GameTestHelper helper, int protectionCap, int unbreakingCap, int sharpnessCap) {
        var protection = holder(helper, Enchantments.PROTECTION);
        var unbreaking = holder(helper, Enchantments.UNBREAKING);
        var sharpness = holder(helper, Enchantments.SHARPNESS);
        for (ItemStack input : List.of(new ItemStack(Items.BOOK), armor(), new ItemStack(MiteTools.get("vibranium_sword")))) {
            int materialProtection = input.is(Items.BOOK) ? Math.min(protectionCap, 4) : protectionCap;
            int materialSharpness = input.is(Items.BOOK) ? Math.min(sharpnessCap, 5) : sharpnessCap;
            int energy = R196EnchantmentTable.energy(input, 48, false, 2, .5F);
            helper.assertTrue(energy > 0 && energy <= 158, "Cap regression uses reachable four-layer table power");
            boolean foundProtectionMaximum = false;
            boolean foundUnbreakingMaximum = false;
            boolean foundSharpnessMaximum = false;
            var random = RandomSource.create(196L);
            for (int draw = 0; draw < 4096; draw++) {
                var selected = R196EnchantmentTable.select(helper.getLevel().registryAccess(), input, energy, random);
                for (var entry : selected) {
                    if (entry.enchantment.equals(protection))
                        helper.assertTrue(entry.level <= materialProtection, "Candidate selection observes the material Protection ceiling");
                    if (entry.enchantment.equals(unbreaking))
                        helper.assertTrue(entry.level <= unbreakingCap, "Candidate selection observes the configured Unbreaking ceiling");
                    if (entry.enchantment.equals(sharpness))
                        helper.assertTrue(entry.level <= materialSharpness, "Candidate selection observes the material Sharpness ceiling");
                }
                var output = R196EnchantmentTable.output(helper.getLevel().registryAccess(), input, energy, random);
                if (output.isEmpty()) continue;
                var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(output);
                helper.assertTrue(enchantments.getLevel(protection) <= materialProtection,
                        "Stored Protection never exceeds its configured and material ceilings");
                helper.assertTrue(enchantments.getLevel(unbreaking) <= unbreakingCap,
                        "Stored Unbreaking cannot exceed its configured table ceiling");
                helper.assertTrue(enchantments.getLevel(sharpness) <= materialSharpness,
                        "Stored Sharpness cannot exceed its configured and material ceilings");
                if (input.is(Items.BOOK)) helper.assertValueEqual(enchantments.size(), 1, "R196 table books store exactly one randomly retained enchantment");
                else helper.assertTrue(enchantments.size() <= 3, "Equipment keeps at most three source selections");
                foundProtectionMaximum |= enchantments.getLevel(protection) == materialProtection;
                foundUnbreakingMaximum |= enchantments.getLevel(unbreaking) == unbreakingCap;
                foundSharpnessMaximum |= enchantments.getLevel(sharpness) == materialSharpness;
            }
            if (input.is(Items.BOOK) || input.getItem() instanceof net.minecraft.world.item.ArmorItem)
                helper.assertTrue(foundProtectionMaximum, "Each fixed eligible sequence reaches the Protection rank allowed by its power and cap " + protectionCap);
            if (input.is(Items.BOOK) || input.is(MiteTools.get("vibranium_sword")))
                helper.assertTrue(foundSharpnessMaximum, "Each fixed eligible sequence reaches its obtainable Sharpness maximum");
            if (input.is(Items.BOOK) || input.getItem() instanceof net.minecraft.world.item.ArmorItem)
                helper.assertTrue(foundUnbreakingMaximum, "Each eligible fixed sequence reaches the Unbreaking rank allowed by its power and cap " + unbreakingCap);
        }
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack armor() { return new ItemStack(MiteArmorContent.get("mite:vibranium_chestplate")); }

    private static BlockPos place(GameTestHelper helper, Block block, int layers) {
        BlockPos table = helper.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 4; dy++)
            helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table, block.defaultBlockState(), 2);
        for (int layer = 0; layer < layers; layer++) addLayer(helper, table, layer);
        return table;
    }

    private static void addLayer(GameTestHelper helper, BlockPos table, int layer) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            helper.getLevel().setBlock(table.offset(dx, layer, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        }
    }

    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block) {
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), place(helper, block, 4)));
    }

    private static ServerPlayer owner(GameTestHelper helper, int points) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FourLayerEnchant"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, points);
        return player;
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();
        private Settings() { MiteConfig.ENCHANTMENT_CHANCES.set(List.of()); set(List.of(), List.of()); }
        private void set(List<String> disabled, List<String> caps) {
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
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
