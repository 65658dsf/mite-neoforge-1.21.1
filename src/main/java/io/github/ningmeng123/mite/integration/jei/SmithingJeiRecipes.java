package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.SmithingRecipeIngredients;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

public final class SmithingJeiRecipes {
    public record Index(List<SmithingJeiRecipe> recipes, List<RecipeHolder<SmithingRecipe>> originals) {
        public Index {
            recipes = List.copyOf(recipes);
            originals = List.copyOf(originals);
        }
    }

    private SmithingJeiRecipes() {}

    public static Index index(Level level) {
        List<SmithingJeiRecipe> pages = new ArrayList<>();
        List<RecipeHolder<SmithingRecipe>> originals = new ArrayList<>();
        ItemStack template = new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get());
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            if (!(holder.value() instanceof SmithingRecipeIngredients ingredients)
                    || !holder.value().isTemplateIngredient(template)) continue;
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) continue;
            int required = MaterialUpgradeRequirements.ingots(output);
            var templates = Arrays.stream(ingredients.mite$templateIngredient().getItems()).map(ItemStack::copy).toList();
            var bases = Arrays.stream(ingredients.mite$baseIngredient().getItems()).map(ItemStack::copy).toList();
            var additions = Arrays.stream(ingredients.mite$additionIngredient().getItems())
                    .map(stack -> stack.copyWithCount(required)).toList();
            if (templates.isEmpty() || bases.isEmpty() || additions.isEmpty()) continue;
            int ticks = TimedCraftingRules.period(MaterialUpgradeRequirements.difficulty(output), 0, 0F, false);
            pages.add(new SmithingJeiRecipe(holder, templates, bases, additions, output, ticks));
            originals.add(holder);
        }
        return new Index(pages, originals);
    }
}
