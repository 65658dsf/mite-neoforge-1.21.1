package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Preserves modern recipe batches that now need several R196-size stacks. @author NingMeng123 */
public final class StackingRecipeAssetsProvider implements DataProvider {
    private final Path output;

    public StackingRecipeAssetsProvider(PackOutput output) {
        this.output = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                StackingRecipeAssetsProvider.class.getResourceAsStream("/mite/stacking-recipes-030.json")), StandardCharsets.UTF_8)) {
            for (var value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries")) {
                JsonObject row = value.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
                String sourceType = row.get("original_type").getAsString();
                String targetType = row.get("adapted_type").getAsString();
                if (!id.getNamespace().equals("minecraft") || !switch (sourceType) {
                    case "minecraft:crafting_shaped" -> targetType.equals("mite:stacking_shaped");
                    case "minecraft:crafting_shapeless" -> targetType.equals("mite:stacking_shapeless");
                    default -> false;
                }) {
                    throw new IllegalStateException("Unexpected native batch serializer adaptation: " + id);
                }
                JsonObject recipe = row.getAsJsonObject("original_json").deepCopy();
                recipe.addProperty("type", targetType);
                writes.add(DataProvider.saveStable(cache, recipe,
                        output.resolve("data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json")));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot generate native batch recipe adaptations", failure);
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "MITE stack limits with preserved modern recipe batches";
    }
}
