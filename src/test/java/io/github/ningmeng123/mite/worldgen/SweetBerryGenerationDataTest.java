package io.github.ningmeng123.mite.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Only natural patch frequency changes; each original placement chain stays intact. @author NingMeng123 */
class SweetBerryGenerationDataTest {
    @Test void allThreeNaturalEntrypointsRetainOneFifthOfTheirOriginalFrequency() {
        for (var entry : Map.of("patch_berry_common", 160, "patch_berry_rare", 1920, "patch_berry_bush", 5).entrySet()) {
            JsonObject placed = feature(entry.getKey());
            assertEquals("minecraft:patch_berry_bush", placed.get("feature").getAsString());
            JsonObject rarity = placed.getAsJsonArray("placement").get(0).getAsJsonObject();
            assertEquals("minecraft:rarity_filter", rarity.get("type").getAsString());
            assertEquals(entry.getValue().intValue(), rarity.get("chance").getAsInt());
        }
    }

    @Test void wildPatchLocationAndVillageFeaturePlacementRemainUnchanged() {
        for (String name : List.of("patch_berry_common", "patch_berry_rare")) {
            var placement = feature(name).getAsJsonArray("placement");
            assertEquals(4, placement.size());
            assertEquals("minecraft:in_square", placement.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals("minecraft:heightmap", placement.get(2).getAsJsonObject().get("type").getAsString());
            assertEquals("WORLD_SURFACE_WG", placement.get(2).getAsJsonObject().get("heightmap").getAsString());
            assertEquals("minecraft:biome", placement.get(3).getAsJsonObject().get("type").getAsString());
        }
        assertEquals(1, feature("patch_berry_bush").getAsJsonArray("placement").size());
    }

    private static JsonObject feature(String name) {
        String path = "/data/minecraft/worldgen/placed_feature/" + name + ".json";
        var stream = SweetBerryGenerationDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing placement override: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Cannot read placement override: " + path, exception);
        }
    }
}
