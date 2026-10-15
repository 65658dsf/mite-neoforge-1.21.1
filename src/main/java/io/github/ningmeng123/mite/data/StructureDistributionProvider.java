package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.rules.StructureDistributionRules;
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

/** Source density with the pinned modern structure choices and biome families. @author NingMeng123 */
public final class StructureDistributionProvider implements DataProvider {
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;

    public StructureDistributionProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output;
        this.existingFiles = existingFiles;
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        spread(work, cache, "villages", StructureDistributionRules.VILLAGE_SALT);
        for (String name : List.of("desert_pyramids", "jungle_temples", "swamp_huts"))
            spread(work, cache, name, StructureDistributionRules.SCATTERED_SALT);

        JsonObject mineshafts = vanilla("mineshafts");
        // The source helper performs the 0.5% draw and the distance draw in their original stream.
        // The modern placement must not independently reduce frequency a second time.
        mineshafts.getAsJsonObject("placement").addProperty("frequency", 1.0);
        save(work, cache, "minecraft/worldgen/structure_set/mineshafts.json", mineshafts);

        JsonObject strongholds = vanilla("strongholds");
        JsonObject rings = strongholds.getAsJsonObject("placement");
        rings.addProperty("count", StructureDistributionRules.STRONGHOLD_COUNT);
        rings.addProperty("distance", StructureDistributionRules.STRONGHOLD_DISTANCE);
        // The coordinate Mixin supplies the source radius formula. This metadata alone is insufficient.
        rings.addProperty("spread", StructureDistributionRules.STRONGHOLD_COUNT);
        save(work, cache, "minecraft/worldgen/structure_set/strongholds.json", strongholds);

        save(work, cache, "mite/tags/worldgen/biome/has_structure/r196_mineshaft_hills.json", JsonParser.parseString("""
                {"replace":false,"values":["#minecraft:is_hill","#minecraft:is_mountain",
                  "minecraft:grove","minecraft:windswept_savanna","minecraft:savanna_plateau","#minecraft:is_badlands"]}
                """).getAsJsonObject());
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private void spread(List<CompletableFuture<?>> work, CachedOutput cache, String name, int salt) {
        JsonObject resource = vanilla(name);
        JsonObject placement = resource.getAsJsonObject("placement");
        placement.addProperty("spacing", StructureDistributionRules.SPACING);
        placement.addProperty("separation", StructureDistributionRules.SEPARATION);
        placement.addProperty("salt", salt);
        save(work, cache, "minecraft/worldgen/structure_set/" + name + ".json", resource);
    }

    private JsonObject vanilla(String name) {
        var resource = existingFiles.getResourceStack(ResourceLocation.withDefaultNamespace("worldgen/structure_set/" + name + ".json"),
                PackType.SERVER_DATA).stream().filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla structure set: " + name));
        try (InputStream input = resource.open()) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read pinned vanilla structure set: " + name, exception);
        }
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonObject value) {
        work.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve("data/" + path)));
    }

    @Override public String getName() { return "MITE R196 structure distribution"; }
}
