package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
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
    public static void bothTableTiersCountTwentyFourShelvesAndUseBookThirty(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var emerald = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        helper.assertValueEqual(R196EnchantmentTable.accessibleBookshelves(helper.getLevel(), helper.absolutePos(SITE)), 24, "Two complete source layers count 24 bookshelves");
        emerald.getSlot(0).set(new ItemStack(Items.BOOK));
        helper.assertValueEqual(emerald.costs[2], 40, "Emerald raw50 compresses to40 with book enchantability30");
        var diamond = table(helper, owner, Blocks.ENCHANTING_TABLE, true);
        diamond.getSlot(0).set(new ItemStack(Items.BOOK));
        helper.assertValueEqual(diamond.costs[2], 53, "Diamond raw100 saturates at52.5 rounded53");
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
    public static void actualBookEnchantSpendsFourThousandPointsAndThreeLapis(GameTestHelper helper) {
        var owner = owner(helper, 4000);
        owner.increaseScore(173);
        int score = owner.getScore();
        var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        menu.getSlot(0).set(new ItemStack(Items.BOOK));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        helper.assertTrue(menu.clickMenuButton(owner, 2), "Exactly4000 points and3 lapis can take40-energy offer");
        var result = menu.getSlot(0).getItem();
        helper.assertTrue(result.is(Items.ENCHANTED_BOOK), "Actual native container transforms the book");
        helper.assertValueEqual(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size(), 1, "Table books store exactly one selected enchantment");
        helper.assertValueEqual(owner.totalExperience, 0, "Payment removes4000 points rather than levels");
        helper.assertValueEqual(owner.getScore(), score, "Source enchanting spends XP points without lowering accumulated score");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Third option consumes exactly3 lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insufficientPointsCannotUseLapisOrAnInvalidButtonToEnchant(GameTestHelper helper) {
        var owner = owner(helper, 3999);
        var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        menu.getSlot(0).set(new ItemStack(Items.BOOK)); menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        helper.assertFalse(menu.clickMenuButton(owner, 2), "3999 points cannot pay4000-point offer");
        helper.assertFalse(menu.clickMenuButton(owner, -1), "Invalid negative button is rejected");
        helper.assertFalse(menu.clickMenuButton(owner, 3), "Out-of-range button is rejected");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.BOOK), "Rejected payment leaves book untouched");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Rejected payment leaves lapis untouched");
        helper.assertValueEqual(owner.totalExperience, 3999, "No partial point deduction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedWaterAndAppleConversionsCostTwoHundredPointsAndTheSelectedLapisFee(GameTestHelper helper) {
        var owner = owner(helper, 200);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            for (int slot = 0; slot < 3; slot++) for (boolean water : new boolean[] {true, false}) {
                PlayerExperience.setTotal(owner, 200);
                var menu = table(helper, owner, block, false);
                menu.getSlot(0).set(water ? PotionContents.createItemStack(Items.POTION, Potions.WATER) : new ItemStack(Items.GOLDEN_APPLE));
                for (int energy : menu.costs) helper.assertValueEqual(energy, 2, "Special conversion is fixed at2 energy");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, slot));
                helper.assertFalse(menu.clickMenuButton(owner, slot), "Special conversion still requires the selected option's lapis fee");
                helper.assertValueEqual(owner.totalExperience, 200, "Missing special-conversion lapis cannot consume XP");
                helper.assertTrue(water ? R196EnchantmentTable.water(menu.getSlot(0).getItem()) : menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE), "Missing lapis preserves special input");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                helper.assertTrue(menu.clickMenuButton(owner, slot), "Special conversion uses exact200 points plus selected lapis fee");
                helper.assertTrue(menu.getSlot(0).getItem().is(water ? Items.EXPERIENCE_BOTTLE : Items.ENCHANTED_GOLDEN_APPLE), "Special conversion creates the matching source result");
                helper.assertValueEqual(owner.totalExperience, 0, "Special conversion costs200 points");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4 - slot - 1, "Special conversion consumes exactly selected option plus1 lapis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledOrModifiedPointPaymentCannotGrantAFreeBook(GameTestHelper helper) {
        var owner = owner(helper, 4000);
        var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
        menu.getSlot(0).set(new ItemStack(Items.BOOK));
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
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.BOOK), "Failed transaction preserves input");
        helper.assertValueEqual(owner.totalExperience, 4000, "Failed transaction preserves canonical points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Modified XP payment cannot consume lapis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothTableTiersRejectMissingOrInsufficientLapisWithoutPartialPayment(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        for (Block block : new Block[] {Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()}) {
            for (int slot = 0; slot < 3; slot++) {
                var menu = table(helper, owner, block, true);
                menu.getSlot(0).set(new ItemStack(Items.BOOK));
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
                menu.getSlot(0).set(new ItemStack(Items.BOOK));
                int[] offers = menu.costs.clone();
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Adding lapis cannot reroll source offers");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Changing lapis count cannot reroll source offers");
                menu.getSlot(1).set(ItemStack.EMPTY);
                for (int i = 0; i < 3; i++) helper.assertValueEqual(menu.costs[i], offers[i], "Removing lapis cannot reroll source offers");
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 4));
                helper.assertTrue(menu.clickMenuButton(owner, slot), "Sufficient source XP and lapis complete selected offer");
                helper.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_BOOK), "Successful payment publishes one enchanted book");
                helper.assertValueEqual(owner.totalExperience, 10000 - offers[slot] * 100, "Source XP fee is the unchanged energy times100");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4 - slot - 1, "Selected offers consume1,2,3 lapis respectively");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paymentCallbacksCannotSwapOrShrinkLapisAndStillProduceEnchantment(GameTestHelper helper) {
        var owner = owner(helper, 4000);
        for (boolean swap : new boolean[] {true, false}) {
            var menu = table(helper, owner, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get(), true);
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
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
            helper.assertValueEqual(owner.totalExperience, 4000, "Invalidated payment does not consume source XP");
            helper.assertTrue(ItemStack.matches(input, menu.getSlot(0).getItem()), "Invalidated payment publishes no result");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), swap ? 3 : 2, "Rejected payment neither charges lapis nor overwrites callback changes");
            helper.assertTrue(menu.getSlot(1).getItem().is(swap ? Items.IRON_INGOT : Items.LAPIS_LAZULI), "Rejected payment preserves the callback's item choice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectionGivesBooksOneAndEquipmentAtMostThreeCompatibleSourceEnchantments(GameTestHelper helper) {
        var random = RandomSource.create(196L);
        var registry = helper.getLevel().registryAccess();
        for (int run = 0; run < 1024; run++) {
            var book = R196EnchantmentTable.output(registry, new ItemStack(Items.BOOK), 53, random);
            helper.assertValueEqual(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size(), 1, "Every table book keeps one enchantment");
            var selected = R196EnchantmentTable.select(registry, new ItemStack(Items.IRON_SWORD), 200, random);
            helper.assertTrue(!selected.isEmpty() && selected.size() <= 3, "High-energy equipment draws have1..3 entries");
            HashSet<String> ids = new HashSet<>();
            for (var entry : selected) {
                String id = entry.enchantment.unwrapKey().orElseThrow().location().toString();
                var definition = R196EnchantmentCatalog.definition(id);
                helper.assertTrue(definition != null && definition.supported(new ItemStack(Items.IRON_SWORD)), "Only source-supported candidates are chosen");
                helper.assertTrue(entry.level >= 1 && entry.level <= definition.maxLevel(), "Source level bound respected");
                for (String previous : ids) helper.assertTrue(R196EnchantmentCatalog.compatible(previous, id), "Every pair passes source compatibility");
                helper.assertTrue(ids.add(id), "Self duplicates are rejected");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBookTransferChargesLevelsPreservesDamageAndSupportsCompatibleUpgrades(GameTestHelper helper) {
        var owner = owner(helper, 650);
        var menu = anvil(helper, owner);
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(1234);
        menu.getSlot(0).set(tool); menu.getSlot(1).set(book(helper, "minecraft:efficiency", 4));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "A source book applies to a fresh unenchanted target");
        helper.assertValueEqual(menu.getCost(), 4, "A fresh Efficiency IV book costs four player levels");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        ItemStack taken = menu.getCarried();
        helper.assertTrue(taken.isEnchanted(), "Actual pickup obtains enchanted source item");
        helper.assertValueEqual(taken.getDamageValue(), 1234, "Book transfer preserves damage");
        helper.assertValueEqual(owner.totalExperience, 270, "Charging four levels carries an exact level-ten player to level six");
        helper.assertValueEqual(taken.getOrDefault(DataComponents.REPAIR_COST, 0), 1, "The first book application records prior-work penalty one");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Whole book is consumed exactly once");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(taken); menu.getSlot(1).set(book(helper, "minecraft:efficiency", 4));
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:efficiency"), menu.getSlot(2).getItem()),
                5, "Equal source levels combine up to the source maximum");
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 1234, "A source upgrade preserves existing wear");
        helper.assertValueEqual(menu.getCost(), 6, "The prior-work penalty one plus upgraded Efficiency V costs six levels");
        menu.getSlot(1).set(book(helper, "minecraft:mending", 1));
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:efficiency"), menu.getSlot(2).getItem()),
                4, "A native modern book retains the existing source enchantment");
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, "minecraft:mending"), menu.getSlot(2).getItem()),
                1, "A compatible modern book can be added to source-enchanted equipment");
        helper.assertTrue(menu.getCost() > 0, "A modern-only book still uses native anvil costs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBookBookMergeAndWrongTargetAreRejectedButModernOnlyMergesRemain(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = anvil(helper, owner);
        menu.getSlot(0).set(book(helper, "minecraft:efficiency", 2));
        menu.getSlot(1).set(book(helper, "minecraft:efficiency", 2));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Source enchanted books cannot be combined to raise level");
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
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
            helper.assertTrue(menu.clickMenuButton(owner, 2), "Creative bypasses point affordability without lapis");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_BOOK), "Creative still uses the source selection result");
            helper.assertValueEqual(owner.totalExperience, 0, "Modern creative zero-payment adaptation does not incur source debt");
            menu.getSlot(0).set(new ItemStack(Items.BOOK));
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
