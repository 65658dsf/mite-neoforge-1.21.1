package io.github.ningmeng123.mite.progression;

/** Independent growth caps and rarity tiers inspired by MITE-ITE 2.0.28. @author NingMeng123 */
public final class GearAffixRules {
    public static final int BASE_REWARD_CHANCE_PERCENT = 25;
    public static final int REWARD_CHANCE_PER_LEVEL = 3;

    public record Definition(int weight, int maximumLevel) {
        public Definition {
            if (weight < 0 || maximumLevel < 1 || maximumLevel > 255)
                throw new IllegalArgumentException("Invalid growth affix definition");
        }
    }

    private GearAffixRules() {}

    /** These are growth limits, not ordinary enchanting limits or a clamp on saved ranks. */
    public static Definition definition(String id, boolean armor, int nativeWeight, int nativeMaximumLevel) {
        return switch (id) {
            case "minecraft:unbreaking" -> new Definition(armor ? 20 : 40, 5);
            case "minecraft:efficiency" -> new Definition(20, 10);
            case "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
                    "minecraft:power", "minecraft:impaling", "minecraft:density", "minecraft:breach" -> new Definition(20, 10);
            case "minecraft:protection" -> new Definition(5, 5);
            case "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection",
                    "minecraft:feather_falling" -> new Definition(7, 5);
            case "minecraft:knockback", "minecraft:punch", "mite:stun" -> new Definition(10, 5);
            case "minecraft:fortune", "minecraft:looting", "mite:fishing_fortune" -> new Definition(6, 5);
            case "minecraft:silk_touch", "minecraft:infinity", "minecraft:channeling" -> new Definition(2, 1);
            case "minecraft:multishot", "minecraft:flame" -> new Definition(4, 1);
            case "minecraft:aqua_affinity" -> new Definition(10, 1);
            case "minecraft:fire_aspect", "mite:poison", "mite:piercing" -> new Definition(6, 5);
            case "minecraft:respiration", "minecraft:loyalty", "minecraft:piercing", "minecraft:quick_charge",
                    "minecraft:lure", "mite:arrow_recovery", "mite:fertility", "mite:tree_felling",
                    "mite:harvesting", "mite:butchering" -> new Definition(10, 5);
            case "minecraft:depth_strider", "minecraft:soul_speed", "mite:speed", "mite:regeneration" -> new Definition(8, 5);
            case "minecraft:frost_walker", "minecraft:riptide", "minecraft:wind_burst", "mite:vampiric" -> new Definition(4, 5);
            case "minecraft:thorns", "mite:disarming" -> new Definition(5, 5);
            case "mite:endurance" -> new Definition(5, 4);
            case "mite:free_action" -> new Definition(3, 4);
            case "mite:quickness", "minecraft:sweeping_edge" -> new Definition(10, 10);
            case "mite:true_flight" -> new Definition(20, 10);
            default -> new Definition(Math.max(0, nativeWeight), nativeMaximumLevel <= 1 ? 1
                    : (int)Math.min(255L, 2L * nativeMaximumLevel));
        };
    }

    /** The newly reached equipment level determines this upgrade's reward chance. */
    public static int rewardChancePercent(int level) {
        if (level < 0) throw new IllegalArgumentException("Negative equipment level");
        return (int)Math.min(100L, BASE_REWARD_CHANCE_PERCENT + (long)level * REWARD_CHANCE_PER_LEVEL);
    }

    public static boolean grantsAffix(int level, int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Reward roll must be in [0, 100)");
        return roll < rewardChancePercent(level);
    }

    public static int totalWeight(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            if (weight < 0) throw new IllegalArgumentException("Negative affix weight");
            total = Math.addExact(total, weight);
        }
        return total;
    }

    /** One integer slot per unit of weight gives exact w / sum(w) conditional probabilities. */
    public static int selectIndex(int[] weights, int roll) {
        int total = totalWeight(weights);
        if (roll < 0 || roll >= total) throw new IllegalArgumentException("Weighted roll outside candidate pool");
        for (int index = 0; index < weights.length; index++) {
            if (roll < weights[index]) return index;
            roll -= weights[index];
        }
        throw new IllegalStateException("Missing weighted candidate");
    }
}
