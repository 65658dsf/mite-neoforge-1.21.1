package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.registry.MiteShields;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Native shield transforms and the six available metal-ingot crafting recipes. @author NingMeng123 */
public final class ShieldAssetsProvider implements DataProvider {
    private final Path output;

    public ShieldAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (var definition : MiteShields.definitions()) {
            if (!definition.isNew()) continue;
            ResourceLocation id = ResourceLocation.parse(definition.id());
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/shield");
            JsonObject blocking = new JsonObject(), predicate = new JsonObject();
            predicate.addProperty("blocking", 1);
            blocking.add("predicate", predicate);
            blocking.addProperty("model", id.getNamespace() + ":item/" + id.getPath() + "_blocking");
            JsonArray overrides = new JsonArray(); overrides.add(blocking);
            model.add("overrides", overrides);
            save(work, cache, "assets/mite/models/item/" + id.getPath() + ".json", model);
            JsonObject blockingModel = new JsonObject();
            blockingModel.addProperty("parent", "minecraft:item/shield_blocking");
            save(work, cache, "assets/mite/models/item/" + id.getPath() + "_blocking.json", blockingModel);

            // Rusted equipment is scavenged from monsters; the material has no ingot item.
            if (definition.material().equals("rusted_iron")) continue;
            String ingot = switch (definition.material()) {
                case "copper", "gold" -> "minecraft:" + definition.material() + "_ingot";
                default -> "mite:" + definition.material() + "_ingot";
            };
            JsonObject recipe = new JsonObject(), key = new JsonObject(), metal = new JsonObject(), wood = new JsonObject();
            recipe.addProperty("type", "minecraft:crafting_shaped");
            recipe.addProperty("category", "equipment");
            recipe.addProperty("group", "mite:shields");
            JsonArray pattern = new JsonArray();
            pattern.add("WMW"); pattern.add("WWW"); pattern.add(" W ");
            recipe.add("pattern", pattern);
            metal.addProperty("item", ingot); wood.addProperty("tag", "minecraft:planks");
            key.add("M", metal); key.add("W", wood); recipe.add("key", key);
            JsonObject result = new JsonObject(); result.addProperty("id", id.toString()); result.addProperty("count", 1);
            recipe.add("result", result);
            save(work, cache, "data/mite/recipe/" + id.getPath() + ".json", recipe);
            save(work, cache, "data/mite/advancement/recipes/combat/" + id.getPath() + ".json", unlock(id, ingot));
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private static JsonObject unlock(ResourceLocation id, String ingot) {
        JsonObject advancement = new JsonObject(), criteria = new JsonObject(), material = new JsonObject();
        advancement.addProperty("parent", "minecraft:recipes/root");
        material.addProperty("trigger", "minecraft:inventory_changed");
        JsonObject conditions = new JsonObject(), item = new JsonObject();
        JsonArray items = new JsonArray(), ids = new JsonArray();
        ids.add(ingot); item.add("items", ids); items.add(item); conditions.add("items", items);
        material.add("conditions", conditions); criteria.add("has_material", material);
        JsonObject recipe = new JsonObject(), recipeCondition = new JsonObject();
        recipe.addProperty("trigger", "minecraft:recipe_unlocked"); recipeCondition.addProperty("recipe", id.toString());
        recipe.add("conditions", recipeCondition); criteria.add("has_the_recipe", recipe);
        advancement.add("criteria", criteria);
        JsonArray alternatives = new JsonArray(), requirements = new JsonArray();
        alternatives.add("has_material"); alternatives.add("has_the_recipe"); requirements.add(alternatives);
        advancement.add("requirements", requirements);
        JsonObject rewards = new JsonObject(); JsonArray recipes = new JsonArray(); recipes.add(id.toString());
        rewards.add("recipes", recipes); advancement.add("rewards", rewards);
        return advancement;
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonObject content) {
        work.add(DataProvider.saveStable(cache, content, output.resolve(path)));
    }

    @Override public String getName() { return "MITE metal shield models and recipes"; }
}
