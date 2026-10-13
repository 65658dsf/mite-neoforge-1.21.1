package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import org.junit.jupiter.api.Test;

import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality.*;
import static io.github.ningmeng123.mite.rules.EquipmentQualityRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal R196 expectations and arithmetic boundaries; no Minecraft runtime. @author NingMeng123 */
class EquipmentQualityRulesTest {
    @Test
    void difficultyDoublesOnlyAboveAverageAndPreservesNull() {
        float[] expected = {1250F, 1250F, 1250F, 2500F, 5000F, 10000F, 20000F, 40000F};
        for (Quality quality : Quality.values()) assertEquals(expected[quality.ordinal()], adjustedDifficulty(1250F, quality));
        assertEquals(1250F, adjustedDifficulty(1250F, null));
    }

    @Test
    void xpUsesFloatRoundingBeforeTheCurseMultiplier() {
        assertEquals(0, experienceCost(2.49F, false));
        assertEquals(1, experienceCost(2.5F, false));
        assertEquals(2, experienceCost(2.5F, true));
        assertEquals(1, experienceCost(7.49F, false));
        assertEquals(2, experienceCost(7.5F, false));
        assertEquals(4, experienceCost(7.5F, true));
        assertEquals(2, experienceCost(3F, true)); // doubling before rounding would incorrectly give 1
    }

    @Test
    void minimumQualityHasNegativeLevelAndCurseBoundaries() {
        int[] levels = {-40, -21, -20, -19, -10, -9, -1, 0, 9, 10, 100};
        Quality[] expected = {WRETCHED, WRETCHED, WRETCHED, POOR, POOR, AVERAGE, AVERAGE, AVERAGE, AVERAGE, AVERAGE, AVERAGE};
        for (int i = 0; i < levels.length; i++) assertEquals(expected[i], minimumQuality(levels[i], false, false, false));
        assertEquals(WRETCHED, minimumQuality(0, true, false, false));
        assertEquals(POOR, minimumQuality(1, true, false, false));
        assertEquals(POOR, minimumQuality(10, true, false, false));
        assertEquals(AVERAGE, minimumQuality(11, true, false, false));
        assertEquals(AVERAGE, minimumQuality(20, true, false, false));
    }

    @Test
    void disabledSkillsHaveNoEffectAndMissingEnabledSkillsCapAtPoor() {
        assertEquals(AVERAGE, minimumQuality(50, false, false, false));
        assertEquals(POOR, minimumQuality(50, false, true, false));
        assertEquals(WRETCHED, minimumQuality(-20, false, true, false));
        assertEquals(AVERAGE, minimumQuality(50, false, true, true));
        assertEquals(POOR, maximumQuality(1250F, 50, 100000, EXCELLENT, false, true, false));
        assertEquals(EXCELLENT, maximumQuality(1250F, 50, 100000, EXCELLENT, false, false, false));
    }

    @Test
    void copperPickaxeUsesTotalPointsAndInclusiveAffordability() {
        assertEquals(AVERAGE, maximumQuality(1250F, 80, 499, EXCELLENT, false, false, true));
        assertEquals(FINE, maximumQuality(1250F, 0, 500, EXCELLENT, false, false, true));
        assertEquals(FINE, maximumQuality(1250F, 80, 999, EXCELLENT, false, false, true));
        assertEquals(EXCELLENT, maximumQuality(1250F, 0, 1000, EXCELLENT, false, false, true));
        assertEquals(EXCELLENT, maximumQuality(1250F, 80, 1000000, EXCELLENT, false, false, true));
    }

    @Test
    void ironPickaxeCostsAreLiteralSourceRecipeDifficulty2450() {
        assertEquals(980, selectedExperienceCost(2450F, FINE, false, false));
        assertEquals(1960, selectedExperienceCost(2450F, EXCELLENT, false, false));
        assertEquals(3920, selectedExperienceCost(2450F, SUPERB, false, false));
        assertEquals(7840, selectedExperienceCost(2450F, MASTERWORK, false, false));
        assertEquals(SUPERB, maximumQuality(2450F, 80, 7839, MASTERWORK, false, false, true));
        assertEquals(MASTERWORK, maximumQuality(2450F, 80, 7840, MASTERWORK, false, false, true));
    }

    @Test
    void nonpositiveXpFallsBackToMinimumEvenForAFreeRoundedRecipe() {
        assertEquals(AVERAGE, maximumQuality(0F, 0, 0, LEGENDARY, false, false, true));
        assertEquals(POOR, maximumQuality(0F, -10, -1, LEGENDARY, false, false, true));
        assertEquals(LEGENDARY, maximumQuality(0F, 0, 1, LEGENDARY, false, false, true));
    }

    @Test
    void curseAffectsMaximumCostAndAllowsIntermediateLowQualities() {
        assertEquals(WRETCHED, maximumQuality(1250F, 0, 999, EXCELLENT, true, false, true));
        QualityRange allowed = range(1250F, 0, 1000, EXCELLENT, true, false, true);
        assertEquals(WRETCHED, allowed.minimum());
        assertEquals(FINE, allowed.maximum());
        assertTrue(allowed.contains(POOR));
        assertTrue(allowed.contains(AVERAGE));
        assertEquals(2000, selectedExperienceCost(1250F, EXCELLENT, false, true));
    }

