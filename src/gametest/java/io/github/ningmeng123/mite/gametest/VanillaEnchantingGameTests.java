package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native offers, synchronized clues and atomic point payment through real menus. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class VanillaEnchantingGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);

    private VanillaEnchantingGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void sourceEquipmentKeepsOriginalPointFeesAndLapisCosts(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (Block block : tables()) for (var item : List.of(Items.IRON_CHESTPLATE, Items.IRON_SWORD)) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block);
                var input = new ItemStack(item);
                menu.getSlot(0).set(input);
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                helper.assertTrue(access(menu).mite$usesSourceEnchanting(), "Source equipment uses point payment");
                int energy = R196EnchantmentTable.energy(input, 24, block != Blocks.ENCHANTING_TABLE, 2, .5F);
                helper.assertValueEqual(menu.costs[2], 30, "Offer strength follows the native fifteen-shelf maximum");
                helper.assertValueEqual(access(menu).mite$experienceCost(2), energy * 100, "The old material and table price is preserved independently");
                assertPreview(helper, menu, 2);
                int clue = menu.enchantClue[2], clueLevel = menu.levelClue[2], points = player.totalExperience;
                helper.assertTrue(menu.clickMenuButton(player, 2), "The funded source offer succeeds");
                var result = menu.getSlot(0).getItem();
                helper.assertTrue(result.is(input.getItem()), "Enchanting preserves the intended equipment type");
                assertClueInResult(helper, result, clue, clueLevel);
                helper.assertValueEqual(player.totalExperience, points - energy * 100, "Enchanting consumes the quoted point price exactly once");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The third option consumes three lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void modernEquipmentUsesPointFeesAndNativePreviews(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (Block block : tables()) for (var item : List.of(Items.CROSSBOW, Items.TRIDENT, Items.MACE)) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block);
                menu.getSlot(0).set(new ItemStack(item));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                helper.assertTrue(access(menu).mite$usesSourceEnchanting(), "Modern equipment uses the same point-payment menu");
                helper.assertValueEqual(menu.costs[2], 30, "Modern equipment retains the level-thirty offer");
                assertPreview(helper, menu, 2);
                int fee = access(menu).mite$experienceCost(2), points = player.totalExperience;
                int clue = menu.enchantClue[2], clueLevel = menu.levelClue[2];
                helper.assertTrue(fee > 0, "Every modern offer has a positive point price");
                helper.assertTrue(menu.clickMenuButton(player, 2), "The funded modern-equipment offer succeeds");
                assertClueInResult(helper, menu.getSlot(0).getItem(), clue, clueLevel);
                helper.assertValueEqual(player.totalExperience, points - fee, "Modern equipment consumes points without an additional level deduction");
                helper.assertValueEqual(player.experienceLevel, ExperienceRules.levelForTotalExperience(points - fee), "The displayed level is recomputed from the remaining points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The third offer consumes three lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void fifteenShelvesReachThirtyAndNativeSeedDeterminesAllOffers(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (Block block : tables()) for (int shelves : new int[]{0, 1, 14, 15, 32}) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block, shelves);
                var input = new ItemStack(Items.IRON_SWORD);
                for (int seed : new int[]{0, 1, 196, Integer.MAX_VALUE}) {
                    menu.setData(3, seed);
                    menu.getSlot(0).set(input.copy());
                    var random = RandomSource.create(seed);
                    for (int slot = 0; slot < 3; slot++) {
                        int expected = EnchantmentHelper.getEnchantmentCost(random, slot, shelves, input);
                        if (expected < slot + 1) expected = 0;
                        helper.assertValueEqual(menu.costs[slot], expected, "Native bookshelf count and seed control every offer: shelves=" + shelves + ", slot=" + slot);
                    }
                    if (shelves >= 15) helper.assertValueEqual(menu.costs[2], 30, "Fifteen shelves already reach the native maximum");
                    else helper.assertTrue(menu.costs[2] < 30, "Fewer than fifteen shelves cannot reach thirty");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void lapisChangesAndInputReinsertionPreserveOffersPricesAndSeed(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var player = owner(helper, 45);
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
            var input = new ItemStack(Items.IRON_SWORD);
            menu.getSlot(0).set(input.copy());
            int seed = menu.getEnchantmentSeed(), points = player.totalExperience;
            int[] costs = menu.costs.clone(), clues = menu.enchantClue.clone(), levels = menu.levelClue.clone(), fees = fees(menu);
            helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_INGOT)), "The lapis slot rejects unrelated items");
            helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.LAPIS_LAZULI)), "The lapis slot accepts lapis");
            for (int count : new int[]{0, 1, 2, 6, 0}) {
                menu.getSlot(1).set(count == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, count));
                helper.assertTrue(Arrays.equals(menu.costs, costs) && Arrays.equals(menu.enchantClue, clues)
                        && Arrays.equals(menu.levelClue, levels) && Arrays.equals(fees(menu), fees), "Changing lapis cannot reroll offers, clues or prices");
                helper.assertValueEqual(menu.getEnchantmentSeed(), seed, "Lapis changes preserve the enchantment seed");
                if (count < 3) helper.assertFalse(menu.clickMenuButton(player, 2), "Insufficient lapis rejects the third offer");
            }
            menu.getSlot(0).set(ItemStack.EMPTY);
            helper.assertTrue(Arrays.stream(menu.costs).allMatch(cost -> cost == 0), "Removing the input clears offers");
            helper.assertTrue(Arrays.stream(fees(menu)).allMatch(fee -> fee == 0), "Removing the input clears point prices");
            menu.getSlot(0).set(input.copy());
            helper.assertTrue(Arrays.equals(menu.costs, costs) && Arrays.equals(menu.enchantClue, clues)
                    && Arrays.equals(menu.levelClue, levels) && Arrays.equals(fees(menu), fees), "Reinserting the same item keeps the same seeded offers and prices");
            helper.assertValueEqual(player.totalExperience, points, "Rejected attempts and previews consume no experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void pointPaymentRejectsOneMissingPointAndChargesExactBalanceOnce(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (Block block : tables()) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, block);
                var input = new ItemStack(MiteTools.get("vibranium_pickaxe"));
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                int fee = access(menu).mite$experienceCost(2);
                helper.assertTrue(fee > ExperienceRules.experienceRequiredForLevel(menu.costs[2]), "The boundary fixture can satisfy the native level requirement even one point short");
                PlayerExperience.setTotal(player, fee - 1);
                helper.assertFalse(menu.clickMenuButton(player, 2), "One missing point rejects the transaction");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Rejected payment preserves the input");
                helper.assertValueEqual(player.totalExperience, fee - 1, "Rejected payment consumes no points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 6, "Rejected payment consumes no lapis");
                PlayerExperience.setTotal(player, fee);
                helper.assertTrue(menu.clickMenuButton(player, 2), "An exact balance funds the transaction");
                helper.assertTrue(menu.getSlot(0).getItem().isEnchanted(), "A successful payment produces the enchanted item");
                helper.assertValueEqual(player.totalExperience, 0, "Exactly the quoted points are consumed");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "A successful payment consumes exactly three lapis");
                helper.assertFalse(menu.clickMenuButton(player, 2), "An already enchanted result cannot be purchased again");
                helper.assertValueEqual(player.totalExperience, 0, "Repeated clicks cannot create experience debt");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Repeated clicks cannot charge lapis again");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void sufficientPointsDoNotBypassNativeLevelRequirement(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var player = owner(helper, 29);
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
            var input = new ItemStack(Items.CROSSBOW);
            menu.getSlot(0).set(input.copy());
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            int points = player.totalExperience;
            helper.assertTrue(points >= access(menu).mite$experienceCost(2), "The fixture can pay the point fee");
            helper.assertValueEqual(menu.costs[2], 30, "The offer requires level thirty");
            helper.assertFalse(menu.clickMenuButton(player, 2), "Level twenty-nine cannot buy the level-thirty offer");
            helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "The level requirement preserves the original item");
            helper.assertValueEqual(player.totalExperience, points, "The rejected level check consumes no points");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "The rejected level check consumes no lapis");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void zeroChanceTablePoolsCannotConsumeExperienceOrLapis(GameTestHelper helper) {
        try (var settings = new Settings()) {
            MiteConfig.ENCHANTMENT_CHANCES.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .holders().map(holder -> holder.key().location() + "=0").toList());
            MiteConfig.refreshEnchantmentRules();
            for (Block block : tables()) for (var item : List.of(Items.IRON_CHESTPLATE, Items.IRON_SWORD, Items.CROSSBOW)) {
                var player = owner(helper, 45);
                int points = player.totalExperience;
                var menu = menu(helper, player, block);
                var input = new ItemStack(item);
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                for (int slot = 0; slot < 3; slot++) {
                    helper.assertValueEqual(menu.enchantClue[slot], -1, "An empty pool has no preview enchantment");
                    helper.assertValueEqual(menu.levelClue[slot], -1, "An empty pool has no preview level");
                    menu.clickMenuButton(player, slot);
                    helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), input), "An empty selection preserves the input");
                    helper.assertValueEqual(player.totalExperience, points, "An empty selection consumes no experience");
                    helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 6, "An empty selection consumes no lapis");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void creativeSourceAndModernEnchantingRemainFree(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (Block block : tables()) for (var item : List.of(Items.IRON_CHESTPLATE, Items.IRON_SWORD, Items.CROSSBOW)) {
                var player = owner(helper, 0);
                player.setGameMode(GameType.CREATIVE);
                var menu = menu(helper, player, block);
                menu.getSlot(0).set(new ItemStack(item));
                helper.assertTrue(menu.clickMenuButton(player, 2), "Creative enchanting succeeds without levels, points or lapis");
                helper.assertTrue(!EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).isEmpty(), "Creative enchanting applies a valid selection");
                helper.assertValueEqual(player.totalExperience, 0, "Creative enchanting consumes no points and creates no debt");
                helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Creative enchanting requires no lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void pointPricesAndNativeCluesReachClientThroughMenuPackets(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var player = owner(helper, 45);
            var server = menu(helper, player, Blocks.ENCHANTING_TABLE);
            var client = new EnchantmentMenu(server.containerId, player.getInventory());
            server.addSlotListener(new ContainerListener() {
                @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}
                @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                    var bytes = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes,
                                new ClientboundContainerSetDataPacket(menu.containerId, index, value));
                        var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);
                        client.setData(packet.getId(), packet.getValue());
                    } finally { bytes.release(); }
                }
            });
            server.getSlot(0).set(new ItemStack(MiteTools.get("vibranium_pickaxe")));
            server.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            server.broadcastChanges();
            helper.assertTrue(access(client).mite$usesSourceEnchanting(), "The client receives the point-payment mode");
            helper.assertTrue(Arrays.equals(client.costs, server.costs) && Arrays.equals(client.enchantClue, server.enchantClue)
                    && Arrays.equals(client.levelClue, server.levelClue), "Native strength and preview fields survive packet encoding");
            helper.assertTrue(Arrays.equals(fees(client), fees(server)), "All three quoted point prices survive packet encoding");
            server.getSlot(0).set(ItemStack.EMPTY);
            server.broadcastChanges();
            helper.assertTrue(Arrays.stream(fees(client)).allMatch(fee -> fee == 0), "Cleared prices reach the client too");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void successfulPaymentRefreshesTheSeedAndTheNextItemOffers(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var player = owner(helper, 60);
            var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
            int seedBefore = menu.getEnchantmentSeed();
            helper.assertTrue(menu.clickMenuButton(player, 2), "A successful transaction can refresh the native enchantment seed");
            helper.assertValueEqual(menu.getEnchantmentSeed(), player.getEnchantmentSeed(), "The menu receives the player's new seed");
            helper.assertTrue(menu.getEnchantmentSeed() != seedBefore, "Successful enchanting advances the native random seed");
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            var fresh = new EnchantmentMenu(2, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(SITE)));
            fresh.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(Arrays.equals(menu.costs, fresh.costs) && Arrays.equals(menu.enchantClue, fresh.enchantClue)
                    && Arrays.equals(menu.levelClue, fresh.levelClue) && Arrays.equals(fees(menu), fees(fresh)),
                    "The next input uses the refreshed seed exactly as a newly opened menu does");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void changedBookshelvesRefreshTheQuoteWithoutChargingForTheStaleOffer(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (boolean removeAll : new boolean[]{false, true}) {
                var player = owner(helper, 60);
                var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
                var input = new ItemStack(MiteTools.get("vibranium_pickaxe"));
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
                int points = player.totalExperience, fee = access(menu).mite$experienceCost(2);
                BlockPos table = helper.absolutePos(SITE);
                if (removeAll) {
                    for (var offset : EnchantingTableBlock.BOOKSHELF_OFFSETS)
                        helper.getLevel().setBlock(table.offset(offset), Blocks.AIR.defaultBlockState(), 2);
                } else {
                    helper.getLevel().setBlock(table.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 2);
                }
                helper.assertFalse(menu.clickMenuButton(player, 2), "A stale bookshelf quote cannot be charged");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "A stale quote preserves the original item");
                helper.assertValueEqual(player.totalExperience, points, "Refreshing a stale quote consumes no points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Refreshing a stale quote consumes no lapis");
                helper.assertTrue(access(menu).mite$experienceCost(2) != fee, "The old fee is replaced by the current bookshelf price");
                if (removeAll) helper.assertTrue(menu.costs[2] < 30, "Removing all shelves lowers native offer strength");
                else helper.assertValueEqual(menu.costs[2], 30, "Even a price-only shelf change is revalidated when strength stays thirty");
                var fresh = new EnchantmentMenu(2, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
                fresh.getSlot(0).set(input.copy());
                helper.assertTrue(Arrays.equals(menu.costs, fresh.costs) && Arrays.equals(menu.enchantClue, fresh.enchantClue)
                        && Arrays.equals(menu.levelClue, fresh.levelClue) && Arrays.equals(fees(menu), fees(fresh)),
                        "The refreshed quote agrees with a newly opened menu in the changed surroundings");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void negativeExperienceCannotPurchaseOrDeepenDebt(GameTestHelper helper) {
        try (var settings = new Settings()) {
            for (int points : new int[]{-1, ExperienceRules.MIN_TOTAL_EXPERIENCE}) {
                var player = owner(helper, 45);
                var menu = menu(helper, player, Blocks.ENCHANTING_TABLE);
                var input = new ItemStack(Items.CROSSBOW);
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
                PlayerExperience.setTotal(player, points);
                for (int slot = 0; slot < 3; slot++) helper.assertFalse(menu.clickMenuButton(player, slot), "Negative experience cannot buy any offer");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Rejected debt payment preserves the input");
                helper.assertValueEqual(player.totalExperience, points, "Rejected payment cannot deepen experience debt");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected debt payment consumes no lapis");
            }
        }
        helper.succeed();
    }

    private static void assertPreview(GameTestHelper helper, EnchantmentMenu menu, int slot) {
        helper.assertTrue(menu.enchantClue[slot] >= 0 && menu.levelClue[slot] > 0, "A nonempty offer exposes a native enchantment preview");
    }

    private static void assertClueInResult(GameTestHelper helper, ItemStack result, int clue, int level) {
        var holder = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap().byId(clue);
        helper.assertTrue(holder != null, "The preview identifies a registered enchantment");
        helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(holder), level,
                "The previewed enchantment and exact rank are present in the final item");
    }

    private static R196EnchantmentMenuAccess access(EnchantmentMenu menu) { return (R196EnchantmentMenuAccess)menu; }
    private static int[] fees(EnchantmentMenu menu) {
        return new int[]{access(menu).mite$experienceCost(0), access(menu).mite$experienceCost(1), access(menu).mite$experienceCost(2)};
    }
    private static List<Block> tables() {
        return List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
    }
    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block) { return menu(helper, player, block, 32); }
    private static EnchantmentMenu menu(GameTestHelper helper, ServerPlayer player, Block block, int shelves) {
        BlockPos table = helper.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 3; dy++)
            helper.getLevel().setBlock(table.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(table, block.defaultBlockState(), 2);
        for (var offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (shelves-- <= 0) break;
            helper.getLevel().setBlock(table.offset(offset), Blocks.BOOKSHELF.defaultBlockState(), 2);
        }
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }
    private static ServerPlayer owner(GameTestHelper helper, int level) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "NativeEnchant"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, ExperienceRules.experienceRequiredForLevel(level));
        return player;
    }
    private static final class Settings implements AutoCloseable {
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private Settings() {
            MiteConfig.ENCHANTMENT_CHANCES.set(List.of());
            MiteConfig.DISABLED_ENCHANTMENTS.set(List.of());
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of());
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
