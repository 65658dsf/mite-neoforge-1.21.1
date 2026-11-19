package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

public record SmithingJeiRecipe(RecipeHolder<SmithingRecipe> source, List<ItemStack> templates,
        List<ItemStack> bases, List<ItemStack> additions, ItemStack output, float difficulty) {
    public SmithingJeiRecipe {
        templates = templates.stream().map(ItemStack::copy).toList();
        bases = bases.stream().map(ItemStack::copy).toList();
        additions = additions.stream().map(ItemStack::copy).toList();
        output = output.copy();
    }

    /** Average-quality duration for the player's current state. */
    public int durationTicks(int level, boolean clumsy) {
        return TimedCraftingRules.period(difficulty, level, 0F, clumsy);
    }
}
