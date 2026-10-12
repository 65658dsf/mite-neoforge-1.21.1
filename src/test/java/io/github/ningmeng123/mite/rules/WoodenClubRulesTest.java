package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Literal acceptance vectors from the archived ItemCudgel/ItemClub/ItemTool chain. @author NingMeng123 */
class WoodenClubRulesTest {
    @Test
    void originalValuesAndSixteenHitLifetime() {
        assertEquals(400, WoodenClubRules.MAX_DAMAGE);
        assertEquals(2.0F, WoodenClubRules.MINING_SPEED);
        assertEquals(10, WoodenClubRules.ENCHANTABILITY);
        assertEquals(25, WoodenClubRules.ATTACK_WEAR);
        assertEquals(16, WoodenClubRules.MAX_DAMAGE / WoodenClubRules.ATTACK_WEAR);
    }

    @Test
    void positiveHardnessUsesTruncationAndOnePointMinimum() {
        float[] hardness = {0.0F, -0.0F, 0.001F, 0.04F, 0.1F, 0.3F, 0.5F, 0.6F, 1.0F};
        int[] wear = {0, 0, 1, 1, 2, 7, 12, 15, 25};
        for (int i = 0; i < hardness.length; i++) {
            assertEquals(wear[i], WoodenClubRules.blockWear(hardness[i]), "hardness " + hardness[i]);
        }
    }
}
