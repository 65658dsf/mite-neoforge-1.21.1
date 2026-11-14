package io.github.ningmeng123.mite.rules;

/**
 * R196-derived health and food rules with modern level-dependent melee reach.
 *
 * @author NingMeng123
 */
public final class ProgressionRules {
    /** R196's highest natural level limits attribute gains, not the modern XP curve. */
    public static final int MAX_ATTRIBUTE_LEVEL = 200;

    private ProgressionRules() {
    }

    /** Keep signed-level penalties while capping natural-level benefits at R196's ceiling. */
    public static int attributeLevel(int level) {
        return Math.min(level, MAX_ATTRIBUTE_LEVEL);
    }

    /**
     * Returns base maximum health in HP (two HP per heart).
     * The caller supplies the synchronized signed experience level. Negative
     * levels retain the minimum of three hearts.
     */
    public static double maxHealthForLevel(int level) {
        return foodLimitForLevel(level);
    }

    /** Modern melee reach gains 0.1 blocks per five current experience levels, up to 1.0 block. */
    public static double meleeReachBonusForLevel(int level) {
        return Math.clamp(level / 5, 0, 10) / 10D;
    }

    /** R196 food capacity uses the same level curve as base maximum health. */
    public static int foodLimitForLevel(int level) {
        // Check boundaries before arithmetic so every int input is supported.
        if (level >= 35) {
            return 20;
        }
        if (level <= 0) {
            return 6;
        }
        return 6 + (level / 5) * 2;
    }
}
