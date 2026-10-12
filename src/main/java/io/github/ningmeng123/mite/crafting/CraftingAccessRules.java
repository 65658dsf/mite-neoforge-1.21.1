package io.github.ningmeng123.mite.crafting;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/** Minimum equipment for basic wood processing. @author NingMeng123 */
public final class CraftingAccessRules {
    private CraftingAccessRules() {}

    public static boolean canCraft(CraftingContainer grid, ItemStack output) {
        // Use the physical grid: CraftingInput trims even a workbench's single log to 1x1.
        return output.isEmpty() || !output.is(ItemTags.PLANKS)
                || (grid.getWidth() >= 3 && grid.getHeight() >= 3);
    }
}
