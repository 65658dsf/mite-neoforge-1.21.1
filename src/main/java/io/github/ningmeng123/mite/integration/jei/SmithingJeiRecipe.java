package io.github.ningmeng123.mite.integration.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

public record SmithingJeiRecipe(RecipeHolder<SmithingRecipe> source, List<ItemStack> templates,
        List<ItemStack> bases, List<ItemStack> additions, ItemStack output, int baseTicks) {
    public SmithingJeiRecipe {
        templates = templates.stream().map(ItemStack::copy).toList();
        bases = bases.stream().map(ItemStack::copy).toList();
        additions = additions.stream().map(ItemStack::copy).toList();
        output = output.copy();
    }
}
