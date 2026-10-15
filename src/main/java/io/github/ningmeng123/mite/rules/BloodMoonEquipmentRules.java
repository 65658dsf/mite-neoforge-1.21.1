package io.github.ningmeng123.mite.rules;

import java.util.List;

/** User-selected modern armor progression, not R196 source probabilities. @author NingMeng123 */
public final class BloodMoonEquipmentRules {
    public static final List<String> PREFIXES = List.of("minecraft:leather_", "minecraft:chainmail_", "minecraft:golden_",
            "mite:copper_", "mite:silver_", "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_");
    public static final int HIGHEST_TIER = PREFIXES.size() - 1;
    private BloodMoonEquipmentRules() {}

    public static long bloodMoons(long highestDay) {
        long day = Math.max(0L, highestDay);
        return day / 32L - day / 128L;
    }

    /** -1 preserves the pre-progression source/native equipment path. */
    public static int stage(long bloodMoons) {
        return bloodMoons < 2L ? -1 : (int)Math.min(HIGHEST_TIER, (bloodMoons - 2L) / 2L);
    }

    public static double armorChance(int stage, boolean bloodDate) {
        checkStage(stage);
        return (bloodDate ? 80 + 2 * stage : 50 + 5 * stage) / 100D;
    }

    public static double heldChance(int stage, boolean bloodDate) {
        checkStage(stage);
        return (bloodDate ? 70 + 3 * stage : 40 + 5 * stage) / 100D;
    }

    public static double tierChance(int stage, int tier, boolean bloodDate) {
        checkStage(stage);
        if (tier < 0 || tier > HIGHEST_TIER) return 0D;
        double total = 0D;
        for (int candidate = 0; candidate <= HIGHEST_TIER; candidate++)
            total += materialWeight(stage, candidate, bloodDate);
        return materialWeight(stage, tier, bloodDate) / total;
    }

    /** The stage is the most likely material; lower and higher materials both remain possible. */
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
        double lower = bloodDate ? .35D : .5D;
        double upper = bloodDate ? .4D : .25D;
        return tier < stage ? Math.pow(lower, stage - tier) : Math.pow(upper, tier - stage);
    }

    private static void checkStage(int stage) {
        if (stage < 0 || stage > HIGHEST_TIER) throw new IllegalArgumentException("Invalid armor progression stage");
    }
}
