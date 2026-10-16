package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import io.github.ningmeng123.mite.crafting.WaterBowlSuspiciousStewRecipe;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Modern soups use the same filled-vessel manufacturing as R196 mushroom stew. @author NingMeng123 */
public final class SoupRecipes {
    private SoupRecipes() {}

    public static void addRecipes(RecipeOutput output) {
        Item waterBowl = MiteFoods.WATER_BOWL.get();
        shapeless(output, "beetroot_soup", "", Items.BEETROOT_SOUP,
                Items.BEETROOT, Items.BEETROOT, Items.BEETROOT,
                Items.BEETROOT, Items.BEETROOT, Items.BEETROOT, waterBowl);
        shapeless(output, "rabbit_stew_from_brown_mushroom", "rabbit_stew", Items.RABBIT_STEW,
                Items.BAKED_POTATO, Items.COOKED_RABBIT, waterBowl, Items.CARROT, Items.BROWN_MUSHROOM);
        shapeless(output, "rabbit_stew_from_red_mushroom", "rabbit_stew", Items.RABBIT_STEW,
                Items.BAKED_POTATO, Items.COOKED_RABBIT, waterBowl, Items.CARROT, Items.RED_MUSHROOM);
        output.accept(ResourceLocation.withDefaultNamespace("suspicious_stew"),
                new WaterBowlSuspiciousStewRecipe(CraftingBookCategory.MISC), null);
        // Mushroom stew already has the R196 water-bowl recipe food_019_703.
    }

    private static void shapeless(RecipeOutput output, String id, String group, Item result, Item... items) {
        var ingredients = NonNullList.<Ingredient>create();
        for (Item item : items) ingredients.add(Ingredient.of(item));
        var vanilla = new ShapelessRecipe(group, CraftingBookCategory.MISC, new ItemStack(result), ingredients);
        var manufacturing = new FoodManufacturingRecipe.Metadata(25F, "", 0, true, false);
        output.accept(ResourceLocation.withDefaultNamespace(id), new FoodShapelessRecipe(vanilla, manufacturing), null);
    }
}
