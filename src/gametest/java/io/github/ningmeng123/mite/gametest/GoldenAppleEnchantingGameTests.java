package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real special-input offers, synchronized point prices and atomic source conversion. @author NingMeng123 */
@GameTestHolder("mite_enchanting")
@PrefixGameTestTemplate(false)
public final class GoldenAppleEnchantingGameTests {
    private static final BlockPos SITE = new BlockPos(2, 2, 2);
    private GoldenAppleEnchantingGameTests() {}

    @GameTest(template = "empty")
    public static void everyChoiceConvertsExactlyOneGoldenAppleForTwoHundredPointsAtEitherTable(GameTestHelper h) {
        for (Block block : tables()) for (int layers : new int[]{0, 4}) for (int slot = 0; slot < 3; slot++) for (int lapis : new int[]{0, 1, 4}) {
            var player = owner(h, 200);
            player.getInventory().setItem(8, new ItemStack(Items.GOLDEN_APPLE, 17));
            var menu = menu(h, player, block, layers);
            menu.setData(3, slot * 196 + layers);
            h.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.GOLDEN_APPLE)), "The actual native input slot accepts a golden apple");
            menu.getSlot(0).set(new ItemStack(Items.GOLDEN_APPLE));
            ItemStack lapisBefore = lapis == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, lapis);
            menu.getSlot(1).set(lapisBefore.copy());
            for (int offer = 0; offer < 3; offer++) {
                h.assertValueEqual(access(menu).mite$experienceCost(offer), 200, "All apple choices quote exactly 200 points regardless of shelves or seed");
                h.assertValueEqual(menu.costs[offer], 2, "Source special input uses energy two");
                h.assertValueEqual(menu.enchantClue[offer], -1, "Conversion does not invent an ordinary preview enchantment");
                h.assertValueEqual(menu.levelClue[offer], -1, "Conversion has no ordinary preview rank");
            }
            h.assertTrue(menu.clickMenuButton(player, slot), "The real special-input transaction accepts exactly 200 points regardless of lapis");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_GOLDEN_APPLE), "The actual menu replaces the plain apple with the enchanted item");
            h.assertValueEqual(menu.getSlot(0).getItem().getCount(), 1, "Exactly one input becomes exactly one enchanted apple");
            h.assertValueEqual(player.getInventory().getItem(8).getCount(), 17, "The conversion cannot consume unrelated inventory apples");
            h.assertValueEqual(player.totalExperience, 0, "Exactly 200 experience points are charged");
            h.assertTrue(ItemStack.matches(lapisBefore, menu.getSlot(1).getItem()), "Apple conversion requires and consumes no lapis");
            for (int again = 0; again < 3; again++) h.assertFalse(menu.clickMenuButton(player, again), "The enchanted result cannot be converted repeatedly");
            h.assertValueEqual(player.totalExperience, 0, "Repeated clicks cannot create debt");
            h.assertTrue(ItemStack.matches(lapisBefore, menu.getSlot(1).getItem()), "Repeated clicks cannot consume lapis");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneMissingPointAndInvalidInputsNeverConsumeOrConvert(GameTestHelper h) {
        for (Block block : tables()) for (int slot = 0; slot < 3; slot++) for (int lapis : new int[]{0, 3}) {
            var player = owner(h, 199);
            var menu = menu(h, player, block, 0);
            menu.getSlot(0).set(new ItemStack(Items.GOLDEN_APPLE));
            ItemStack lapisBefore = lapis == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, lapis);
            menu.getSlot(1).set(lapisBefore.copy());
            h.assertFalse(menu.clickMenuButton(player, slot), "199 points cannot fund the fixed 200-point conversion");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE), "A failed point check preserves the plain apple");
            h.assertValueEqual(player.totalExperience, 199, "A failed point check is atomic");
            h.assertTrue(ItemStack.matches(lapisBefore, menu.getSlot(1).getItem()), "A failed point check consumes no lapis");
            PlayerExperience.setTotal(player, 200);
            for (int invalid : new int[]{-1, 3, Integer.MAX_VALUE}) h.assertFalse(menu.clickMenuButton(player, invalid), "Invalid buttons do not convert");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE), "Invalid buttons preserve the plain apple");
            h.assertValueEqual(player.totalExperience, 200, "Invalid buttons consume no points");
            h.assertTrue(ItemStack.matches(lapisBefore, menu.getSlot(1).getItem()), "Invalid buttons consume no lapis");
            for (ItemStack invalid : List.of(new ItemStack(Items.APPLE), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.GOLDEN_APPLE, 2))) {
                menu.getSlot(0).set(invalid.copy());
                h.assertFalse(menu.clickMenuButton(player, slot), "Only one plain golden apple is a legal special input");
                h.assertTrue(ItemStack.matches(invalid, menu.getSlot(0).getItem()), "Invalid input is preserved exactly");
                h.assertValueEqual(player.totalExperience, 200, "Invalid input consumes no points");
                h.assertTrue(ItemStack.matches(lapisBefore, menu.getSlot(1).getItem()), "Invalid input consumes no lapis");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAlteredOrReentrantApplePaymentsCannotPublishAFreeResult(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) {
            var player = owner(h, 200);
            var menu = menu(h, player, Blocks.ENCHANTING_TABLE, 0);
            menu.getSlot(0).set(new ItemStack(Items.GOLDEN_APPLE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            final int change = mode;
            Consumer<PlayerXpEvent.XpChange> mutation = event -> {
                if (event.getEntity() != player || event.getAmount() >= 0) return;
                switch (change) {
                    case 0 -> event.setCanceled(true);
                    case 1 -> event.setAmount(0);
                    case 2 -> menu.getSlot(0).set(new ItemStack(Items.APPLE));
                    default -> menu.getSlot(1).getItem().shrink(1);
                }
            };
            NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, mutation);
            try { h.assertFalse(menu.clickMenuButton(player, 2), "A denied or mutated payment cannot publish output"); }
            finally { NeoForge.EVENT_BUS.unregister(mutation); }
            h.assertValueEqual(player.totalExperience, 200, "Failed payment preserves all experience points");
            h.assertTrue(menu.getSlot(0).getItem().is(mode == 2 ? Items.APPLE : Items.GOLDEN_APPLE), "A rejected payment preserves input or the callback's own replacement");
            h.assertValueEqual(menu.getSlot(1).getItem().getCount(), mode == 3 ? 2 : 3, "A rejected payment never additionally charges or undoes callback lapis changes");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void applePricesAndAbsenceOfOrdinaryPreviewSynchronizeThroughActualMenuPackets(GameTestHelper h) {
        var player = owner(h, 200);
        var server = menu(h, player, Blocks.ENCHANTING_TABLE, 0);
        var client = new EnchantmentMenu(server.containerId, player.getInventory());
        server.addSlotListener(new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) { client.getSlot(slot).set(stack.copy()); }
            @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                var bytes = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes, new ClientboundContainerSetDataPacket(menu.containerId, index, value));
                    var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);
                    client.setData(packet.getId(), packet.getValue());
                } finally { bytes.release(); }
            }
        });
        server.getSlot(0).set(new ItemStack(Items.GOLDEN_APPLE));
        server.broadcastChanges();
        h.assertTrue(access(client).mite$usesSourceEnchanting(), "The client receives source point-payment mode");
        h.assertTrue(client.getSlot(0).getItem().is(Items.GOLDEN_APPLE), "The client receives the actual special input");
        h.assertTrue(client.getSlot(1).getItem().isEmpty(), "The client receives an empty lapis slot");
        for (int slot = 0; slot < 3; slot++) {
            h.assertValueEqual(access(client).mite$experienceCost(slot), 200, "The client receives the exact fixed point price");
            h.assertValueEqual(client.costs[slot], 2, "The client receives the special energy requirement");
            h.assertValueEqual(client.enchantClue[slot], -1, "The client receives no invented preview enchantment");
            h.assertValueEqual(client.levelClue[slot], -1, "The client receives no invented preview rank");
            h.assertTrue(client.clickMenuButton(player, slot), "The synchronized client accepts every apple choice without lapis");
        }
        h.assertValueEqual(player.totalExperience, 200, "Client click prediction never charges experience");
        h.succeed();
    }

    private static R196EnchantmentMenuAccess access(EnchantmentMenu menu) { return (R196EnchantmentMenuAccess)menu; }
    private static List<Block> tables() { return List.of(Blocks.ENCHANTING_TABLE, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()); }
    private static ServerPlayer owner(GameTestHelper h, int points) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "AppleEnchant"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(player, points);
        return player;
    }
    private static EnchantmentMenu menu(GameTestHelper h, ServerPlayer player, Block block, int layers) {
        BlockPos pos = h.absolutePos(SITE);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy < 4; dy++)
            h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        h.getLevel().setBlock(pos, block.defaultBlockState(), 2);
        for (int dy = 0; dy < layers; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && !(Math.abs(dx) == 2 && Math.abs(dz) == 2))
                h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
    }
}
