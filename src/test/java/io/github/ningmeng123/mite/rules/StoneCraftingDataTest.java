package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent resource checks for the modern stone processing policy. @author NingMeng123 */
class StoneCraftingDataTest {
    private static JsonObject recipes(String name) throws Exception {
        try (var reader = new InputStreamReader(StoneCraftingDataTest.class.getResourceAsStream(
                "/mite/" + name + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes");
        }
    }

    private static Map<String, JsonObject> existing() throws Exception {
        var result = new LinkedHashMap<String, JsonObject>();
        for (String name : new String[]{"016", "021", "024", "027-armor", "027-ranged", "027-functional",
                "027-storage", "028-weapons", "028-utility", "028-decorative", "equipment-upgrades",
                "material-processing", "r196-common", "deepslate", "phantom-blades", "modern-stone-walls"}) {
            for (var entry : recipes("timed-crafting-" + name).entrySet()) {
                if (name.equals("r196-common")) result.putIfAbsent(entry.getKey(), entry.getValue().getAsJsonObject());
                else result.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
        return result;
    }

    @Test void allNewRulesUseDisclosedExistingBenchmarksWithoutChangingOldRules() throws Exception {
        var old = existing();
        var added = recipes("timed-crafting-stone-family");
        assertEquals(91, added.size());
        for (var row : added.entrySet()) {
            assertFalse(old.containsKey(row.getKey()), "Previously explicit timing must remain outside this completion file: " + row.getKey());
            assertTrue(row.getKey().startsWith("minecraft:"));
            var data = row.getValue().getAsJsonObject();
            var benchmark = old.get(data.get("benchmark").getAsString());
            assertNotNull(benchmark, "Every adaptation cites an existing explicit rule: " + row.getKey());
            float expected = (float) (benchmark.get("difficulty").getAsFloat() * data.get("factor").getAsDouble());
            assertEquals(expected, data.get("difficulty").getAsFloat(), row.getKey());
            assertTrue(expected > 0 && Float.isFinite(expected));
            assertTrue(data.get("required_bench").isJsonNull(), "Stone processing introduces no metal workbench gate");
            assertFalse(data.get("operation").getAsString().isBlank());
        }
    }

    @Test void masonryOperationsAndMaterialsKeepDistinctWholeBatchDifficulties() throws Exception {
        var data = recipes("timed-crafting-stone-family");
        assertDifficulty(data, "granite_slab", 600F);
        assertDifficulty(data, "granite_stairs", 1200F);
        assertDifficulty(data, "granite_wall", 1200F);
        assertDifficulty(data, "sandstone_wall", 480F);
        assertDifficulty(data, "red_sandstone_wall", 480F);
        assertDifficulty(data, "stone_brick_wall", 900F);
        assertDifficulty(data, "mossy_stone_brick_wall", 900F);
        assertDifficulty(data, "stone_stairs", 1440.00012F);
        assertDifficulty(data, "polished_tuff", 960.00006F);
        assertDifficulty(data, "chiseled_tuff", 100F);
        assertDifficulty(data, "mud_bricks", 840F);
        assertDifficulty(data, "prismarine_bricks", 1890F);
        assertDifficulty(data, "quartz_bricks", 14400F);
        assertDifficulty(data, "polished_blackstone_button", 240.00002F);
        assertDifficulty(data, "polished_blackstone_pressure_plate", 480.00003F);
        for (String id : data.keySet()) {
            assertFalse(id.contains("wood") || id.contains("glass") || id.contains("wool") || id.contains("copper"));
            assertFalse(id.contains("stonecutting"), "Stonecutting is not relabelled as timed crafting");
        }
    }

    private static void assertDifficulty(JsonObject data, String id, float expected) {
        assertEquals(expected, data.getAsJsonObject("minecraft:" + id).get("difficulty").getAsFloat(), id);
    }
}
