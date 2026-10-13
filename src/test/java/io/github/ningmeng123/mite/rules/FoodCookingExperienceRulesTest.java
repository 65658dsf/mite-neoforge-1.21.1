package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.FoodCookingExperienceRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source item rewards and actual partial-withdrawal vectors. @author NingMeng123 */
class FoodCookingExperienceRulesTest {
    @Test void literalSourceFoodRewardsIncludeTheThreeManagedZeroRewardFoods() {
        assertEquals(3, rewardPerItem("minecraft:cooked_porkchop"));
        assertEquals(3, rewardPerItem("minecraft:cooked_cod"));
        assertEquals(4, rewardPerItem("minecraft:cooked_beef"));
        assertEquals(3, rewardPerItem("minecraft:cooked_chicken"));
        assertEquals(2, rewardPerItem("minecraft:cooked_mutton"));
        assertEquals(4, rewardPerItem("mite:cooked_large_fish"));
        assertEquals(0, rewardPerItem("minecraft:baked_potato"));
        assertEquals(0, rewardPerItem("minecraft:bread"));
        assertEquals(0, rewardPerItem("mite:cooked_worm"));
    }
    @Test void rewardDependsOnTheActuallyTakenCountAndDoesNotPayDuplicateZeroCountCallbacks() {
        assertEquals(4, rewardForTaken("minecraft:cooked_beef", 1));
        assertEquals(12, rewardForTaken("minecraft:cooked_beef", 3));
        assertEquals(36, rewardForTaken("minecraft:cooked_beef", 9));
        assertEquals(0, rewardForTaken("minecraft:cooked_beef", 0));
        assertEquals(0, rewardForTaken("minecraft:cooked_beef", -1));
        assertEquals(21, rewardForTaken("minecraft:cooked_cod", 7));
        assertEquals(8, rewardForTaken("minecraft:cooked_mutton", 4));
    }
    @Test void modernFoodsAndOreRewardsDoNotEnterThisFoodPolicy() {
        assertEquals(-1, rewardPerItem("minecraft:cooked_salmon"));
        assertEquals(-1, rewardPerItem("minecraft:dried_kelp"));
        assertEquals(-1, rewardPerItem("minecraft:iron_ingot"));
        assertEquals(-1, rewardPerItem("mite:mithril_ingot"));
        assertEquals(0, rewardForTaken("minecraft:iron_ingot", 64));
        assertEquals(0, rewardForTaken("minecraft:bread", 64));
    }
}
