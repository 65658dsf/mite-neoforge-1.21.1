package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.R196OreRules;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Configurable rates change attempts rather than vein size or existing blocks. @author NingMeng123 */
class OreGenerationRatesTest {
    @Test void defaultRatesPreserveEveryUnderworldCandidateCountAndRandomStream() {
        Random actual = new Random(196), expected = new Random(196);
        for (var kind : R196OreRules.UNDERWORLD) {
            int original = R196OreRules.candidateCount(kind, R196OreRules.Dimension.UNDERWORLD);
            assertEquals(original, OreGenerationRates.scaleCandidates(original, 1.0D, actual), kind.name());
            assertEquals(original, OreGenerationRates.scaleCandidates(1, original, actual), "Actual configured count: " + kind);
        }
        assertEquals(expected.nextLong(), actual.nextLong());
    }

    @Test void defaultCandidateProbabilityMatchesEveryOriginalRollAndTheFollowingRandomStream() {
        var dimension = R196OreRules.Dimension.UNDERWORLD;
        for (long seed : new long[]{0L, 196L, -1L, Long.MAX_VALUE}) {
            Random actual = new Random(seed), expected = new Random(seed);
            for (var kind : R196OreRules.UNDERWORLD) {
                int originalCount = R196OreRules.candidateCount(kind, dimension);
                int actualCount = OreGenerationRates.scaleCandidates(1, originalCount, actual);
                assertEquals(originalCount, actualCount);
                for (int candidate = 0; candidate < actualCount; candidate++) {
                    boolean originalPass = expected.nextInt(10) == 0;
                    assertEquals(originalPass, OreGenerationRates.passesChance(R196OreRules.candidateChance(dimension), dimension, actual),
                            kind + " candidate " + candidate + " for seed " + seed);
                    // A successful candidate consumes coordinate and vein-generation draws before the next pass roll.
                    if (originalPass) {
                        assertEquals(expected.nextInt(16), actual.nextInt(16));
                        assertEquals(expected.nextInt(256), actual.nextInt(256));
                        assertEquals(expected.nextInt(16), actual.nextInt(16));
                        assertEquals(expected.nextLong(), actual.nextLong());
                    }
                }
            }
            assertEquals(expected.nextLong(), actual.nextLong(), "Post-generation stream for seed " + seed);
        }
    }

    @Test void zeroRatesDisableEveryUnderworldCandidateAndIntegerMultipliersScaleIndependently() {
        Random noDraws = new Random(0) {
            @Override public double nextDouble() { return fail("Integral candidate counts need no extra random draw"); }
        };
        for (var kind : R196OreRules.UNDERWORLD) {
            int original = R196OreRules.candidateCount(kind, R196OreRules.Dimension.UNDERWORLD);
            assertEquals(0, OreGenerationRates.scaleCandidates(original, 0.0D, noDraws));
            assertEquals(original * 2, OreGenerationRates.scaleCandidates(original, 2.0D, noDraws));
            assertEquals(original * 100, OreGenerationRates.scaleCandidates(original, 100.0D, noDraws));
        }
    }

    @Test void fractionalRatesRetainSubCandidateDepositsInsteadOfRoundingThemAway() {
        Random random = new Random(196);
        int samples = 100_000, total = 0;
        for (int i = 0; i < samples; i++) {
            int count = OreGenerationRates.scaleCandidates(40, 0.001D, random);
            assertTrue(count == 0 || count == 1);
            total += count;
        }
        assertEquals(samples * 0.04D, total, 200.0D);
    }

    @Test void fractionalCandidateBoundariesUseTheConfiguredProbability() {
        assertEquals(3, OreGenerationRates.scaleCandidates(10, 0.25D, draw(0.499D)));
        assertEquals(2, OreGenerationRates.scaleCandidates(10, 0.25D, draw(0.5D)));
        assertEquals(3, OreGenerationRates.scaleCandidates(1, 2.5D, draw(0.499D)));
        assertEquals(2, OreGenerationRates.scaleCandidates(1, 2.5D, draw(0.5D)));
    }

    @Test void zeroAndCertainPassesDoNotConsumeRandomnessAndInvalidProbabilitiesAreRejected() {
        Random actual = new Random(196), expected = new Random(196);
        for (int sample = 0; sample < 100; sample++) {
            assertFalse(OreGenerationRates.passesChance(0D, R196OreRules.Dimension.UNDERWORLD, actual));
            assertTrue(OreGenerationRates.passesChance(1D, R196OreRules.Dimension.UNDERWORLD, actual));
        }
        assertEquals(expected.nextLong(), actual.nextLong());
        for (double chance : new double[]{-.1D, 1.1D, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,
                    () -> OreGenerationRates.passesChance(chance, R196OreRules.Dimension.UNDERWORLD, actual));
        assertEquals(expected.nextLong(), actual.nextLong(), "Rejected probabilities cannot consume random values");
    }

    @Test void customPassProbabilitiesUseExactlyOneDoubleDrawWithTheCorrectBoundary() {
        var dimension = R196OreRules.Dimension.UNDERWORLD;
        assertTrue(OreGenerationRates.passesChance(.25D, dimension, draw(.249999D)));
        assertFalse(OreGenerationRates.passesChance(.25D, dimension, draw(.25D)));
        assertFalse(OreGenerationRates.passesChance(.25D, dimension, draw(.9D)));
        Random actual = new Random(196), expected = new Random(196);
        for (int sample = 0; sample < 1000; sample++)
            assertEquals(expected.nextDouble() < .25D, OreGenerationRates.passesChance(.25D, dimension, actual));
        assertEquals(expected.nextLong(), actual.nextLong());
    }

    @Test void aQuarterPassRateProducesTheExpectedFrequencyForActualConfiguredCandidates() {
        Random random = new Random(196);
        int candidates = OreGenerationRates.scaleCandidates(1, 100_000D, random), accepted = 0;
        for (int candidate = 0; candidate < candidates; candidate++)
            if (OreGenerationRates.passesChance(.25D, R196OreRules.Dimension.UNDERWORLD, random)) accepted++;
        assertEquals(25_000D, accepted, 600D);
    }

    private static Random draw(double value) {
        return new Random(0) {
            @Override public double nextDouble() { return value; }
        };
    }
}
