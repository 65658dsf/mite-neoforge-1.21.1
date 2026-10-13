package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Drives the production clock; no test flag bypasses manufacture or its final validation. @author NingMeng123 */
final class TimedCraftingTestSupport {
    private TimedCraftingTestSupport() {}
    static Block flintTable() { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:flint_crafting_table")); }
    static Item flintTableItem() { return flintTable().asItem(); }

    static void craftOne(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu, ClickType click) {
        player.containerMenu = menu;
        helper.assertTrue(menu.getSlot(1).container instanceof CraftingContainer, "The test uses a real crafting grid");
        CraftingContainer grid = (CraftingContainer) menu.getSlot(1).container;
        List<ItemStack> before = new ArrayList<>();
        for (int i = 0; i < grid.getContainerSize(); i++) before.add(grid.getItem(i).copy());
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) inventory.add(player.getInventory().getItem(i).copy());
        ItemStack cursor = menu.getCarried().copy();
        int period = TimedCraftingController.period(player, menu);
        helper.assertTrue(period >= 25, "The independently timed recipe requires a real manufacturing period");
        try {
            menu.clicked(0, 0, click, player);
            for (int tick = 1; tick < period; tick++) TimedCraftingController.tick(player);
            helper.assertValueEqual(TimedCraftingController.progress(player), period - 1, "N-1 production ticks cannot complete the recipe");
            for (int i = 0; i < before.size(); i++) helper.assertTrue(ItemStack.matches(before.get(i), grid.getItem(i)), "N-1 ticks cannot consume ingredients");
            for (int i = 0; i < inventory.size(); i++) helper.assertTrue(ItemStack.matches(inventory.get(i), player.getInventory().getItem(i)), "N-1 ticks cannot publish inventory output");
            helper.assertTrue(ItemStack.matches(cursor, menu.getCarried()), "Manufacturing leaves the cursor unchanged before completion");
            TimedCraftingController.tick(player);
        } finally {
            // Isolate one completed batch, including failure cleanup, without bypassing production validation.
            TimedCraftingController.cancel(player);
        }
    }

    static void craftBatches(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu, int batches) {
        for (int i = 0; i < batches; i++) craftOne(helper, player, menu, i == 0 ? ClickType.PICKUP : ClickType.QUICK_MOVE);
    }
}
