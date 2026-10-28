package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilExperienceAccess;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fixed book point payment is rechecked by actual normal, shift and direct quick-move transactions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilEnchantmentExperienceGameTests {
    private AnvilEnchantmentExperienceGameTests() {}

    @GameTest(template = "empty")
    public static void zeroDebtInsufficientExactAndFractionalXpUseTheSamePointFeeForAllPickupPaths(GameTestHelper helper) {
        for (Take take : Take.values()) for (int points : new int[] {-20, 0, 720, 7999, 8000, 10000, 20000})
                try (Fixture f = new Fixture(helper, points, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            f.prepare(input, book(helper, 4));
            helper.assertValueEqual(f.menu.getCost(), 8000, "A command-fresh Unbreaking IV equipment operation quotes 8000 XP");
            helper.assertTrue(experience(f.menu).mite$usesExperiencePoints(), "Source book operations advertise point payment");
            helper.assertValueEqual(experience(f.menu).mite$experienceCost(), 8000, "Equipment book operations cost a fixed 8000 XP");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "An unaffordable operation still has a priced preview");
            helper.assertValueEqual(f.entity().damage(), 0, "Quoting the point fee never consumes anvil durability");
            take.run(f);
            if (points < 8000) {
                helper.assertTrue(f.transferred().isEmpty(), "Any total below 8000 XP cannot take the result through " + take);
                helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), input), "An unaffordable transaction retains the original armor");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "An unaffordable transaction retains the book");
                helper.assertValueEqual(f.owner.totalExperience, points, "An unaffordable transaction preserves exact XP, including debt");
                helper.assertValueEqual(f.entity().damage(), 0, "An unaffordable transaction consumes no durability");
            } else {
                helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "A funded transaction obtains Unbreaking IV through " + take);
                helper.assertValueEqual(f.owner.totalExperience, points - 8000,
                        "The same fixed 8000 XP is charged once at every player level and bar fraction");
                helper.assertValueEqual(f.owner.experienceLevel, ExperienceRules.levelForTotalExperience(points - 8000),
                        "The displayed level is recomputed from the exact remaining point total");
                helper.assertValueEqual(f.entity().damage(), 400, "A successful transaction charges its independent enchantment wear once");
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                        "A successful transaction consumes both ingredients once");
                take.run(f);
                helper.assertValueEqual(f.owner.totalExperience, points - 8000, "A second exhausted-result click cannot charge more XP");
                helper.assertValueEqual(f.entity().damage(), 400, "A second exhausted-result click cannot charge more durability");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void losingXpAfterPreviewBlocksAllPickupPathsAndReplenishingAllowsTheSameMenu(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 8000, GameType.SURVIVAL)) {
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, 4));
            helper.assertTrue(f.menu.getSlot(2).mayPickup(f.owner), "8000 XP initially funds the quoted result");
            PlayerExperience.setTotal(f.owner, 7999);
            take.run(f);
            helper.assertTrue(f.transferred().isEmpty(), "Losing XP after preview blocks " + take);
            helper.assertValueEqual(f.owner.totalExperience, 7999, "A stale affordable preview cannot create XP debt");
            helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "A stale affordable preview cannot consume the book");
            helper.assertValueEqual(f.entity().damage(), 0, "A stale affordable preview cannot consume anvil durability");
            PlayerExperience.setTotal(f.owner, 8000);
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "Replenishing XP enables the same menu and ingredients");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The replenished transaction pays 8000 XP exactly once");
            helper.assertValueEqual(f.entity().damage(), 400, "The replenished transaction adds one wear charge");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeOnlyBooksUseTheSameEightThousandPointFeeForAllPickupPaths(GameTestHelper helper) {
        for (Take take : Take.values()) for (int points : new int[] {7999, 8000, 20000})
                try (Fixture f = new Fixture(helper, points, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            f.prepare(input, book(helper, Enchantments.MENDING, 1));
            helper.assertValueEqual(f.menu.getCost(), 8000, "Modern-only Mending I uses the same fixed equipment quote");
            helper.assertTrue(experience(f.menu).mite$usesExperiencePoints(), "The native book route also advertises point payment");
            helper.assertValueEqual(experience(f.menu).mite$experienceCost(), 8000, "The native screen quotes 8000 XP");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "A native point-priced operation keeps its preview despite costing more than forty");
            take.run(f);
            if (points < 8000) {
                helper.assertTrue(f.transferred().isEmpty(), "The native route blocks insufficient points through " + take);
                helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), input), "An unfunded native transaction preserves equipment");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "An unfunded native transaction preserves the book");
                helper.assertValueEqual(f.owner.totalExperience, points, "An unfunded native transaction spends no points");
                helper.assertValueEqual(f.entity().damage(), 0, "An unfunded native transaction causes no wear");
            } else {
                helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(helper.getLevel().registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), f.transferred()), 1,
                        "The funded native transaction obtains Mending I through " + take);
                helper.assertValueEqual(f.owner.totalExperience, points - 8000, "The native transaction charges 8000 XP exactly once");
                helper.assertValueEqual(f.entity().damage(), 100, "Mending I independently wears the anvil by 100");
                take.run(f);
                helper.assertValueEqual(f.owner.totalExperience, points - 8000, "A second native result click cannot charge twice");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renameWithoutABookRetainsItsNativeLevelPayment(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 650, GameType.SURVIVAL)) {
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), ItemStack.EMPTY);
            f.menu.setItemName("NingMeng123");
            helper.assertFalse(experience(f.menu).mite$usesExperiencePoints(), "A rename without a book does not enter book point payment");
            helper.assertValueEqual(f.menu.getCost(), 1, "A standalone rename retains its native one-level quote");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertValueEqual(f.transferred().get(DataComponents.CUSTOM_NAME), Component.literal("NingMeng123"),
                    "The standalone rename still produces its named equipment");
            helper.assertValueEqual(f.owner.totalExperience, 540, "A standalone rename retains native level payment from level ten to nine");
            helper.assertValueEqual(f.entity().damage(), 0, "A standalone rename does not cause enchantment wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstApplicationAndPriorWorkUpgradeBothChargeEightThousandPoints(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 16000, GameType.SURVIVAL)) {
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, 4));
            take.run(f);
            ItemStack first = f.transferred().copy();
            helper.assertValueEqual(first.getOrDefault(DataComponents.REPAIR_COST, 0), 1, "Initial prior-work penalty zero increases to one");
            helper.assertValueEqual(f.owner.totalExperience, 8000, "The first application spends exactly 8000 of the initial 16000 XP");
            helper.assertValueEqual(f.owner.experienceLevel, 38, "A point payment recomputes the level from the remaining 8000 XP");
            f.clearTransferred();
            f.prepare(first, book(helper, 4));
            helper.assertValueEqual(f.menu.getCost(), 8000, "Prior work does not increase the fixed equipment point fee");
            helper.assertValueEqual(experience(f.menu).mite$experienceCost(), 8000, "An upgrade costs the same 8000 XP as the initial application");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), 3,
                    "A second book operation increases prior-work penalty one to three");
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 5, "The funded prior-work operation obtains source Unbreaking V");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The second operation spends the remaining 8000 XP exactly once");
            helper.assertValueEqual(f.entity().damage(), 900, "Independent wear is 400 for the first result plus 500 for the upgraded result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void priorWorkAndRenameDoNotChangeTheFixedFeeOrRestoreTheOldFortyLevelLimit(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 51500, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 3);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 7);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 8000, "Both input prior-work penalties leave the equipment quote at 8000 XP");
            helper.assertValueEqual(experience(f.menu).mite$experienceCost(), 8000, "The screen uses the same fixed point fee");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), 15,
                    "The result increases the greater input penalty seven to fifteen");
            f.menu.setItemName("NingMeng123");
            helper.assertValueEqual(f.menu.getCost(), 8000, "Renaming during a book operation adds no extra fee");
            helper.assertValueEqual(experience(f.menu).mite$experienceCost(), 8000, "The renamed result still quotes 8000 XP");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().get(DataComponents.CUSTOM_NAME), Component.literal("NingMeng123"),
                    "The fixed quote still permits a concrete renamed result");
            f.menu.setItemName("");
            helper.assertValueEqual(f.menu.getCost(), 8000, "Canceling the rename leaves the fixed fee unchanged");
        }
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 51500, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 100000);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 100000);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 8000, "Even extremely high prior work cannot change the fixed equipment quote");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The old forty-level gate cannot hide a point-priced book operation");
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "A funded survival player takes the result despite high prior work through " + take);
            helper.assertValueEqual(f.owner.totalExperience, 43500, "High-prior-work equipment still charges exactly 8000 XP");
            helper.assertValueEqual(f.entity().damage(), 400, "The successful point-priced result charges the normal enchantment wear once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeDataPacketsSynchronizePositiveBookCostAndRemoteAffordability(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 0, GameType.SURVIVAL)) {
            AnvilMenu client = new AnvilMenu(72, f.owner.getInventory());
            Bridge bridge = new Bridge(f.menu, client);
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, 4));
            client.getSlot(2).set(f.menu.getSlot(2).getItem().copy());
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet -> packet.getId() == 0 && packet.getValue() == 8000),
                    "Established native data index zero carries the positive 8000 XP quote through packet encoding");
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet -> packet.getId() == 1 && packet.getValue() == 3),
                    "Established data index one still identifies the source result and one consumed book");
            helper.assertValueEqual(client.getCost(), 8000, "A NULL-access remote menu receives the authoritative source-book point fee");
            helper.assertTrue(experience(client).mite$usesExperiencePoints(), "Native data packets synchronize point payment to the remote view");
            helper.assertValueEqual(experience(client).mite$experienceCost(), 8000, "The remote screen sees the same 8000 XP quote as the server");
            helper.assertFalse(client.getSlot(2).mayPickup(f.owner), "The synchronized source flag cannot bypass remote zero-XP affordability");
            PlayerExperience.setTotal(f.owner, 7999);
            helper.assertFalse(client.getSlot(2).mayPickup(f.owner), "The synchronized remote view blocks a total one point below the quote");
            PlayerExperience.setTotal(f.owner, 8000);
            helper.assertTrue(client.getSlot(2).mayPickup(f.owner), "The same synchronized remote result becomes clickable with enough XP");
            helper.assertValueEqual(f.entity().damage(), 0, "Remote affordability checks never change the physical anvil");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highPriorWorkBookQuotesRemainEightThousandAndClearWhenReturningToRename(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 10000, GameType.SURVIVAL)) {
            AnvilMenu client = new AnvilMenu(72, f.owner.getInventory());
            new Bridge(f.menu, client);
            // Command-created overlevel entries are retained while a different enchantment is added.
            ItemStack left = book(helper, Enchantments.EFFICIENCY, 255);
            left.set(DataComponents.REPAIR_COST, 100000);
            ItemStack right = book(helper, 1);
            right.set(DataComponents.REPAIR_COST, 100000);
            f.prepare(left.copy(), right);
            client.getSlot(2).set(f.menu.getSlot(2).getItem().copy());
            helper.assertValueEqual(f.menu.getCost(), 8000, "High prior work and retained command levels leave book merging at 8000 XP");
            helper.assertValueEqual(client.getCost(), 8000, "Actual native data packets preserve the fixed book point quote");
            helper.assertValueEqual(experience(client).mite$experienceCost(), 8000, "The remote accessor returns the same fixed point quote");
            PlayerExperience.setTotal(f.owner, 7999);
            helper.assertFalse(client.getSlot(2).mayPickup(f.owner), "The remote book result rejects one point below its quote");
            PlayerExperience.setTotal(f.owner, 8000);
            helper.assertTrue(client.getSlot(2).mayPickup(f.owner), "The remote book result permits its exact point quote");

            f.prepare(left.copy(), book(helper, 2));
            client.getSlot(2).set(f.menu.getSlot(2).getItem().copy());
            helper.assertValueEqual(client.getCost(), 8000, "Changing incoming book levels cannot change the synchronized fixed fee");

            var stored = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
            for (var key : List.of(Enchantments.SHARPNESS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
                    Enchantments.LOOTING, Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY,
                    Enchantments.FEATHER_FALLING, Enchantments.THORNS))
                stored.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), 255);
            ItemStack extended = left.copy();
            extended.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
            f.prepare(extended, book(helper, 2));
            client.getSlot(2).set(f.menu.getSlot(2).getItem().copy());
            helper.assertValueEqual(f.menu.getCost(), 8000, "Ten final types and high retained levels still cost a fixed 8000 XP");
            helper.assertValueEqual(client.getCost(), 8000, "The remote quote stays fixed when the output contains more enchantments");
            helper.assertValueEqual(experience(client).mite$experienceCost(), 8000, "The remote accessor retains the fixed point quote");

            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), ItemStack.EMPTY);
            f.menu.setItemName("NingMeng123");
            client.getSlot(2).set(f.menu.getSlot(2).getItem().copy());
            helper.assertFalse(experience(client).mite$usesExperiencePoints(), "Removing the book synchronizes the return to native rename payment");
            helper.assertValueEqual(client.getCost(), 1, "The previous book point quote cannot replace the new one-level rename quote");
            helper.assertTrue(client.getSlot(2).mayPickup(f.owner), "The restored native rename uses its own affordability check");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroXpCreativePlayersKeepThePointQuoteWithoutXpOrDurabilityCharges(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 0, GameType.CREATIVE)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 31);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 5);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 8000, "Creative sees the same 8000 XP quote as survival");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Creative keeps a valid book operation visible at zero XP");
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "Creative can take the real result through " + take);
            helper.assertValueEqual(f.owner.totalExperience, 0, "Creative book payment cannot create XP debt");
            helper.assertValueEqual(f.entity().damage(), 0, "Creative book payment consumes no anvil durability");
        }
        helper.succeed();
    }

    private static ItemStack book(GameTestHelper helper, int level) {
        return book(helper, Enchantments.UNBREAKING, level);
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(key), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static int unbreaking(GameTestHelper helper, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), stack);
    }

    private static AnvilExperienceAccess experience(AnvilMenu menu) { return (AnvilExperienceAccess)menu; }

    private enum Take {
        PICKUP, SHIFT_CLICK, DIRECT_QUICK_MOVE;
        void run(Fixture f) {
            if (this == DIRECT_QUICK_MOVE) f.menu.quickMoveStack(f.owner, 2);
            else f.menu.clicked(2, 0, this == PICKUP ? ClickType.PICKUP : ClickType.QUICK_MOVE, f.owner);
        }
    }

    private static final class Bridge implements ContainerListener {
        final AnvilMenu client;
        final List<ClientboundContainerSetDataPacket> decoded = new ArrayList<>();
        Bridge(AnvilMenu server, AnvilMenu client) { this.client = client; server.addSlotListener(this); }
        @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}
        @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes,
                        new ClientboundContainerSetDataPacket(menu.containerId, index, value));
                var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);
                decoded.add(packet);
                client.setData(packet.getId(), packet.getValue());
            } finally { bytes.release(); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final Player owner;
        final BlockPos pos;
        final AnvilMenu menu;
        final BlockState original;
        final BlockState below;

        Fixture(GameTestHelper helper, int points, GameType mode) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3);
            owner = helper.makeMockPlayer(mode);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, points);
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }
        MaterialAnvilBlockEntity entity() { return (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos); }
        void prepare(ItemStack input, ItemStack book) {
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.setItemName("");
            menu.getSlot(0).set(input);
            menu.getSlot(1).set(book);
        }
        ItemStack transferred() {
            if (!menu.getCarried().isEmpty()) return menu.getCarried();
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.is(Items.LEATHER_CHESTPLATE)) return stack;
            }
            return ItemStack.EMPTY;
        }
        void clearTransferred() { menu.setCarried(ItemStack.EMPTY); owner.getInventory().clearContent(); }
        @Override public void close() {
            clearTransferred();
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            helper.getLevel().setBlock(pos, original, 3);
            helper.getLevel().setBlock(pos.below(), below, 3);
        }
    }
}
