package io.github.ningmeng123.mite.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Reference boundary cases, including truncation and ITE's rank-independent Indomitable. @author NingMeng123 */
class GearModifierRulesTest {
    @Test void miningAddsToMaterialEfficiencyBeforeTheBaseToolFactor() {
        assertEquals(9F, GearModifierRules.miningSpeed(8F, 2F, .25F, 0F, 0, 0F, false));
        assertEquals(10.2F, GearModifierRules.miningSpeed(8F, 2F, .25F, .1F, 3, 0F, false), .0001F);
        assertEquals(15.2F, GearModifierRules.miningSpeed(8F, 2F, .25F, .1F, 3, 1.25F, true), .0001F);
        assertEquals(9F, GearModifierRules.miningSpeed(8F, 2F, .25F, .1F, -3, 1.25F, false));
        assertEquals(1F, GearModifierRules.miningSpeed(1F, 2F, 2.5F, .5F, 6, 6.25F, true));
    }

    @Test void wetBonusRequiresWaterOrRainAndScalesWithTheMaterial() {
        assertEquals(8F, GearModifierRules.miningSpeed(8F, 2F, 0F, 0F, 0, 1.25F, false));
        assertEquals(13F, GearModifierRules.miningSpeed(8F, 2F, 0F, 0F, 0, 1.25F, true));
        assertEquals(17F, GearModifierRules.miningSpeed(12F, 3F, 0F, 0F, 0, 1.25F, true));
    }

    @Test void durabilityTruncatesToolWearAndPreservesArmorMinimum() {
        assertEquals(0, GearModifierRules.wear(1, .1F, false));
        assertEquals(1, GearModifierRules.wear(1, .1F, true));
        assertEquals(5, GearModifierRules.wear(10, .5F, false));
        assertEquals(5, GearModifierRules.wear(10, .5F, true));
        assertEquals(0, GearModifierRules.wear(0, .5F, true));
        assertEquals(10, GearModifierRules.wear(10, 0F, true));
    }

    @Test void indomitableUsesReferenceHealthThresholds() {
        assertEquals(1F, GearModifierRules.indomitable(.5F, false));
        assertEquals(1F, GearModifierRules.indomitable(.50001F, true));
        assertEquals(1.25F, GearModifierRules.indomitable(.5F, true));
        assertEquals(1.35F, GearModifierRules.indomitable(.35F, true));
        assertEquals(1.6F, GearModifierRules.indomitable(.2F, true));
        assertEquals(2F, GearModifierRules.indomitable(.1F, true));
        assertEquals(2F, GearModifierRules.indomitable(.01F, true));
    }

    @Test void slowingAndImmunityUseReferenceDurationAndAmplifierRounding() {
        for (int level = 1; level <= 5; level++) {
            assertEquals(20 * level, GearModifierRules.slowTicks(level));
            assertEquals(level / 2, GearModifierRules.slowAmplifier(level));
        }
        assertEquals(39, GearModifierRules.effectDuration(100, .6F));
        assertEquals(8, GearModifierRules.effectDuration(10, .15F));
        assertEquals(-1, GearModifierRules.effectDuration(-1, .6F));
    }

    @Test void unnaturalUsesRawRepairMaterialLevelsInsteadOfNonmetalToolHarvestTiers() {
        assertEquals(2, GearModifierRules.materialHarvestLevel("flint"));
        assertEquals(3, GearModifierRules.materialHarvestLevel("obsidian"));
        assertEquals(2, GearModifierRules.materialHarvestLevel("copper"));
        assertEquals(5, GearModifierRules.materialHarvestLevel("adamantium"));
        assertEquals(3.5F, GearModifierRules.materialEfficiency("vibranium"));
        assertEquals(1.25F, GearModifierRules.materialEfficiency("rusted_iron"));
    }
}
