package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
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

/** Modern vanilla dungeon templates within the existing accessible underworld cavern. @author NingMeng123 */
public final class UnderworldDungeonWorldgenProvider implements DataProvider {
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;

    public UnderworldDungeonWorldgenProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output;
        this.existingFiles = existingFiles;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        JsonObject chambers = vanilla("worldgen/structure/trial_chambers.json");
        chambers.addProperty("biomes", "#mite:has_structure/underworld_trial_chambers");
        chambers.add("start_height", JsonParser.parseString("""
                {"type":"minecraft:uniform","min_inclusive":{"absolute":168},"max_inclusive":{"absolute":232}}
                """));
        JsonObject padding = new JsonObject();
        padding.addProperty("bottom", ModernUnderworldTerrain.CAVERN_OFFSET - ModernUnderworldTerrain.MIN_Y);
        padding.addProperty("top", ModernUnderworldTerrain.MAX_Y_EXCLUSIVE - ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE);
        chambers.add("dimension_padding", padding);
        // This explicit terrain generator has no Beardifier. Templates provide their own shell and
        // carve their own rooms; do not pretend ENCAPSULATE would reshape the surrounding caverns.
        chambers.addProperty("terrain_adaptation", "none");
        save(work, cache, "worldgen/structure/underworld_trial_chambers.json", chambers);

        JsonObject set = vanilla("worldgen/structure_set/trial_chambers.json");
        set.getAsJsonArray("structures").get(0).getAsJsonObject().addProperty("structure", "mite:underworld_trial_chambers");
        save(work, cache, "worldgen/structure_set/underworld_trial_chambers.json", set);
        save(work, cache, "tags/worldgen/biome/has_structure/underworld_trial_chambers.json",
                "{\"replace\":false,\"values\":[\"mite:underworld\"]}");

        // Keep MONSTER_ROOM's solid floor/ceiling and 1..5 cave-opening checks. Candidate placement
        // never adds artificial caves, and stays above the water band and below the ceiling stratum.
        save(work, cache, "worldgen/placed_feature/underworld_monster_room.json", """
                {"feature":"minecraft:monster_room","placement":[
                  {"type":"minecraft:count","count":10},
                  {"type":"minecraft:in_square"},
                  {"type":"minecraft:height_range","height":{"type":"minecraft:uniform",
                    "min_inclusive":{"absolute":154},"max_inclusive":{"absolute":300}}},
                  {"type":"minecraft:biome"}]}
                """);
        save(work, cache, "neoforge/biome_modifier/add_underworld_monster_rooms.json", """
                {"type":"neoforge:add_features","biomes":"mite:underworld",
                  "features":["mite:underworld_monster_room"],"step":"underground_structures"}
                """);
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private JsonObject vanilla(String path) {
        var resource = existingFiles.getResourceStack(ResourceLocation.withDefaultNamespace(path), PackType.SERVER_DATA).stream()
                .filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla dungeon resource: " + path));
        try (InputStream input = resource.open()) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read pinned vanilla dungeon resource: " + path, exception);
        }
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        save(work, cache, path, JsonParser.parseString(json).getAsJsonObject());
    }

    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonObject json) {
        work.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve("data/mite/" + path)));
    }

    @Override public String getName() { return "MITE underworld trial chambers and monster rooms"; }
}
