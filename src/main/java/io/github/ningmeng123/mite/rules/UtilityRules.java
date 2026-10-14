package io.github.ningmeng123.mite.rules;

/** Source ItemCoin values and finite signed-XP arithmetic. @author NingMeng123 */
public final class UtilityRules {
    private UtilityRules() {}
    public static int coinExperience(String material) {
        return switch (material) {
            case "copper" -> 5;
            case "silver" -> 25;
            case "gold" -> 100;
            case "ancient_metal" -> 500;
            case "mithril" -> 2500;
            case "adamantium" -> 10000;
            default -> throw new IllegalArgumentException("No source coin material " + material);
        };
    }
    public static int totalValue(int value, int count) {
        if (value < 0 || count < 0) throw new IllegalArgumentException("Negative coin quantity");
        return (int)Math.min((long)value * count, Integer.MAX_VALUE);
    }
}
