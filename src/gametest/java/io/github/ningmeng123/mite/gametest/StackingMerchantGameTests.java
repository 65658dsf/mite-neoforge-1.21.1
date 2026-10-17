package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native eighteen-cookie trades retain yield, payment, uses, events and statistics under R196 limits. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StackingMerchantGameTests {
    private StackingMerchantGameTests() {}

    @GameTest(template = "ranged_027")
    public static void nativeFarmerOfferPreviewAndSerializationRetainAllEighteenCookies(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 6)) {
            helper.assertValueEqual(fixture.offer.getCostA().getCount(), 3, "The pinned vanilla farmer offer keeps its actual three-emerald price");
            helper.assertValueEqual(fixture.offer.getResult().getCount(), 18, "The pinned vanilla farmer offer produces eighteen cookies");
            helper.assertValueEqual(fixture.menu.getSlot(2).getItem().getCount(), 18, "Only the merchant result preview preserves the full trade batch");
            helper.assertValueEqual(fixture.menu.getSlot(2).getItem().getMaxStackSize(), 16, "Cookie inventory stacks still obey the source sixteen-item cap");
            var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            MerchantOffer decoded = MerchantOffer.CODEC.parse(ops, MerchantOffer.CODEC.encodeStart(ops, fixture.offer).getOrThrow()).getOrThrow();
            helper.assertValueEqual(decoded.getResult().getCount(), 18, "Merchant offer persistence preserves the complete result");
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                MerchantOffer.STREAM_CODEC.encode(buffer, fixture.offer);
                MerchantOffer received = MerchantOffer.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(received.getResult().getCount(), 18, "Server/client offer synchronization preserves the complete result");
                helper.assertValueEqual(received.getCostA().getCount(), 3, "The native network price is unchanged");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void leftRightHotbarOffhandAndThrowTakeOneFullTradeAndChargeExactlyOnce(GameTestHelper helper) {
        record Action(ClickType type, int button) {}
        for (Action action : List.of(new Action(ClickType.PICKUP, 0), new Action(ClickType.PICKUP, 1),
                new Action(ClickType.SWAP, 0), new Action(ClickType.SWAP, 40), new Action(ClickType.THROW, 0), new Action(ClickType.THROW, 1)))
                try (Fixture fixture = new Fixture(helper, 9)) {
            fixture.menu.clicked(2, action.button(), action.type(), fixture.player);
            fixture.assertTrades(1, 6);
            fixture.assertOutput(18);
            if (action.type() == ClickType.PICKUP) helper.assertValueEqual(fixture.menu.getCarried().getCount(), action.button() == 0 ? 16 : 1,
                    "A trade cursor receives only its legal selected first portion");
            if (action.type() == ClickType.SWAP) helper.assertValueEqual(fixture.player.getInventory().getItem(action.button()).getCount(), 16,
                    "A hotbar/offhand swap stores one legal first stack");
            if (action.type() == ClickType.THROW) helper.assertTrue(fixture.menu.getCarried().isEmpty(), "Throwing the complete offer does not populate the cursor");
            helper.assertValueEqual(fixture.menu.getSlot(2).getItem().getCount(), 18, "Remaining payment permits a complete next offer preview");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void directShiftTakesOneTradeAndNativeShiftLoopPaysForEachFurtherCompleteBatch(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 9)) {
            ItemStack result = fixture.menu.quickMoveStack(fixture.player, 2);
            helper.assertValueEqual(result.getCount(), 18, "A direct quickMoveStack caller receives the full one-transaction marker");
            fixture.assertTrades(1, 6); fixture.assertOutput(18);
            fixture.menu.clicked(2, 0, ClickType.QUICK_MOVE, fixture.player);
            fixture.assertTrades(3, 0); fixture.assertOutput(54);
            helper.assertTrue(fixture.menu.getSlot(2).getItem().isEmpty(), "The native Shift loop stops when the existing payment is exhausted");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void fullInventoriesPreserveCursorOffhandAndOverflowInSurvivalAndCreative(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) for (boolean offhand : List.of(false, true))
                try (Fixture fixture = new Fixture(helper, 6)) {
            fixture.player.setGameMode(mode); fixture.fill(false);
            fixture.menu.clicked(2, offhand ? 40 : 0, offhand ? ClickType.SWAP : ClickType.PICKUP, fixture.player);
            fixture.assertTrades(1, 3); fixture.assertOutput(18);
            helper.assertValueEqual(fixture.droppedCookies(), 2, "A full backpack drops only the two cookies beyond the legal first cursor/offhand stack");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void partialShiftWithOneFreeSlotPreservesOverflowAndStopsBeforeChargingBlockedNextOffer(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) try (Fixture fixture = new Fixture(helper, 9)) {
            fixture.player.setGameMode(mode); fixture.fill(true);
            fixture.menu.clicked(2, 0, ClickType.QUICK_MOVE, fixture.player);
            fixture.assertTrades(1, 6); fixture.assertOutput(18);
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.COOKIE), 16, "The only free slot stores a legal full cookie stack");
            helper.assertValueEqual(fixture.droppedCookies(), 2, "The remainder is preserved without paying for another blocked offer");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void blockedCursorHotbarAndShiftLeavePaymentOfferUsesAndExperienceUntouched(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 6)) {
            fixture.fill(false);
            fixture.menu.clicked(2, 0, ClickType.QUICK_MOVE, fixture.player);
            fixture.assertTrades(0, 6); fixture.assertOutput(0);
            fixture.menu.clicked(2, 0, ClickType.SWAP, fixture.player);
            fixture.assertTrades(0, 6); fixture.assertOutput(0);
            fixture.menu.setCarried(new ItemStack(Items.NETHERITE_SWORD));
            for (ClickType click : List.of(ClickType.PICKUP, ClickType.THROW)) {
                fixture.menu.clicked(2, 0, click, fixture.player);
                fixture.assertTrades(0, 6); fixture.assertOutput(0);
                helper.assertTrue(fixture.menu.getCarried().is(Items.NETHERITE_SWORD), "An incompatible occupied cursor cannot be replaced or discarded by a trade");
            }
            fixture.menu.setCarried(new ItemStack(Items.COOKIE, 16));
            fixture.menu.clicked(2, 0, ClickType.PICKUP, fixture.player);
            fixture.assertTrades(0, 6); fixture.assertOutput(16);
        }
        try (Fixture fixture = new Fixture(helper, 2)) {
            helper.assertTrue(fixture.menu.getSlot(2).getItem().isEmpty(), "Two emeralds cannot preview a three-emerald trade");
            fixture.menu.clicked(2, 0, ClickType.PICKUP, fixture.player);
            fixture.assertTrades(0, 2); fixture.assertOutput(0);
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void paymentAndUnverifiedResultSlotsStillApplyOrdinarySourceLimits(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 6)) {
            fixture.container.setItem(1, new ItemStack(Items.COOKIE, 64));
            helper.assertValueEqual(fixture.container.getItem(1).getCount(), 16, "The batch preview exception does not relax an ordinary payment slot");
            fixture.container.setItem(2, new ItemStack(Items.STONE, 9));
            helper.assertValueEqual(fixture.container.getItem(2).getCount(), 4, "A manually supplied result that does not match an active offer is not a batch exemption");
            fixture.container.setItem(0, new ItemStack(Items.EMERALD, 64));
            helper.assertValueEqual(fixture.container.getItem(0).getCount(), 32, "The primary payment slot keeps the source emerald cap");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void nativePrimaryPriceClampingAndUnclampedSecondaryPriceRemainExplicitAdaptationBoundaries(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 6)) {
            MerchantOffer wheat = VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(1)[0].getOffer(fixture.villager, RandomSource.create(30));
            helper.assertTrue(wheat != null && wheat.getBaseCostA().is(Items.WHEAT), "The independent pinned farmer listing buys wheat");
            helper.assertValueEqual(wheat.getBaseCostA().getCount(), 20, "The native authored wheat price remains twenty");
            helper.assertValueEqual(wheat.getCostA().getCount(), 16, "Vanilla's existing primary-price formula clamps to the new wheat stack cap; this batch fix does not rewrite it");
            MerchantOffer gravel = java.util.Arrays.stream(VillagerTrades.TRADES.get(VillagerProfession.FLETCHER).get(1))
                    .map(listing -> listing.getOffer(fixture.villager, RandomSource.create(30)))
                    .filter(offer -> offer != null && offer.getCostB().is(Items.GRAVEL)).findFirst().orElseThrow();
            helper.assertValueEqual(gravel.getCostB().getCount(), 10, "The pinned secondary gravel price remains ten");
            helper.assertValueEqual(new ItemStack(Items.GRAVEL).getMaxStackSize(), 4, "The source gravel cap is four");
            helper.assertFalse(gravel.satisfiedBy(new ItemStack(Items.EMERALD), new ItemStack(Items.GRAVEL, 4)),
                    "A legal four-gravel payment cannot satisfy the unchanged ten-gravel secondary price; modern trading adaptation is still required");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Villager villager;
        final MerchantOffer offer;
        final MerchantMenu menu;
        final MerchantContainer container;
        final AtomicInteger events = new AtomicInteger();
        final Consumer<TradeWithVillagerEvent> listener;
        final int startingTradeStat;
        final int startingCraftStat;
        final int startingMerchantXp;

        Fixture(GameTestHelper helper, int emeralds) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-trade-stack"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            player.setNoGravity(true); player.setOnGround(true); player.getInventory().clearContent(); PlayerExperience.setTotal(player, 0);
            villager = new Villager(EntityType.VILLAGER, helper.getLevel());
            villager.setNoAi(true); villager.setNoGravity(true); villager.moveTo(player.position().add(1, 0, 0));
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
            helper.getLevel().addFreshEntity(villager);
            // Complete native/source stock initialization before this fixture replaces the entire list
            // with its one authored three-emerald quote. Otherwise getOffers legitimately appends
            // unrelated one-emerald source trades, which the native matcher may preview for two emeralds.
            villager.getOffers();
            offer = java.util.Arrays.stream(VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(3))
                    .map(listing -> listing.getOffer(villager, RandomSource.create(30)))
                    .filter(value -> value != null && value.getResult().is(Items.COOKIE)).findFirst().orElseThrow();
            MerchantOffers offers = new MerchantOffers(); offers.add(offer); villager.setOffers(offers); villager.setTradingPlayer(player);
            villager.overrideXp(0); startingMerchantXp = villager.getVillagerXp();
            menu = new MerchantMenu(89, player.getInventory(), villager); player.containerMenu = menu;
            container = (MerchantContainer) menu.getSlot(0).container;
            startingTradeStat = player.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
            startingCraftStat = player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.COOKIE));
            listener = event -> { if (event.getEntity() == player && event.getAbstractVillager() == villager) events.incrementAndGet(); };
            NeoForge.EVENT_BUS.addListener(listener);
            container.setItem(0, new ItemStack(Items.EMERALD, emeralds));
        }

        void assertTrades(int count, int remainingEmeralds) {
            helper.assertValueEqual(container.getItem(0).getCount(), remainingEmeralds, "Each completed offer charges three emeralds exactly once");
            helper.assertValueEqual(offer.getUses(), count, "Each completed offer consumes exactly one use");
            helper.assertValueEqual(player.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER)) - startingTradeStat, count,
                    "Each completed offer awards exactly one native trade statistic");
            helper.assertValueEqual(player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.COOKIE)) - startingCraftStat, count * 18,
                    "The native item statistic records every cookie in every paid batch");
            helper.assertValueEqual(villager.getVillagerXp() - startingMerchantXp, count * offer.getXp(),
                    "Merchant experience is awarded once per completed offer");
            helper.assertValueEqual(events.get(), count, "The native NeoForge trade event is retained once per completed offer");
        }

        void fill(boolean leaveLastFree) {
            for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                player.getInventory().setItem(slot, leaveLastFree && slot == 35 ? ItemStack.EMPTY : new ItemStack(Items.NETHERITE_SWORD));
        }

        int droppedCookies() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), entity -> entity.getItem().is(Items.COOKIE))
                    .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        void assertOutput(int expected) {
            int total = player.getInventory().countItem(Items.COOKIE);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(Items.COOKIE)) helper.assertTrue(stack.getCount() <= 16, "Every inventory/offhand output stack fits the source cookie cap");
            }
            ItemStack carried = menu.getCarried();
            if (carried.is(Items.COOKIE)) { total += carried.getCount(); helper.assertTrue(carried.getCount() <= 16, "The trade cursor fits the source cookie cap"); }
            for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), entity -> entity.getItem().is(Items.COOKIE))) {
                total += drop.getItem().getCount(); helper.assertTrue(drop.getItem().getCount() <= 16, "Every overflow/throw entity fits the source cookie cap");
            }
            helper.assertValueEqual(total, expected, "All paid trade output is conserved across inventory, cursor, offhand and ground");
        }

        @Override public void close() {
            try {
                NeoForge.EVENT_BUS.unregister(listener); menu.setCarried(ItemStack.EMPTY); container.clearContent(); player.getInventory().clearContent();
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), entity -> entity.getItem().is(Items.COOKIE)).forEach(ItemEntity::discard);
                villager.setTradingPlayer(null); villager.discard(); player.containerMenu = player.inventoryMenu; player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
