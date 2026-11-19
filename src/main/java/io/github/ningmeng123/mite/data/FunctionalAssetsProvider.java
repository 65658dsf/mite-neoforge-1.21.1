package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Source shapes and recipes are projected into modern resource IDs. @author NingMeng123 */
public final class FunctionalAssetsProvider implements DataProvider {
    private final PackOutput output;
    public FunctionalAssetsProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (String metal : List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            String prefix = metal.equals("gold") ? "golden" : metal;
            String name = metal.equals("iron") ? "anvil" : prefix + "_anvil";
            String namespace = metal.equals("iron") ? "minecraft" : "mite";
            for (int stage = 0; stage < 3; stage++) {
                String modelName = metal.equals("iron") ? List.of("anvil", "chipped_anvil", "damaged_anvil").get(stage) : name + "_" + stage;
                save(tasks, cache, "assets/" + namespace + "/models/block/" + modelName + ".json",
                        "{\"parent\":\"minecraft:block/template_anvil\",\"textures\":{\"particle\":\"mite:block/anvil/" + metal
                                + "/base\",\"body\":\"mite:block/anvil/" + metal + "/base\",\"top\":\"mite:block/anvil/" + metal + "/top_damaged_" + stage + "\"}}");
            }
            if (!metal.equals("iron")) {
                JsonObject variants = new JsonObject();
                for (int facing = 0; facing < 4; facing++) for (int stage = 0; stage < 3; stage++) {
                    JsonObject model = new JsonObject(); model.addProperty("model", "mite:block/" + name + "_" + stage);
                    model.addProperty("y", facing * 90);
                    variants.add("facing=" + List.of("north", "east", "south", "west").get(facing) + ",damage_stage=" + stage, model);
                }
                JsonObject states = new JsonObject(); states.add("variants", variants);
                save(tasks, cache, "assets/mite/blockstates/" + name + ".json", states.toString());
                save(tasks, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"mite:block/" + name + "_0\",\"overrides\":["
                        + "{\"predicate\":{\"mite:anvil_stage\":1},\"model\":\"mite:block/" + name + "_1\"},"
                        + "{\"predicate\":{\"mite:anvil_stage\":2},\"model\":\"mite:block/" + name + "_2\"}]}");
                save(tasks, cache, "data/mite/loot_table/blocks/" + name + ".json", anvilLoot("mite:" + name));
            }
            if (metal.equals("vibranium")) continue;
            String box = prefix + "_strongbox";
            save(tasks, cache, "assets/mite/blockstates/" + box + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + box + "\"}}}");
            save(tasks, cache, "assets/mite/models/block/" + box + ".json", "{\"textures\":{\"particle\":\"minecraft:block/iron_block\"}}");
            save(tasks, cache, "assets/mite/models/item/" + box + ".json", "{\"parent\":\"minecraft:builtin/entity\",\"gui_light\":\"front\",\"display\":{\"gui\":{\"rotation\":[30,45,0],\"scale\":[0.625,0.625,0.625]},\"ground\":{\"translation\":[0,3,0],\"scale\":[0.25,0.25,0.25]},\"fixed\":{\"rotation\":[0,180,0],\"scale\":[0.5,0.5,0.5]}}}");
            save(tasks, cache, "data/mite/loot_table/blocks/" + box + ".json", ordinaryLoot("mite:" + box));
        }
        MiteFunctionalBlocks.SMITHING_TABLES.forEach((material, block) -> {
            String name = block.getId().getPath();
            save(tasks, cache, "assets/mite/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + name + "\"}}}");
            save(tasks, cache, "assets/mite/models/block/" + name + ".json",
                    "{\"parent\":\"mite:block/template_material_smithing_table\",\"textures\":{\"metal\":\"mite:block/" + material + "_block\"}}");
            save(tasks, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"mite:block/" + name + "\"}");
            save(tasks, cache, "data/mite/loot_table/blocks/" + name + ".json", ordinaryLoot("mite:" + name));
        });
        for (String name : List.of("anvil", "chipped_anvil", "damaged_anvil"))
            save(tasks, cache, "data/minecraft/loot_table/blocks/" + name + ".json", anvilLoot("minecraft:" + name));
        save(tasks, cache, "assets/mite/blockstates/emerald_enchanting_table.json", "{\"variants\":{\"\":{\"model\":\"mite:block/emerald_enchanting_table\"}}}");
        save(tasks, cache, "assets/mite/models/block/emerald_enchanting_table.json", "{\"parent\":\"minecraft:block/enchanting_table\",\"textures\":{\"particle\":\"mite:block/emerald_enchanting_table_side\",\"bottom\":\"minecraft:block/enchanting_table_bottom\",\"side\":\"mite:block/emerald_enchanting_table_side\",\"top\":\"mite:block/emerald_enchanting_table_top\"}}");
        save(tasks, cache, "assets/mite/models/item/emerald_enchanting_table.json", "{\"parent\":\"mite:block/emerald_enchanting_table\"}");
        save(tasks, cache, "data/mite/loot_table/blocks/emerald_enchanting_table.json", ordinaryLoot("mite:emerald_enchanting_table"));
        JsonArray sprites = new JsonArray();
        for (String metal : List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium")) {
            JsonObject sprite = new JsonObject(); sprite.addProperty("type", "minecraft:single");
            sprite.addProperty("resource", "mite:entity/chest/" + metal + "_single"); sprites.add(sprite);
        }
        JsonObject atlas = new JsonObject(); atlas.add("sources", sprites);
        save(tasks, cache, "assets/minecraft/atlases/chests.json", atlas.toString());
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/mite/functional-blocks-027.json"), StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("recipes")) {
                JsonObject source = entry.getAsJsonObject(); String[] id = source.get("id").getAsString().split(":");
                JsonObject recipe = new JsonObject(); recipe.addProperty("type", "minecraft:crafting_shaped"); recipe.addProperty("category", "misc");
                recipe.add("pattern", source.get("pattern")); recipe.add("key", source.get("key")); recipe.add("result", source.get("result"));
                save(tasks, cache, "data/" + id[0] + "/recipe/" + id[1] + ".json", recipe.toString());
            }
        } catch (Exception exception) { throw new IllegalStateException("Cannot load source functional recipes", exception); }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    private static String ordinaryLoot(String id) {
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + id + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
    }
    private static String anvilLoot(String id) {
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + id
                + "\",\"functions\":[{\"function\":\"minecraft:copy_components\",\"source\":\"block_entity\",\"include\":[\"mite:anvil_damage\",\"minecraft:custom_name\"]}]}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
    }
    private void save(List<CompletableFuture<?>> tasks, CachedOutput cache, String path, String json) {
        tasks.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE R196 functional blocks"; }
}
