package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source-book level payment is rechecked by actual normal, shift and direct quick-move transactions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilEnchantmentExperienceGameTests {
    private AnvilEnchantmentExperienceGameTests() {}

    @GameTest(template = "empty")
    public static void zeroDebtInsufficientExactAndFractionalXpUseTheSameLevelFeeForAllPickupPaths(GameTestHelper helper) {
        for (Take take : Take.values()) for (int points : new int[] {-20, 0, 90, 140, 720})
                try (Fixture f = new Fixture(helper, points, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            f.prepare(input, book(helper, 4));
            helper.assertValueEqual(f.menu.getCost(), 4, "A command-fresh Unbreaking IV book quotes four levels");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "An unaffordable operation still has a priced preview");
            helper.assertValueEqual(f.entity().damage(), 0, "Quoting the level fee never consumes anvil durability");
            take.run(f);
            if (points < 140) {
                helper.assertTrue(f.transferred().isEmpty(), "Zero, debt and three-level players cannot take the result through " + take);
                helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), input), "An unaffordable transaction retains the original armor");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "An unaffordable transaction retains the book");
                helper.assertValueEqual(f.owner.totalExperience, points, "An unaffordable transaction preserves exact XP, including debt");
                helper.assertValueEqual(f.entity().damage(), 0, "An unaffordable transaction consumes no durability");
            } else {
                helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "A funded transaction obtains Unbreaking IV through " + take);
                helper.assertValueEqual(f.owner.totalExperience, points == 140 ? 0 : 316,
                        "Four levels are charged once, carrying the R196 bar fraction to the destination level");
                helper.assertValueEqual(f.owner.experienceLevel, points == 140 ? 0 : 6, "Payment spends levels rather than four XP points");
                helper.assertValueEqual(f.entity().damage(), 400, "A successful transaction charges its independent enchantment wear once");
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                        "A successful transaction consumes both ingredients once");
                take.run(f);
                helper.assertValueEqual(f.owner.totalExperience, points == 140 ? 0 : 316, "A second exhausted-result click cannot charge more XP");
                helper.assertValueEqual(f.entity().damage(), 400, "A second exhausted-result click cannot charge more durability");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void losingXpAfterPreviewBlocksAllPickupPathsAndReplenishingAllowsTheSameMenu(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 140, GameType.SURVIVAL)) {
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, 4));
            helper.assertTrue(f.menu.getSlot(2).mayPickup(f.owner), "Four levels initially fund the quoted result");
            PlayerExperience.setTotal(f.owner, 90);
            take.run(f);
            helper.assertTrue(f.transferred().isEmpty(), "Losing XP after preview blocks " + take);
            helper.assertValueEqual(f.owner.totalExperience, 90, "A stale affordable preview cannot create XP debt");
            helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "A stale affordable preview cannot consume the book");
            helper.assertValueEqual(f.entity().damage(), 0, "A stale affordable preview cannot consume anvil durability");
            PlayerExperience.setTotal(f.owner, 140);
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "Replenishing XP enables the same menu and ingredients");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The replenished transaction pays four levels exactly once");
            helper.assertValueEqual(f.entity().damage(), 400, "The replenished transaction adds one wear charge");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstApplicationRecordsPriorWorkAndTheNextUpgradeChargesSixLevels(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 650, GameType.SURVIVAL)) {
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, 4));
            take.run(f);
            ItemStack first = f.transferred().copy();
            helper.assertValueEqual(first.getOrDefault(DataComponents.REPAIR_COST, 0), 1, "Initial prior-work penalty zero increases to one");
            helper.assertValueEqual(f.owner.experienceLevel, 6, "The first application spends four of ten levels");
            f.clearTransferred();
            f.prepare(first, book(helper, 4));
            helper.assertValueEqual(f.menu.getCost(), 6, "Prior-work penalty one plus the resulting Unbreaking V costs six levels");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), 3,
                    "A second book operation increases prior-work penalty one to three");
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 5, "The funded prior-work operation obtains source Unbreaking V");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The second operation spends the six remaining levels exactly once");
            helper.assertValueEqual(f.entity().damage(), 900, "Independent wear is 400 for the first result plus 500 for the upgraded result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothPriorWorkPenaltiesAndActualRenameContributeToTheQuoteAndFortyIsTooExpensive(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 51500, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 3);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 7);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 14, "Both input prior-work penalties plus Unbreaking IV cost 3+7+4 levels");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), 15,
                    "The result increases the greater input penalty seven to fifteen");
            f.menu.setItemName("NingMeng123");
            helper.assertValueEqual(f.menu.getCost(), 15, "Actually renaming the book result adds one level");
            helper.assertValueEqual(f.menu.getSlot(2).getItem().get(DataComponents.CUSTOM_NAME), Component.literal("NingMeng123"),
                    "The additional rename fee has a concrete renamed result");
            f.menu.setItemName("");
            helper.assertValueEqual(f.menu.getCost(), 14, "Canceling the rename removes its one-level fee");
        }
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 51500, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 31);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 4);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 39, "A 39-level source operation remains visible");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The survival limit starts at forty levels");
            book.set(DataComponents.REPAIR_COST, 5);
            f.menu.getSlot(1).set(book);
            helper.assertValueEqual(f.menu.getCost(), 40, "The too-expensive quote includes both penalties and the enchantment level");
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "A survival result costing forty levels is hidden");
            take.run(f);
            helper.assertTrue(f.transferred().isEmpty(), "A high-XP survival player cannot bypass the forty-level limit through " + take);
            helper.assertValueEqual(f.owner.totalExperience, 51500, "The hidden too-expensive result charges no XP");
            helper.assertValueEqual(f.entity().damage(), 0, "The hidden too-expensive result charges no durability");
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
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet -> packet.getId() == 0 && packet.getValue() == 4),
                    "Established native data index zero carries the positive four-level quote through packet encoding");
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet -> packet.getId() == 1 && packet.getValue() == 3),
                    "Established data index one still identifies the source result and one consumed book");
            helper.assertValueEqual(client.getCost(), 4, "A NULL-access remote menu receives the authoritative source-book fee");
            helper.assertFalse(client.getSlot(2).mayPickup(f.owner), "The synchronized source flag cannot bypass remote zero-XP affordability");
            PlayerExperience.setTotal(f.owner, 140);
            helper.assertTrue(client.getSlot(2).mayPickup(f.owner), "The same synchronized remote result becomes clickable with enough XP");
            helper.assertValueEqual(f.entity().damage(), 0, "Remote affordability checks never change the physical anvil");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroXpCreativePlayersBypassTheFortyLevelLimitWithoutXpOrDurabilityCharges(GameTestHelper helper) {
        for (Take take : Take.values()) try (Fixture f = new Fixture(helper, 0, GameType.CREATIVE)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            input.set(DataComponents.REPAIR_COST, 31);
            ItemStack book = book(helper, 4);
            book.set(DataComponents.REPAIR_COST, 5);
            f.prepare(input, book);
            helper.assertValueEqual(f.menu.getCost(), 40, "Creative sees the actual forty-level quote");
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Creative keeps a too-expensive survival result visible");
            take.run(f);
            helper.assertValueEqual(unbreaking(helper, f.transferred()), 4, "Creative can take the real result through " + take);
            helper.assertValueEqual(f.owner.totalExperience, 0, "Creative book payment cannot create XP debt");
            helper.assertValueEqual(f.entity().damage(), 0, "Creative book payment consumes no anvil durability");
        }
        helper.succeed();
    }

    private static ItemStack book(GameTestHelper helper, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static int unbreaking(GameTestHelper helper, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), stack);
    }

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
