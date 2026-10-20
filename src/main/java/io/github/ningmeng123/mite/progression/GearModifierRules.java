package io.github.ningmeng123.mite.progression;

/** Numerical behavior copied from MITE-ITE 2.0.28's modifier consumers. @author NingMeng123 */
public final class GearModifierRules {
    private GearModifierRules() {}

    public static float miningSpeed(float original, float materialEfficiency, float efficiency,
            float unnatural, int excessHarvestLevels, float aquadynamic, boolean wet) {
        if (!(original > 1F) || !(materialEfficiency > 0F)) return original;
        float addition = efficiency + unnatural * Math.max(0, excessHarvestLevels) + (wet ? aquadynamic : 0F);
        return original * (materialEfficiency + addition) / materialEfficiency;
    }

    /** The original truncates tool wear, but keeps at least one point of armor wear. */
    public static int wear(int amount, float reduction, boolean armor) {
        if (amount <= 0 || !(reduction > 0F)) return amount;
        return (int)Math.max(armor ? 1F : 0F, amount * Math.max(0F, 1F - reduction));
    }

    /** In the reference code all four Indomitable ranks enable these same health thresholds. */
    public static float indomitable(float healthFraction, boolean present) {
        if (!present) return 1F;
        if (healthFraction <= .1F) return 2F;
        if (healthFraction <= .2F) return 1.6F;
        if (healthFraction <= .35F) return 1.35F;
        if (healthFraction <= .5F) return 1.25F;
        return 1F;
    }

    public static int slowTicks(int level) { return Math.max(0, level) * 20; }
    public static int slowAmplifier(int level) { return Math.max(0, level) / 2; }
    public static int effectDuration(int duration, float immunity) {
        return duration < 0 ? duration : (int)(duration * Math.max(0F, 1F - immunity));
    }

    public static float materialEfficiency(String material) {
        if (material == null) return 1F;
        return switch (material) {
            case "wood" -> 1F;
            case "flint", "rusted_iron" -> 1.25F;
            case "stone", "obsidian" -> 1.5F;
            case "copper", "silver", "gold" -> 1.75F;
            case "iron", "ancient_metal" -> 2F;
            case "mithril", "diamond" -> 2.5F;
            case "adamantium", "netherite" -> 3F;
            case "vibranium" -> 3.5F;
            default -> 1F;
        };
    }

    /** Unnatural compares raw repair-material minimums, including nonmetal material levels. */
    public static int materialHarvestLevel(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "flint", "stone", "rusted_iron", "copper", "silver", "gold" -> 2;
            case "obsidian", "iron", "ancient_metal" -> 3;
            case "mithril", "diamond" -> 4;
            case "adamantium", "netherite" -> 5;
            case "vibranium" -> 6;
            default -> 0;
        };
    }
}
