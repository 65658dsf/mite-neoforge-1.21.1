package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** The shipped 1.21.1 JSON is the independent golden for preserving every modern terrain field. @author NingMeng123 */
class ModernOreNoiseSettingsTest {
    @ParameterizedTest
    @ValueSource(strings = {"overworld", "amplified", "large_biomes"})
    void oreVeinFlagIsTheOnlyDifferenceFromTheShippedModernNoisePreset(String name) throws IOException {
        String resource = "data/minecraft/worldgen/noise_settings/" + name + ".json";
        Path generated = Path.of("src/generated/resources").resolve(resource);
        assertTrue(Files.isRegularFile(generated), "Data generation must create the actual modern noise override");
        JsonObject actual = JsonParser.parseString(Files.readString(generated)).getAsJsonObject();
        JsonObject original;
        try (ZipFile vanilla = new ZipFile(Path.of("build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar").toFile())) {
            var entry = vanilla.getEntry(resource);
            assertNotNull(entry, "The independent bundled Minecraft resource must contain this modern preset");
            try (var input = vanilla.getInputStream(entry)) {
                original = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        assertTrue(original.get("ore_veins_enabled").getAsBoolean(), "The unmodified vanilla preset enables its separate copper/iron veins");
        assertFalse(actual.get("ore_veins_enabled").getAsBoolean(), "The separate vanilla ore route must be explicitly disabled");
        original.remove("ore_veins_enabled"); actual.remove("ore_veins_enabled");
        assertEquals(original, actual, "Every remaining density router, cave, biome, aquifer, surface, random and height field must remain exactly 1.21.1");
    }
}
