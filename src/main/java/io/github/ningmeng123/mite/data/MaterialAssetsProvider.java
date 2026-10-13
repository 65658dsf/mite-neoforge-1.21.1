package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original material sprites, block loot and modern Nether gravel generation. @author NingMeng123 */
public final class MaterialAssetsProvider implements DataProvider {
    private final Path output;

    public MaterialAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String metal : List.of("copper", "silver", "ancient_metal", "mithril", "adamantium")) {
            model(work, cache, metal + "_nugget", metal.equals("copper")
                    ? "minecraft:item/iron_nugget" : "mite:item/" + metal + "_nugget");
            if (!metal.equals("copper")) model(work, cache, metal + "_ingot", "mite:item/" + metal + "_ingot");
        }
        for (String material : List.of("obsidian", "emerald", "diamond", "quartz", "glass")) {
            model(work, cache, material + "_shard", "mite:item/" + material + "_shard");
        }
        save(work, cache, "assets/mite/blockstates/nether_gravel.json",
                "{\"variants\":{\"\":{\"model\":\"mite:block/nether_gravel\"}}}");
        save(work, cache, "assets/mite/models/item/nether_gravel.json", "{\"parent\":\"mite:block/nether_gravel\"}");
        save(work, cache, "assets/mite/models/block/nether_gravel.json", """
                {"parent":"minecraft:block/cube_all","textures":{"all":"mite:block/nether_gravel"}}
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
