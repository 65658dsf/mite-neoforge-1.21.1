package io.github.ningmeng123.mite.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Explicit recipe IDs and whole-batch manufacturing difficulties. @author NingMeng123 */
public final class TimedCraftingRecipes {
    public record Rule(float difficulty, String requiredBench, int sourceOrder) {
        public float requiredCapacity() { return WorkbenchRules.materialCapacity(requiredBench); }
    }
    private static final Map<ResourceLocation, Rule> RULES = load();
    private TimedCraftingRecipes() {}
    public static Rule get(ResourceLocation recipe) { return RULES.get(recipe); }

    private static Map<ResourceLocation, Rule> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                TimedCraftingRecipes.class.getResourceAsStream("/mite/timed-crafting-016.json")), StandardCharsets.UTF_8)) {
            JsonObject recipes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes");
            Map<ResourceLocation, Rule> rules = new HashMap<>();
            recipes.entrySet().forEach(entry -> {
                JsonObject value = entry.getValue().getAsJsonObject();
                String bench = value.get("required_bench").isJsonNull() ? null : value.get("required_bench").getAsString();
                rules.put(ResourceLocation.parse(entry.getKey()), new Rule(value.get("difficulty").getAsFloat(), bench,
                        value.get("source_order").getAsInt()));
            });
            try (var worldReader = new InputStreamReader(Objects.requireNonNull(
                    TimedCraftingRecipes.class.getResourceAsStream("/mite/timed-crafting-021.json")), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(worldReader).getAsJsonObject().getAsJsonObject("recipes").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    rules.put(ResourceLocation.parse(entry.getKey()), new Rule(value.get("difficulty").getAsFloat(),
                            value.get("required_bench").getAsString(), value.get("source_order").getAsInt()));
                });
            }
            try (var furnaceReader = new InputStreamReader(Objects.requireNonNull(
                    TimedCraftingRecipes.class.getResourceAsStream("/mite/timed-crafting-024.json")), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(furnaceReader).getAsJsonObject().getAsJsonObject("recipes").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    rules.put(ResourceLocation.parse(entry.getKey()), new Rule(value.get("difficulty").getAsFloat(),
                            value.get("required_bench").isJsonNull() ? null : value.get("required_bench").getAsString(),
                            value.get("source_order").getAsInt()));
                });
            }
            for (String resource : java.util.List.of("/mite/timed-crafting-027-armor.json",
                    "/mite/timed-crafting-027-ranged.json", "/mite/timed-crafting-027-functional.json",
                    "/mite/timed-crafting-027-storage.json", "/mite/timed-crafting-028-weapons.json",
                    "/mite/timed-crafting-028-utility.json", "/mite/timed-crafting-028-decorative.json",
                    "/mite/timed-crafting-equipment-upgrades.json", "/mite/timed-crafting-material-processing.json")) {
                try (var reader027 = new InputStreamReader(Objects.requireNonNull(
                        TimedCraftingRecipes.class.getResourceAsStream(resource)), StandardCharsets.UTF_8)) {
                    JsonParser.parseReader(reader027).getAsJsonObject().getAsJsonObject("recipes").entrySet().forEach(entry -> {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        rules.put(ResourceLocation.parse(entry.getKey()), new Rule(value.get("difficulty").getAsFloat(),
                                value.get("required_bench").isJsonNull() ? null : value.get("required_bench").getAsString(),
                                value.get("source_order").getAsInt()));
                    });
                }
            }
            return Map.copyOf(rules);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load R196 timed crafting evidence", exception);
        }
    }
}
