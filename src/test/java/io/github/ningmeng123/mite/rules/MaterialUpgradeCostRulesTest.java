package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Whole-ingot upgrades pay for every nugget in the complete equipment recipe. @author NingMeng123 */
class MaterialUpgradeCostRulesTest {
    @Test void completeMetalCostsRoundUpWithoutDiscountingNuggets() {
        assertEquals(5, MaterialUpgradeCostRules.roundedIngots(5, 0));
        assertEquals(2, MaterialUpgradeCostRules.roundedIngots(2, 0));
        assertEquals(3, MaterialUpgradeCostRules.roundedIngots(2, 1));
        assertEquals(3, MaterialUpgradeCostRules.roundedIngots(2, 4));
        assertEquals(4, MaterialUpgradeCostRules.roundedIngots(3, 1));
        assertEquals(4, MaterialUpgradeCostRules.roundedIngots(3, 4));
        assertEquals(3, MaterialUpgradeCostRules.roundedIngots(0, 20));
        assertEquals(4, MaterialUpgradeCostRules.roundedIngots(0, 32));
        assertEquals(4, MaterialUpgradeCostRules.roundedIngots(0, 28));
        assertEquals(2, MaterialUpgradeCostRules.roundedIngots(0, 16));
        assertEquals(1, MaterialUpgradeCostRules.roundedIngots(0, 1));
        assertEquals(1, MaterialUpgradeCostRules.roundedIngots(0, 0));
    }

    @Test void invalidAndOverflowingCostsCannotBecomeFreeUpgrades() {
        assertThrows(IllegalArgumentException.class, () -> MaterialUpgradeCostRules.roundedIngots(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> MaterialUpgradeCostRules.roundedIngots(0, -1));
        assertEquals(Integer.MAX_VALUE, MaterialUpgradeCostRules.roundedIngots(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
