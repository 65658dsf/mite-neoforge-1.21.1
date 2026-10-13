package io.github.ningmeng123.mite.rules;

import java.util.Map;

/** vq and ye: reward the actual quantity removed, independent of furnace production history. @author NingMeng123 */
public final class FoodCookingExperienceRules {
    private static final Map<String, Integer> REWARDS = Map.of(
            "minecraft:cooked_porkchop", 3, "minecraft:cooked_cod", 3,
            "minecraft:cooked_beef", 4, "minecraft:cooked_chicken", 3,
            "minecraft:baked_potato", 0, "minecraft:cooked_mutton", 2,
            "minecraft:bread", 0, "mite:cooked_large_fish", 4, "mite:cooked_worm", 0);
    private FoodCookingExperienceRules() {}

    /** Negative means outside this source-food policy; zero is a managed source result with no reward. */
    public static int rewardPerItem(String itemId) { return REWARDS.getOrDefault(itemId, -1); }

    public static int rewardForTaken(String itemId, int quantity) {
        int reward = rewardPerItem(itemId);
        if (reward <= 0 || quantity <= 0) return 0;
        return (int) Math.min((long) reward * quantity, Integer.MAX_VALUE);
    }
}
