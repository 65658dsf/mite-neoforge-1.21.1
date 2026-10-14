package io.github.ningmeng123.mite.crafting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** Instant modern batches keep their full yield while cursor, inventory and overflow obey source limits. @author NingMeng123 */
public final class StackBatchTransfers {
    private StackBatchTransfers() {}

    public static boolean handles(AbstractContainerMenu menu, net.minecraft.world.entity.player.Player player, int slot, ClickType type) {
        if (slot != 0 || type != ClickType.PICKUP && type != ClickType.THROW
                || !(menu instanceof CraftingMenu || menu instanceof InventoryMenu)
                || !(menu.getSlot(1).container instanceof CraftingContainer grid)
                || TimedCraftingController.isTimed(player, menu)) return false;
        ItemStack preview = menu.getSlot(0).getItem();
        if (preview.isEmpty() || preview.getCount() <= preview.getMaxStackSize()) return false;
        return player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.level())
                .filter(recipe -> recipe.value() instanceof StackBatchShapedRecipe || recipe.value() instanceof StackBatchShapelessRecipe)
                .map(recipe -> ItemStack.matches(preview, recipe.value().assemble(grid.asCraftInput(), player.level().registryAccess())))
                .orElse(false);
    }

    public static void take(ServerPlayer player, AbstractContainerMenu menu, int button, ClickType type) {
        if (player.containerMenu != menu || type == ClickType.PICKUP && button != 0 && button != 1) return;
        ItemStack carried = menu.getCarried();
        ItemStack preview = menu.getSlot(0).getItem();
        if (type == ClickType.THROW && !carried.isEmpty()) return;
        int cursorRoom = carried.isEmpty() ? preview.getMaxStackSize()
                : ItemStack.isSameItemSameComponents(carried, preview) ? Math.max(0, carried.getMaxStackSize() - carried.getCount()) : 0;
        if (type == ClickType.PICKUP && cursorRoom == 0) return;
        // One native onTake supplies container remainders, crafting events and exactly one ingredient batch.
        ItemStack batch = menu.getSlot(0).safeTake(preview.getCount(), Integer.MAX_VALUE, player);
        if (batch.isEmpty()) return;
        if (type == ClickType.PICKUP) {
            int first = Math.min(batch.getCount(), button == 1 ? Math.min(1, cursorRoom) : cursorRoom);
            if (carried.isEmpty()) menu.setCarried(batch.split(first));
            else { carried.grow(first); batch.shrink(first); }
            TimedCraftingController.insertWithoutDiscard(player.getInventory(), batch);
        }
        while (!batch.isEmpty()) player.drop(batch.split(Math.min(batch.getCount(), batch.getMaxStackSize())), false);
        menu.broadcastChanges();
    }
}
