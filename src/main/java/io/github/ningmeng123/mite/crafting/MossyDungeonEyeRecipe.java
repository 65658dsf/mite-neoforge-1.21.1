package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/** The mithril cross is fixed and each of the four different mob drops can occupy any corner. @author NingMeng123 */
public final class MossyDungeonEyeRecipe extends ShapedRecipe {
    private static final int[] CROSS_SLOTS = {1, 3, 4, 5, 7};

    public MossyDungeonEyeRecipe(CraftingBookCategory category) {
        super("", category, ShapedRecipePattern.of(Map.of(
                'R', Ingredient.of(Items.ROTTEN_FLESH),
                'G', Ingredient.of(Items.GUNPOWDER),
                'S', Ingredient.of(Items.STRING),
                'B', Ingredient.of(Items.BONE),
                'M', Ingredient.of(MiteMaterials.MITHRIL_NUGGET.get()),
                'P', Ingredient.of(Items.ENDER_PEARL)), List.of("RMG", "MPM", "SMB")),
                new ItemStack(MiteItems.MOSSY_DUNGEON_EYE.get()));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) return false;
        var ingredients = getIngredients();
        for (int slot : CROSS_SLOTS) {
            if (!ingredients.get(slot).test(input.getItem(slot))) return false;
        }
        // With the cross fixed, the normal unordered ingredient check covers all corner permutations.
        return input.stackedContents().canCraft(this, null);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MiteItems.MOSSY_DUNGEON_EYE_RECIPE.get();
    }
}
