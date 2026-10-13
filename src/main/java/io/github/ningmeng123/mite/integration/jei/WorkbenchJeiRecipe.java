package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Read-only display data with no JEI or client linkage. @author NingMeng123 */
public record WorkbenchJeiRecipe(String workstationId, RecipeHolder<CraftingRecipe> source,
        int width, int height, List<List<ItemStack>> inputs, ItemStack output,
        TimedCraftingRecipes.Rule rule, float requiredCapacity, boolean shapeless) {
    public WorkbenchJeiRecipe {
        inputs = inputs.stream().map(slot -> slot.stream().map(ItemStack::copy).toList()).toList();
        output = output.copy();
    }

    /** Level zero, default quality, no active curse; actual player manufacturing can take a different time. */
    public int baselineTicks() {
        if (rule == null) return 0;
        float capacity = WorkbenchJeiRecipes.profiles().get(workstationId).capacity();
        return TimedCraftingRules.period(rule.difficulty(), 0,
                TimedCraftingRules.benchModifier(capacity, rule.requiredBench() != null), false);
    }

    public List<ItemStack> remainders() {
        CraftingInput example = CraftingInput.of(width, height,
                inputs.stream().map(slot -> slot.isEmpty() ? ItemStack.EMPTY : slot.getFirst().copy()).toList());
        return source.value().getRemainingItems(example).stream().filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy).toList();
    }
}
