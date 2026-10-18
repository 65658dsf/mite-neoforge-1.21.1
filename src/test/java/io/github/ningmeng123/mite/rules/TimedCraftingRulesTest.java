package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Literal vectors independently recorded in the R196 timing evidence. @author NingMeng123 */
class TimedCraftingRulesTest {
    @Test void sourceFloatBoundariesAndWholeBatchVectors() {
        assertEquals(25, TimedCraftingRules.basePeriod(10F));
        assertEquals(26, TimedCraftingRules.basePeriod(25.5F));
        assertEquals(100, TimedCraftingRules.basePeriod(Math.nextUp(100F)));
        assertEquals(101, TimedCraftingRules.basePeriod(101F));
        assertEquals(196, TimedCraftingRules.basePeriod(400F));
        assertEquals(132, TimedCraftingRules.basePeriod(175F));
        assertEquals(161, TimedCraftingRules.basePeriod(270F));
        assertEquals(111, TimedCraftingRules.basePeriod(120.00001F));
    }
    @Test void signedLevelsUseFloatTruncationAndMinimumPeriod() {
        assertEquals(499, TimedCraftingRules.period(100F, -40, 0F, false));
        assertEquals(249, TimedCraftingRules.period(100F, -40, .2F, false));
        assertEquals(52, TimedCraftingRules.period(100F, 35, .2F, false));
        assertEquals(25, TimedCraftingRules.period(100F, 200, .2F, false));
        assertEquals(166, TimedCraftingRules.period(100F, 0, .2F, true));
        assertEquals(163, TimedCraftingRules.period(400F, 0, .2F, false));
    }
    @Test void naturalLevelSpeedStopsAtTwoHundredForLongRecipes() {
        assertEquals(230, TimedCraftingRules.period(6400F, 199, .2F, false));
        for (int level : new int[]{200, 201, Integer.MAX_VALUE}) {
            assertEquals(229, TimedCraftingRules.period(6400F, level, .2F, false));
            assertEquals(459, TimedCraftingRules.period(6400F, level, .2F, true));
        }
    }
    @Test void noMaterialRequirementAlwaysUsesPointTwoEvenAtHighestBench() {
        assertEquals(0F, TimedCraftingRules.benchModifier(0F, false));
        assertEquals(.2F, TimedCraftingRules.benchModifier(256F, false));
        assertEquals(.7F, TimedCraftingRules.benchModifier(256F, true));
        assertEquals(.3F, TimedCraftingRules.benchModifier(4F, true));
        assertEquals(50, TimedCraftingRules.damageSetback(1F));
        assertEquals(163835, TimedCraftingRules.damageSetback(100000F));
    }
}
