package io.github.ningmeng123.mite.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** Complete batches remain separate from the limits of their stored inventory stacks. @author NingMeng123 */
public final class BulkMaterialTransfers {
    private BulkMaterialTransfers() {}

    public static boolean isBulkResult(AbstractContainerMenu menu, Player player, ItemStack stack) {
        if (!(menu instanceof InventoryMenu || menu instanceof CraftingMenu) || stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize()
                || !(menu.getSlot(1).container instanceof CraftingContainer grid)) return false;
        return player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.level())
                .filter(holder -> holder.value() instanceof BulkMaterialRecipe && BulkMaterialRecipe.isValidResult(stack)
                        || holder.value() instanceof StackBatchShapedRecipe || holder.value() instanceof StackBatchShapelessRecipe)
                .map(holder -> ItemStack.isSameItemSameComponents(stack, holder.value().getResultItem(player.level().registryAccess())))
                .orElse(false);
    }
}
