package io.github.ningmeng123.mite.rules;

/** R196 food eligibility, nutrient and metabolism arithmetic. @author NingMeng123 */
public final class NutritionRules {
    public static final int MICRONUTRIENT_MAX = 160_000;
    public static final int MICRONUTRIENT_INITIAL = MICRONUTRIENT_MAX;
    public static final int RESISTANCE_MAX = 192_000;
    public static final int NO_RESISTANCE = -1;
    public static final int MILD_RESISTANCE = 0;
    public static final int MODERATE_RESISTANCE = 1;
    public static final int SEVERE_RESISTANCE = 2;
    public static final float HUNGER_PER_TICK = 0.002F;
    public static final float HUNGER_PER_FOOD_UNIT = 4F;
    public static final float NUTRITION_ONLY_HUNGER_PER_TICK = 0.0005F;

    private NutritionRules() {}

    /**
     * uf:1694-1795. The food values must already include sugar's satiation adjustment.
     * Curse restrictions and item-type validation belong to the ingestion caller.
     * Essential fats do not affect eligibility or the malnourishment check.
     */
    public static boolean canIngest(int satiation, int nutrition, int cap, int foodS, int foodN,
                                    int sugarContent, boolean alwaysEdible, boolean foodProtein,
                                    boolean foodPlants, int protein, int phytonutrients,
                                    boolean nauseated, boolean cannotBreathe) {
        if (nauseated || cannotBreathe) return false;
        if (alwaysEdible) return true;
        if (foodS == 0 && foodN == 0 && sugarContent == 0) return true;
        // yc.getProtein/getPhytonutrients return nutrition * 8000, not the category flag.
        if (foodN > 0 && (foodProtein && protein < 1 || foodPlants && phytonutrients < 1)) return true;
        if (satiation < cap) {
            if (foodS > 0) return true;
            if (nutrition >= cap) return false;
        } else if (foodS > 0 && nutrition > 0) {
            return false;
        }
        return foodN > 0 && nutrition < cap;
    }

    /** jv:319-329. All three nutrient stores share these bounds. */
    public static int clampMicronutrient(int value) {
        return Math.clamp(value, 0, MICRONUTRIENT_MAX);
    }

    /** yc:1531-1540 and jv:366-384. Long arithmetic also handles malformed modern inputs safely. */
    public static int addMicronutrient(int current, int nutrition, boolean foodContainsNutrient) {
        long added = foodContainsNutrient ? (long) nutrition * 8_000L : 0L;
        return (int) Math.clamp((long) current + added, 0L, MICRONUTRIENT_MAX);
    }

    /** jv:1257-1262. The stored essential-fats value does not participate. */
    public static boolean isMalnourished(int protein, int phytonutrients) {
        return protein < 1 || phytonutrients < 1;
    }

    /** yc:1145-1146. Cast after float multiplication, preserving R196's rounding order. */
    public static int addedResistance(int sugarContent) {
        return (int) (sugarContent * 4.8F);
    }

    /**
     * jv:333-350 and EnumInsulinResistanceLevel:81-90. Recovery has one-state hysteresis;
     * existing mild resistance remains until its numeric value reaches zero.
     */
    public static int resistanceLevel(int resistance, int previousLevel) {
        int bounded = Math.clamp(resistance, 0, RESISTANCE_MAX);
        int previous = Math.clamp(previousLevel, NO_RESISTANCE, SEVERE_RESISTANCE);
        if (bounded == 0) return NO_RESISTANCE;
        int level = bounded >= 144_000 ? SEVERE_RESISTANCE
                : bounded >= 96_000 ? MODERATE_RESISTANCE
                : bounded >= 48_000 ? MILD_RESISTANCE : NO_RESISTANCE;
        if (level == NO_RESISTANCE) return previous == NO_RESISTANCE ? NO_RESISTANCE : MILD_RESISTANCE;
        if (previous > level) return level + 1;
        return level;
    }

    /** yc:1127-1134. Negative food satiation is possible and must not be hidden by clamping. */
    public static int adjustedSatiation(int baseSatiation, int sugarContent, int resistanceLevel) {
        if (resistanceLevel < MODERATE_RESISTANCE || sugarContent <= 0) return baseSatiation;
        return baseSatiation - (sugarContent < 1_000 ? 1 : sugarContent / 1_000);
    }

    /**
     * jv:1508-1525; acq:260-261. Immersion is 0=dry, 1=water at feet, 2=water above feet.
     * Thunder contributes only when the player is actually exposed to rain.
     */
    public static float metabolicMultiplier(boolean rainy, boolean thunder, int immersion,
                                            float temperature, boolean malnourished) {
        float rain = rainy ? (thunder ? 0.5F : 0.25F) : 0F;
        float water = immersion >= 2 ? 0.5F : immersion == 1 ? 0.25F : 0F;
        float wetness = Math.max(rain, water);
        if (rainy && !thunder && water == 0.25F) wetness += 0.125F;
        if (temperature <= 0.15F) wetness *= 2F;
        else if (temperature >= 1.4F) wetness = 0F;
        return 1F + wetness + (malnourished ? 0.5F : 0F);
    }

    /** ux:74-85 and aaw:333-334, with the requested eightfold recovery while in bed. */
    public static float regenerationIncrement(int nutrition, boolean malnourished, boolean inBed,
                                              int regenerationEnchantmentLevel) {
        return (0.0004F + nutrition * 0.00002F)
                * (malnourished ? 0.25F : 1F)
                * (inBed ? 8F : 1F)
                * (1F + regenerationEnchantmentLevel * 0.5F);
    }

    /** ux:64-73. Difficulty IDs follow peaceful=0, easy=1, normal=2, hard=3. */
    public static boolean starvationCanDamage(float health, int difficulty) {
        return health > 10F || difficulty >= 3 || health > 1F && difficulty >= 2;
    }
}
