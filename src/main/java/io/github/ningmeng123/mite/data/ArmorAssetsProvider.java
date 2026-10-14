package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Model references preserve the user's source-pack sprites and wearable layers. @author NingMeng123 */
public final class ArmorAssetsProvider implements DataProvider {
    private final Path output;
    public ArmorAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        MiteArmorContent.definitions().stream().filter(MiteArmorContent.Definition::isNew).forEach(d -> model(cache, d.id(), work));
        MiteArmorContent.chains().stream().filter(d -> d.id().startsWith("mite:")).forEach(d -> model(cache, d.id(), work));
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private void model(CachedOutput cache, String key, List<CompletableFuture<?>> work) {
        ResourceLocation id = ResourceLocation.parse(key);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "mite:item/" + id.getPath());
        model.add("textures", textures);
        work.add(DataProvider.saveStable(cache, model, output.resolve("assets/mite/models/item/" + id.getPath() + ".json")));
    }
    @Override public String getName() { return "MITE R196 armor and chain models"; }
}
