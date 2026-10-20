package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Random selection retains source point fees and the native modern-equipment route. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class VanillaEnchantingGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);

    private VanillaEnchantingGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void sourceBooksAndEquipmentKeepOriginalPointFeesAndLapisCosts(GameTestHelper helper) {
        try (var settings = new ChanceSettings()) {
            for (Block block : tables()) for (var item : List.of(Items.BOOK, Items.IRON_SWORD)) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block);
                var input = new ItemStack(item);
                menu.getSlot(0).set(input);
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                helper.assertTrue(((R196EnchantmentMenuAccess)menu).mite$usesSourceEnchanting(), "Source books and equipment keep the point-cost menu");
                int energy = R196EnchantmentTable.energy(input, 24, block != Blocks.ENCHANTING_TABLE, 2, .5F);
                helper.assertValueEqual(menu.costs[2], energy, "Source offers retain the original bookshelf and material energy");
                helper.assertValueEqual(menu.enchantClue[2], -1, "The point-cost menu retains its unannounced random result");
                int points = player.totalExperience;
                helper.assertTrue(menu.clickMenuButton(player, 2), "The funded source offer succeeds");
                var result = menu.getSlot(0).getItem();
                helper.assertTrue(result.is(input.is(Items.BOOK) ? Items.ENCHANTED_BOOK : input.getItem()), "Source enchanting preserves the intended result type");
                var actual = EnchantmentHelper.getEnchantmentsForCrafting(result);
                helper.assertTrue(actual.size() >= 1, "Random source selection produces at least one compatible enchantment");
                helper.assertValueEqual(player.totalExperience, points - energy * 100, "Source enchanting still consumes energy times one hundred experience points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The third source offer consumes three lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void modernEquipmentRetainsNativeLevelFeesAndPreviews(GameTestHelper helper) {
        try (var settings = new ChanceSettings()) {
            for (Block block : tables()) for (var item : List.of(Items.CROSSBOW, Items.TRIDENT, Items.MACE)) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block);
                menu.getSlot(0).set(new ItemStack(item));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                helper.assertFalse(((R196EnchantmentMenuAccess)menu).mite$usesSourceEnchanting(), "Modern equipment keeps the native menu");
                helper.assertValueEqual(menu.costs[2], 30, "Native modern-equipment offers retain the level-thirty requirement");
                helper.assertTrue(menu.enchantClue[2] >= 0 && menu.levelClue[2] > 0, "Modern equipment retains its native preview");
                helper.assertTrue(menu.clickMenuButton(player, 2), "The funded native modern-equipment offer succeeds");
                helper.assertTrue(menu.getSlot(0).getItem().isEnchanted(), "The native route publishes enchanted modern equipment");
                helper.assertValueEqual(player.experienceLevel, 42, "The native modern-equipment route consumes three levels");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The native route consumes three lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void lapisChangesPreserveSourceOffersWithoutPartialPayment(GameTestHelper helper) {
        try (var settings = new ChanceSettings()) {
            var player = owner(helper, 45);
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            int seed = menu.getEnchantmentSeed();
            int[] costs = menu.costs.clone(), clues = menu.enchantClue.clone(), levels = menu.levelClue.clone();
            helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_INGOT)), "The native lapis slot rejects unrelated items");
            helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.LAPIS_LAZULI)), "The native lapis slot accepts lapis");
            for (int count : new int[] {0, 1, 2, 6, 0}) {
                menu.getSlot(1).set(count == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, count));
                helper.assertTrue(Arrays.equals(menu.costs, costs) && Arrays.equals(menu.enchantClue, clues)
                        && Arrays.equals(menu.levelClue, levels), "Changing lapis never rerolls source offers");
                helper.assertValueEqual(menu.getEnchantmentSeed(), seed, "Lapis changes preserve the displayed runic seed");
                if (count < 3) helper.assertFalse(menu.clickMenuButton(player, 2), "Missing or insufficient lapis rejects the third offer");
            }
            menu.getSlot(0).set(ItemStack.EMPTY);
            helper.assertTrue(Arrays.stream(menu.costs).allMatch(cost -> cost == 0), "Removing the input clears source offers");
            helper.assertValueEqual(player.experienceLevel, 45, "Rejected lapis attempts never consume experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void zeroChanceTablePoolsCannotConsumeExperienceOrLapis(GameTestHelper helper) {
        try (var settings = new ChanceSettings()) {
            MiteConfig.ENCHANTMENT_CHANCES.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .holders().map(holder -> holder.key().location() + "=0").toList());
            MiteConfig.refreshEnchantmentRules();
            for (Block block : tables()) for (var item : List.of(Items.BOOK, Items.IRON_SWORD, Items.CROSSBOW)) {
                var player = owner(helper, 45);
                int points = player.totalExperience;
                var menu = menu(helper, player, block);
                var input = new ItemStack(item);
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                for (int slot = 0; slot < 3; slot++) {
                    helper.assertValueEqual(menu.enchantClue[slot], -1, "A zero-chance pool has no preview enchantment");
                    helper.assertValueEqual(menu.levelClue[slot], -1, "A zero-chance pool has no preview level");
                    menu.clickMenuButton(player, slot);
                    helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), input), "An empty random selection preserves the input");
                    helper.assertValueEqual(player.totalExperience, points, "An empty selection consumes no experience");
                    helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 6, "An empty selection consumes no lapis");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void creativeSourceAndModernEnchantingRemainFree(GameTestHelper helper) {
        try (var settings = new ChanceSettings()) {
            for (Block block : tables()) for (var item : List.of(Items.BOOK, Items.IRON_SWORD, Items.CROSSBOW)) {
                var player = owner(helper, 0);
                player.setGameMode(GameType.CREATIVE);
                var menu = menu(helper, player, block);
                menu.getSlot(0).set(new ItemStack(item));
                helper.assertTrue(menu.clickMenuButton(player, 2), "Creative enchanting can succeed without experience or lapis");
                helper.assertTrue(!EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).isEmpty(), "Creative enchanting still applies a valid random selection");
                helper.assertValueEqual(player.totalExperience, 0, "Creative enchanting consumes no experience and creates no debt");
                helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Creative enchanting requires no lapis");
            }
        }
        helper.succeed();
    }

    private static List<Block> tables() {
        return List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
    }

    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block) {
        BlockPos table = helper.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 3; dy++)
            helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table, block.defaultBlockState(), 2);
        for (int dy = 0; dy <= 1; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 2)
                helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }

    private static ServerPlayer owner(GameTestHelper helper, int level) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "NativeEnchant"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, ExperienceRules.experienceRequiredForLevel(level));
        return player;
    }

    private static final class ChanceSettings implements AutoCloseable {
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();
        private ChanceSettings() {
            MiteConfig.ENCHANTMENT_CHANCES.set(List.of());
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
