package io.github.ningmeng123.mite.rules;

import java.util.Map;

/** ye/vq: reward the quantity actually withdrawn, including source metal ingots. @author NingMeng123 */
public final class OreCookingExperienceRules {
    private static final Map<String, Integer> REWARDS = Map.ofEntries(
            Map.entry("minecraft:copper_ingot", 10), Map.entry("mite:silver_ingot", 15),
            Map.entry("minecraft:iron_ingot", 10), Map.entry("minecraft:gold_ingot", 20),
            Map.entry("mite:mithril_ingot", 40), Map.entry("mite:adamantium_ingot", 100),
            Map.entry("minecraft:diamond", 30), Map.entry("minecraft:emerald", 20), Map.entry("minecraft:quartz", 10),
            Map.entry("minecraft:coal", 0), Map.entry("minecraft:charcoal", 0),
            Map.entry("minecraft:redstone", 0), Map.entry("minecraft:lapis_lazuli", 5));
    private OreCookingExperienceRules() {}
    public static int rewardPerItem(String itemId) { return REWARDS.getOrDefault(itemId, -1); }
    public static int rewardForTaken(String itemId, int quantity) {
        int reward = rewardPerItem(itemId);
        return reward > 0 && quantity > 0 ? (int)Math.min((long)reward * quantity, Integer.MAX_VALUE) : 0;
    }
}
