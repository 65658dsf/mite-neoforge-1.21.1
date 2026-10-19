package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
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

/** Four-layer table geometry, vibranium payment and default enchantment ceilings. @author NingMeng123 */
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
            helper.assertValueEqual(ordinary.costs[2], 158, "Four layers yield the exact ordinary-table maximum");
            helper.assertValueEqual(R196EnchantmentTable.experienceCost(ordinary.costs[2]), 15800,
                    "The maximum ordinary-table offer costs 15800 experience points");
            var emerald = menu(helper, player, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
            emerald.getSlot(0).set(equipment.copy());
            helper.assertValueEqual(emerald.costs[2], 98, "Emerald tables retain their separate lower power");
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
        helper.assertValueEqual(menu.costs[2], 158, "The actual menu presents the four-layer maximum");
        helper.assertFalse(menu.clickMenuButton(player, 2), "One missing experience point rejects the maximum offer");
        helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Rejected payment retains the armor");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "Rejected payment retains every lapis");
        helper.assertValueEqual(player.totalExperience, 15799, "Rejected payment deducts no experience points");
        PlayerExperience.setTotal(player, 15800);
        helper.assertTrue(menu.clickMenuButton(player, 2), "Exactly 15800 points fund the maximum offer");
        helper.assertTrue(menu.getSlot(0).getItem().is(input.getItem()) && menu.getSlot(0).getItem().isEnchanted(),
                "The successful transaction publishes enchanted vibranium armor");
        helper.assertValueEqual(player.totalExperience, 0, "Successful enchanting deducts exactly 15800 points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "The third option consumes exactly three lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultCeilingsAllowUnbreakingFiveAndFourLayerVibraniumSharpnessSeven(GameTestHelper helper) {
        helper.assertValueEqual(R196EnchantmentCatalog.definition("minecraft:unbreaking").maxLevel(), 5,
                "Default Unbreaking ceiling is V");
        helper.assertValueEqual(R196EnchantmentCatalog.definition("minecraft:sharpness").maxLevel(), 7,
                "Default Sharpness ceiling is VII");
        var sharpness = holder(helper, Enchantments.SHARPNESS);
        var unbreaking = holder(helper, Enchantments.UNBREAKING);
        helper.assertValueEqual(sharpness.value().getMaxLevel(), 7, "Native Sharpness metadata exposes VII");
        helper.assertValueEqual(unbreaking.value().getMaxLevel(), 5, "Native Unbreaking metadata exposes V");
        boolean foundSharpnessSeven = false;
        boolean foundUnbreakingFive = false;
        var random = RandomSource.create(196L);
        for (ItemStack input : List.of(new ItemStack(MiteTools.get("vibranium_sword")), armor())) {
            int energy = R196EnchantmentTable.energy(input, 48, false, 2, .5F);
            helper.assertValueEqual(energy, 158, "Both source routes use reachable four-layer vibranium power");
            for (int draw = 0; draw < 256; draw++) {
                var output = R196EnchantmentTable.output(helper.getLevel().registryAccess(), input, energy, random);
                var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(output);
                helper.assertTrue(enchantments.getLevel(sharpness) <= 7, "Default Sharpness stops at VII");
                helper.assertTrue(enchantments.getLevel(unbreaking) <= 5, "Default Unbreaking stops at V");
                foundSharpnessSeven |= enchantments.getLevel(sharpness) == 7;
                foundUnbreakingFive |= enchantments.getLevel(unbreaking) == 5;
            }
        }
        helper.assertTrue(foundSharpnessSeven, "A fixed four-layer vibranium sword sequence reaches Sharpness VII");
        helper.assertTrue(foundUnbreakingFive, "A fixed four-layer vibranium armor sequence reaches Unbreaking V");
        helper.succeed();
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

}
