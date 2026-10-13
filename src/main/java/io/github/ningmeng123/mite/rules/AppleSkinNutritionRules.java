package io.github.ningmeng123.mite.rules;

/** Read-only AppleSkin projections; none of these values is another food reserve. @author NingMeng123 */
public final class AppleSkinNutritionRules {
    private AppleSkinNutritionRules() {}

    public static float displayedCurrent(int nutrition, float seedBuffer, int capacity) {
        int cap = Math.max(0, capacity);
        return Math.clamp(Math.clamp(nutrition, 0, cap) + positiveFinite(seedBuffer), 0F, (float) cap);
    }

    /** Source nutrition is capped independently of S; only seed buffer gains are capped to S. */
    public static float previewGain(int nutrition, float seedBuffer, int satiation, int capacity,
                                    int foodNutrition, boolean seeds, float addedSeedBuffer, boolean canIngest) {
        if (!canIngest) return 0F;
        int cap = Math.max(0, capacity);
        float before = displayedCurrent(nutrition, seedBuffer, cap);
        if (seeds) {
            float afterBuffer = Math.min(Math.clamp(satiation, 0, cap), positiveFinite(seedBuffer) + positiveFinite(addedSeedBuffer));
            return Math.max(0F, displayedCurrent(nutrition, afterBuffer, cap) - before);
        }
        int afterNutrition = Math.clamp((long) nutrition + Math.max(0, foodNutrition), 0, cap);
        return Math.max(0F, displayedCurrent(afterNutrition, seedBuffer, cap) - before);
    }

    public static int maximumIcons(int capacity) { return (int) ((Math.max(0, capacity) + 1L) / 2L); }
    private static float positiveFinite(float value) { return Float.isFinite(value) ? Math.max(0F, value) : 0F; }
}
