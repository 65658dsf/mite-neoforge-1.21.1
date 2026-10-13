package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonArray;
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

/** Modern fossil structures stay enabled; only their coal/diamond overlays cease adding bypass ores. @author NingMeng123 */
class OreFossilAdaptationTest {
    @ParameterizedTest @ValueSource(strings = {"fossil_coal", "fossil_diamonds"})
    void fossilOverlayReplacesCoalWithBoneWithoutChangingRotationOrProtectedBlocks(String name) throws IOException {
        String resource = "data/minecraft/worldgen/processor_list/" + name + ".json";
        JsonArray actual = JsonParser.parseString(Files.readString(Path.of("src/generated/resources").resolve(resource))).getAsJsonObject().getAsJsonArray("processors");
        JsonArray original;
        try (ZipFile vanilla = new ZipFile(Path.of("build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar").toFile())) {
            try (var input = vanilla.getInputStream(vanilla.getEntry(resource))) {
                original = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("processors");
            }
        }
        assertEquals(original.get(0), actual.get(0), "Modern fossil overlay integrity/rotation stays identical");
        assertEquals(original.get(original.size() - 1), actual.get(actual.size() - 1), "Modern protected-block rules stay identical");
        int conversion = 0;
        for (var element : actual) {
            JsonObject processor = element.getAsJsonObject();
            if (!processor.get("processor_type").getAsString().equals("minecraft:rule")) continue;
            for (var ruleElement : processor.getAsJsonArray("rules")) {
                JsonObject rule = ruleElement.getAsJsonObject();
                String output = rule.getAsJsonObject("output_state").get("Name").getAsString();
                assertFalse(output.endsWith("_ore"), "A modern fossil processor may not silently add a second mineral progression route");
                if (rule.getAsJsonObject("input_predicate").get("block").getAsString().equals("minecraft:coal_ore")) {
                    assertEquals("minecraft:bone_block", output); conversion++;
                }
            }
        }
        assertEquals(1, conversion, "The coal-bearing structure palette must be explicitly converted once");
    }
}
