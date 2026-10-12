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
     * The caller currently supplies the modern experience level; the R196
     * experience curve and negative experience are separate rules.
     */
    public static double maxHealthForLevel(int level) {
        // Check boundaries before arithmetic so every int input is supported.
        if (level >= 35) {
            return 20.0;
        }
        if (level <= 0) {
            return 6.0;
        }
        return 6.0 + (level / 5) * 2.0;
    }
}
