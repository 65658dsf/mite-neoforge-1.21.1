package io.github.ningmeng123.mite.rules;

/**
 * Level-dependent rules verified against R196 EntityPlayer.
 *
 * @author NingMeng123
 */
public final class ProgressionRules {
    private ProgressionRules() {
    }

    /**
     * Returns base maximum health in HP (two HP per heart).
     * The caller supplies the synchronized signed experience level. Negative
     * levels retain the minimum of three hearts.
     */
    public static double maxHealthForLevel(int level) {
        return foodLimitForLevel(level);
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
