package io.github.ningmeng123.mite.progression;

/** Equipment progression arithmetic; one experience point is 1000 stored units. @author NingMeng123 */
public final class GearProgressionRules {
    public static final long SCALE = 1000L;
    public static final long TILL_EXPERIENCE_MILLIS = 12 * SCALE;
    private GearProgressionRules() {}

    public static int maximumLevel(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian",
                    "iron", "ancient_metal", "mithril", "diamond", "adamantium", "netherite", "runic", "special",
                    "vibranium", "unique" -> 25;
            default -> 0;
        };
    }

    public static long nextLevelExperienceMillis(int currentLevel) {
        return nextLevelExperienceMillis(null, currentLevel, false, -1);
    }

    /** Material does not change the approved tool curve. */
    public static long nextLevelExperienceMillis(String material, int currentLevel) {
        return nextLevelExperienceMillis(currentLevel);
    }

    public static long nextLevelExperienceMillis(String material, int currentLevel, boolean weapon, int armorSlot) {
        if (currentLevel < 0 || currentLevel > 27) throw new IllegalArgumentException("Invalid gear level");
        // Source slot identity: -1 = non-armor, 0 = head, 1 = chest, 2 = legs, 3 = feet.
        int armorFactor = switch (armorSlot) {
            case -1 -> 0;
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 3;
            case 3 -> 1;
            default -> throw new IllegalArgumentException("Invalid armor slot");
        };
        long points = armorFactor > 0 ? (20L + 10L * currentLevel) * armorFactor
                : weapon ? 150L + 100L * currentLevel : 100L + 50L * currentLevel;
        return points * SCALE;
    }

    public static long killExperienceMillis(double maximumHealth) {
        return maximumHealth > 0 && Double.isFinite(maximumHealth)
                ? boundedMillis(Math.ceil(maximumHealth)) : 0;
    }

    public static long miningExperienceMillis(float hardness, int harvestTier) {
        if (!(hardness > 0) || !Float.isFinite(hardness)) return 0;
        return boundedMillis(Math.ceil(2D * hardness * (1D + Math.max(0, harvestTier) / 2D)));
    }

    public static long damageExperienceMillis(float damage) {
        return damage > 0 && Float.isFinite(damage) ? boundedMillis(damage) : 0;
    }

    public static long shieldExperienceMillis(float blockedDamage) {
        return blockedDamage > 0 && Float.isFinite(blockedDamage) ? boundedMillis(2D * blockedDamage) : 0;
    }

    private static long boundedMillis(double experience) {
        // Awards above the entire 25-level curve have no additional gameplay value.
        return (long)Math.floor(Math.min(experience, 1_000_000D) * SCALE);
    }

    public static long saturatedAdd(long first, long second) {
        if (first < 0 || second < 0) throw new IllegalArgumentException("Negative gear experience");
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }
}
