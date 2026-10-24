package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import static io.github.ningmeng123.mite.rules.NutritionRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent literal examples from uf, ux, jv, yc and EnumInsulinResistanceLevel. @author NingMeng123 */
class NutritionRulesTest {
    @Test
    void sicknessAndInabilityToBreatheOverrideEvenAlwaysEdibleFood() {
        assertFalse(canIngest(6, 6, 6, 0, 0, 0, true, true, true, 0, 0, true, false));
        assertFalse(canIngest(6, 6, 6, 0, 0, 0, true, true, true, 0, 0, false, true));
        assertTrue(canIngest(6, 6, 6, 2, 2, 0, true, false, false, 160_000, 160_000, false, false));
    }

    @Test
    void ZeroEnergyDrinksRemainUsableButSugarOnlyFoodDoesNotUseThatException() {
        assertTrue(canIngest(6, 6, 6, 0, 0, 0, false, false, false, 160_000, 160_000, false, false));
        assertFalse(canIngest(6, 6, 6, 0, 0, 1_000, false, false, false, 160_000, 160_000, false, false));
        assertFalse(canIngest(5, 6, 6, 0, 0, 1_000, false, false, false, 160_000, 160_000, false, false));
    }

    @Test
    void FullSatiationBlocksFoodWithSatiationUnlessNutritionIsCompletelyEmpty() {
        assertFalse(canIngest(6, 1, 6, 2, 2, 0, false, false, false, 160_000, 160_000, false, false));
        assertTrue(canIngest(6, 0, 6, 2, 2, 0, false, false, false, 160_000, 160_000, false, false));
        assertTrue(canIngest(6, 5, 6, 0, 2, 0, false, false, false, 160_000, 160_000, false, false));
        assertFalse(canIngest(6, 6, 6, 0, 2, 0, false, false, false, 160_000, 160_000, false, false));
    }

    @Test
    void MissingSatiationAcceptsSatiatingFoodEvenWhenNutritionIsFull() {
        assertTrue(canIngest(5, 6, 6, 1, 0, 0, false, false, false, 160_000, 160_000, false, false));
        assertFalse(canIngest(5, 6, 6, 0, 1, 0, false, false, false, 160_000, 160_000, false, false));
        assertTrue(canIngest(5, 5, 6, 0, 1, 0, false, false, false, 160_000, 160_000, false, false));
    }

    @Test
    void OnlyDepletedMicronutrientsGrantTheFullEnergyFoodException() {
        assertTrue(canIngest(20, 20, 20, 2, 2, 0, false, true, false, 0, 160_000, false, false));
        assertTrue(canIngest(20, 20, 20, 2, 2, 0, false, false, true, 160_000, 0, false, false));
        assertFalse(canIngest(20, 20, 20, 2, 2, 0, false, true, true, 1, 1, false, false));
        assertFalse(canIngest(20, 20, 20, 2, 2, 0, false, false, true, 0, 160_000, false, false));
        assertFalse(canIngest(20, 20, 20, 2, 0, 0, false, true, true, 0, 0, false, false));
    }

    @Test
    void MicronutrientsStartFullAndSaturateUsingEightThousandPerNutrition() {
        assertEquals(160_000, MICRONUTRIENT_INITIAL);
        assertEquals(160_000, MICRONUTRIENT_MAX);
        assertEquals(0, clampMicronutrient(-1));
        assertEquals(160_000, clampMicronutrient(160_001));
        assertEquals(24_000, addMicronutrient(0, 3, true));
        assertEquals(159_000, addMicronutrient(159_000, 3, false));
        assertEquals(160_000, addMicronutrient(159_000, 3, true));
        assertEquals(160_000, addMicronutrient(0, Integer.MAX_VALUE, true));
        assertEquals(0, addMicronutrient(0, Integer.MIN_VALUE, true));
    }

    @Test
    void MalnourishmentDependsOnProteinOrPlantNutrientsOnly() {
        assertFalse(isMalnourished(1, 1));
        assertTrue(isMalnourished(0, 1));
        assertTrue(isMalnourished(1, 0));
        assertTrue(isMalnourished(0, 0));
    }

    @Test
    void SugarResistanceUsesFloatMultiplicationThenIntegerTruncation() {
        assertEquals(0, addedResistance(0));
        assertEquals(4, addedResistance(1));
        assertEquals(9, addedResistance(2));
        assertEquals(4_800, addedResistance(1_000));
        assertEquals(48_000, addedResistance(10_000));
        assertEquals(192_000, addedResistance(40_000));
    }

    @Test
    void FreshResistanceEntersAtEachInclusiveThreshold() {
        int[] input = {0, 1, 47_999, 48_000, 95_999, 96_000, 143_999, 144_000, 192_000};
        int[] expected = {-1, -1, -1, 0, 0, 1, 1, 2, 2};
        for (int i = 0; i < input.length; i++) assertEquals(expected[i], resistanceLevel(input[i], -1));
    }

