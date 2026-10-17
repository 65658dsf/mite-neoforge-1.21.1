package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilDurabilityAccess;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
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
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual book transactions price all final enchantments, including the additive surcharge per extra type. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilEnchantmentWearGameTests {
    private static final List<Case> CASES = List.of(
            new Case(Items.LEATHER_CHESTPLATE, Enchantments.UNBREAKING, 4, 400, true),
            new Case(Items.LEATHER_CHESTPLATE, Enchantments.MENDING, 1, 100, false),
            new Case(Items.CROSSBOW, Enchantments.QUICK_CHARGE, 3, 300, false));

    private AnvilEnchantmentWearGameTests() {}

    @GameTest(template = "empty")
    public static void allAnvilsChargeSourceAndNativeBooksOnceForPickupAndShiftClick(GameTestHelper helper) {
        List<Block> anvils = new ArrayList<>(List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL));
        MiteFunctionalBlocks.ANVILS.values().forEach(anvil -> anvils.add(anvil.get()));
        for (Block anvil : anvils) for (Case c : CASES)
                for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, anvil, GameType.SURVIVAL)) {
            int before = f.entity().damage();
            f.prepare(new ItemStack(c.item), book(helper, new Entry(c.enchantment, c.level)));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "A valid book produces a result on " + anvil + ": " + c);
            f.menu.createResult();
            f.menu.broadcastChanges();
            helper.assertValueEqual(f.entity().damage(), before, "Repeated preview and synchronization consume no anvil durability");
            helper.assertTrue(c.source ? f.menu.getCost() == 4 : f.menu.getCost() > 0,
                    "Durability charging retains positive source and native level fees");
            f.menu.clicked(2, 0, click, f.owner);
            ItemStack result = f.result(click, c.item);
            helper.assertValueEqual(level(helper, result, c.enchantment), c.level, "The real transaction applies the requested enchantment");
            helper.assertValueEqual(f.entity().damage(), before + c.wear, "A one-type result charges 100 durability per final level exactly once, including native shift-click");
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                    "An actual transaction consumes its input item and book");
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertValueEqual(f.entity().damage(), before + c.wear, "Clicking the exhausted result again charges no additional durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalEnchantmentTotalsIncludeExistingLevelsAndAddTenPercentPerExtraType(GameTestHelper helper) {
        for (boolean nativeEquipment : new boolean[] {false, true})
                for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL, GameType.SURVIVAL)) {
            Item item = nativeEquipment ? Items.CROSSBOW : Items.LEATHER_LEGGINGS;
            ItemStack input = new ItemStack(item);
            ResourceKey<Enchantment> upgraded = nativeEquipment ? Enchantments.QUICK_CHARGE : Enchantments.UNBREAKING;
            int currentLevel = nativeEquipment ? 2 : 4;
            enchant(helper, input, upgraded, currentLevel);
            if (!nativeEquipment) enchant(helper, input, Enchantments.PROTECTION, 3);
            List<Entry> entries = new ArrayList<>(List.of(new Entry(upgraded, currentLevel),
                    new Entry(Enchantments.MENDING, 1), new Entry(Enchantments.EFFICIENCY, 4)));
            if (!nativeEquipment) entries.add(new Entry(Enchantments.PROTECTION, 2));
            f.prepare(input, book(helper, entries.toArray(Entry[]::new)));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "A mixed book retains its applicable enchantments");
            helper.assertValueEqual(f.entity().damage(), 0, "A mixed-book preview has no wear");
            f.menu.clicked(2, 0, click, f.owner);
            ItemStack result = f.result(click, item);
            helper.assertValueEqual(level(helper, result, upgraded), currentLevel + 1, "Matching levels upgrade once to the resulting level");
            helper.assertValueEqual(level(helper, result, Enchantments.MENDING), 1, "A compatible new modern enchantment is added");
            helper.assertValueEqual(level(helper, result, Enchantments.EFFICIENCY), 0, "An unsupported book entry is not applied or charged");
            if (!nativeEquipment) helper.assertValueEqual(level(helper, result, Enchantments.PROTECTION), 3,
                    "A weaker incoming Protection entry leaves the existing level unchanged");
            helper.assertValueEqual(f.entity().damage(), nativeEquipment ? 440 : 1080,
                    nativeEquipment ? "Final Quick Charge III plus Mending I has four levels and two types, costing 440 durability"
                            : "Final Unbreaking V, Protection III and Mending I has nine levels and three types, costing 1080 durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradingOneOfFourExistingTypesCountsThatTypeOnceAndUsesThirtyPercentSurcharge(GameTestHelper helper) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL, GameType.SURVIVAL)) {
            ItemStack input = new ItemStack(Items.LEATHER_CHESTPLATE);
            enchant(helper, input, Enchantments.PROTECTION, 3);
            enchant(helper, input, Enchantments.THORNS, 3);
            enchant(helper, input, Enchantments.MENDING, 1);
            enchant(helper, input, Enchantments.UNBREAKING, 4);
            f.prepare(input, book(helper, new Entry(Enchantments.UNBREAKING, 4)));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "An existing compatible four-type item can upgrade Unbreaking IV to V");
            helper.assertValueEqual(f.entity().damage(), 0, "Calculating a four-type surcharge has no preview wear");
            f.menu.clicked(2, 0, click, f.owner);
            ItemStack result = f.result(click, Items.LEATHER_CHESTPLATE);
            helper.assertValueEqual(level(helper, result, Enchantments.UNBREAKING), 5, "Only Unbreaking is upgraded to V");
            helper.assertValueEqual(level(helper, result, Enchantments.PROTECTION), 3, "Existing Protection III contributes to the final level sum");
            helper.assertValueEqual(level(helper, result, Enchantments.THORNS), 3, "Existing Thorns III contributes to the final level sum");
            helper.assertValueEqual(level(helper, result, Enchantments.MENDING), 1, "Existing Mending I contributes to the final level sum");
            helper.assertValueEqual(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size(), 4,
                    "Upgrading an existing enchantment does not add a duplicate enchantment type");
            helper.assertValueEqual(f.entity().damage(), 1560,
                    "Twelve final levels with four distinct types cost 1200 plus an additive thirty percent surcharge");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeSourceAndNativeBookOperationsConsumeNoAnvilDurability(GameTestHelper helper) {
        for (Case c : CASES) for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, MiteFunctionalBlocks.ANVILS.get("adamantium").get(), GameType.CREATIVE)) {
            f.entity().addDamage(12345);
            f.prepare(new ItemStack(c.item), book(helper, new Entry(c.enchantment, c.level)));
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertValueEqual(level(helper, f.result(click, c.item), c.enchantment), c.level,
                    "Creative book transactions still produce the enchanted equipment");
            helper.assertValueEqual(f.entity().damage(), 12345, "Creative source and native books preserve exact existing anvil wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previewsUnaffordableRejectedAndUnchangedBooksDoNotWearTheAnvil(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.ANVIL, GameType.SURVIVAL)) {
            PlayerExperience.setTotal(f.owner, 0);
            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, new Entry(Enchantments.MENDING, 1)));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The unaffordable native Mending preview exists");
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
            helper.assertValueEqual(f.entity().damage(), 0, "A failed native shift-click at zero XP consumes no durability");
            helper.assertFalse(f.menu.getSlot(0).getItem().isEmpty() || f.menu.getSlot(1).getItem().isEmpty(),
                    "An unaffordable native transaction retains both inputs");

            f.prepare(new ItemStack(Items.LEATHER_CHESTPLATE), book(helper, new Entry(Enchantments.EFFICIENCY, 4)));
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Wrong-target source enchantment is rejected");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertValueEqual(f.entity().damage(), 0, "A rejected source transaction has zero wear");

            ItemStack capped = new ItemStack(Items.LEATHER_CHESTPLATE);
            enchant(helper, capped, Enchantments.UNBREAKING, 5);
            f.prepare(capped, book(helper, new Entry(Enchantments.UNBREAKING, 5)));
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Source maximum level does not produce a no-change result");
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
            helper.assertValueEqual(f.entity().damage(), 0, "An unchanged source enchantment has zero wear");

            PlayerExperience.setTotal(f.owner, 10000);
            ItemStack nativeCapped = new ItemStack(Items.CROSSBOW);
            enchant(helper, nativeCapped, Enchantments.MENDING, 1);
            f.prepare(nativeCapped, book(helper, new Entry(Enchantments.MENDING, 1)));
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
            helper.assertValueEqual(f.entity().damage(), 0, "An unchanged native enchantment has zero wear even when vanilla permits taking it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceAndNativeBookWearChangesIronStagesAndBreakageWithNativePacketSynchronization(GameTestHelper helper) {
        for (Case c : CASES.subList(0, 2)) for (int threshold : new int[] {198400, 317440, 396800})
                for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL, GameType.SURVIVAL)) {
            int start = threshold - c.wear;
            f.entity().addDamage(start);
            MaterialAnvilBlockEntity original = f.entity();
            AnvilMenu client = new AnvilMenu(72, f.owner.getInventory());
            new Bridge(f.menu, client);
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(), 396800 - start,
                    "Native packet encoding synchronizes the existing wear before the book transaction");
            f.prepare(new ItemStack(c.item), book(helper, new Entry(c.enchantment, c.level)));
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertValueEqual(level(helper, f.result(click, c.item), c.enchantment), c.level,
                    "The book transaction succeeds even when its wear changes or destroys the anvil");
            f.menu.broadcastChanges();
            if (threshold == 396800) {
                helper.assertTrue(helper.getLevel().getBlockState(f.pos).isAir(), "Book wear reaching zero remaining durability removes the physical anvil");
                helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(), 0, "The server immediately sees the removed anvil");
                helper.assertValueEqual(view(client).mite$anvilMaximumDurability(), 0, "A native packet clears the remote maximum after book-induced breakage");
                helper.assertValueEqual(view(client).mite$anvilRemainingDurability(), 0, "No remote durability survives book-induced breakage");
            } else {
                Block expected = threshold == 198400 ? Blocks.CHIPPED_ANVIL : Blocks.DAMAGED_ANVIL;
                helper.assertTrue(helper.getLevel().getBlockState(f.pos).is(expected), "Book wear deterministically changes the native iron damage stage");
                helper.assertTrue(f.entity() != original, "The native iron stage change replaces the physical block entity");
                helper.assertValueEqual(f.entity().damage(), threshold, "The replacement entity retains the exact accumulated book wear");
                helper.assertValueEqual(view(f.menu).mite$anvilRemainingDurability(), 396800 - threshold,
                        "The server reads exact durability from the current replacement entity");
                helper.assertValueEqual(view(client).mite$anvilRemainingDurability(), 396800 - threshold,
                        "The existing remote menu receives exact replacement-entity wear through native packet encoding");
            }
        }
        helper.succeed();
    }

    private static void enchant(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key, int value) {
        stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), value);
    }

    private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), stack);
    }

    private static ItemStack book(GameTestHelper helper, Entry... entries) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Entry entry : entries) enchantments.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(entry.enchantment), entry.level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static AnvilDurabilityAccess view(AnvilMenu menu) { return (AnvilDurabilityAccess)menu; }
    private record Case(Item item, ResourceKey<Enchantment> enchantment, int level, int wear, boolean source) {}
    private record Entry(ResourceKey<Enchantment> enchantment, int level) {}

    private static final class Bridge implements ContainerListener {
        final AnvilMenu client;
        Bridge(AnvilMenu server, AnvilMenu client) { this.client = client; server.addSlotListener(this); }
        @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}
        @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes,
                        new ClientboundContainerSetDataPacket(menu.containerId, index, value));
                var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);
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

        Fixture(GameTestHelper helper, Block anvil, GameType mode) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, anvil.defaultBlockState(), 3);
            owner = helper.makeMockPlayer(mode);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, 10000);
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }

        MaterialAnvilBlockEntity entity() { return (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos); }
        void prepare(ItemStack first, ItemStack second) {
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.getSlot(0).set(first);
            menu.getSlot(1).set(second);
        }
        ItemStack result(ClickType click, Item item) {
            if (click == ClickType.PICKUP) return menu.getCarried();
            int count = 0;
            ItemStack found = ItemStack.EMPTY;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.is(item)) { count += stack.getCount(); found = stack; }
            }
            helper.assertValueEqual(count, 1, "A real shift-click produces exactly one enchanted result");
            return found;
        }
        @Override public void close() {
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            owner.getInventory().clearContent();
            helper.getLevel().setBlock(pos, original, 3);
            helper.getLevel().setBlock(pos.below(), below, 3);
        }
    }
}
