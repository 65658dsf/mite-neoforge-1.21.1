package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Source setter evidence and literal curse arithmetic, independent of server integration. @author NingMeng123 */
class WitchCurseProductsTest {
    @Test void sourceProductEvidenceCoversEveryFoodWithoutInferringMicronutrients() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of("spec/witch-curse-products.json"))).getAsJsonObject();
        assertEquals("NingMeng123", data.get("author").getAsString());
        assertEquals(82, data.getAsJsonArray("products").size());
        int animal = 0, plants = 0, modern = 0;
        for (var value : data.getAsJsonArray("products")) {
            var entry = value.getAsJsonObject();
            String id = entry.get("id").getAsString();
            assertEquals(entry.get("animal_product").getAsBoolean(), CurseProductRules.animalProduct(id), id);
            assertEquals(entry.get("plant_product").getAsBoolean(), CurseProductRules.plantProduct(id), id);
            if (entry.get("animal_product").getAsBoolean()) animal++;
            if (entry.get("plant_product").getAsBoolean()) plants++;
            if (entry.get("kind").getAsString().equals("modern_adaptation")) {
                assertTrue(entry.has("policy"), "Modern classifications must be explicitly adapted");
                modern++;
            } else assertFalse(entry.getAsJsonArray("source_refs").isEmpty(), "Original flags must have source references");
        }
        assertEquals(43, animal);
        assertEquals(47, plants);
        assertEquals(15, modern);
    }

    @Test void zeroPlantNutritionFoodsAndMixedDishesKeepIndependentProductFlags() {
        for (String id : new String[]{"minecraft:bread", "minecraft:mushroom_stew", "minecraft:brown_mushroom",
                "minecraft:sugar", "minecraft:wheat_seeds", "mite:chocolate"}) {
            assertTrue(CurseProductRules.plantProduct(id), id);
            assertFalse(CurseProductRules.animalProduct(id), id);
        }
        for (String id : new String[]{"minecraft:cake", "minecraft:pumpkin_pie", "mite:ice_cream",
                "mite:mashed_potato", "mite:cereal", "mite:beef_stew"}) {
            assertTrue(CurseProductRules.animalProduct(id), id);
            assertTrue(CurseProductRules.plantProduct(id), id);
        }
        assertFalse(CurseProductRules.animalProduct("mite:water_bowl"));
        assertFalse(CurseProductRules.plantProduct("mite:water_bowl"));
        assertFalse(CurseProductRules.animalProduct("external:unknown_food"));
        assertFalse(CurseProductRules.plantProduct("external:unknown_food"));
    }

    @Test void clumsinessSubtractsTwentyLevelsAndDoublesRoundedCostBeforeSpeedDivision() {
        assertEquals(Quality.WRETCHED, EquipmentQualityRules.minimumQuality(0, true, false, true));
        assertEquals(Quality.POOR, EquipmentQualityRules.minimumQuality(10, true, false, true));
        assertEquals(Quality.AVERAGE, EquipmentQualityRules.minimumQuality(11, true, false, true));
        assertEquals(Quality.AVERAGE, EquipmentQualityRules.minimumQuality(20, true, false, true));
        assertEquals(2, EquipmentQualityRules.experienceCost(2.5F, true));
        assertEquals(1000, EquipmentQualityRules.selectedExperienceCost(1250F, Quality.FINE, false, true));
        assertEquals(2000, EquipmentQualityRules.selectedExperienceCost(1250F, Quality.EXCELLENT, false, true));
        assertEquals(0, EquipmentQualityRules.selectedExperienceCost(1250F, Quality.FINE, true, true));
        assertFalse(EquipmentQualityRules.canRepair(Quality.FINE, 1250F, 12, 999, Quality.EXCELLENT, true, false, true));
        assertTrue(EquipmentQualityRules.canRepair(Quality.FINE, 1250F, 12, 1000, Quality.EXCELLENT, true, false, true));
        assertEquals(586, TimedCraftingRules.period(1250F, 0, .3F, true));
        assertEquals(787, TimedCraftingRules.period(2500F, 12, .3F, true));
        assertEquals(25, TimedCraftingRules.period(10F, 200, .7F, true));
    }
}
