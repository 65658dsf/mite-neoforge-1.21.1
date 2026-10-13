package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original table details with modern bottom/top where no material-specific surface exists. @author NingMeng123 */
public final class WorkbenchAssetsProvider implements DataProvider {
    private final PackOutput output;
    public WorkbenchAssetsProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String material : MiteWorkbenches.TABLES.keySet()) {
            String name = material + "_crafting_table";
            String top = "minecraft:block/crafting_table_top";
            String front = "minecraft:block/crafting_table_front";
            String side = "minecraft:block/crafting_table_side";
            if (material.equals("flint") || material.equals("obsidian")) {
                top = "mite:block/crafting_table/" + material + "/top";
                front = side = "minecraft:block/oak_log";
            } else if (!material.equals("copper")) {
                front = "mite:block/crafting_table/" + material + "/front";
                side = "mite:block/crafting_table/" + material + "/side";
            }
            save(work, cache, "assets/mite/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + name + "\"}}}");
            save(work, cache, "assets/mite/models/block/" + name + ".json", "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"" + front
                    + "\",\"down\":\"minecraft:block/oak_planks\",\"up\":\"" + top + "\",\"north\":\"" + front
                    + "\",\"south\":\"" + front + "\",\"east\":\"" + side + "\",\"west\":\"" + side + "\"}}");
            save(work, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"mite:block/" + name + "\"}");
            save(work, cache, "data/mite/loot_table/blocks/" + name + ".json", loot("mite:" + name));
        }
        // Old saves used the vanilla identity for the flint table. Breaking one migrates its drop.
        save(work, cache, "data/minecraft/loot_table/blocks/crafting_table.json", loot("mite:flint_crafting_table"));
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private static String loot(String item) {
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + item
                + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
    }
    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        work.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE material workbench assets"; }
}