    @Test
    void ResistanceRetreatHasTheOriginalOneLevelHysteresis() {
        assertEquals(2, resistanceLevel(143_999, 2));
        assertEquals(2, resistanceLevel(96_000, 2));
        assertEquals(1, resistanceLevel(95_999, 2));
        assertEquals(1, resistanceLevel(48_000, 1));
        assertEquals(0, resistanceLevel(47_999, 2));
        assertEquals(0, resistanceLevel(1, 0));
        assertEquals(-1, resistanceLevel(0, 2));
        assertEquals(-1, resistanceLevel(-1, 1));
        assertEquals(2, resistanceLevel(Integer.MAX_VALUE, -1));
    }

    @Test
    void ModerateAndSevereResistanceSubtractSugarWithoutClampingFoodSatiation() {
        assertEquals(4, adjustedSatiation(4, 2_000, -1));
        assertEquals(4, adjustedSatiation(4, 2_000, 0));
        assertEquals(4, adjustedSatiation(4, 0, 2));
        assertEquals(3, adjustedSatiation(4, 1, 1));
        assertEquals(3, adjustedSatiation(4, 999, 2));
        assertEquals(3, adjustedSatiation(4, 1_000, 1));
        assertEquals(3, adjustedSatiation(4, 1_999, 2));
        assertEquals(2, adjustedSatiation(4, 2_000, 1));
        assertEquals(-2, adjustedSatiation(0, 2_000, 2));
    }

    @Test
    void NormalRainAndFeetImmersionHaveAnAdditionalCombinedPenalty() {
        assertEquals(1F, metabolicMultiplier(false, false, 0, 0.8F, false));
        assertEquals(1F, metabolicMultiplier(false, true, 0, 0.8F, false));
        assertEquals(1.25F, metabolicMultiplier(true, false, 0, 0.8F, false));
        assertEquals(1.25F, metabolicMultiplier(false, false, 1, 0.8F, false));
        assertEquals(1.375F, metabolicMultiplier(true, false, 1, 0.8F, false));
        assertEquals(1.5F, metabolicMultiplier(true, true, 1, 0.8F, false));
        assertEquals(1.5F, metabolicMultiplier(true, false, 2, 0.8F, false));
    }

    @Test
    void FreezingDoublesWetnessButNotTheMalnourishmentPenalty() {
        assertEquals(1.75F, metabolicMultiplier(true, false, 1, 0.15F, false));
        assertEquals(2.25F, metabolicMultiplier(true, false, 1, 0.15F, true));
        assertEquals(1.5F, metabolicMultiplier(false, false, 0, 0F, true));
        assertEquals(2.5F, metabolicMultiplier(true, true, 2, 0F, true));
        assertEquals(1.375F, metabolicMultiplier(true, false, 1, 0.15001F, false));
    }

    @Test
    void DesertRiverTemperatureRemovesWetnessIncludingFullImmersion() {
        assertEquals(1F, metabolicMultiplier(true, true, 2, 1.4F, false));
        assertEquals(1.5F, metabolicMultiplier(true, true, 2, 1.4F, true));
        assertEquals(1.5F, metabolicMultiplier(false, false, 2, 1.3999F, false));
    }

    @Test
    void RegenerationCombinesNutritionMalnourishmentBedAndHighestEnchantment() {
        assertEquals(0.00052F, regenerationIncrement(6, false, false, 0), 0.000000001F);
        assertEquals(0.0008F, regenerationIncrement(20, false, false, 0), 0.000000001F);
        assertEquals(0.00013F, regenerationIncrement(6, true, false, 0), 0.000000001F);
        assertEquals(0.00416F, regenerationIncrement(6, false, true, 0), 0.000000001F);
        assertEquals(0.00104F, regenerationIncrement(6, true, true, 0), 0.000000001F);
        assertEquals(0.0013F, regenerationIncrement(6, false, false, 3), 0.000000001F);
        assertEquals(0.0004F, regenerationIncrement(0, false, false, 0), 0.000000001F);
    }

    @Test
    void StarvationDamageKeepsTheLiteralHealthThresholdsForAllDifficulties() {
        for (int difficulty : new int[]{0, 1}) {
            assertFalse(starvationCanDamage(10F, difficulty));
            assertTrue(starvationCanDamage(10.01F, difficulty));
        }
        assertFalse(starvationCanDamage(1F, 2));
        assertTrue(starvationCanDamage(1.01F, 2));
        assertTrue(starvationCanDamage(1F, 3));
        assertTrue(starvationCanDamage(0.5F, 3));
    }
}
