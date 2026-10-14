package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.UtilityShapelessRecipe;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Source counts, water potion subtype and deterministic recipe identities from the guarded runtime. @author NingMeng123 */
public final class UtilityRecipes {
    private UtilityRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(UtilityRecipes.class.getResourceAsStream("/mite/utility-028.json")), StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("crafting_recipes")) {
                JsonObject row = element.getAsJsonObject();
                NonNullList<Ingredient> ingredients = NonNullList.create();
                for (var input : row.getAsJsonArray("inputs")) {
                    var component = input.getAsJsonObject();
                    for (int index = 0; index < component.get("count").getAsInt(); index++) ingredients.add(Ingredient.of(MiteUtilityContent.item(component.get("item").getAsString())));
                }
                ItemStack result = new ItemStack(MiteUtilityContent.item(row.get("output").getAsString()), row.get("count").getAsInt());
                output.accept(ResourceLocation.parse(row.get("id").getAsString()), new UtilityShapelessRecipe(result, ingredients,
                        result.is(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()), row.get("propagate_components").getAsBoolean()), null);
            }
        } catch (Exception failure) { throw new IllegalStateException("Cannot generate R196 utility recipes", failure); }
    }
}
