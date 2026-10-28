package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual modern menus exercise independently extracted R196 payment and book-transfer constraints. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class R196EnchantmentTableGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);
    private R196EnchantmentTableGameTests() {}
    private static ServerPlayer owner(GameTestHelper helper, int points) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EnchantSource"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, points);
        return player;
    }
    private static BlockPos place(GameTestHelper helper, Block block, boolean shelves) {
        BlockPos pos = helper.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++)
            helper.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 2);
        if (shelves) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            for (int dy = 0; dy <= 1; dy++) helper.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        }
        return pos;
    }
    private static EnchantmentMenu table(GameTestHelper helper, ServerPlayer owner, Block block, boolean shelves) {
        return new EnchantmentMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), place(helper, block, shelves)));
    }
    private static Holder<Enchantment> enchantment(GameTestHelper helper, String id) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id)));
    }
    private static ItemStack book(GameTestHelper helper, String id, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment(helper, id), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }
    private static AnvilMenu anvil(GameTestHelper helper, ServerPlayer owner) {
        BlockPos pos = place(helper, Blocks.ANVIL, false);
        return new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
    }

    @GameTest(template = "empty")
    public static void bothTableTiersCountTwentyFourShelvesAndUseIronArmorThirty(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var emerald = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), helper.absolutePos(SITE)), 24, "Two complete source layers count 24 bookshelves");
        emerald.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
        helper.assertValueEqual(((R196EnchantmentMenuAccess)emerald).mite$experienceCost(2), 4000, "Emerald pricing retains raw50 compressed to40 times100 points");
        var diamond = table(helper, owner, Blocks.ENCHANTING_TABLE, true);
        diamond.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
        helper.assertValueEqual(((R196EnchantmentMenuAccess)diamond).mite$experienceCost(2), 5300, "Ordinary pricing retains raw100 compressed to53 times100 points");
        helper.assertTrue(((R196EnchantmentMenuAccess)diamond).mite$usesSourceEnchanting(), "Actual menu synchronizes source point-cost mode");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookshelfRoutesPassTorchesRejectWaterAndCountAlternateDiagonalPath(GameTestHelper helper) {
        BlockPos pos = place(helper, Blocks.ENCHANTING_TABLE, false);
        var level = helper.getLevel();
        level.setBlock(pos.offset(2, 0, 1), Blocks.BOOKSHELF.defaultBlockState(), 2);
        level.setBlock(pos.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(level, pos), 1, "A diagonal near cell and the alternate cardinal route remain sufficient");
        level.setBlock(pos.offset(0, 0, 1), Blocks.TORCH.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(level, pos), 1, "Non-solid torch transmits source power");
        level.setBlock(pos.offset(0, 0, 1), Blocks.WATER.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(level, pos), 0, "Liquid cannot transmit");
        level.setBlock(pos.offset(2, 1, 0), Blocks.BOOKSHELF.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(level, pos), 1, "Upper shelves count separately");
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(level, pos), 0, "Blocked center excludes the entire upper layer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryBooksHaveNoOffersAndConsumeNoPointsOrLapis(GameTestHelper helper) {
        var owner = owner(helper, 5300);
        owner.increaseScore(173);
        int score = owner.getScore();
        var menu = table(helper, owner, Blocks.ENCHANTING_TABLE, true);
        menu.getSlot(0).set(new ItemStack(Items.BOOK));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        for (int slot = 0; slot < 3; slot++) {
            helper.assertValueEqual(menu.costs[slot], 0, "Ordinary books have no player-table offer");
            helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot), 0, "Ordinary books have no player-table point price");
            helper.assertFalse(menu.clickMenuButton(owner, slot), "A funded player cannot turn an ordinary book into an enchanted book");
        }
        var result = menu.getSlot(0).getItem();
        helper.assertTrue(result.is(Items.BOOK) && result.getCount() == 1, "Player-table rejection preserves the ordinary book");
        helper.assertTrue(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(), "Player tables add no stored enchantment to ordinary books");
        helper.assertValueEqual(owner.totalExperience, 5300, "Rejected book enchanting consumes no XP points");
        helper.assertValueEqual(owner.getScore(), score, "Rejected book enchanting preserves accumulated score");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected book enchanting consumes no lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insufficientPointsCannotUseLapisOrAnInvalidButtonToEnchant(GameTestHelper helper) {
        var owner = owner(helper, 5299);
        var menu = table(helper, owner, Blocks.ENCHANTING_TABLE, true);
        menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE)); menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        helper.assertFalse(menu.clickMenuButton(owner, 2), "5299 points cannot pay5300-point offer");
        helper.assertFalse(menu.clickMenuButton(owner, -1), "Invalid negative button is rejected");
        helper.assertFalse(menu.clickMenuButton(owner, 3), "Out-of-range button is rejected");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_CHESTPLATE), "Rejected payment leaves equipment untouched");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected payment leaves lapis untouched");
        helper.assertValueEqual(owner.totalExperience, 5299, "No partial point deduction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeTablesRejectWaterAndAlreadyEnchantedAppleInputsWithoutPayment(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            for (boolean water : new boolean[] {true, false}) {
                var menu = table(helper, owner, block, true);
                ItemStack input = water ? PotionContents.createItemStack(Items.POTION, Potions.WATER) : new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
                menu.getSlot(0).set(input.copy());
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                for (int slot = 0; slot < 3; slot++) {
                    helper.assertValueEqual(menu.costs[slot], 0, "Native non-enchantable inputs have no offer");
                    helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot), 0, "Rejected inputs have no point quote");
                    helper.assertFalse(menu.clickMenuButton(owner, slot), "Only a plain golden apple has the requested conversion path");
                }
                helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), input), "Rejected conversion preserves its input");
                helper.assertValueEqual(owner.totalExperience, 10000, "Rejected conversion consumes no points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "Rejected conversion consumes no lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledOrModifiedPointPaymentCannotGrantFreeEnchantedEquipment(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        Consumer<PlayerXpEvent.XpChange> cancel = event -> { if (event.getEntity() == owner && event.getAmount() < 0) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, cancel);
        try { helper.assertFalse(menu.clickMenuButton(owner, 2), "Canceled XP transaction cannot publish output"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Canceled XP payment cannot consume lapis");
        Consumer<PlayerXpEvent.XpChange> modify = event -> { if (event.getEntity() == owner && event.getAmount() < 0) event.setAmount(0); };
        NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, modify);
        try { helper.assertFalse(menu.clickMenuButton(owner, 2), "Altered point cost cannot silently grant free output"); }
        finally { NeoForge.EVENT_BUS.unregister(modify); }
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_CHESTPLATE), "Failed transaction preserves input");
        helper.assertValueEqual(owner.totalExperience, 10000, "Failed transaction preserves canonical points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Modified XP payment cannot consume lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothTableTiersRejectMissingOrInsufficientLapisWithoutPartialPayment(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            for (int slot = 0; slot < 3; slot++) {
                var menu = table(helper, owner, block, true);
                menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
                var input = menu.getSlot(0).getItem().copy();
                helper.assertFalse(menu.clickMenuButton(owner, slot), "Every survival offer requires lapis even with enough XP");
                helper.assertValueEqual(owner.totalExperience, 10000, "Missing lapis never consumes source XP");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Missing lapis leaves input unchanged");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, slot));
                helper.assertFalse(menu.clickMenuButton(owner, slot), "One less than selected fee is insufficient");
                helper.assertValueEqual(owner.totalExperience, 10000, "Insufficient lapis never consumes source XP");
                helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Insufficient lapis leaves input unchanged");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), slot, "Rejected offer consumes no lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothTableTiersChargeExactlyOneTwoOrThreeLapisWithoutRerollingOffers(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            for (int slot = 0; slot < 3; slot++) {
                PlayerExperience.setTotal(owner, 10000);
                var menu = table(helper, owner, block, true);
                menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
                int[] offers = menu.costs.clone();
                int pointCost = ((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot);
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Adding lapis cannot reroll source offers");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Changing lapis count cannot reroll source offers");
                menu.getSlot(1).set(ItemStack.EMPTY);
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Removing lapis cannot reroll source offers");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                helper.assertTrue(menu.clickMenuButton(owner, slot), "Sufficient source XP and lapis complete selected offer");
                helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_CHESTPLATE) && menu.getSlot(0).getItem().isEnchanted(),
                        "Successful payment publishes one enchanted equipment item");
                helper.assertValueEqual(owner.totalExperience, 10000 - pointCost, "Payment uses the independent synchronized point quote");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4 - slot - 1, "Selected offers consume1,2,3 lapis respectively");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paymentCallbacksCannotSwapOrShrinkLapisAndStillProduceEnchantment(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        for (boolean swap : new boolean[] {true, false}) {
            var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
            menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            var input = menu.getSlot(0).getItem().copy();
            Consumer<PlayerXpEvent.XpChange> mutate = event -> {
                if (event.getEntity() != owner || event.getAmount() >= 0) return;
                if (swap) menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 3));
                else menu.getSlot(1).getItem().shrink(1);
            };
            NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, mutate);
            try { helper.assertFalse(menu.clickMenuButton(owner, 2), "Reentrant lapis mutation invalidates the payment snapshot"); }
            finally { NeoForge.EVENT_BUS.unregister(mutate); }
            helper.assertValueEqual(owner.totalExperience, 10000, "Invalidated payment does not consume source XP");
            helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Invalidated payment publishes no result");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), swap ? 3 : 2, "Rejected payment neither charges lapis nor overwrites callback changes");
            helper.assertTrue(menu.getSlot(1).getItem().is(swap ? Items.IRON_INGOT : Items.LAPIS_LAZULI), "Rejected payment preserves the callback's item choice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceTableEquipmentHasAtMostThreeAndBooksKeepExactlyOne(GameTestHelper helper) {
        var random = RandomSource.create(196L);
        var registry = helper.getLevel().registryAccess();
        boolean foundMultiple = false;
        for (int run = 0; run < 1024; run++) {
            var book = R196EnchantmentTable.output(registry, new ItemStack(Items.BOOK), 53, random);
            int bookCount = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size();
            helper.assertValueEqual(bookCount, 1, "R196 chooses exactly one selected entry for the final book");
            var selected = R196EnchantmentTable.select(registry, new ItemStack(Items.IRON_SWORD), 200, random);
            helper.assertFalse(selected.isEmpty(), "High-energy equipment has an eligible initial enchantment");
            foundMultiple |= selected.size() > 1;
            helper.assertTrue(selected.size() <= 3, "The remaining-budget loop has the source three-entry ceiling");
            HashSet<Holder<Enchantment>> chosen = new HashSet<>();
            for (var entry : selected) {
                String id = entry.enchantment.unwrapKey().orElseThrow().location().toString();
                helper.assertTrue(VanillaTableEnchanting.primaryItem(new ItemStack(Items.IRON_SWORD), entry.enchantment), "Every candidate supports the native sword table route: " + id);
                helper.assertTrue(entry.level >= 1 && entry.level <= VanillaTableEnchanting.maximumLevel(new ItemStack(Items.IRON_SWORD), entry.enchantment), "Material-specific table level bound respected");
                for (var previous : chosen) helper.assertTrue(VanillaTableEnchanting.compatible(previous, entry.enchantment), "Every pair passes native table compatibility");
                helper.assertTrue(chosen.add(entry.enchantment), "Self duplicates are rejected");
            }
        }
        helper.assertTrue(foundMultiple, "Equipment can spend its remaining budget on compatible additional entries");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBookTransferChargesFixedPointsPreservesDamageAndSupportsCompatibleUpgrades(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = anvil(helper, owner);
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(1234);
        menu.getSlot(0).set(tool); menu.getSlot(1).set(book(helper, "minecraft:efficiency", 4));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "A source book applies to a fresh unenchanted target");
        helper.assertValueEqual(menu.getCost(), 8000, "Applying a book to equipment costs a fixed 8000 XP points");
        helper.assertValueEqual(((io.github.ningmeng123.mite.crafting.AnvilExperienceAccess) menu).mite$experienceCost(),
                8000, "The UI displays the fixed 8000 XP point equipment fee");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        ItemStack taken = menu.getCarried();
        helper.assertTrue(taken.isEnchanted(), "Actual pickup obtains enchanted source item");
        helper.assertValueEqual(taken.getDamageValue(), 1234, "Book transfer preserves damage");
        helper.assertValueEqual(owner.totalExperience, 2000, "The equipment transfer charges exactly 8000 XP points");
        helper.assertValueEqual(taken.getOrDefault(DataComponents.REPAIR_COST, 0), 1, "The first book application records prior-work penalty one");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Whole book is consumed exactly once");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(taken); menu.getSlot(1).set(book(helper, "minecraft:efficiency", 4));
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:efficiency"), menu.getSlot(2).getItem()),
                5, "Equal source levels combine up to the source maximum");
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 1234, "A source upgrade preserves existing wear");
        helper.assertValueEqual(menu.getCost(), 8000, "Upgrading equipment with another book retains the fixed 8000 point price");
        helper.assertValueEqual(((io.github.ningmeng123.mite.crafting.AnvilExperienceAccess) menu).mite$experienceCost(),
                8000, "Prior-work penalty does not inflate the displayed equipment point fee");
        menu.getSlot(1).set(book(helper, "minecraft:mending", 1));
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:efficiency"), menu.getSlot(2).getItem()),
                4, "A native modern book retains the existing source enchantment");
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:mending"), menu.getSlot(2).getItem()),
                1, "A compatible modern book can be added to source-enchanted equipment");
        helper.assertTrue(menu.getCost() > 0, "A modern-only book still uses native anvil costs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBooksMergeWhileWrongTargetsAreRejectedAndModernOnlyTransfersRemain(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = anvil(helper, owner);
        menu.getSlot(0).set(book(helper, "minecraft:efficiency", 2));
        menu.getSlot(1).set(book(helper, "minecraft:efficiency", 2));
        helper.assertTrue(menu.getSlot(2).getItem().is(Items.ENCHANTED_BOOK), "Compatible source books combine into an enchanted book");
        helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem())
                .getLevel(enchantment(helper, "minecraft:efficiency")), 3, "Efficiency II plus II creates stored Efficiency III");
        helper.assertValueEqual(menu.getCost(), 8000, "Combining enchanted books costs a fixed 8000 XP points");
        helper.assertValueEqual(((io.github.ningmeng123.mite.crafting.AnvilExperienceAccess) menu).mite$experienceCost(),
                8000, "The merged-book quote displays the fixed 8000 XP point fee");
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Mining-only source book cannot enchant a sword");
        menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
        menu.getSlot(1).set(book(helper, "minecraft:mending", 1));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "Modern-only mending retains native integration");
        helper.assertTrue(menu.getCost() > 0, "Modern-only book retains modern payment");
        helper.assertFalse(R196EnchantmentTable.sourceRoute(new ItemStack(Items.CROSSBOW)), "Modern crossbow keeps native table pool");
        helper.assertFalse(R196EnchantmentTable.sourceRoute(new ItemStack(Items.TRIDENT)), "Modern trident keeps native table pool");
        helper.assertFalse(R196EnchantmentTable.sourceRoute(new ItemStack(Items.MACE)), "Modern mace keeps native table pool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeSourceEnchantingDoesNotCreateHistoricalExperienceDebt(GameTestHelper helper) {
        var owner = owner(helper, 0); owner.setGameMode(GameType.CREATIVE);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            var menu = table(helper, owner, block, true);
            menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
            helper.assertTrue(menu.clickMenuButton(owner, 2), "Creative bypasses point affordability without lapis for equipment");
            helper.assertTrue(menu.getSlot(0).getItem().isEnchanted(), "Creative still uses the source equipment selection result");
            helper.assertValueEqual(owner.totalExperience, 0, "Modern creative zero-payment adaptation does not incur source debt");
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
            helper.assertFalse(menu.clickMenuButton(owner, 2), "Creative cannot bypass the ordinary-book enchanting restriction");
            menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            helper.assertTrue(menu.clickMenuButton(owner, 2), "Creative can enchant while lapis is present");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Creative mode consumes no lapis");
            helper.assertValueEqual(owner.totalExperience, 0, "Creative mode consumes no source XP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void globalLootSelectionUsesSourcePoolWithoutInfinityAndRetainsModernOnlyBooks(GameTestHelper helper) {
        var holders = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .map(holder -> (Holder<Enchantment>)holder).toList();
        var random = RandomSource.create(196196L);
        for (int draw = 0; draw < 256; draw++) {
            var selected = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.BOW), 100, holders.stream());
            for (var entry : selected) {
                String id = entry.enchantment.unwrapKey().orElseThrow().location().toString();
                helper.assertTrue(R196EnchantmentCatalog.definition(id) != null, "Actual helper only chooses registered R196 source bow enchantments");
                helper.assertFalse(id.equals("minecraft:infinity"), "Unregistered source infinity is not smuggled through modern loot selection");
            }
        }
        var infinity = enchantment(helper, "minecraft:infinity");
        helper.assertTrue(EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.BOW), 30, java.util.stream.Stream.of(infinity)).isEmpty(), "Even an explicit infinity-only random source bow pool is rejected");
        var depth = enchantment(helper, "minecraft:depth_strider");
        var modernBook = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.BOOK), 30, java.util.stream.Stream.of(depth));
        helper.assertFalse(modernBook.isEmpty(), "Explicit modern-only book loot keeps its native enchantment path");
        helper.assertTrue(modernBook.stream().allMatch(entry -> entry.enchantment.equals(depth)), "Modern-only book pool remains constrained by its own tag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedNativeEquipmentUsesSourceMaterialEnchantability(GameTestHelper helper) {
        helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(Items.BOW)), 10, "Native wooden bow uses source wood10 rather than modern bow1");
        helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(Items.FISHING_ROD)), 30, "Native iron-hook rod uses source iron30 rather than modern rod1");
        helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(Items.IRON_BOOTS)), 30, "Retained native iron armor uses source iron30");
        helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(Items.GOLDEN_BOOTS)), 50, "Retained native gold armor uses source gold50");
        helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(Items.LEATHER_BOOTS)), 10, "Retained native leather armor uses source leather10");
        for (var item : java.util.List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS))
            helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(item)), 30, "All native iron and iron-chain slots retain source iron30");
        for (var item : java.util.List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS))
            helper.assertValueEqual(R196EnchantmentTable.enchantability(new ItemStack(item)), 10, "Every retained native leather slot uses source leather10");
        ItemStack flintRod = new ItemStack(io.github.ningmeng123.mite.registry.MiteRangedItems.rod(io.github.ningmeng123.mite.rules.R196RangedRules.Material.FLINT));
        helper.assertValueEqual(R196EnchantmentTable.enchantability(flintRod), 10, "Flint hook still inherits wood10 through the source highest-material rule");
        helper.assertTrue(R196EnchantmentTable.enchantable(flintRod), "Flint hook rod is source-enchantable despite its zero-enchantability hook");
        helper.succeed();
    }
}
