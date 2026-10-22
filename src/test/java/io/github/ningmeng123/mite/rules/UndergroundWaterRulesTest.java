package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class UndergroundWaterRulesTest {
    @Test
    void aquiferRegionsUseFloorDivisionAcrossNegativeCoordinatesAndChunkEdges() {
        assertEquals(-2, UndergroundWaterRules.horizontalRegion(-65));
        assertEquals(-1, UndergroundWaterRules.horizontalRegion(-64));
        assertEquals(-1, UndergroundWaterRules.horizontalRegion(-1));
        assertEquals(0, UndergroundWaterRules.horizontalRegion(0));
        assertEquals(0, UndergroundWaterRules.horizontalRegion(63));
        assertEquals(1, UndergroundWaterRules.horizontalRegion(64));
        assertEquals(-2, UndergroundWaterRules.verticalRegion(-41));
        assertEquals(-1, UndergroundWaterRules.verticalRegion(-40));
        assertEquals(-1, UndergroundWaterRules.verticalRegion(-1));
        assertEquals(0, UndergroundWaterRules.verticalRegion(39));
        assertEquals(1, UndergroundWaterRules.verticalRegion(40));
        for (int coordinate = -64; coordinate < 0; coordinate++)
            assertEquals(-1, UndergroundWaterRules.horizontalRegion(coordinate));
        for (int coordinate = 0; coordinate < 64; coordinate++)
            assertEquals(0, UndergroundWaterRules.horizontalRegion(coordinate));
    }

    @Test
    void suppressionKeepsFourOfTenBucketsIncludingUnsignedSamples() {
        for (long bucket = 0L; bucket < 10L; bucket++)
            assertEquals(bucket < 6L, UndergroundWaterRules.suppress(bucket));
        assertTrue(UndergroundWaterRules.suppress(-1L));
        assertFalse(UndergroundWaterRules.suppress(Long.MIN_VALUE));
    }

    @Test
    void independentLargeSamplesRetainFortyPercentOfWaterRegions() {
        for (long seed : new long[]{196L, 20261022L, -741923469L}) {
            Random random = new Random(seed);
            int retained = 0;
            for (int sample = 0; sample < 100_000; sample++)
                if (!UndergroundWaterRules.suppress(random.nextLong())) retained++;
            assertTrue(retained >= 39_300 && retained <= 40_700, "Retained regional samples: " + retained);
        }
    }

    @Test
    void signedSamplesRepeatWithoutDependingOnEvaluationOrder() {
        Random random = new Random(196L);
        long[] samples = new long[1000];
        boolean[] decisions = new boolean[samples.length];
        for (int sample = 0; sample < samples.length; sample++) {
            samples[sample] = random.nextLong();
            decisions[sample] = UndergroundWaterRules.suppress(samples[sample]);
        }
        for (int sample = samples.length - 1; sample >= 0; sample--)
            assertEquals(decisions[sample], UndergroundWaterRules.suppress(samples[sample]));
    }
}
