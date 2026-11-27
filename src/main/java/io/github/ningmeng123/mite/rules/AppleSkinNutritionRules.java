package io.github.ningmeng123.mite.rules;

/** Read-only AppleSkin projections; none of these values is another food reserve. @author NingMeng123 */
public final class AppleSkinNutritionRules {
    private AppleSkinNutritionRules() {}

    public static float displayedCurrent(int nutrition, float saturation, int capacity) {
        int cap = Math.max(0, capacity);
        return Math.clamp(Math.clamp(nutrition, 0, cap) + positiveFinite(saturation), 0F, (float) cap);
    }

    /** Source nutrition is capped independently of S, while native saturation gains are capped to S. */
    public static float previewGain(int nutrition, float saturation, int satiation, int capacity,
                                    int foodNutrition, boolean addsSaturation, float saturationGain, boolean canIngest) {
        if (!canIngest) return 0F;
        int cap = Math.max(0, capacity);
        float before = displayedCurrent(nutrition, saturation, cap);
        float afterSaturation = addsSaturation
                ? Math.min(Math.clamp(satiation, 0, cap), positiveFinite(saturation) + positiveFinite(saturationGain))
                : saturation;
        int afterNutrition = Math.clamp((long) nutrition + Math.max(0, foodNutrition), 0, cap);
        return Math.max(0F, displayedCurrent(afterNutrition, afterSaturation, cap) - before);
    }

    public static int maximumIcons(int capacity) { return (int) ((Math.max(0, capacity) + 1L) / 2L); }
    private static float positiveFinite(float value) { return Float.isFinite(value) ? Math.max(0F, value) : 0F; }
}
