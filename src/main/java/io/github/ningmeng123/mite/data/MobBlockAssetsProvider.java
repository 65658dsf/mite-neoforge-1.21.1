package io.github.ningmeng123.mite.data;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Source copper-inhabited stone is visually indistinguishable from the modern host. @author NingMeng123 */
public final class MobBlockAssetsProvider implements DataProvider {
    private final PackOutput output;
    public MobBlockAssetsProvider(PackOutput output) { this.output=output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var rows=List.of(
                new String[]{"assets/mite/blockstates/copperspine_stone.json","{\"variants\":{\"\":{\"model\":\"mite:block/copperspine_stone\"}}}"},
                new String[]{"assets/mite/models/block/copperspine_stone.json","{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}"},
                new String[]{"assets/mite/models/item/copperspine_stone.json","{\"parent\":\"mite:block/copperspine_stone\"}"},
                new String[]{"data/mite/loot_table/blocks/copperspine_stone.json","{\"type\":\"minecraft:block\",\"pools\":[]}"});
        return CompletableFuture.allOf(rows.stream().map(row->DataProvider.saveStable(cache,JsonParser.parseString(row[1]),output.getOutputFolder().resolve(row[0]))).toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "MITE R196 inhabited host assets"; }
}
