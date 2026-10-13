package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Source entrance materials, with manufacturing metadata loaded separately. @author NingMeng123 */
public final class WorldRecipes extends RecipeProvider {
    private WorldRecipes() { super(null, null); }
    @Override protected void buildRecipes(RecipeOutput output) {}
    public static void addRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.FLINT_AND_STEEL)
                .pattern("I ").pattern(" F").define('I', Items.IRON_NUGGET).define('F', Items.FLINT)
                .unlockedBy("has_iron_nugget", has(Items.IRON_NUGGET))
                .save(output, ResourceLocation.withDefaultNamespace("flint_and_steel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get())
                .pattern(" N ").pattern("NON").pattern(" N ").define('O', Items.OBSIDIAN).define('N', MiteMaterials.MITHRIL_NUGGET.get())
                .unlockedBy("has_mithril", has(MiteMaterials.MITHRIL_NUGGET.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath("mite", "mithril_runestone"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorldBlocks.ADAMANTIUM_RUNESTONE_ITEM.get())
                .pattern(" N ").pattern("NON").pattern(" N ").define('O', Items.OBSIDIAN).define('N', MiteMaterials.ADAMANTIUM_NUGGET.get())
                .unlockedBy("has_adamantium", has(MiteMaterials.ADAMANTIUM_NUGGET.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath("mite", "adamantium_runestone"));
    }
}
