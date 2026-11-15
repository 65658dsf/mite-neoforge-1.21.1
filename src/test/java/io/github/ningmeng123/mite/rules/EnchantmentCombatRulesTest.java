package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independently extracted aaw/of/abh boundary examples. @author NingMeng123 */
class EnchantmentCombatRulesTest {
    @Test void stunUsesTenPercentPerLevelAndTheSourceHighSlownessAmplifier() {
        assertFalse(EnchantmentCombatRules.meleeProc(0, 0D));
        assertTrue(EnchantmentCombatRules.meleeProc(1, .099999D));
        assertFalse(EnchantmentCombatRules.meleeProc(1, .1D));
        assertEquals(250, EnchantmentCombatRules.stunTicks(5));
        assertEquals(25, EnchantmentCombatRules.stunAmplifier(5));
    }
    @Test void vampiricHasMinimumOneButNeverDrainsAnIneligibleOrUndamagedTarget() {
        assertEquals(0, EnchantmentCombatRules.vampiricTransfer(5, 0F, true, 0D, .9D));
        assertEquals(0, EnchantmentCombatRules.vampiricTransfer(5, 10F, false, 0D, .9D));
        assertEquals(0, EnchantmentCombatRules.vampiricTransfer(5, 10F, true, .50001D, .9D));
        assertEquals(1, EnchantmentCombatRules.vampiricTransfer(1, .5F, true, .09D, .99D));
        assertEquals(4, EnchantmentCombatRules.vampiricTransfer(5, 10F, true, .49D, .99D));
        assertEquals(0, EnchantmentCombatRules.vampiricTransfer(0, 10F, true, .1D, .9D));
    }
    @Test void paralysisResistanceAffectsTheNegativeCombinedAmountAndCapsAtEightyPercent() {
        assertEquals(.96F, EnchantmentCombatRules.movementFactor(0, -1, false, 4), .000001F);
        assertEquals(.85F, EnchantmentCombatRules.movementFactor(-1, -1, true, 4), .000001F);
        assertEquals(1.2F, EnchantmentCombatRules.movementFactor(0, 1, false, 4), .000001F);
        assertEquals(.76F, EnchantmentCombatRules.movementFactor(5, -1, false, 4), .000001F);
        assertEquals(0F, EnchantmentCombatRules.movementFactor(25, -1, false, 4));
        assertEquals(.8F, EnchantmentCombatRules.freeActionResistance(100));
    }
    @Test void webAndPotionFactorsAreComposedWithoutApplyingTheResistanceTwice() {
        double potion = 1D + EnchantmentCombatRules.playerSlownessAmount(0, -1, 4);
        double web = EnchantmentCombatRules.webMovementRatio(0, -1, 4);
        assertEquals(.96D, potion, .000001D);
        assertEquals(.81D * .25D, potion * web * .25D, .000001D);
        assertEquals(1.25F, EnchantmentCombatRules.speedMultiplier(5));
    }
    @Test void websSlowBothMouseAxesUsingTheR196SensitivityCurve() {
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, false, 0F));
        assertEquals(1D / 12.25D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, true, 0F), .000001D);
        assertEquals(1D / 3.25D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, true, .8F), .000001D);
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, true, 1F));
        assertEquals(1D / 12.25D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, true, -1F), .000001D);
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(-1, -1, true, 2F));
    }
    @Test void lookSlowdownCombinesWebsPotionsAndFreeActionBeforeItsOwnCap() {
        assertEquals(1D / 4D, EnchantmentCombatRules.lookSensitivityFactor(0, -1, false, 0F), .000001D);
        assertEquals(1D / 13D, EnchantmentCombatRules.lookSensitivityFactor(5, -1, false, 0F), .000001D);
        assertEquals(1D / 13D, EnchantmentCombatRules.lookSensitivityFactor(25, -1, true, 0F), .000001D);
        assertEquals(1D / 4.6D, EnchantmentCombatRules.lookSensitivityFactor(5, -1, false, .8F), .000001D);
        assertEquals(1D / 3.85D, EnchantmentCombatRules.lookSensitivityFactor(0, -1, true, .8F), .000001D);
        assertEquals(1D / 9.25D, EnchantmentCombatRules.lookSensitivityFactor(-1, 0, true, 0F), .000001D);
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(0, 0, false, 0F));
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(-1, 3, true, 0F));
        assertEquals(1D, EnchantmentCombatRules.lookSensitivityFactor(-1, 10, false, 0F));
    }
    @Test void thornsRollAndDamageBoundariesMatchAbhIncludingUnsafeCommandLevels() {
        assertFalse(EnchantmentCombatRules.thornsProc(0, 0F));
        assertTrue(EnchantmentCombatRules.thornsProc(3, .4499F));
        assertFalse(EnchantmentCombatRules.thornsProc(3, .45001F));
        assertEquals(1, EnchantmentCombatRules.thornsDamage(3, 0));
        assertEquals(4, EnchantmentCombatRules.thornsDamage(3, 3));
        assertEquals(2, EnchantmentCombatRules.thornsDamage(12, 0));
    }
}
