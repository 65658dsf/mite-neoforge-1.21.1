package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Keep modern terrain/biomes/structures while replacing the mineral entry points. @author NingMeng123 */
public final class OreWorldgenProvider implements DataProvider {
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;
    public OreWorldgenProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output; this.existingFiles = existingFiles;
    }
    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String dimension : List.of("overworld", "underworld", "nether")) {
            String id = "ores_" + dimension;
            save(work, cache, "data/mite/worldgen/configured_feature/" + id + ".json",
                    "{\"type\":\"mite:r196_ores\",\"config\":{\"dimension\":\"" + dimension + "\"}}");
            save(work, cache, "data/mite/worldgen/placed_feature/" + id + ".json",
                    "{\"feature\":\"mite:" + id + "\",\"placement\":[]}");
            String biomes = dimension.equals("underworld") ? "mite:underworld" : "#minecraft:is_" + dimension;
            save(work, cache, "data/mite/neoforge/biome_modifier/add_" + id + ".json",
                    "{\"type\":\"neoforge:add_features\",\"biomes\":\"" + biomes
                            + "\",\"features\":[\"mite:" + id + "\"],\"step\":\"underground_ores\"}");
        }
        remove(work, cache, "overworld", List.of("ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle", "ore_iron_small",
                "ore_copper", "ore_copper_large", "ore_gold", "ore_gold_lower", "ore_gold_extra", "ore_redstone", "ore_redstone_lower",
                "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried",
                "ore_emerald", "ore_dirt", "ore_gravel", "ore_infested"));
        remove(work, cache, "nether", List.of("ore_gold_nether", "ore_gold_deltas", "ore_quartz_nether", "ore_quartz_deltas"));
        save(work, cache, "data/mite/tags/worldgen/biome/has_emerald_ore.json", """
                {"replace":false,"values":["minecraft:cherry_grove","minecraft:frozen_peaks","minecraft:grove",
                  "minecraft:jagged_peaks","minecraft:meadow","minecraft:snowy_slopes","minecraft:stony_peaks",
                  "minecraft:windswept_forest","minecraft:windswept_gravelly_hills","minecraft:windswept_hills"]}
                """);
        for (String preset : List.of("overworld", "amplified", "large_biomes")) {
            String path = "data/minecraft/worldgen/noise_settings/" + preset + ".json";
            JsonObject vanilla = resource(path);
            vanilla.addProperty("ore_veins_enabled", false);
            save(work, cache, path, vanilla.toString());
        }
        // Keep the fossil feature/structure shape. Its independent coal/diamond overlay becomes bone,
        // so the preserved modern structure cannot bypass the source mineral rarity and depth.
        for (String fossil : List.of("fossil_coal", "fossil_diamonds")) {
            String path = "data/minecraft/worldgen/processor_list/" + fossil + ".json";
            JsonObject vanilla = resource(path);
            JsonArray processors = vanilla.getAsJsonArray("processors");
            List<com.google.gson.JsonElement> retained = new ArrayList<>();
            for (var processor : processors) {
                if (!processor.getAsJsonObject().get("processor_type").getAsString().equals("minecraft:rule")) retained.add(processor);
            }
            JsonArray adjusted = new JsonArray();
            for (var processor : retained) {
                if (processor.getAsJsonObject().get("processor_type").getAsString().equals("minecraft:protected_blocks")) {
                    adjusted.add(JsonParser.parseString("{\"processor_type\":\"minecraft:rule\",\"rules\":[{\"input_predicate\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:coal_ore\"},\"location_predicate\":{\"predicate_type\":\"minecraft:always_true\"},\"output_state\":{\"Name\":\"minecraft:bone_block\",\"Properties\":{\"axis\":\"y\"}}}]}"));
                }
                adjusted.add(processor);
            }
            vanilla.add("processors", adjusted);
            save(work, cache, path, vanilla.toString());
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private void remove(List<CompletableFuture<?>> work, CachedOutput cache, String dimension, List<String> features) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoforge:remove_features");
        json.addProperty("biomes", "#minecraft:is_" + dimension);
        JsonArray array = new JsonArray();
        features.forEach(name -> array.add("minecraft:" + name));
        json.add("features", array);
        save(work, cache, "data/mite/neoforge/biome_modifier/remove_vanilla_ores_" + dimension + ".json", json.toString());
    }
    private JsonObject resource(String path) {
        ResourceLocation location = ResourceLocation.withDefaultNamespace(path.substring("data/minecraft/".length()));
        var resource = existingFiles.getResourceStack(location, PackType.SERVER_DATA).stream()
                .filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla resource: " + path));
        try (InputStream input = resource.open()) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) { throw new IllegalStateException("Cannot read worldgen source: " + path, exception); }
    }
    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        work.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE R196 mineral generation with modern terrain"; }
}
