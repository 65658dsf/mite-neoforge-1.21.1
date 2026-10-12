package io.github.ningmeng123.mite.rules;

/** R196's level multiplier, independent of modern base mining speed. @author NingMeng123 */
public final class MiningRules {
    private MiningRules() {
    }

    /** Preserves the original float arithmetic within the supported level range. */
    public static float speedMultiplierForLevel(int level) {
        int boundedLevel = Math.max(ExperienceRules.MIN_LEVEL, Math.min(ExperienceRules.MAX_LEVEL, level));
        return 1.0F + boundedLevel * 0.02F;
    }
}
