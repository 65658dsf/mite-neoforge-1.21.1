package io.github.ningmeng123.mite.rules;

/**
 * Pure experience arithmetic verified against R196 EntityPlayer ({@code uf.class}).
 *
 * <p>The original {@code <clinit>} first adds the current increment (initially 20)
 * and then increases that increment by 10: bytecode offsets 31-35 and 38-43.
 * Thus the positive threshold is {@code 5 * level * level + 15 * level}.
 * CFR 0.152 incorrectly folds those operations into a pre-increment expression
 * in {@code uf.java:2656}, which would incorrectly make level one cost 30 XP.
 * The verified class SHA-256 is
 * {@code 6aadb6b6596d5328fe512a14f1714abb44b9d1dcc791d0b3abb2349140aeca19}.
 *
 * <p>These methods describe survival arithmetic only. Creative-mode overrides,
 * storage, rewards and a modern gameplay cap belong to callers. R196 returns
 * {@link Integer#MAX_VALUE} for thresholds above level 200. Its unbounded level
 * search cannot terminate normally when total XP equals that sentinel. This
 * implementation bounds the search to level 200, while retaining the sentinel
 * for its final progress interval; it does not invent a level 201.
 *
 * @author NingMeng123
 */
public final class ExperienceRules {
    public static final int MIN_LEVEL = -40;
    public static final int MAX_LEVEL = 200;
    public static final int MIN_TOTAL_EXPERIENCE = -800;
    public static final int MAX_LEVEL_EXPERIENCE = 203000;

    private ExperienceRules() {
    }

    /**
     * Returns the cumulative XP threshold. Levels below the supported minimum
     * normalize to -40; levels above 200 retain R196's sentinel.
     */
    public static int experienceRequiredForLevel(int level) {
        if (level <= MIN_LEVEL) {
            return MIN_TOTAL_EXPERIENCE;
        }
        if (level < 0) {
            return level * 20;
        }
        if (level > MAX_LEVEL) {
            return Integer.MAX_VALUE;
        }
        // Bounds were checked first, so this cannot overflow for any int input.
        return 5 * level * level + 15 * level;
    }

    /** Returns a survival level in [-40, 200] without an unbounded scan. */
    public static int levelForTotalExperience(int totalExperience) {
        if (totalExperience < 0) {
            return Math.max(MIN_LEVEL, Math.floorDiv(totalExperience, 20));
        }
        int lower = 0;
        int upper = MAX_LEVEL;
        while (lower < upper) {
            int middle = (lower + upper + 1) / 2;
            if (experienceRequiredForLevel(middle) <= totalExperience) {
                lower = middle;
            } else {
                upper = middle - 1;
            }
        }
        return lower;
    }

    /** Returns earned points within the current level, normalizing XP below -800. */
    public static int experienceWithinLevel(int totalExperience) {
        int normalized = clampTotalExperience(totalExperience);
        return normalized - experienceRequiredForLevel(levelForTotalExperience(normalized));
    }

    /**
     * Returns the full width of a level's experience bar, not its unearned remainder.
     * Unsupported levels normalize to [-40, 200]. At level 200 the width is
     * R196's sentinel minus 203000, rather than an invented next-level cost.
     */
    public static int experienceNeededForNextLevel(int level) {
        int normalized = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        return experienceRequiredForLevel(normalized + 1) - experienceRequiredForLevel(normalized);
    }

    /**
     * Returns progress in [0, 1], using R196's float division. At very large
     * totals float rounding can reach 1 even though the level remains 200.
     */
    public static float progressForTotalExperience(int totalExperience) {
        return (float) experienceWithinLevel(totalExperience)
                / (float) experienceNeededForNextLevel(levelForTotalExperience(totalExperience));
    }

    /** Normalizes a total to [-800, Integer.MAX_VALUE], without choosing a gameplay cap. */
    public static int clampTotalExperience(long totalExperience) {
        return (int) Math.max(MIN_TOTAL_EXPERIENCE, Math.min(Integer.MAX_VALUE, totalExperience));
    }

    /**
     * Adds a signed delta in long precision, then normalizes the result. A
     * negative delta spends XP; even Integer.MIN_VALUE cannot wrap around.
     */
    public static int addTotalExperience(int currentTotal, int signedDelta) {
        return clampTotalExperience((long) currentTotal + signedDelta);
    }
}
