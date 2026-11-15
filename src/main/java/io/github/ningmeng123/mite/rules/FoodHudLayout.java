package io.github.ningmeng123.mite.rules;

/** R196 food icons share ten-column rows with capacity-dependent vertical spacing. @author NingMeng123 */
public final class FoodHudLayout {
    private FoodHudLayout() {}

    public static int rowCount(int capacity) {
        return (AppleSkinNutritionRules.maximumIcons(capacity) + 9) / 10;
    }

    public static int rowSpacing(int capacity) {
        return Math.max(12 - rowCount(capacity), 3);
    }

    public static int rowOffset(int capacity, int icon) {
        return icon / 10 * rowSpacing(capacity);
    }

    public static int iconX(int right, int icon) {
        return right - icon % 10 * 8 - 9;
    }

    /** NeoForge already reserves ten pixels for the bottom row. */
    public static int extraHeight(int capacity) {
        return Math.max(0, rowCount(capacity) - 1) * rowSpacing(capacity);
    }
}
