package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Custom template visuals and optional Simply Swords smithing routes. @author NingMeng123 */
public final class UpgradeTemplateAssetsProvider implements DataProvider {
    private final Path output;

    public UpgradeTemplateAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        JsonObject model = new JsonObject(), textures = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        textures.addProperty("layer0", "mite:item/equipment_upgrade_smithing_template");
        model.add("textures", textures);
        save(work, cache, "assets/mite/models/item/equipment_upgrade_smithing_template.json", model);
        for (var route : MaterialUpgradeRecipes.optionalUpgrades()) {
            JsonObject recipe = new JsonObject();
            recipe.add("neoforge:conditions", conditions());
            recipe.addProperty("type", "minecraft:smithing_transform");
            recipe.add("template", ingredient("mite:equipment_upgrade_smithing_template"));
            recipe.add("base", ingredient(route.base().toString()));
            recipe.add("addition", ingredient(route.addition().toString()));
            JsonObject result = new JsonObject(); result.addProperty("id", route.result().toString());
            recipe.add("result", result);
            ResourceLocation id = ResourceLocation.parse(route.id());
            save(work, cache, "data/mite/recipe/" + id.getPath() + ".json", recipe);
            save(work, cache, "data/mite/advancement/recipes/combat/" + id.getPath() + ".json", advancement(route.id()));
        }
        // Keep native runic recipes craftable after their retired netherite bases disappear.
        for (String family : SimplySwordsCompat.families()) {
            JsonObject recipe = new JsonObject();
            recipe.add("neoforge:conditions", conditions());
            recipe.addProperty("type", "minecraft:smithing_transform");
            recipe.add("template", ingredient("simplyswords:runic_tablet"));
            recipe.add("base", ingredient("mite:" + SimplySwordsCompat.path("mithril", family)));
            recipe.add("addition", ingredient("minecraft:diamond"));
            JsonObject result = new JsonObject(); result.addProperty("id", "simplyswords:runic_" + family);
            recipe.add("result", result);
            save(work, cache, "data/simplyswords/recipe/runic_" + family + ".json", recipe);
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private static JsonArray conditions() {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "neoforge:mod_loaded"); condition.addProperty("modid", "simplyswords");
        JsonArray conditions = new JsonArray(); conditions.add(condition); return conditions;
    }

    private static JsonObject ingredient(String id) {
        JsonObject ingredient = new JsonObject(); ingredient.addProperty("item", id); return ingredient;
    }

    private static JsonObject advancement(String id) {
        JsonObject advancement = new JsonObject();
        advancement.add("neoforge:conditions", conditions());
        advancement.addProperty("parent", "minecraft:recipes/root");
        JsonObject criteria = new JsonObject(), criterion = new JsonObject(), criterionConditions = new JsonObject();
        JsonObject item = new JsonObject(); JsonArray items = new JsonArray(), ids = new JsonArray();
        ids.add("mite:equipment_upgrade_smithing_template"); item.add("items", ids); items.add(item);
        criterionConditions.add("items", items); criterion.addProperty("trigger", "minecraft:inventory_changed");
        criterion.add("conditions", criterionConditions); criteria.add("has_upgrade_template", criterion);
        JsonObject unlocked = new JsonObject(), unlockedConditions = new JsonObject();
        unlocked.addProperty("trigger", "minecraft:recipe_unlocked"); unlockedConditions.addProperty("recipe", id);
        unlocked.add("conditions", unlockedConditions); criteria.add("has_the_recipe", unlocked);
        advancement.add("criteria", criteria);
        JsonArray requirements = new JsonArray(), alternatives = new JsonArray();
        alternatives.add("has_upgrade_template"); alternatives.add("has_the_recipe"); requirements.add(alternatives);
        advancement.add("requirements", requirements);
        JsonObject rewards = new JsonObject(); JsonArray recipes = new JsonArray(); recipes.add(id);
        rewards.add("recipes", recipes); advancement.add("rewards", rewards);
        return advancement;
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonObject json) {
        work.add(DataProvider.saveStable(cache, json, output.resolve(path)));
    }

    @Override public String getName() { return "MITE equipment upgrade template and optional smithing recipes"; }
}
