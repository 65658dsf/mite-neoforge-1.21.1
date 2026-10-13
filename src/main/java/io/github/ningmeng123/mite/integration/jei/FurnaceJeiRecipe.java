package io.github.ningmeng123.mite.integration.jei;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One real processing batch; input stacks are alternatives for the same single slot. @author NingMeng123 */
public record FurnaceJeiRecipe(String furnaceId, List<ItemStack> inputs, ItemStack output,
        List<ItemStack> fuels, int heat, int cycleTicks, ResourceLocation sourceRecipe) {
    public FurnaceJeiRecipe {
        inputs = inputs.stream().map(ItemStack::copy).toList();
        fuels = fuels.stream().map(ItemStack::copy).toList();
        output = output.copy();
    }
}
