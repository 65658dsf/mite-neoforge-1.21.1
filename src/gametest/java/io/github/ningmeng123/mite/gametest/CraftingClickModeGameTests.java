package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real menu click paths distinguish single and continuous manufacturing. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CraftingClickModeGameTests {
    @GameTest(template = "empty")
    public static void ordinaryClickMakesExactlyOneRecipeBatchInBothMenus(GameTestHelper helper) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(helper, workbench)) {
                fixture.load(3);
                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                fixture.finishBatchAtBoundary(0);
                fixture.assertAmounts(2, 9);
                fixture.tick(fixture.period * 2);
                fixture.assertAmounts(2, 9);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 0,
                        "A completed ordinary click leaves no queued batch");
                helper.assertTrue(fixture.menu.getSlot(0).getItem().is(MiteMaterials.COPPER_NUGGET.get()),
                        "A valid remaining recipe stays visible without automatically starting it");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectingBackpackItemsPreservesActiveCraftingInputsInBothMenus(GameTestHelper helper) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(helper, workbench)) {
                fixture.load(2);
                var inventory = fixture.player.getInventory();
                int slot = fixture.menu.findSlot(inventory, 9).orElseThrow();
                inventory.setItem(9, new ItemStack(Items.COPPER_INGOT));
                inventory.setItem(10, new ItemStack(Items.COPPER_INGOT));
                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                fixture.tick(5);

                fixture.menu.clicked(slot, 0, ClickType.PICKUP, fixture.player);
                fixture.tick(1);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 6,
                        "Picking up a backpack item advances the running batch");
                // Vanilla predicts that the double-click also empties the grid onto the cursor.
                var predictedSlots = new Int2ObjectOpenHashMap<ItemStack>();
                predictedSlots.put(1, ItemStack.EMPTY);
                predictedSlots.put(fixture.menu.findSlot(inventory, 10).orElseThrow(), ItemStack.EMPTY);
                fixture.player.connection.handleContainerClick(new ServerboundContainerClickPacket(fixture.menu.containerId,
                        fixture.menu.getStateId(), slot, 0, ClickType.PICKUP_ALL, new ItemStack(Items.COPPER_INGOT, 4), predictedSlots));
                fixture.tick(1);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 7,
                        "Double-clicking the backpack keeps the running batch");
                helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 2, "Both grid ingredients stay in place");
                fixture.tick(fixture.period - 7);
                helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 1, "Finishing consumes one grid ingredient");
                helper.assertValueEqual(inventory.countItem(MiteMaterials.COPPER_NUGGET.get()), 9,
                        "The full batch arrives in the backpack while the cursor is occupied");
                helper.assertTrue(ItemStack.matches(fixture.menu.getCarried(), new ItemStack(Items.COPPER_INGOT, 2)),
                        "The cursor keeps both collected backpack ingots");

                fixture.menu.clicked(slot, 0, ClickType.PICKUP_ALL, fixture.player);
                helper.assertValueEqual(fixture.menu.getCarried().getCount(), 3,
                        "After the batch, normal double-click collection includes the idle grid");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickRepeatsOnlyAfterEachWholePeriodInBothMenus(GameTestHelper helper) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(helper, workbench)) {
                fixture.load(3);
                fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
                for (int batch = 0; batch < 3; batch++) {
                    fixture.finishBatchAtBoundary(batch);
                    fixture.assertAmounts(2 - batch, 9 * (batch + 1));
                }
                helper.assertTrue(fixture.menu.getSlot(0).getItem().isEmpty(),
                        "Continuous manufacturing stops when the last real input is consumed");
                fixture.tick(fixture.period * 2);
                fixture.assertAmounts(0, 27);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedClicksCannotQueueOrChangeAnActiveBatchInBothMenus(GameTestHelper helper) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(helper, workbench)) {
                fixture.load(3);
                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                fixture.tick(1);
                for (int duplicate = 0; duplicate < 10; duplicate++) {
                    fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                }
                fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 1,
                        "Repeated normal or Shift clicks neither accelerate nor reset an active single batch");
                fixture.finishBatchAtBoundary(0);
                fixture.tick(fixture.period * 2);
                fixture.assertAmounts(2, 9);

                fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
                fixture.tick(1);
                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 1,
                        "A duplicate ordinary click cannot reset an active continuous batch");
                fixture.finishBatchAtBoundary(1);
                fixture.finishBatchAtBoundary(2);
                fixture.assertAmounts(0, 27);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickCancelsContinuousAndNextOrdinaryClickIsSingleInBothMenus(GameTestHelper helper) {
        for (boolean workbench : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(helper, workbench)) {
                fixture.load(4);
                fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
                fixture.finishBatchAtBoundary(0);
                fixture.tick(fixture.period / 2);
                fixture.assertAmounts(3, 9);
                fixture.menu.clicked(0, 1, ClickType.PICKUP, fixture.player);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 0,
                        "The real right-click path clears continuous progress");
                fixture.tick(fixture.period * 2);
                fixture.assertAmounts(3, 9);

                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 0,
                        "An ordinary click after cancellation starts a new full period");
                fixture.finishBatchAtBoundary(1);
                fixture.tick(fixture.period * 2);
                fixture.assertAmounts(2, 18);
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final AbstractContainerMenu menu;
        private final CraftingContainer grid;
        private final BlockPos table;
        private final BlockState original;
        private int initialIngots;
        private int period;

        private Fixture(GameTestHelper helper, boolean workbench) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-click-test"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            if (workbench) {
                helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
                menu = new CraftingMenu(42, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            } else {
                menu = player.inventoryMenu;
            }
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }

        private void load(int ingots) {
            initialIngots = ingots;
            menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT, ingots));
            helper.assertTrue(menu.stillValid(player), "The real inventory or physically placed workbench menu is valid");
            helper.assertTrue(menu.getSlot(0).getItem().is(MiteMaterials.COPPER_NUGGET.get())
                    && menu.getSlot(0).getItem().getCount() == 9, "One recipe batch unpacks one ingot into nine nuggets");
            period = TimedCraftingController.period(player, menu);
            helper.assertTrue(period >= 25, "The test drives a full production manufacturing period");
        }

        private void finishBatchAtBoundary(int completedBatches) {
            int beforeProgress = TimedCraftingController.progress(player);
            tick(period - 1 - beforeProgress);
            helper.assertValueEqual(TimedCraftingController.progress(player), period - 1,
                    "Every started batch reaches N-1 without finishing early");
            assertAmounts(initialIngots - completedBatches, completedBatches * 9);
            tick(1);
            assertAmounts(initialIngots - completedBatches - 1, (completedBatches + 1) * 9);
            helper.assertValueEqual(TimedCraftingController.progress(player), 0,
                    "Completion never carries elapsed ticks into the next batch");
        }

        private void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) TimedCraftingController.tick(player);
        }

        private void assertAmounts(int ingots, int nuggets) {
            helper.assertValueEqual(grid.getItem(0).getCount(), ingots, "Only completed batches consume their one input ingot");
            helper.assertValueEqual(player.getInventory().countItem(MiteMaterials.COPPER_NUGGET.get()), nuggets,
                    "Each completed recipe batch delivers exactly nine nuggets");
            helper.assertValueEqual(grid.getItem(0).getCount() * 9
                    + player.getInventory().countItem(MiteMaterials.COPPER_NUGGET.get()), initialIngots * 9,
                    "Inputs and delivered output conserve the complete starting copper quantity");
            helper.assertTrue(menu.getCarried().isEmpty(), "No duplicate or partial result is placed on the cursor");
            helper.assertValueEqual(player.getInventory().countItem(Items.COPPER_INGOT), 0,
                    "Unconsumed ingredients remain in the actual recipe grid");
            for (int slot = 1; slot < grid.getContainerSize(); slot++) {
                helper.assertTrue(grid.getItem(slot).isEmpty(), "Unused crafting slots stay empty");
            }
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, original);
                channel.finishAndReleaseAll();
            }
        }
    }
}
