package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent reserve/capacity boundary examples; no AppleSkin or Minecraft initialization. @author NingMeng123 */
class AppleSkinNutritionRulesTest {
    @ParameterizedTest
    @CsvSource({"6,0,6,6", "3,2,6,5", "6,3,6,6", "0,1,6,1", "20,0,20,20", "-1,-1,6,0",
            "26,0,26,26", "39,3,40,40", "20,0,40,20"})
    void showsCanonicalNutritionAndOnlyExistingSeedBuffer(int nutrition, float buffer, int capacity, float expected) {
        assertEquals(expected, AppleSkinNutritionRules.displayedCurrent(nutrition, buffer, capacity));
    }

    @ParameterizedTest
    @CsvSource({
            "3,0,0,6,8,false,0,true,3",
            "3,0,1,6,2,false,0,true,2",
            "5,0,0,6,4,false,0,true,1",
            "6,0,0,6,8,false,0,true,0",
            "3,2,4,6,3,false,0,true,1",
            "3,0,0,6,8,false,0,false,0",
            "6,0,0,6,0,true,1,true,0",
            "3,4,4,8,0,true,1,true,0",
            "3,3,4,8,0,true,1,true,1",
            "3,0.25,4,8,0,true,0.5,true,0.5",
            "3,3.75,4,8,0,true,0.5,true,0.25",
            "3,0,0,6,0,true,1,true,0",
            "3,0,4,8,0,true,1,false,0",
            "20,0,20,26,8,false,0,true,6",
            "25,0,25,26,8,false,0,true,1",
            "37,0,37,40,8,false,0,true,3"
    })
    void previewUsesIndependentNutritionCapAndRealSeedSatiationCap(int nutrition, float buffer, int satiation,
            int capacity, int foodNutrition, boolean seeds, float seedGain, boolean qualified, float expected) {
        assertEquals(expected, AppleSkinNutritionRules.previewGain(nutrition, buffer, satiation, capacity,
                foodNutrition, seeds, seedGain, qualified));
    }

    @Test void invalidExternalFloatValuesCannotProduceInvalidRendering() {
        assertEquals(3F, AppleSkinNutritionRules.displayedCurrent(3, Float.NaN, 6));
        assertEquals(3F, AppleSkinNutritionRules.displayedCurrent(3, Float.POSITIVE_INFINITY, 6));
        assertEquals(0F, AppleSkinNutritionRules.previewGain(3, 0F, 4, 6, 0, true, Float.NaN, true));
        assertEquals(0F, AppleSkinNutritionRules.displayedCurrent(Integer.MAX_VALUE, Float.MAX_VALUE, 0));
    }

    @ParameterizedTest
    @CsvSource({"6,3", "8,4", "10,5", "12,6", "14,7", "16,8", "18,9", "20,10", "21,11", "26,13", "40,20", "44,22"})
    void nutritionOverlayFitsEveryR196FoodRow(int capacity, int icons) {
        assertEquals(icons, AppleSkinNutritionRules.maximumIcons(capacity));
    }
}
