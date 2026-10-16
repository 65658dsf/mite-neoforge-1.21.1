package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.campfire.CampfireRules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** User-defined modern campfires cook only meat in fifteen seconds; R196 source evidence remains unchanged. @author NingMeng123 */
public final class CampfireRecipeAssetsProvider implements DataProvider {
    private final Path output;

    public CampfireRecipeAssetsProvider(PackOutput output) {
        this.output = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        // Preserve the source cooking rewards and native recipe IDs. Rabbit and salmon
        // retain modern vanilla rewards because they are outside the nine R196 mappings.
        for (Cooking recipe : List.of(
                nativeMeat("porkchop", "cooked_porkchop", 3F),
                nativeMeat("cod", "cooked_cod", 3F),
                nativeMeat("beef", "cooked_beef", 4F),
                nativeMeat("chicken", "cooked_chicken", 3F),
                nativeMeat("mutton", "cooked_mutton", 2F),
                nativeMeat("rabbit", "cooked_rabbit", .35F),
                nativeMeat("salmon", "cooked_salmon", .35F),
                new Cooking("mite:food_019_cook_1229_campfire_cooking", "mite:raw_large_fish", "mite:cooked_large_fish", 4F),
                new Cooking("mite:food_019_cook_1280_campfire_cooking", "mite:raw_worm", "mite:cooked_worm", 0F))) {
            write(writes, cache, recipe, true);
        }
        // Disable recipe-book/JEI entries as well as the server insertion gate. The
        // original furnace200/smoker100 recipes and historical019 evidence are retained.
        write(writes, cache, new Cooking("minecraft:baked_potato_from_campfire_cooking", "minecraft:potato", "minecraft:baked_potato", 0F), false);
        write(writes, cache, new Cooking("minecraft:dried_kelp_from_campfire_cooking", "minecraft:kelp", "minecraft:dried_kelp", .1F), false);
        write(writes, cache, new Cooking("mite:food_019_cook_1185_campfire_cooking", "mite:dough", "minecraft:bread", 0F), false);
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static Cooking nativeMeat(String input, String result, float experience) {
        return new Cooking("minecraft:" + result + "_from_campfire_cooking", "minecraft:" + input,
                "minecraft:" + result, experience);
    }

    private void write(List<CompletableFuture<?>> writes, CachedOutput cache, Cooking recipe, boolean enabled) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:campfire_cooking");
        json.addProperty("category", "food");
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", recipe.input());
        json.add("ingredient", ingredient);
        JsonObject result = new JsonObject();
        result.addProperty("id", recipe.result());
        result.addProperty("count", 1);
        json.add("result", result);
        json.addProperty("experience", recipe.experience());
        json.addProperty("cookingtime", CampfireRules.COOKING_TICKS);
        if (!enabled) {
            JsonObject disabled = new JsonObject();
            disabled.addProperty("type", "neoforge:false");
            JsonArray conditions = new JsonArray();
            conditions.add(disabled);
            json.add("neoforge:conditions", conditions);
        }
        ResourceLocation id = ResourceLocation.parse(recipe.id());
        writes.add(DataProvider.saveStable(cache, json,
                output.resolve("data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json")));
    }

    @Override
    public String getName() {
        return "MITE modern campfire meat-only fifteen-second recipes";
    }

    private record Cooking(String id, String input, String result, float experience) {}
}
