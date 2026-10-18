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
 * <p>By the user's requested modern adaptation, the same quadratic curve continues
 * beyond R196's original level 200. There is no configured gameplay level cap.
 * Natural-level attribute benefits independently retain R196's ceiling through
 * {@link ProgressionRules#attributeLevel(int)}.
 * Vanilla stores total XP as an int, whose largest representable natural level is
 * 20722. The arithmetic protects that storage boundary without wrapping, while
 * preserving all R196 level-zero-to-200 thresholds and signed debt semantics.
 *
 * @author NingMeng123
 */
public final class ExperienceRules {
    public static final int MIN_LEVEL = -40;
    public static final int MIN_TOTAL_EXPERIENCE = -800;
    public static final int MAX_TOTAL_EXPERIENCE = Integer.MAX_VALUE;
    /** Derived from int XP storage, not a configured gameplay level cap. */
    public static final int MAX_REPRESENTABLE_LEVEL = 20722;

    private ExperienceRules() {
    }

    /** R196 jv.onDeath: only totals below level one's threshold incur debt. */
    public static int experienceAfterDeath(int totalExperience) {
        return totalExperience < 20
                ? (int) Math.max(MIN_TOTAL_EXPERIENCE, (long) totalExperience - 20L) : 0;
    }

    /** R196 uf.getExperienceValue: positive cumulative XP divided by three. */
    public static int deathExperienceReward(int totalExperience) {
        return Math.max(0, totalExperience / 3);
    }

    /**
     * Returns the cumulative XP threshold. Levels below the supported minimum
     * normalize to -40; thresholds beyond int XP storage saturate instead of overflowing.
     */
    public static int experienceRequiredForLevel(int level) {
        if (level <= MIN_LEVEL) {
            return MIN_TOTAL_EXPERIENCE;
        }
        if (level < 0) {
            return level * 20;
        }
        if (level > MAX_REPRESENTABLE_LEVEL) {
            return Integer.MAX_VALUE;
        }
        return (int)(5L * level * level + 15L * level);
    }

    /** Resolves the quadratic curve with a bounded search over representable totals. */
    public static int levelForTotalExperience(int totalExperience) {
        if (totalExperience < 0) {
            return Math.max(MIN_LEVEL, Math.floorDiv(totalExperience, 20));
        }
        int lower = 0;
        int upper = MAX_REPRESENTABLE_LEVEL;
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

    /** Returns earned points within the current level, normalizing invalid stored totals. */
    public static int experienceWithinLevel(int totalExperience) {
        int normalized = clampTotalExperience(totalExperience);
        return normalized - experienceRequiredForLevel(levelForTotalExperience(normalized));
    }

    /**
     * Returns the full width of a level's experience bar, not its unearned remainder.
     * Positive widths continue as 10*level+20, including beyond 200. Only the
     * returned int storage width saturates for mathematically unrepresentable inputs.
     */
    public static int experienceNeededForNextLevel(int level) {
        if (level < 0) return 20;
        return (int)Math.min(10L * level + 20L, Integer.MAX_VALUE);
    }

    /**
     * Returns progress in [0, 1], using R196's float division and the continued level curve.
     */
    public static float progressForTotalExperience(int totalExperience) {
        return (float) experienceWithinLevel(totalExperience)
                / (float) experienceNeededForNextLevel(levelForTotalExperience(totalExperience));
    }

    /** Protects the vanilla int storage boundary; this is not a gameplay level cap. */
    public static int clampTotalExperience(long totalExperience) {
        return (int) Math.max(MIN_TOTAL_EXPERIENCE, Math.min(MAX_TOTAL_EXPERIENCE, totalExperience));
    }

    /**
     * Adds a signed delta in long precision, then normalizes the result. A
     * negative delta spends XP; even Integer.MIN_VALUE cannot wrap around.
     */
    public static int addTotalExperience(int currentTotal, int signedDelta) {
        return clampTotalExperience((long) currentTotal + signedDelta);
    }
}
