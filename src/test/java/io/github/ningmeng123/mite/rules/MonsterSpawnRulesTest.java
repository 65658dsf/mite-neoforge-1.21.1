package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent mass, endpoint and fractional-weight checks for editable spawn rates. @author NingMeng123 */
class MonsterSpawnRulesTest {
    @Test void candidateWeightAndFinalSpeciesRatesComposeWithoutChangingTheReplacementLottery() {
        double candidate = 40D, probability = .25D, parentRate = .5D, variantRate = 2D;
        double total = candidate * MonsterSpawnRules.familyMultiplier(probability, parentRate, variantRate);
        double replacement = MonsterSpawnRules.replacementProbability(probability, parentRate, variantRate);
        assertEquals(15D, total * (1D - replacement), 1E-12D);
        assertEquals(20D, total * replacement, 1E-12D);
        assertEquals(0, MonsterSpawnRules.weightedIndex(new double[]{total, 35D}, .25D));
        assertEquals(1, MonsterSpawnRules.weightedIndex(new double[]{total, 35D}, .75D));
        assertEquals(-1, MonsterSpawnRules.weightedIndex(new double[]{0D * total}, .5D));
    }

    @Test void replacementFamilyConservesEachSpeciesWeightedMass() {
        for (double probability : new double[]{0D, .05D, 1D / 6D, .5D, 1D})
            for (double parent : new double[]{0D, .000001D, 1D, 100D})
                for (double child : new double[]{0D, .000001D, 1D, 100D}) {
                    double family = MonsterSpawnRules.familyMultiplier(probability, parent, child);
                    double conditional = MonsterSpawnRules.replacementProbability(probability, parent, child);
                    assertEquals((1D - probability) * parent, family * (1D - conditional), 1E-12D);
                    assertEquals(probability * child, family * conditional, 1E-12D);
                }
    }

    @Test void disabledParentsKeepEnabledVariantsAndDisabledVariantsCannotBeSelected() {
        assertEquals(1D / 6D, MonsterSpawnRules.familyMultiplier(1D / 6D, 0D, 1D));
        assertEquals(1D, MonsterSpawnRules.replacementProbability(1D / 6D, 0D, 1D));
        assertEquals(0D, MonsterSpawnRules.replacementProbability(1D / 6D, 1D, 0D));
        assertEquals(0D, MonsterSpawnRules.replacementProbability(.05D, 0D, 0D));
        assertEquals(1D, MonsterSpawnRules.replacementProbability(1D, 0D, 1D));
        assertEquals(0D, MonsterSpawnRules.replacementProbability(0D, 0D, 1D));
    }

    @Test void increasingReplacementRateRaisesItsConditionalProbability() {
        assertEquals(.05D, MonsterSpawnRules.replacementProbability(.05D, 1D, 1D));
        assertEquals(.1D / 1.05D, MonsterSpawnRules.replacementProbability(.05D, 1D, 2D), 1E-15D);
        assertEquals(.5D, MonsterSpawnRules.replacementProbability(1D / 6D, 1D, 5D), 1E-15D);
        assertEquals(1D, MonsterSpawnRules.familyMultiplier(1D / 6D, 1D, 1D));
    }

    @Test void infernalDepthProbabilityMatchesAllFortyExistingIntegerOutcomes() {
        for (int sourceY : new int[]{-100, 0, 1, 20, 39, 40, 100}) {
            int accepted = 0;
            for (int depthDraw = 0; depthDraw < 40; depthDraw++)
                if (R196SpawnRules.infernalCreeper(sourceY, depthDraw, 0F)) accepted++;
            assertEquals(accepted / 80D, MonsterSpawnRules.infernalCreeperProbability(sourceY));
        }
    }

    @Test void fractionalAndVeryLargeWeightsDoNotBecomeZeroOrOverflow() {
        assertEquals(0, MonsterSpawnRules.weightedIndex(new double[]{.00000001D, 1D}, 0D));
        assertEquals(1, MonsterSpawnRules.weightedIndex(new double[]{.00000001D, 1D}, .5D));
        assertEquals(0, MonsterSpawnRules.weightedIndex(new double[]{Double.MAX_VALUE, Double.MAX_VALUE}, .25D));
        assertEquals(1, MonsterSpawnRules.weightedIndex(new double[]{Double.MAX_VALUE, Double.MAX_VALUE}, .75D));
        assertEquals(1, MonsterSpawnRules.weightedIndex(new double[]{0D, Double.MIN_NORMAL, 0D}, .99D));
        assertEquals(2, MonsterSpawnRules.weightedIndex(new double[]{1D, 2D, 3D}, Math.nextDown(1D)));
        assertEquals(-1, MonsterSpawnRules.weightedIndex(new double[]{0D, 0D}, .5D));
        assertEquals(-1, MonsterSpawnRules.weightedIndex(new double[0], .5D));
    }

    @Test void invalidWeightsProbabilitiesAndDrawsAreRejected() {
        for (double invalid : new double[]{-1D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> MonsterSpawnRules.weightedIndex(new double[]{invalid}, .5D));
            assertThrows(IllegalArgumentException.class, () -> MonsterSpawnRules.familyMultiplier(.5D, invalid, 1D));
        }
        assertThrows(IllegalArgumentException.class, () -> MonsterSpawnRules.replacementProbability(1.01D, 1D, 1D));
        assertThrows(IllegalArgumentException.class, () -> MonsterSpawnRules.weightedIndex(new double[]{1D}, 1D));
    }
}
