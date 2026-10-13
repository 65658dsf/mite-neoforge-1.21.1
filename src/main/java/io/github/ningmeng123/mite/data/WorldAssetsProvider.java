package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Boundaries and all 32 rune symbols, without replacing modern vanilla resources. @author NingMeng123 */
public final class WorldAssetsProvider implements DataProvider {
    private final PackOutput output;
    public WorldAssetsProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String boundary : List.of("mantle", "core")) {
            save(work, cache, "assets/mite/blockstates/" + boundary + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + boundary + "\"}}}");
            save(work, cache, "assets/mite/models/block/" + boundary + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mite:block/" + boundary + "\"}}");
            save(work, cache, "data/mite/loot_table/blocks/" + boundary + ".json", "{\"type\":\"minecraft:block\",\"pools\":[]}");
        }
        for (String metal : List.of("mithril", "adamantium")) {
            String name = metal + "_runestone";
            StringBuilder states = new StringBuilder("{\"variants\":{");
            StringBuilder overrides = new StringBuilder("["), pools = new StringBuilder("[");
            for (int rune = 0; rune < 16; rune++) {
                if (rune > 0) { states.append(','); overrides.append(','); pools.append(','); }
                String model = "mite:block/" + name + "_" + rune;
                states.append("\"rune=").append(rune).append("\":{\"model\":\"").append(model).append("\"}");
                overrides.append("{\"predicate\":{\"custom_model_data\":").append(rune).append("},\"model\":\"").append(model).append("\"}");
                String side = "mite:block/runestones/" + metal + "/" + rune;
                save(work, cache, "assets/mite/models/block/" + name + "_" + rune + ".json",
                        "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":\"minecraft:block/obsidian\",\"side\":\"" + side + "\"}}");
                pools.append("{\"rolls\":1,\"conditions\":[{\"condition\":\"minecraft:block_state_property\",\"block\":\"mite:").append(name)
                        .append("\",\"properties\":{\"rune\":\"").append(rune).append("\"}},{\"condition\":\"minecraft:survives_explosion\"}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mite:")
                        .append(name).append("\",\"functions\":[{\"function\":\"minecraft:set_components\",\"components\":{\"minecraft:block_state\":{\"rune\":\"")
                        .append(rune).append("\"},\"minecraft:custom_model_data\":").append(rune).append("}}]}]}");
            }
            save(work, cache, "assets/mite/blockstates/" + name + ".json", states.append("}}").toString());
            save(work, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"mite:block/" + name + "_0\",\"overrides\":" + overrides.append(']') + "}");
            save(work, cache, "data/mite/loot_table/blocks/" + name + ".json", "{\"type\":\"minecraft:block\",\"pools\":" + pools.append(']') + "}");
        }
        save(work, cache, "data/mite/worldgen/configured_feature/nether_boundary.json", "{\"type\":\"mite:nether_boundary\",\"config\":{}}");
        save(work, cache, "data/mite/worldgen/placed_feature/nether_boundary.json", "{\"feature\":\"mite:nether_boundary\",\"placement\":[]}");
        save(work, cache, "data/mite/neoforge/biome_modifier/nether_boundary.json", "{\"type\":\"neoforge:add_features\",\"biomes\":\"#minecraft:is_nether\",\"features\":[\"mite:nether_boundary\"],\"step\":\"raw_generation\"}");
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        work.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE underworld preparation assets"; }
}
