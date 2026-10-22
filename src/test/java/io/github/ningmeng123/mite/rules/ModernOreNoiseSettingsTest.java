package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** The shipped 1.21.1 JSON is the independent golden for preserving every modern terrain field. @author NingMeng123 */
class ModernOreNoiseSettingsTest {
    @ParameterizedTest
    @ValueSource(strings = {"overworld", "amplified", "large_biomes"})
    void oreVeinFlagAndExplicitUpperCaveRoutingAreTheOnlyModernNoiseChanges(String name) throws IOException {
        String resource = "data/minecraft/worldgen/noise_settings/" + name + ".json";
        Path generated = Path.of("src/generated/resources").resolve(resource);
        assertTrue(Files.isRegularFile(generated), "Data generation must create the actual modern noise override");
        JsonObject actual = JsonParser.parseString(Files.readString(generated)).getAsJsonObject();
        JsonObject original = vanillaResource(resource);
        assertTrue(original.get("ore_veins_enabled").getAsBoolean(), "The unmodified vanilla preset enables its separate copper/iron veins");
        assertFalse(actual.get("ore_veins_enabled").getAsBoolean(), "The separate vanilla ore route must be explicitly disabled");
        JsonObject router = actual.getAsJsonObject("noise_router");
        int[] restored = {0, 0};
        router.add("final_density", restoreCaveRouting(router.get("final_density"), "minecraft:cave_cheese", 0.5, restored));
        assertEquals(1, restored[0], "Exactly one native cheese noise receives the explicit upper-cave adjustment");
        assertEquals(2, restored[1], "Both near-surface and underground branches use the separately adjusted entrance function");
        original.remove("ore_veins_enabled"); actual.remove("ore_veins_enabled");
        assertEquals(original, actual, "Removing the explicit cheese wrapper and two entrance references restores every remaining modern field");
    }

    @Test
    void adjustedEntranceFunctionOnlyNarrowsTheNativeSpaghettiBranch() throws IOException {
        Path generated = Path.of("src/generated/resources/data/mite/worldgen/density_function/upper_cave_entrances.json");
        assertTrue(Files.isRegularFile(generated));
        JsonObject actual = JsonParser.parseString(Files.readString(generated)).getAsJsonObject();
        JsonObject original = vanillaResource("data/minecraft/worldgen/density_function/overworld/caves/entrances.json");
        JsonObject branches = actual.getAsJsonObject("argument");
        JsonObject wrapper = branches.getAsJsonObject("argument2");
        assertEquals("minecraft:add", wrapper.get("type").getAsString());
        assertEquals(expectedBias(0.035), wrapper.get("argument2"), "Only the whole spaghetti branch receives a small tapered positive bias");
        branches.add("argument2", wrapper.get("argument1"));
        assertEquals(original, actual, "Wide entrances, native spaghetti-3D noises, roughness, thickness, scales and gradients remain intact");
    }

    private static JsonObject vanillaResource(String resource) throws IOException {
        try (ZipFile vanilla = new ZipFile(Path.of("build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar").toFile())) {
            var entry = vanilla.getEntry(resource);
            assertNotNull(entry, "The independent bundled Minecraft resource must contain the requested golden");
            try (var input = vanilla.getInputStream(entry)) {
                return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    private static JsonElement restoreCaveRouting(JsonElement density, String noise, double maximumBias, int[] restored) {
        if (density.isJsonObject()) {
            JsonObject object = density.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:add")
                    && object.get("argument1").isJsonObject()) {
                JsonObject candidate = object.getAsJsonObject("argument1");
                if (candidate.has("type") && candidate.get("type").getAsString().equals("minecraft:noise")
                        && candidate.has("noise") && candidate.get("noise").getAsString().equals(noise)) {
                    assertEquals(expectedBias(maximumBias), object.get("argument2"), "The sole cave bias has fixed, tapered height bounds");
                    restored[0]++;
                    return candidate.deepCopy();
                }
            }
            JsonObject adjusted = new JsonObject();
            object.entrySet().forEach(entry -> adjusted.add(entry.getKey(), restoreCaveRouting(entry.getValue(), noise, maximumBias, restored)));
            return adjusted;
        }
        if (density.isJsonArray()) {
            JsonArray adjusted = new JsonArray();
            density.getAsJsonArray().forEach(element -> adjusted.add(restoreCaveRouting(element, noise, maximumBias, restored)));
            return adjusted;
        }
        if (restored.length > 1 && density.isJsonPrimitive() && density.getAsJsonPrimitive().isString()
                && density.getAsString().equals("mite:upper_cave_entrances")) {
            restored[1]++;
            return new JsonPrimitive("minecraft:overworld/caves/entrances");
        }
        return density.deepCopy();
    }

    private static JsonObject expectedBias(double maximumBias) {
        return JsonParser.parseString("""
                {"type":"minecraft:min",
                  "argument1":{"type":"minecraft:y_clamped_gradient","from_y":-24,"to_y":8,"from_value":0.0,"to_value":%s},
                  "argument2":{"type":"minecraft:y_clamped_gradient","from_y":80,"to_y":112,"from_value":%s,"to_value":0.0}}
                """.formatted(maximumBias, maximumBias)).getAsJsonObject();
    }
}