    @Test
    void exemptionDoesNotChangeDifficultyAndLowQualityChargesNothing() {
        for (Quality quality : new Quality[]{WRETCHED, POOR, AVERAGE})
            assertEquals(0, selectedExperienceCost(2450F, quality, false, true));
        assertEquals(0, selectedExperienceCost(2450F, null, false, true));
        assertEquals(0, selectedExperienceCost(2450F, MASTERWORK, true, true));
        assertEquals(39200F, adjustedDifficulty(2450F, MASTERWORK));
    }

    @Test
    void materialCapAlsoAppliesWhenThePlayerMinimumIsHigher() {
        QualityRange rusted = range(1250F, 50, 100000, POOR, false, false, true);
        assertEquals(POOR, rusted.minimum());
        assertEquals(POOR, rusted.maximum());
        assertFalse(rusted.canCycle());
        assertEquals(POOR, rusted.next(AVERAGE));
    }

    @Test
    void previewRangeIncludesUnaffordableQualitiesWithoutChangingManufacturingAbility() {
        QualityRange selection = selectionRange(0, EXCELLENT, false, false, true);
        assertEquals(AVERAGE, selection.minimum());
        assertEquals(EXCELLENT, selection.maximum());
        assertTrue(selection.contains(FINE));
        assertTrue(selection.contains(EXCELLENT));
        assertFalse(range(1250F, 0, 499, EXCELLENT, false, false, true).contains(FINE));
        assertTrue(range(1250F, 0, 500, EXCELLENT, false, false, true).contains(FINE));
        assertFalse(selection.contains(SUPERB));
    }

    @Test
    void previewStillRespectsNegativeLevelMinimumAndLowMaterialCap() {
        QualityRange debt = selectionRange(-10, EXCELLENT, false, false, true);
        assertEquals(POOR, debt.minimum());
        assertEquals(EXCELLENT, debt.maximum());
        assertTrue(debt.contains(AVERAGE));
        assertFalse(range(1250F, -10, -200, EXCELLENT, false, false, true).contains(AVERAGE));
        QualityRange rusted = selectionRange(0, POOR, false, false, true);
        assertEquals(POOR, rusted.minimum());
        assertEquals(POOR, rusted.maximum());
        assertFalse(rusted.canCycle());
    }

    @Test
    void rightClickRaisesQualityWrapsAndClampsWhenXpDrops() {
        QualityRange allowed = range(1250F, 0, 1000, EXCELLENT, false, false, true);
        assertEquals(FINE, allowed.next(AVERAGE));
        assertEquals(EXCELLENT, allowed.next(FINE));
        assertEquals(AVERAGE, allowed.next(EXCELLENT));
        assertTrue(allowed.canCycle());
        assertFalse(allowed.contains(LEGENDARY));
        assertFalse(allowed.contains(null));
        QualityRange depleted = range(1250F, 0, 499, EXCELLENT, false, false, true);
        assertEquals(AVERAGE, depleted.clamp(EXCELLENT));
    }

    @Test
    void repairEligibilityUsesFullManufacturingDifficultyButNeverChargesQualityXp() {
        assertFalse(canRepair(FINE, 2450F, 0, 979, MASTERWORK, false, false, true));
        assertTrue(canRepair(FINE, 2450F, 0, 980, MASTERWORK, false, false, true));
        assertTrue(canRepair(WRETCHED, 2450F, 0, 0, MASTERWORK, false, false, true));
        assertEquals(2450F, adjustedDifficulty(1225F, FINE)); // aaf's half difficulty, then quality
        assertEquals(0, selectedExperienceCost(1225F, FINE, true, false));
        assertTrue(canRepair(null, 2450F, 0, 0, MASTERWORK, false, false, true));
    }

    @Test
    void paidCraftingStopsWhereFreeAndRepairCraftingMayContinue() {
        assertTrue(stopAfterCompletion(980));
        assertTrue(stopAfterCompletion(1));
        assertFalse(stopAfterCompletion(0));
    }

    @Test
    void qualityDifficultyFlowsThroughExistingTimeFormulaWithoutXpExemptionShortcuts() {
        assertEquals(83, TimedCraftingRules.period(adjustedDifficulty(100F, AVERAGE), 0, .2F, false));
        assertEquals(116, TimedCraftingRules.period(adjustedDifficulty(100F, FINE), 0, .2F, false));
        assertEquals(233, TimedCraftingRules.period(adjustedDifficulty(100F, FINE), 0, .2F, true));
    }

    @Test
    void malformedDifficultiesAndInvertedRangesAreRejected() {
        for (float difficulty : new float[]{-1F, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> adjustedDifficulty(difficulty, AVERAGE));
            assertThrows(IllegalArgumentException.class, () -> experienceCost(difficulty, false));
        }
        assertThrows(IllegalArgumentException.class, () -> new QualityRange(EXCELLENT, FINE));
    }
}
