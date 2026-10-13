package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;

import java.util.Objects;

/** R196 crafting quality arithmetic, traced to CraftingResult, uf and wd. @author NingMeng123 */
public final class EquipmentQualityRules {
    private EquipmentQualityRules() {}

    /** Effective stack qualities, including ye.setQuality's material ceiling. */
    public record QualityRange(Quality minimum, Quality maximum) {
        public QualityRange {
            Objects.requireNonNull(minimum);
            Objects.requireNonNull(maximum);
            if (minimum.ordinal() > maximum.ordinal()) throw new IllegalArgumentException("Inverted quality range");
        }

        public Quality clamp(Quality requested) {
            Objects.requireNonNull(requested);
            return Quality.values()[Math.clamp(requested.ordinal(), minimum.ordinal(), maximum.ordinal())];
        }

        /** wd:192-210: one right click advances upward and wraps back to the minimum. */
        public Quality next(Quality current) {
            Quality valid = clamp(current);
            return valid == maximum ? minimum : Quality.values()[valid.ordinal() + 1];
        }

        public boolean contains(Quality quality) {
            return quality != null && quality.ordinal() >= minimum.ordinal() && quality.ordinal() <= maximum.ordinal();
        }

        public boolean canCycle() { return minimum != maximum; }
    }

    /** CraftingResult:36-46; poor/wretched are not cheaper or quicker than average. */
    public static float adjustedDifficulty(float difficulty, Quality quality) {
        requireDifficulty(difficulty);
        if (quality == null) return difficulty;
        float result = difficulty;
        for (int i = Quality.AVERAGE.ordinal(); i < quality.ordinal(); i++) result *= 2F;
        return result;
    }

    /** uf:2219-2225; round first, then double the integer for clumsiness. */
    public static int experienceCost(float adjustedDifficulty, boolean clumsy) {
        requireDifficulty(adjustedDifficulty);
        int cost = Math.round(adjustedDifficulty / 5F);
        return clumsy ? cost * 2 : cost;
    }

    /** wd:287; repair/dye overrides preserve quality but exempt its manufacturing XP charge. */
    public static int selectedExperienceCost(float difficulty, Quality quality, boolean exempt, boolean clumsy) {
        requireDifficulty(difficulty);
        return quality == null || quality.ordinal() <= Quality.AVERAGE.ordinal() || exempt
                ? 0 : experienceCost(adjustedDifficulty(difficulty, quality), clumsy);
    }

    /**
     * uf:2184-2196, before ye.setQuality applies the material cap. Java integer division truncates
     * toward zero: an un-cursed player at level -1 through -9 still crafts average quality.
     * hasApplicableSkill also means there is no applicable skill restriction.
     */
    public static Quality minimumQuality(int level, boolean clumsy, boolean skillsEnabled, boolean hasApplicableSkill) {
        int effectiveLevel = level - (clumsy ? 20 : 0);
        int ordinal = Math.clamp(Quality.AVERAGE.ordinal() + effectiveLevel / 10, 0, Quality.AVERAGE.ordinal());
        if (skillsEnabled && !hasApplicableSkill) ordinal = Math.min(ordinal, Quality.POOR.ordinal());
        return Quality.values()[ordinal];
    }

    /**
     * uf:2199-2216, followed by ye:934-939's cap. Eligibility uses spendable total XP, not level;
     * level affects only minimum quality and manufacturing speed. Non-positive XP never grants
     * a paid quality, even when a recipe's rounded charge would be zero.
     */
    public static Quality maximumQuality(float difficulty, int level, int totalXp, Quality materialMaximum,
                                         boolean clumsy, boolean skillsEnabled, boolean hasApplicableSkill) {
        requireDifficulty(difficulty);
        Objects.requireNonNull(materialMaximum);
        if (totalXp > 0 && (!skillsEnabled || hasApplicableSkill)) {
            for (int ordinal = materialMaximum.ordinal(); ordinal > Quality.AVERAGE.ordinal(); ordinal--) {
                Quality quality = Quality.values()[ordinal];
                if (experienceCost(adjustedDifficulty(difficulty, quality), clumsy) <= totalXp) return quality;
            }
        }
        return lower(minimumQuality(level, clumsy, skillsEnabled, hasApplicableSkill), materialMaximum);
    }

    public static QualityRange range(float difficulty, int level, int totalXp, Quality materialMaximum,
                                     boolean clumsy, boolean skillsEnabled, boolean hasApplicableSkill) {
        Objects.requireNonNull(materialMaximum);
        return new QualityRange(lower(minimumQuality(level, clumsy, skillsEnabled, hasApplicableSkill), materialMaximum),
                maximumQuality(difficulty, level, totalXp, materialMaximum, clumsy, skillsEnabled, hasApplicableSkill));
    }

    /** User adaptation: preview all material-supported qualities, including those not yet affordable. */
    public static QualityRange selectionRange(int level, Quality materialMaximum,
                                               boolean clumsy, boolean skillsEnabled, boolean hasApplicableSkill) {
        Objects.requireNonNull(materialMaximum);
        return new QualityRange(lower(minimumQuality(level, clumsy, skillsEnabled, hasApplicableSkill), materialMaximum),
                materialMaximum);
    }

    /** aaf:312/333 and va:87 use the item's FULL lowest manufacture difficulty, not repair difficulty. */
    public static boolean canRepair(Quality inputQuality, float fullManufacturingDifficulty,
                                    int level, int totalXp, Quality materialMaximum,
                                    boolean clumsy, boolean skillsEnabled, boolean hasApplicableSkill) {
        return inputQuality == null || inputQuality.ordinal() <= maximumQuality(fullManufacturingDifficulty,
                level, totalXp, materialMaximum, clumsy, skillsEnabled, hasApplicableSkill).ordinal();
    }

    /** MITEContainerCrafting:92-94 stops automatic crafting after a positive XP charge. */
    public static boolean stopAfterCompletion(int experienceCost) { return experienceCost > 0; }

    private static Quality lower(Quality first, Quality second) {
        return first.ordinal() < second.ordinal() ? first : second;
    }

    private static void requireDifficulty(float difficulty) {
        if (!Float.isFinite(difficulty) || difficulty < 0F) throw new IllegalArgumentException("Invalid crafting difficulty");
    }
}
