package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** yc.java setXPReward, ye quantity multiplication and vq's actual result-slot withdrawal. @author NingMeng123 */
class OreCookingExperienceRulesTest {
    @ParameterizedTest
    @CsvSource({"minecraft:copper_ingot,10", "mite:silver_ingot,15", "minecraft:iron_ingot,10", "minecraft:gold_ingot,20",
            "mite:mithril_ingot,40", "mite:adamantium_ingot,100", "minecraft:diamond,30", "minecraft:emerald,20", "minecraft:quartz,10",
            "minecraft:coal,0", "minecraft:charcoal,0", "minecraft:redstone,0", "minecraft:lapis_lazuli,5"})
    void independentSourceOutputRewardsApplyOnlyToTheWithdrawnQuantity(String id, int reward) {
        assertEquals(reward, OreCookingExperienceRules.rewardPerItem(id));
        assertEquals(reward * 3, OreCookingExperienceRules.rewardForTaken(id, 3));
        assertEquals(0, OreCookingExperienceRules.rewardForTaken(id, 0));
        assertEquals(0, OreCookingExperienceRules.rewardForTaken(id, -1));
    }
    @Test void unknownModernOutputsKeepTheirNativeRewardPathAndHugeWithdrawalsDoNotOverflow() {
        assertEquals(-1, OreCookingExperienceRules.rewardPerItem("minecraft:netherite_scrap"));
        assertEquals(0, OreCookingExperienceRules.rewardForTaken("minecraft:netherite_scrap", 3));
        assertEquals(Integer.MAX_VALUE, OreCookingExperienceRules.rewardForTaken("mite:adamantium_ingot", Integer.MAX_VALUE));
    }
}
