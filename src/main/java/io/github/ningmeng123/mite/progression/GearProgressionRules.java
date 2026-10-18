package io.github.ningmeng123.mite.progression;

/** Equipment progression arithmetic; one experience point is 1000 stored units. @author NingMeng123 */
public final class GearProgressionRules {
    public static final long SCALE = 1000L;
    public static final long TILL_EXPERIENCE_MILLIS = 12 * SCALE;
    private GearProgressionRules() {}

    public static int maximumLevel(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian" -> 5;
            case "iron", "ancient_metal" -> 10;
            case "mithril", "diamond" -> 15;
            case "adamantium", "netherite", "runic", "special" -> 20;
            case "vibranium", "unique" -> 25;
            default -> 0;
        };
    }

    public static long nextLevelExperienceMillis(int currentLevel) {
        if (currentLevel < 0 || currentLevel > 25) throw new IllegalArgumentException("Invalid gear level");
        return 100L * (currentLevel + 1L) * (1L + currentLevel / 5) * SCALE;
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
        return damage > 0 && Float.isFinite(damage) ? boundedMillis(4D * damage) : 0;
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
