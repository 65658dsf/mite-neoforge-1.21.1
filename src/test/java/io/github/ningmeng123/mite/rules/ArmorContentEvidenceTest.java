package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently stated source boundaries; these tests never initialize Minecraft registries. @author NingMeng123 */
class ArmorContentEvidenceTest {
    private static final JsonObject DATA = resource("armor-content-027.json");
    private static JsonObject resource(String file) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ArmorContentEvidenceTest.class.getResourceAsStream(
                "/mite/" + file)), StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static JsonObject item(String id) {
        return DATA.getAsJsonArray("items").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("id").getAsString().equals(id)).findFirst().orElseThrow();
    }
    private static JsonObject recipe(String id) {
        return DATA.getAsJsonArray("recipes").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("id").getAsString().equals(id)).findFirst().orElseThrow();
    }
    @Test void allCatalogEquipmentIsAccountedForWithoutCountingParentPages() {
        JsonArray items = DATA.getAsJsonArray("items");
        assertEquals(65, items.size());
        assertEquals(61L, items.asList().stream().filter(e -> e.getAsJsonObject().get("catalog_leaf").getAsBoolean()).count());
        assertEquals(57L, items.asList().stream().filter(e -> e.getAsJsonObject().get("id").getAsString().startsWith("mite:")).count());
        assertEquals(5L, items.asList().stream().filter(e -> e.getAsJsonObject().get("horse").getAsBoolean()).count());
        assertEquals(8, DATA.getAsJsonArray("chains").size());
        assertEquals(70, DATA.getAsJsonArray("recipes").size());
    }
    @Test void literalArmorDurabilitiesComeFromSourceComponentsAndMaterialScale() {
        String[] types = {"boots", "leggings", "chestplate", "helmet"};
        int[] copper = {32, 56, 64, 40}, mithril = {512, 896, 1024, 640}, adamantium = {2048, 3584, 4096, 2560};
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(copper[slot], item("mite:copper_" + types[slot]).get("base_durability").getAsInt());
            assertEquals(copper[slot] / 2, item("mite:copper_chain_" + types[slot]).get("base_durability").getAsInt());
            assertEquals(mithril[slot], item("mite:mithril_" + types[slot]).get("base_durability").getAsInt());
            assertEquals(adamantium[slot], item("mite:adamantium_" + types[slot]).get("base_durability").getAsInt());
        }
    }
    @Test void materialCapsAreNotMistakenForMaximumPlayerLevel() {
        Map<String, String> maximum = Map.of("copper", "EXCELLENT", "silver", "EXCELLENT", "golden_chain", "SUPERB",
                "rusted_iron", "POOR", "ancient_metal", "MASTERWORK", "mithril", "LEGENDARY", "adamantium", "LEGENDARY");
        maximum.forEach((stem, cap) -> assertEquals(cap, item("mite:" + stem + "_helmet").get("maximum_quality").getAsString()));
        assertEquals("POOR", item("mite:rusted_iron_helmet").get("default_quality").getAsString());
        assertEquals("AVERAGE", item("mite:copper_helmet").get("default_quality").getAsString());
    }
    @Test void rustedPlateHasRepairDifficultyOverrideAndNoInventedManufacturing() {
        assertEquals(2000F, item("mite:rusted_iron_helmet").get("lowest_difficulty").getAsFloat());
        assertEquals(3200F, item("mite:rusted_iron_chestplate").get("lowest_difficulty").getAsFloat());
        assertEquals(0, item("mite:rusted_iron_helmet").getAsJsonArray("source_orders").size());
        assertEquals(888.88885F, recipe("mite:rusted_iron_chain_helmet").get("difficulty").getAsFloat());
        assertEquals("rusted_iron", recipe("mite:rusted_iron_chain_helmet").get("required_bench").getAsString());
    }
    @Test void ironChainPreservesItsSourceFourNuggetDiamondAndReverseQuantity() {
        JsonObject chain = recipe("minecraft:chain");
        assertEquals(3, chain.get("width").getAsInt());
        assertEquals(3, chain.get("height").getAsInt());
        assertEquals(4L, chain.getAsJsonArray("ingredients").asList().stream().filter(e -> !e.isJsonNull()).count());
        assertTrue(chain.getAsJsonArray("ingredients").asList().stream().filter(e -> !e.isJsonNull()).allMatch(e -> e.getAsString().equals("minecraft:iron_nugget")));
        JsonObject reverse = recipe("mite:chain_to_nuggets");
        assertEquals(4, reverse.get("count").getAsInt());
        assertEquals("minecraft:iron_nugget", reverse.get("output").getAsString());
        assertTrue(reverse.get("required_bench").isJsonNull());
    }
    @Test void sourceManufacturingCostsKeepLargeMaterialGapsAndFloatPrecision() {
        assertEquals(3200F, recipe("mite:copper_chestplate").get("difficulty").getAsFloat());
        assertEquals(12800F, recipe("mite:ancient_metal_chestplate").get("difficulty").getAsFloat());
        assertEquals(51200F, recipe("mite:mithril_chestplate").get("difficulty").getAsFloat());
        assertEquals(204800F, recipe("mite:adamantium_chestplate").get("difficulty").getAsFloat());
        assertEquals(91022.22F, recipe("mite:adamantium_chain_chestplate").get("difficulty").getAsFloat());
    }
    @Test void horseProtectionAndNoDurabilityRemainSeparateFromPlayerQuality() {
        Map<String, Integer> protection = Map.of("copper", 4, "silver", 4, "ancient_metal", 5, "mithril", 6, "adamantium", 7);
        protection.forEach((metal, expected) -> {
            JsonObject horse = item("mite:" + metal + "_horse_armor");
            assertEquals(expected.intValue(), horse.get("horse_protection").getAsInt());
            assertEquals(0, horse.get("base_durability").getAsInt());
            assertTrue(horse.get("default_quality").isJsonNull());
            assertEquals(0, horse.getAsJsonArray("source_orders").size());
        });
    }
    @Test void lootSourceTotalWeightsIncludeEmptyOutcomesAndExtraBookWeight() {
        JsonObject pools = resource("armor-loot-027.json").getAsJsonObject("pools");
        Map<String, Integer> totals = Map.of("dungeon_surface", 156, "dungeon_underworld", 74, "mineshaft", 169,
                "village", 190, "desert", 68, "jungle", 63, "nether", 66);
        totals.forEach((name, expected) -> assertEquals(expected.intValue(), pools.getAsJsonObject(name).get("source_total_weight").getAsInt()));
        pools.entrySet().forEach(pair -> assertTrue(pair.getValue().getAsJsonObject().getAsJsonArray("entries").asList().stream()
                .noneMatch(e -> e.getAsJsonObject().get("item").getAsString().matches("mite:(mithril|adamantium)_horse_armor"))));
    }
}
