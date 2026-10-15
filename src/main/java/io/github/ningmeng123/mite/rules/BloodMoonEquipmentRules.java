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
        return (bloodDate ? 70 + 3 * stage : 20 + 8 * stage) / 100D;
    }

    public static double tierChance(int stage, int tier, boolean bloodDate) {
        checkStage(stage);
        if (tier < stage || tier > HIGHEST_TIER) return 0D;
        double rate = bloodDate ? .4D : .25D;
        return Math.pow(rate, tier - stage) * (tier == HIGHEST_TIER ? 1D : 1D - rate);
    }

    /** One conditional material draw, with the highest tier absorbing the geometric tail. */
    public static int selectTier(int stage, boolean bloodDate, double draw) {
        checkStage(stage);
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        double cumulative = 0D;
        for (int tier = stage; tier < HIGHEST_TIER; tier++) {
            cumulative += tierChance(stage, tier, bloodDate);
            if (draw < cumulative) return tier;
        }
        return HIGHEST_TIER;
    }

    private static void checkStage(int stage) {
        if (stage < 0 || stage > HIGHEST_TIER) throw new IllegalArgumentException("Invalid armor progression stage");
    }
}
