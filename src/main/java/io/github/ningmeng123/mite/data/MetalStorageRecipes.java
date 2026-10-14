package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Source nine-ingot storage, including the eight-stack inventory exception. @author NingMeng123 */
public final class MetalStorageRecipes extends RecipeProvider {
    private MetalStorageRecipes() { super(null, null); }
    public static void addRecipes(RecipeOutput output) {
        // Copper's modern block identity already exists; its original packing pattern is unchanged.
        for (var entry : MiteMetalBlocks.STORAGE.entrySet()) {
            String material = entry.getKey();
            var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:" + material + "_ingot"));
            var block = entry.getValue().get().asItem();
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, block).pattern("III").pattern("III").pattern("III")
                    .define('I', ingot).unlockedBy("has_ingot", has(ingot))
                    .save(output, ResourceLocation.parse("mite:" + material + "_block"));
            output.accept(ResourceLocation.parse("mite:" + material + "_ingots_from_block"),
                    new BulkMaterialRecipe(material + "_ingot", Ingredient.of(block), new ItemStack(ingot, 9)), null);
        }
    }
}
