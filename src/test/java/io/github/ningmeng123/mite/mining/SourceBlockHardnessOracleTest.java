package io.github.ningmeng123.mite.mining;

import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.rules.BlockHardnessRules;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Compare the adapter with independently captured R196 registration values. @author NingMeng123 */
class SourceBlockHardnessOracleTest {
    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }

    @TestFactory
    Stream<DynamicTest> mappedHardnessMatchesOriginalRegistrationAndMetadataOverrides() throws IOException {
        JsonObject registry = read("reference-data/runtime-registry.json");
        JsonObject spec = read("spec/r196-block-hardness.json");
        assertEquals(registry.get("archive_sha256"), spec.get("archive_sha256"));
        Map<Integer, Float> original = new HashMap<>();
        registry.getAsJsonArray("blocks").forEach(element -> {
            JsonObject block = element.getAsJsonObject();
            original.put(block.get("id").getAsInt(), block.getAsJsonObject("scalars").get("aqz.cG").getAsFloat());
        });
        // BlockSlab.java applies a dedicated material table before multiplying by 0.5.
        float[] stoneSlab = {1.2F, .5F, .4F, 1F, 1F, 1F, 1F, .4F};
        return spec.getAsJsonArray("rules").asList().stream().map(element -> element.getAsJsonObject())
                .flatMap(row -> row.getAsJsonArray("modern_ids").asList().stream().map(idElement -> {
                    String id = idElement.getAsString();
                    return DynamicTest.dynamicTest(id, () -> {
                        String kind = row.get("kind").getAsString();
                        int legacyId = row.get("legacy_id").getAsInt();
                        float expected = kind.equals("slab")
                                ? legacyId == 44 ? stoneSlab[row.get("legacy_metadata").getAsInt()] : .4F
                                : original.get(legacyId);
                        assertEquals(expected, row.get("hardness").getAsFloat(), "Spec must match the original, not modern raw hardness");
                        if (kind.equals("layers") || kind.equals("vine_faces")) return; // State formulas run in GameTest.
                        String path = id.substring("minecraft:".length());
                        assertEquals(expected, BlockHardnessRules.effectiveHardness("minecraft", path, false, 123F));
                        assertEquals(kind.equals("slab") ? expected * 2F : expected,
                                BlockHardnessRules.effectiveHardness("minecraft", path, true, 123F));
                        assertEquals(-1F, BlockHardnessRules.effectiveHardness("minecraft", path, false, -1F));
                        assertEquals(123F, BlockHardnessRules.effectiveHardness("another_mod", path, false, 123F));
                    });
                }));
    }

    @Test
    void placementUsesSourceMetadataZeroWithoutChangingMiningStateHardness() {
        assertEquals(.05F, BlockHardnessRules.placementHardness("minecraft", "snow", .4F));
        assertEquals(.2F, BlockHardnessRules.placementHardness("minecraft", "vine", .8F));
        for (String path : new String[]{"smooth_stone_slab", "sandstone_slab", "cobblestone_slab", "brick_slab",
                "stone_brick_slab", "nether_brick_slab", "quartz_slab"}) {
            // uf queries metadata 0 on old group 44, which always selects the stone model.
            assertEquals(1.2F, BlockHardnessRules.placementHardness("minecraft", path, 2F));
            assertEquals(2F, BlockHardnessRules.placementHardness("another_mod", path, 2F));
        }
        for (String path : new String[]{"oak_slab", "spruce_slab", "birch_slab", "jungle_slab"})
            assertEquals(.4F, BlockHardnessRules.placementHardness("minecraft", path, .8F));
        assertEquals(2.4F, BlockHardnessRules.placementHardness("minecraft", "obsidian", 2.4F));
        assertEquals(17F, BlockHardnessRules.placementHardness("minecraft", "bamboo_slab", 17F));
        assertEquals(-1F, BlockHardnessRules.placementHardness("minecraft", "snow", -1F));
        assertEquals(.5F, BlockHardnessRules.effectiveHardness("minecraft", "sandstone_slab", false, 17F));
        assertEquals(1F, BlockHardnessRules.effectiveHardness("minecraft", "sandstone_slab", true, 17F));
    }

    @Test
    void specAndProductionCoverExactlyTheSameDirectCounterparts() throws IOException {
        Map<String, Float> registered = new HashMap<>();
        Map<String, Float> slabs = new HashMap<>();
        Set<String> unique = new HashSet<>();
        for (var element : read("spec/r196-block-hardness.json").getAsJsonArray("rules")) {
            JsonObject row = element.getAsJsonObject();
            for (var idElement : row.getAsJsonArray("modern_ids")) {
                String id = idElement.getAsString();
                assertTrue(id.startsWith("minecraft:"));
                assertTrue(unique.add(id), "Duplicate modern counterpart " + id);
                switch (row.get("kind").getAsString()) {
                    case "registered" -> registered.put(id.substring(10), row.get("hardness").getAsFloat());
                    case "slab" -> slabs.put(id.substring(10), row.get("hardness").getAsFloat());
                    case "layers", "vine_faces" -> { }
                    default -> fail("Unknown source hardness rule " + row);
                }
            }
        }
        assertEquals(registered, BlockHardnessRules.registeredHardness());
        assertEquals(slabs, BlockHardnessRules.slabHardness());
        for (String modern : new String[]{"stone_slab", "smooth_sandstone", "deepslate", "crying_obsidian",
                "acacia_planks", "tinted_glass", "red_stained_glass", "petrified_oak_slab"}) {
            assertEquals(7.25F, BlockHardnessRules.effectiveHardness("minecraft", modern, false, 7.25F),
                    "Unmapped modern material must retain its own hardness: " + modern);
        }
    }
}
