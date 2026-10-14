package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.RangedRodConversionRecipe;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;

/** Exact guarded-runtime arrow/rod recipes, including source arrowhead dismantling. @author NingMeng123 */
public final class RangedRecipes {
    private RangedRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(RangedRecipes.class.getResourceAsStream(
                "/mite/ranged-recipes-027.json")), StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("recipes")) {
                JsonObject row = element.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
                var result = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("output").getAsString()));
                String kind = row.get("kind").getAsString();
                if (kind.equals("rod_conversion")) {
                    var recipe = new RangedRodConversionRecipe(Material.byId(row.get("material").getAsString()), row.get("attach_carrot").getAsBoolean());
                    var first = recipe.getIngredients().getFirst().getItems()[0].getItem();
                    var advancement = output.advancement().addCriterion("has_input", InventoryChangeTrigger.TriggerInstance.hasItems(first))
                            .rewards(net.minecraft.advancements.AdvancementRewards.Builder.recipe(id));
                    output.accept(id, recipe, advancement.build(id.withPrefix("recipes/tools/")));
                } else if (kind.equals("shaped")) {
                    var builder = ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, result, row.get("count").getAsInt());
                    row.getAsJsonArray("pattern").forEach(pattern -> builder.pattern(pattern.getAsString()));
                    var keys = row.getAsJsonObject("key");
                    keys.entrySet().forEach(key -> builder.define(key.getKey().charAt(0),
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse(key.getValue().getAsString()))));
                    var first = BuiltInRegistries.ITEM.get(ResourceLocation.parse(keys.entrySet().iterator().next().getValue().getAsString()));
                    builder.unlockedBy("has_input", InventoryChangeTrigger.TriggerInstance.hasItems(first)).save(output, id);
                } else {
                    var input = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("input").getAsString()));
                    ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result, row.get("count").getAsInt()).requires(input)
                            .unlockedBy("has_input", InventoryChangeTrigger.TriggerInstance.hasItems(input)).save(output, id);
                }
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot generate guarded R196 ranged recipes", error); }
    }
}
