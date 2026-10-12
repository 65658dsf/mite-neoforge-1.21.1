package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Modern models, block loot and the unchanged modern Nether gravel feature shape. @author NingMeng123 */
public final class MaterialAssetsProvider implements DataProvider {
    private final Path output;

    public MaterialAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String metal : List.of("copper", "silver", "ancient_metal", "mithril", "adamantium")) {
            model(work, cache, metal + "_nugget", "minecraft:item/iron_nugget");
            if (!metal.equals("copper")) model(work, cache, metal + "_ingot", "minecraft:item/iron_ingot");
        }
        for (String material : List.of("obsidian", "emerald", "diamond", "quartz", "glass")) {
            // Reuse the project's already authored modern fragment silhouette; tint is applied at render time.
            model(work, cache, material + "_shard", "mite:item/flint_chip");
        }
        save(work, cache, "assets/mite/blockstates/nether_gravel.json",
                "{\"variants\":{\"\":{\"model\":\"mite:block/nether_gravel\"}}}");
        save(work, cache, "assets/mite/models/item/nether_gravel.json", "{\"parent\":\"mite:block/nether_gravel\"}");
        save(work, cache, "assets/mite/models/block/nether_gravel.json", """
                {"parent":"minecraft:block/block","textures":{"all":"minecraft:block/gravel","particle":"#all"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                   "down":{"texture":"#all","cullface":"down","tintindex":0},
                   "up":{"texture":"#all","cullface":"up","tintindex":0},
                   "north":{"texture":"#all","cullface":"north","tintindex":0},
                   "south":{"texture":"#all","cullface":"south","tintindex":0},
                   "west":{"texture":"#all","cullface":"west","tintindex":0},
                   "east":{"texture":"#all","cullface":"east","tintindex":0}}}]}
                """);
        save(work, cache, "data/mite/loot_table/blocks/nether_gravel.json", """
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"mite:nether_gravel"}],
                "conditions":[{"condition":"minecraft:survives_explosion"}]}],"random_sequence":"mite:blocks/nether_gravel"}
                """);
        save(work, cache, "data/minecraft/worldgen/configured_feature/ore_gravel_nether.json", """
                {"type":"minecraft:ore","config":{"discard_chance_on_air_exposure":0.0,"size":33,
                 "targets":[{"state":{"Name":"mite:nether_gravel"},"target":{"block":"minecraft:netherrack","predicate_type":"minecraft:block_match"}}]}}
                """);
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private void model(List<CompletableFuture<?>> work, CachedOutput cache, String name, String texture) {
        save(work, cache, "assets/mite/models/item/" + name + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + texture + "\"}}");
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        work.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.resolve(path)));
    }

    @Override
    public String getName() { return "MITE material models, loot and Nether gravel feature"; }
}
