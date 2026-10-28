package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Only player-table creation of enchanted books is disabled; stored-book generation stays available. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OrdinaryBookEnchantingGameTests {
    private OrdinaryBookEnchantingGameTests() {}

    @GameTest(template = "empty")
    public static void bothTablesRejectOrdinaryBooksFromClicksShiftAndAutomaticInsertionInSurvivalAndCreative(GameTestHelper helper) {
        for (Block table : List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()))
                for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (Insert insert : Insert.values()) {
            Player player = helper.makeMockPlayer(mode);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(pos, table.defaultBlockState(), 3);
            player.moveTo(pos.getCenter());
            PlayerExperience.setTotal(player, 20000);
            EnchantmentMenu menu = new EnchantmentMenu(71, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            insert.run(menu, player);
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            assertRejected(helper, menu, player);
            helper.assertValueEqual(totalBooks(menu, player), 14, "Rejecting " + insert + " insertion preserves all fourteen ordinary books");
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacingAQuotedEquipmentOfferWithABookClearsAllOffersAndPreservesOtherBookSources(GameTestHelper helper) {
        for (Block table : List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get())) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlock(pos, table.defaultBlockState(), 3);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && !(Math.abs(dx) == 2 && Math.abs(dz) == 2))
                    helper.getLevel().setBlock(pos.offset(dx, 0, dz), Blocks.BOOKSHELF.defaultBlockState(), 3);
            player.moveTo(pos.getCenter());
            PlayerExperience.setTotal(player, 20000);
            EnchantmentMenu menu = new EnchantmentMenu(72, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            helper.assertTrue(menu.costs[2] > 0 && ((R196EnchantmentMenuAccess)menu).mite$experienceCost(2) > 0,
                    "Eligible equipment still receives an actual positive player-table quote");
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
            assertRejected(helper, menu, player);
            ItemStack generated = R196EnchantmentTable.output(helper.getLevel().registryAccess(), new ItemStack(Items.BOOK), 53,
                    RandomSource.create(196L));
            helper.assertTrue(generated.is(Items.ENCHANTED_BOOK) && !EnchantmentHelper.getEnchantmentsForCrafting(generated).isEmpty(),
                    "Shared book generation remains available to sources outside the player-table menu");
        }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, EnchantmentMenu menu, Player player) {
        R196EnchantmentMenuAccess experience = (R196EnchantmentMenuAccess) menu;
        for (int slot = 0; slot < 3; slot++) {
            helper.assertValueEqual(menu.costs[slot], 0, "Ordinary books have no native table offer");
            helper.assertValueEqual(menu.enchantClue[slot], -1, "Ordinary books expose no enchantment preview");
            helper.assertValueEqual(menu.levelClue[slot], -1, "Ordinary books expose no enchantment rank");
            helper.assertValueEqual(experience.mite$experienceCost(slot), 0, "Ordinary books have no quoted point fee");
            helper.assertFalse(menu.clickMenuButton(player, slot), "Normal, Shift and automatic insertion cannot bypass the book restriction");
        }
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.BOOK), "Rejected enchanting preserves the ordinary book in the input slot");
        helper.assertValueEqual(player.totalExperience, 20000, "Rejected book enchanting consumes no XP points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected book enchanting consumes no lapis");
        helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).isEmpty(),
                "Rejected enchanting never adds enchantments to the ordinary input book");
    }

    private static int totalBooks(EnchantmentMenu menu, Player player) {
        return menu.getSlot(0).getItem().getCount() + (menu.getCarried().is(Items.BOOK) ? menu.getCarried().getCount() : 0)
                + player.getInventory().countItem(Items.BOOK);
    }

    private enum Insert {
        PICKUP, SHIFT, AUTOMATIC;
        void run(EnchantmentMenu menu, Player player) {
            if (this == PICKUP) {
                menu.setCarried(new ItemStack(Items.BOOK, 14));
                menu.clicked(0, 0, ClickType.PICKUP, player);
            } else if (this == SHIFT) {
                player.getInventory().setItem(9, new ItemStack(Items.BOOK, 14));
                menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
            } else menu.getSlot(0).set(new ItemStack(Items.BOOK, 14));
        }
    }
}
