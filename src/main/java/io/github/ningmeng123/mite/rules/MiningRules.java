package io.github.ningmeng123.mite.rules;

/** R196's food-energy penalty, level multiplier and progress formula. @author NingMeng123 */
public final class MiningRules {
    private MiningRules() {
    }

    /** Preserves R196 float arithmetic; the user's extended levels keep gaining the same bonus. */
    public static float speedMultiplierForLevel(int level) {
        int boundedLevel = Math.max(ExperienceRules.MIN_LEVEL, level);
        return 1.0F + boundedLevel * 0.02F;
    }

    /** Apply food-energy loss before the level factor, matching R196's float division order. */
    public static float adjustStrengthForSurvival(float strength, int level, boolean hasFoodEnergy) {
        if (!hasFoodEnergy) {
            strength /= 5.0F;
        }
        return strength * speedMultiplierForLevel(level);
    }

    /** Preserves R196's zero-hardness priority, strict instant threshold and float operation order. */
    public static float destroyProgress(float strength, float hardness) {
        if (hardness == 0.0F) {
            return 1.0F;
        }
        if (hardness < 0.0F || Float.isNaN(hardness) || !(strength > 0.0F)) {
            return 0.0F;
        }
        float relativeHardness = hardness / strength;
        if (relativeHardness < 0.0029296875F) {
            return 1.0F;
        }
        return (1.0F / relativeHardness) / 512.0F;
    }
}
