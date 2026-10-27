package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
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
import net.minecraft.world.item.Items;

/** Source doors/bars/dyes and a distinct modern copper-trapdoor shape. @author NingMeng123 */
public final class DecorativeRecipes {
    private DecorativeRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        // R196 orders 273/274 both use six blocks for a complete batch of eight walls.
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Items.COBBLESTONE_WALL, 8)
                .pattern("III").pattern("III").define('I', Items.COBBLESTONE)
                .unlockedBy("has_cobblestone", InventoryChangeTrigger.TriggerInstance.hasItems(Items.COBBLESTONE))
                .save(output, ResourceLocation.withDefaultNamespace("cobblestone_wall"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Items.MOSSY_COBBLESTONE_WALL, 8)
                .pattern("III").pattern("III").define('I', Items.MOSSY_COBBLESTONE)
                .unlockedBy("has_mossy_cobblestone", InventoryChangeTrigger.TriggerInstance.hasItems(Items.MOSSY_COBBLESTONE))
                .save(output, ResourceLocation.withDefaultNamespace("mossy_cobblestone_wall"));
        try (var reader = new InputStreamReader(Objects.requireNonNull(DecorativeRecipes.class.getResourceAsStream("/mite/decorative-028.json")), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            var rows = data.getAsJsonArray("recipes");
            rows.addAll(data.getAsJsonArray("modern_adapted_recipes"));
            for (var entry : rows) {
                var row = entry.getAsJsonObject(); var id = ResourceLocation.parse(row.get("id").getAsString());
                var result = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("output").getAsString()));
                int count = row.get("count").getAsInt();
                if (row.has("pattern")) {
                    var ingredient = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("ingredient").getAsString()));
                    var category = id.toString().equals("minecraft:copper_trapdoor") ? RecipeCategory.REDSTONE : RecipeCategory.BUILDING_BLOCKS;
                    var builder = ShapedRecipeBuilder.shaped(category, result, count);
                    row.getAsJsonArray("pattern").forEach(line -> builder.pattern(line.getAsString()));
                    builder.define('I', ingredient).unlockedBy("has_input", InventoryChangeTrigger.TriggerInstance.hasItems(ingredient)).save(output, id);
                } else {
                    var input = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("input").getAsString()));
                    ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result, count).requires(input)
                            .unlockedBy("has_input", InventoryChangeTrigger.TriggerInstance.hasItems(input)).save(output, id);
                }
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot generate source decorative recipes", error); }
    }
}
