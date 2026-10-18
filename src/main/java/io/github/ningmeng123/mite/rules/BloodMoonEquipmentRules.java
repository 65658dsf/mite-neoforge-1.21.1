package io.github.ningmeng123.mite.rules;

import java.util.List;

/** User-selected modern armor progression, not R196 source probabilities. @author NingMeng123 */
public final class BloodMoonEquipmentRules {
    public static final List<String> PREFIXES = List.of("minecraft:leather_", "minecraft:chainmail_", "minecraft:golden_",
            "mite:copper_", "mite:silver_", "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_");
    public static final int HIGHEST_TIER = PREFIXES.size() - 1;
    public static final long ADAMANTIUM_BLOOD_MOON = 8L;
    public static final long VIBRANIUM_BLOOD_MOON = 9L;
    public static final long ORDINARY_START_DAY = 64L;
    public static final double ORDINARY_EQUIPMENT_FACTOR = .5D;
    public static final double FIRST_BLOOD_ARMOR_CHANCE = .6D;
    public static final double EARLY_ORDINARY_ARMOR_CHANCE = FIRST_BLOOD_ARMOR_CHANCE * ORDINARY_EQUIPMENT_FACTOR;
    private BloodMoonEquipmentRules() {}

    public static long bloodMoons(long highestDay) {
        long day = Math.max(0L, highestDay);
        return Math.max(0L, io.github.ningmeng123.mite.api.MiteExtensions.policy().bloodMoonCount(day, day / 32L - day / 128L));
    }

    /** -1 preserves the pre-progression source/native equipment path. */
    public static int stage(long bloodMoons) {
        if (bloodMoons < 2L) return -1;
        if (bloodMoons >= VIBRANIUM_BLOOD_MOON) return HIGHEST_TIER;
        if (bloodMoons >= ADAMANTIUM_BLOOD_MOON) return 8;
        int stage = (int)bloodMoons - 2;
        // Copper and silver share a grade; do not spend a second blood moon on silver.
        return stage >= 4 ? stage + 1 : stage;
    }

    /** Stable item indices remain compatible with saved equipment and calendar basis rows. */
    public static int materialGrade(int tier) {
        checkStage(tier);
        return tier <= 3 ? tier : tier - 1;
    }

    public static boolean ordinaryActive(long highestDay) {
        return highestDay >= ORDINARY_START_DAY;
    }

    /** Ordinary dates use the material stage unlocked by the same highest calendar date. */
    public static double ordinaryProgress(long highestDay) {
        return Math.max(0, stage(bloodMoons(highestDay)));
    }

    public static double ordinaryArmorChance(double progress) {
        checkProgress(progress);
        return ORDINARY_EQUIPMENT_FACTOR * (80D + 2D * progress) / 100D;
    }

    public static double ordinaryHeldChance(double progress) {
        checkProgress(progress);
        return ORDINARY_EQUIPMENT_FACTOR * (70D + 3D * progress) / 100D;
    }

    /** Keep blood-date material weights; interpolation also supports compatible saved fractional decisions. */
    public static double ordinaryTierChance(double progress, int tier) {
        checkProgress(progress);
        if (tier < 0 || tier > HIGHEST_TIER) return 0D;
        int lower = (int)Math.floor(progress);
        double fraction = progress - lower;
        double lowerChance = tierChance(lower, tier, false);
        if (fraction == 0D) return lowerChance;
        return (1D - fraction) * lowerChance + fraction * tierChance(lower + 1, tier, false);
    }

    public static int selectOrdinaryTier(double progress, double draw) {
        checkProgress(progress);
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        double cumulative = 0D;
        for (int tier = 0; tier < HIGHEST_TIER; tier++) {
            cumulative += ordinaryTierChance(progress, tier);
            if (draw < cumulative) return tier;
        }
        return HIGHEST_TIER;
    }

    /** Blood dates roll each slot independently; ordinary whole-set acceptance is exactly half. */
    public static double armorChance(int stage, boolean bloodDate) {
        checkStage(stage);
        return (80 + 2 * stage) / 100D * (bloodDate ? 1D : ORDINARY_EQUIPMENT_FACTOR);
    }

    /** Chance that all four initially empty slots reject their independent blood-date armor roll. */
    public static double bloodAllEmptyChance(int stage) {
        return Math.pow(1D - armorChance(stage, true), 4);
    }

    /** Chance that all four initially empty slots accept armor, potentially in different materials. */
    public static double bloodAllFourChance(int stage) {
        return Math.pow(armorChance(stage, true), 4);
    }

    public static double heldChance(int stage, boolean bloodDate) {
        checkStage(stage);
        return (70 + 3 * stage) / 100D * (bloodDate ? 1D : ORDINARY_EQUIPMENT_FACTOR);
    }

    public static double tierChance(int stage, int tier, boolean bloodDate) {
        checkStage(stage);
        if (tier < 0 || tier > HIGHEST_TIER) return 0D;
        double total = 0D;
        for (int candidate = 0; candidate <= HIGHEST_TIER; candidate++)
            total += materialWeight(stage, candidate, bloodDate);
        return materialWeight(stage, tier, bloodDate) / total;
    }

    /** The stage's grade is most likely; copper/silver split their shared grade equally. */
    public static int selectTier(int stage, boolean bloodDate, double draw) {
        checkStage(stage);
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        double cumulative = 0D;
        for (int tier = 0; tier < HIGHEST_TIER; tier++) {
            cumulative += tierChance(stage, tier, bloodDate);
            if (draw < cumulative) return tier;
        }
        return HIGHEST_TIER;
    }

    private static double materialWeight(int stage, int tier, boolean bloodDate) {
        double lower = .35D;
        double upper = .4D;
        int distance = materialGrade(tier) - materialGrade(stage);
        double weight = distance < 0 ? Math.pow(lower, -distance) : Math.pow(upper, distance);
        return tier == 3 || tier == 4 ? weight / 2D : weight;
    }

    private static void checkStage(int stage) {
        if (stage < 0 || stage > HIGHEST_TIER) throw new IllegalArgumentException("Invalid armor progression stage");
    }

    private static void checkProgress(double progress) {
        if (!Double.isFinite(progress) || progress < 0D || progress > HIGHEST_TIER)
            throw new IllegalArgumentException("Ordinary equipment progress must be finite and in [0,9]");
    }
}
