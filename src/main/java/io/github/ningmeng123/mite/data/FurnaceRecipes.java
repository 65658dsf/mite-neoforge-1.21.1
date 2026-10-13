package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteFurnaces;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Exact source furnace shapes; manufacturing time is loaded from the 024 evidence. @author NingMeng123 */
public final class FurnaceRecipes extends RecipeProvider {
    private FurnaceRecipes() { super(null, null); }
    @Override protected void buildRecipes(RecipeOutput output) {}
    public static void addRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteFurnaces.CLAY_OVEN.get())
                .pattern("CC").pattern("CC").define('C', Items.CLAY)
                .unlockedBy("has_clay", has(Items.CLAY))
                .save(output, ResourceLocation.fromNamespaceAndPath("mite", "clay_oven"));
        ring(output, "large_clay_oven", MiteFurnaces.LARGE_CLAY_OVEN.get(), Items.TERRACOTTA);
        ring(output, "sandstone_furnace", MiteFurnaces.SANDSTONE_FURNACE.get(), Items.SANDSTONE);
        ring(output, "obsidian_furnace", MiteFurnaces.OBSIDIAN_FURNACE.get(), Items.OBSIDIAN);
        ring(output, "netherrack_furnace", MiteFurnaces.NETHERRACK_FURNACE.get(), Items.NETHERRACK);
        // Replace the modern #stone_tool_materials substitution with source eight cobblestone.
        ring(output, "minecraft:furnace", Items.FURNACE, Items.COBBLESTONE);
    }
    private static void ring(RecipeOutput output, String id, ItemLike result, ItemLike ingredient) {
        ResourceLocation location = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("mite", id);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, result)
                .pattern("MMM").pattern("M M").pattern("MMM").define('M', ingredient)
                .unlockedBy("has_furnace_material", has(ingredient)).save(output, location);
    }
}
