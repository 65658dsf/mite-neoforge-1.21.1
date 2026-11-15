package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Food-capacity boundaries retain half icons, ten columns and safe air-bar spacing. @author NingMeng123 */
class FoodHudLayoutTest {
    @ParameterizedTest
    @CsvSource({"1,1,1,11,0", "6,3,1,11,0", "19,10,1,11,0", "20,10,1,11,0",
            "21,11,2,10,10", "26,13,2,10,10", "39,20,2,10,10", "40,20,2,10,10",
            "44,22,3,9,18", "181,91,10,3,27"})
    void capacityDeterminesIconsRowsAndAdditionalReservedHeight(int capacity, int icons, int rows, int spacing, int extra) {
        assertEquals(icons, AppleSkinNutritionRules.maximumIcons(capacity));
        assertEquals(rows, FoodHudLayout.rowCount(capacity));
        assertEquals(spacing, FoodHudLayout.rowSpacing(capacity));
        assertEquals(extra, FoodHudLayout.extraHeight(capacity));
    }

    @ParameterizedTest
    @CsvSource({"0,182,0", "9,110,0", "10,182,9", "19,110,9", "20,182,18", "21,174,18"})
    void subsequentRowsStayInTheRightHandFoodColumns(int icon, int x, int offset) {
        assertEquals(x, FoodHudLayout.iconX(191, icon));
        assertEquals(offset, FoodHudLayout.rowOffset(44, icon));
    }
}
