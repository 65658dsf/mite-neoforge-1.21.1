package io.github.ningmeng123.mite.integration.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Independent repair example containing copies of the real rule's inputs and output. @author NingMeng123 */
public record AnvilJeiRecipe(String anvilId, ResourceLocation itemId, Kind kind,
        ItemStack left, ItemStack right, ItemStack output, int consumed, int anvilWear, float requiredCapacity) {
    public enum Kind { MATERIAL, COMBINATION }

    public AnvilJeiRecipe {
        left = left.copy();
        right = right.copy();
        output = output.copy();
    }

    @Override public ItemStack left() { return left.copy(); }
    @Override public ItemStack right() { return right.copy(); }
    @Override public ItemStack output() { return output.copy(); }
}
