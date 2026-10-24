package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real table previews and payments must honor the configured rank ceiling. @author NingMeng123 */
@GameTestHolder("mite_enchanting")
@PrefixGameTestTemplate(false)
public final class ConfiguredTableCapGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);
    private ConfiguredTableCapGameTests() {}

    @GameTest(template = "empty")
    public static void configuredSharpnessSevenAppearsInPreviewAndPaidOutputOnBothTables(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            for (Block block : List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get())) {
                for (int cap : new int[]{7, 6}) {
                    settings.cap(cap);
                    var player = player(helper);
                    var menu = menu(helper, player, block, new ItemStack(MiteTools.get("vibranium_sword")));
                    int price = ((R196EnchantmentMenuAccess)menu).mite$experienceCost(2);
                    helper.assertValueEqual(menu.costs[2], 30, "Native offer strength stays thirty");
                    helper.assertValueEqual(price, block == Blocks.ENCHANTING_TABLE ? 10000 : 5000, "Existing material and table prices are unchanged");
                    int expectedRank = block == Blocks.ENCHANTING_TABLE ? cap : 5;
                    helper.assertValueEqual(menu.levelClue[2], expectedRank, "The preview honors both the configured cap and the paid energy budget");
                    var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
                    helper.assertValueEqual(menu.enchantClue[2], helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap().getId(sharpness),
                            "The preview identifies Sharpness");
                    helper.assertTrue(menu.clickMenuButton(player, 2), "The native table accepts the funded high-rank offer");
                    helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).getLevel(sharpness), expectedRank,
                            "The paid item contains exactly the previewed configured rank");
                    helper.assertValueEqual(player.totalExperience, 20000 - price, "High ranks keep point-only payment");
                    helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "The third offer consumes exactly three lapis");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loweringAnOpenTablesCapRefreshesPreviewBeforeAnyPayment(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.cap(7);
            var player = player(helper);
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE, new ItemStack(MiteTools.get("vibranium_sword")));
            helper.assertValueEqual(menu.levelClue[2], 7, "The initial quote includes Sharpness VII");
            int seed = menu.getEnchantmentSeed();
            settings.cap(6);
            helper.assertFalse(menu.clickMenuButton(player, 2), "A stale VII preview cannot be purchased after lowering the cap");
            helper.assertValueEqual(menu.levelClue[2], 6, "The open menu refreshes to Sharpness VI");
            helper.assertValueEqual(player.totalExperience, 20000, "Refreshing the quote consumes no experience");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Refreshing the quote consumes no lapis");
            helper.assertValueEqual(menu.getEnchantmentSeed(), seed, "Refreshing the cap does not reroll the seed");
            helper.assertTrue(menu.clickMenuButton(player, 2), "The refreshed VI quote can be purchased");
            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).getLevel(sharpness), 6,
                    "No hidden acquisition cap or stale output restores the old rank");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raisingTheCapDoesNotBypassNativePowerOrForceTheMaximumRank(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.cap(99);
            var player = player(helper);
            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            helper.assertValueEqual(VanillaTableEnchanting.maximumLevel(sharpness), 99, "The configured cap is not silently reduced to five");
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE, new ItemStack(MiteTools.get("vibranium_sword")));
            helper.assertValueEqual(menu.levelClue[2], 7, "Power 67..76 permits VII but cannot reach VIII's minimum of 78");
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(menu.levelClue[2] > 0 && menu.levelClue[2] <= 4,
                    "Ordinary iron still uses its native enchantability instead of receiving rank 99");
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TableCap"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, 20000);
        return player;
    }

    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block, ItemStack input) {
        BlockPos table = helper.absolutePos(SITE);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y < 4; y++)
            helper.getLevel().setBlock(table.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table, block.defaultBlockState(), 2);
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS)
            helper.getLevel().setBlock(table.offset(offset), Blocks.BOOKSHELF.defaultBlockState(), 2);
        var menu = new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
        menu.setData(3, sevenSeed());
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        return menu;
    }

    // Independent native power formula: vibranium enchantability 120, third offer 30.
    private static int sevenSeed() {
        for (int seed = 0; seed < 4096; seed++) {
            var random = RandomSource.create(seed + 2L);
            int base = 31 + random.nextInt(31) + random.nextInt(31);
            float variance = (random.nextFloat() + random.nextFloat() - 1F) * .15F;
            int power = Math.max(1, Math.round(base + base * variance));
            if (power >= 67 && power <= 76) return seed;
        }
        throw new AssertionError("Missing deterministic Sharpness VII seed");
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();
        private Settings(GameTestHelper helper) {
            MiteConfig.ENCHANTMENT_CHANCES.set(List.of());
            MiteConfig.DISABLED_ENCHANTMENTS.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                    .map(holder -> holder.key().location().toString()).filter(id -> !id.equals("minecraft:sharpness")).toList());
            cap(7);
        }
        private void cap(int value) {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:sharpness=" + value));
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
