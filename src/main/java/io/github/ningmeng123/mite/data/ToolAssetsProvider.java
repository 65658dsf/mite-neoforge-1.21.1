package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Models for the registered R196 tools using unchanged source sprites. @author NingMeng123 */
public final class ToolAssetsProvider implements DataProvider {
    private final Path output;

    public ToolAssetsProvider(PackOutput output) {
        this.output = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (MiteTools.Definition tool : MiteTools.definitions()) {
            // These two established models remain in src/main/resources.
            if (tool.id().equals("mite:flint_hatchet") || tool.id().equals("minecraft:wooden_sword")) continue;
            ResourceLocation id = ResourceLocation.parse(tool.id());
            JsonObject model = new JsonObject();
            boolean shears = tool.family().name().equals("SHEARS");
            model.addProperty("parent", "minecraft:item/" + (shears ? "generated" : "handheld"));
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "mite:item/" + id.getPath());
            model.add("textures", textures);
            Path path = output.resolve("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json");
            work.add(DataProvider.saveStable(cache, model, path));
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "MITE R196 tool models";
    }
}
