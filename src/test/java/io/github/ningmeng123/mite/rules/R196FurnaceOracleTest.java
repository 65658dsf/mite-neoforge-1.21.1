package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Executes modern rules against independently exported original class-bytecode observations. @author NingMeng123 */
class R196FurnaceOracleTest {
    private static final Map<Integer, String> FURNACES = Map.of(218, "mite:clay_oven", 250, "mite:large_clay_oven",
            220, "mite:sandstone_furnace", 61, "minecraft:furnace", 222, "mite:obsidian_furnace", 224, "mite:netherrack_furnace");
    private static final JsonObject SPEC = load();
    private static final JsonObject ORACLE = SPEC.getAsJsonObject("source_oracle");
    private static final Map<String, JsonObject> ITEMS = sourceItems();
    private static JsonObject load() {
        try { return JsonParser.parseString(Files.readString(Path.of("spec/furnaces-024.json"))).getAsJsonObject(); }
        catch (Exception failure) { throw new AssertionError("The committed original-bytecode furnace fixture must be available", failure); }
    }
    private static String key(int sourceId, int subtype) { return sourceId + ":" + subtype; }
    private static Map<String, JsonObject> sourceItems() {
        Map<String, JsonObject> result = new HashMap<>();
        ORACLE.getAsJsonArray("items").forEach(value -> {
            JsonObject item = value.getAsJsonObject();
            String key = key(item.get("source_item_id").getAsInt(), item.get("subtype").getAsInt());
            assertNull(result.put(key, item), "An original item/subtype pair must not mask duplicate observations");
        });
        return Map.copyOf(result);
    }
    private static Stream<Arguments> mappings() {
        return SPEC.getAsJsonArray("source_modern_mappings").asList().stream()
                .map(value -> value.getAsJsonObject()).map(value -> Arguments.of(value.get("id").getAsString(), value));
    }
    private static Stream<Arguments> profiles() {
        return ORACLE.getAsJsonArray("profiles").asList().stream().map(value -> value.getAsJsonObject())
                .map(value -> Arguments.of(FURNACES.get(value.get("source_block_id").getAsInt()), value));
    }
    private static Stream<Arguments> fuelSlots() {
        return ORACLE.getAsJsonArray("fuel_slot_cases").asList().stream().map(value -> value.getAsJsonObject())
                .map(value -> Arguments.of("furnace=" + value.get("furnace_source_id") + ", fuel=" + value.get("fuel_source_id")
                        + ":" + value.get("subtype"), value));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappings")
    void allMappedFuelAndNonFuelValuesMatchOriginalBytecodeRatherThanModernDefaults(String id, JsonObject mapping) {
        JsonObject original = ITEMS.get(key(mapping.get("source_item_id").getAsInt(), mapping.get("subtype").getAsInt()));
        assertNotNull(original, "Every modern source mapping points to a real original registry observation");
        int ticks = original.get("ticks").getAsInt(), heat = original.get("heat").getAsInt();
        boolean large = original.get("large").getAsBoolean();
        assertEquals(ticks, mapping.get("ticks").getAsInt(), "Mapping facts cannot replace the original reference observation");
        assertEquals(heat, mapping.get("heat").getAsInt());
        assertEquals(large, mapping.get("large").getAsBoolean());
        assertEquals(new R196FurnaceRules.Fuel(ticks, heat), R196FurnaceRules.fuel(id));
        assertEquals(large, R196FurnaceRules.large(id));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void allSixStageProfilesMatchTheOriginalRegisteredFurnaceObjects(String id, JsonObject original) {
        assertNotNull(id, "Every original furnace profile has an explicit modern identity");
        var actual = R196FurnaceRules.profile(id);
        assertEquals(original.get("max_heat").getAsInt(), actual.maxHeat());
        assertEquals(original.get("accepts_large").getAsBoolean(), actual.acceptsLarge());
        assertEquals(200, actual.cycleTicks(), "Original asg processing is exactly200 ticks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fuelSlots")
    void realOriginalFuelSlotAndAutomationObservationsAgreeWithTheUnifiedModernGuard(String label, JsonObject original) {
        var profile = R196FurnaceRules.profile(FURNACES.get(original.get("furnace_source_id").getAsInt()));
        JsonObject sourceFuel = ITEMS.get(key(original.get("fuel_source_id").getAsInt(), original.get("subtype").getAsInt()));
        assertNotNull(sourceFuel);
        boolean actual = R196FurnaceRules.fuelAccepted(sourceFuel.get("heat").getAsInt(), profile.maxHeat(),
                sourceFuel.get("large").getAsBoolean(), profile.acceptsLarge());
        assertEquals(original.get("slot_accepts").getAsBoolean(), actual);
        assertEquals(original.get("automation_accepts").getAsBoolean(), actual);
    }

    @Test void sourceFixtureWasObtainedWithoutStartingTheOldGameOrCreatingAWorld() {
        assertEquals("guarded_reference_bytecode_furnace", ORACLE.get("mode").getAsString());
        assertFalse(ORACLE.get("game_main_invoked").getAsBoolean());
        assertFalse(ORACLE.get("world_instantiated").getAsBoolean());
        assertTrue(ORACLE.get("professions_suppressed").getAsBoolean(), "Optional source professions cannot silently gate the default reconstruction");
        assertEquals("fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3", ORACLE.get("archive_sha256").getAsString());
        assertEquals(6, ORACLE.getAsJsonArray("profiles").size());
        assertEquals(48, ORACLE.getAsJsonArray("fuel_slot_cases").size());
        assertEquals(1200, ORACLE.getAsJsonArray("batch_cases").size());
    }
}
