package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteArmorContent;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Source shapes include rusted chainmail, all metal chain rings and their four-nugget reverse recipes. @author NingMeng123 */
public final class ArmorRecipes extends RecipeProvider {
    private ArmorRecipes() { super(null, null); }
    @Override protected void buildRecipes(RecipeOutput output) {}
    public static void addRecipes(RecipeOutput output) {
        MiteArmorContent.source().getAsJsonArray("recipes").forEach(element -> {
            var recipe = element.getAsJsonObject();
            var inputs = recipe.getAsJsonArray("ingredients");
            Item ingredient = MiteArmorContent.get(inputs.asList().stream().filter(e -> !e.isJsonNull()).findFirst().orElseThrow().getAsString());
            var builder = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT,
                    MiteArmorContent.get(recipe.get("output").getAsString()), recipe.get("count").getAsInt());
            int width = recipe.get("width").getAsInt(), height = recipe.get("height").getAsInt();
            for (int row = 0; row < height; row++) {
                StringBuilder pattern = new StringBuilder();
                for (int col = 0; col < width; col++) pattern.append(inputs.get(row * width + col).isJsonNull() ? ' ' : 'M');
                builder.pattern(pattern.toString());
            }
            builder.define('M', ingredient).unlockedBy("has_armor_material", has(ingredient))
                    .save(output, ResourceLocation.parse(recipe.get("id").getAsString()));
        });
    }
}
