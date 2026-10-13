package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Modern facing/lit states reference unchanged source-pack furnace artwork. @author NingMeng123 */
public final class FurnaceAssetsProvider implements DataProvider {
    private final PackOutput output;
    public FurnaceAssetsProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        String[][] furnaces = {{"clay_oven", "clay"}, {"large_clay_oven", "hardened_clay"},
                {"sandstone_furnace", "sandstone"}, {"obsidian_furnace", "obsidian"}, {"netherrack_furnace", "netherrack"}};
        for (String[] furnace : furnaces) {
            String name = furnace[0], textures = "mite:block/furnace/" + furnace[1] + "/";
            JsonObject variants = new JsonObject();
            for (int facing = 0; facing < 4; facing++) {
                String direction = List.of("north", "east", "south", "west").get(facing);
                for (boolean lit : new boolean[] {false, true}) {
                    JsonObject model = new JsonObject();
                    model.addProperty("model", "mite:block/" + name + (lit ? "_lit" : ""));
                    if (facing > 0) model.addProperty("y", facing * 90);
                    variants.add("facing=" + direction + ",lit=" + lit, model);
                }
            }
            JsonObject states = new JsonObject(); states.add("variants", variants);
            tasks.add(DataProvider.saveStable(cache, states, output.getOutputFolder().resolve("assets/mite/blockstates/" + name + ".json")));
            for (boolean lit : new boolean[] {false, true}) {
                save(tasks, cache, "assets/mite/models/block/" + name + (lit ? "_lit" : "") + ".json",
                        "{\"parent\":\"minecraft:block/orientable_with_bottom\",\"textures\":{\"bottom\":\"" + textures + "top\",\"top\":\"" + textures + "top\",\"front\":\""
                                + textures + (lit ? "front_on" : "front_off") + "\",\"side\":\"" + textures + "side\"}}");
            }
            save(tasks, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"mite:block/" + name + "\"}");
            // Source explosion replacements are handled before native loot; normal harvest drops the idle furnace.
            save(tasks, cache, "data/mite/loot_table/blocks/" + name + ".json",
                    "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mite:"
                            + name + "\",\"functions\":[{\"function\":\"minecraft:copy_components\",\"include\":[\"minecraft:custom_name\"],\"source\":\"block_entity\"}]}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
        }
        for (String metal : List.of("copper", "silver", "gold", "mithril", "adamantium", "ancient_metal")) {
            String name = metal + "_lava_bucket";
            save(tasks, cache, "assets/mite/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mite:item/" + name + "\"}}");
        }
        cooking(tasks, cache, "minecraft:glass", "#minecraft:smelts_to_glass", "minecraft:glass", 1);
        cooking(tasks, cache, "minecraft:brick", "minecraft:clay_ball", "minecraft:brick", 1);
        cooking(tasks, cache, "minecraft:terracotta", "minecraft:clay", "minecraft:terracotta", 1);
        cooking(tasks, cache, "minecraft:green_dye", "minecraft:cactus", "minecraft:green_dye", 1);
        cooking(tasks, cache, "mite:glass_from_sandstone", "minecraft:sandstone", "minecraft:glass", 1);
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    private void cooking(List<CompletableFuture<?>> tasks, CachedOutput cache, String id, String input, String result, int count) {
        String[] location = id.split(":");
        save(tasks, cache, "data/" + location[0] + "/recipe/" + location[1] + ".json",
                "{\"type\":\"minecraft:smelting\",\"category\":\"misc\",\"ingredient\":{\"" + (input.startsWith("#") ? "tag" : "item")
                        + "\":\"" + (input.startsWith("#") ? input.substring(1) : input) + "\"},\"result\":{\"id\":\""
                        + result + "\",\"count\":" + count + "},\"experience\":0.0,\"cookingtime\":200}");
    }
    private void save(List<CompletableFuture<?>> tasks, CachedOutput cache, String path, String json) {
        tasks.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE R196 furnace assets"; }
}
