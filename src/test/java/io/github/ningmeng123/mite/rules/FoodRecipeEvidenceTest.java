package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Literal source recipe families and explicit modern cooking adaptations. @author NingMeng123 */
class FoodRecipeEvidenceTest {
    private static JsonObject evidence() {
        try (var reader = new InputStreamReader(FoodRecipeEvidenceTest.class.getResourceAsStream(
                "/mite/food-manufacturing-019.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Missing food evidence", exception);
        }
    }

    @Test
    void completeSourceFoodAndVesselFamiliesRemainTraceable() {
        JsonObject data = evidence();
        assertEquals("NingMeng123", data.get("author").getAsString());
        assertEquals("fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3", data.get("archive_sha256").getAsString());
        assertEquals(202, data.get("original_food_recipe_count").getAsInt());
        assertEquals(52, data.get("dependency_recipe_count").getAsInt());
        assertEquals(254, data.getAsJsonObject("recipes").size());
    }

    @Test
    void retiredAppleBottleCraftRemainsEvidenceButCannotRegisterAsAnActiveRecipe() throws Exception {
        JsonObject data = evidence();
        assertEquals(253, data.get("active_source_crafting_recipe_count").getAsInt());
        var retired = data.getAsJsonArray("excluded_modern_crafting_orders");
        assertEquals(1, retired.size());
        assertEquals(773, retired.get(0).getAsInt());
        var source = data.getAsJsonObject("recipes").getAsJsonObject("mite:food_019_773");
        assertEquals(773, source.get("source_order").getAsInt());
        assertEquals("minecraft:enchanted_golden_apple", source.getAsJsonObject("output").get("id").getAsString());
        assertEquals(2, source.getAsJsonArray("source_components").size());
        assertEquals(50F, source.get("difficulty").getAsFloat());
        try (var reader = new InputStreamReader(FoodRecipeEvidenceTest.class.getResourceAsStream(
                "/data/mite/recipe/food_019_773.json"), StandardCharsets.UTF_8)) {
            var live = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(1, live.getAsJsonArray("neoforge:conditions").size());
            assertEquals("neoforge:false", live.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString());
            assertEquals(source.getAsJsonObject("output"), live.getAsJsonObject("result"));
        }
    }

    @Test
    void cheeseCosts6400ForEachOfIts65DistinctMilkBatchRecipes() {
        var cheese = evidence().getAsJsonObject("recipes").entrySet().stream()
                .map(Map.Entry::getValue).map(value -> value.getAsJsonObject())
                .filter(value -> value.getAsJsonObject("output").get("id").getAsString().equals("mite:cheese")).toList();
        assertEquals(65, cheese.size());
        assertEquals(63L, cheese.stream().filter(recipe -> recipe.get("any_milk_buckets").getAsBoolean()).count());
        for (JsonObject recipe : cheese) assertEquals(6400F, recipe.get("difficulty").getAsFloat());
        assertEquals(9, evidence().getAsJsonObject("recipes").getAsJsonObject("mite:food_019_509")
                .getAsJsonObject("output").get("count").getAsInt());
    }

    @Test
    void bowlCakeFlourAndFourCookieBatchesKeepIndependentSourceNumbers() {
        JsonObject recipes = evidence().getAsJsonObject("recipes");
        assertEquals(75F, recipes.getAsJsonObject("mite:food_019_400").get("difficulty").getAsFloat());
        assertEquals(3, recipes.getAsJsonObject("mite:food_019_400").getAsJsonArray("source_components").size());
        assertEquals(100F, recipes.getAsJsonObject("mite:food_019_642").get("difficulty").getAsFloat());
        assertFalse(recipes.getAsJsonObject("mite:food_019_642").get("output_contains_bowl").getAsBoolean());
        assertEquals(25F, recipes.getAsJsonObject("mite:food_019_744").get("difficulty").getAsFloat());
        assertTrue(recipes.getAsJsonObject("mite:food_019_744").get("output_contains_bowl").getAsBoolean());
        assertEquals(16, recipes.getAsJsonObject("mite:food_019_516").getAsJsonObject("output").get("count").getAsInt());
        assertEquals(9, recipes.getAsJsonObject("mite:food_019_516").getAsJsonArray("source_components").size());
        assertEquals(225F, recipes.getAsJsonObject("mite:food_019_516").get("difficulty").getAsFloat());
    }

    @Test
    void vesselFillingRecordsAccountForEveryBowlAndFullBucketInput() {
        JsonObject recipes = evidence().getAsJsonObject("recipes");
        JsonObject waterBowls = recipes.getAsJsonObject("mite:food_019_608");
        assertEquals(4, waterBowls.getAsJsonObject("output").get("count").getAsInt());
        assertEquals(5, waterBowls.getAsJsonArray("source_components").size());
        assertEquals(25F, waterBowls.get("difficulty").getAsFloat());
        JsonObject milkBucket = recipes.getAsJsonObject("mite:food_019_596");
        assertEquals("minecraft:milk_bucket", milkBucket.getAsJsonObject("output").get("id").getAsString());
        assertEquals(5, milkBucket.getAsJsonArray("source_components").size());
        assertFalse(milkBucket.get("output_contains_bowl").getAsBoolean());
    }

    @Test
    void allNineCookingOutputsHaveLiteralSourceRewardsAnd200TicksAcrossModernDevices() {
        Map<String, Integer> expected = Map.of("minecraft:cooked_porkchop", 3, "minecraft:cooked_cod", 3,
                "minecraft:cooked_beef", 4, "minecraft:cooked_chicken", 3, "minecraft:baked_potato", 0,
                "minecraft:cooked_mutton", 2, "minecraft:bread", 0, "mite:cooked_large_fish", 4, "mite:cooked_worm", 0);
        JsonArray cooking = evidence().getAsJsonArray("cooking");
        assertEquals(9, cooking.size());
        for (var value : cooking) {
            JsonObject recipe = value.getAsJsonObject();
            assertEquals(expected.get(recipe.get("output").getAsString()).intValue(), recipe.get("experience").getAsInt());
            assertEquals(200, recipe.get("cooking_time").getAsInt());
            assertEquals(1, recipe.get("source_required_heat").getAsInt());
            assertEquals(3, recipe.getAsJsonArray("recipe_ids").size());
        }
    }

    @Test
    void literalFoodDifficultiesUseFullBatchTimingAtLevelZeroOnFlintBench() {
        assertEquals(62, TimedCraftingRules.period(75F, 0, .2F, false)); // Flour and beef stew.
        assertEquals(83, TimedCraftingRules.period(100F, 0, .2F, false)); // Milk-bowl cake.
        assertEquals(123, TimedCraftingRules.period(225F, 0, .2F, false)); // Sixteen cookies, not per cookie.
        assertEquals(995, TimedCraftingRules.period(6400F, 0, .2F, false)); // Cheese, whether one or nine outputs.
    }
}
