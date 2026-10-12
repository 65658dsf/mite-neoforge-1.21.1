package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finite probability-space checks independent of Minecraft RNG. @author NingMeng123 */
class FlintGravelRulesTest {
    @Test
    void enumeratedFlintBranchesMatchAllFortuneProbabilities() {
        for (int fortune = 0; fortune <= 3; fortune++) {
            int denominator = new int[]{12, 10, 8, 6}[fortune];
            int[] counts = new int[3];
            for (int initial = 0; initial < denominator; initial++) {
                for (int branch = 0; branch < 3; branch++) {
                    for (int flint = 0; flint < 16; flint++) {
                        int[] values = {initial, branch, flint};
                        int[] bounds = {denominator, 3, 16};
                        int[] cursor = {0};
                        FlintGravelRules.Drop result = FlintGravelRules.roll(fortune, bound -> {
                            int index = cursor[0]++;
                            assertEquals(bounds[index], bound);
                            return values[index];
                        });
                        counts[result.ordinal()]++;
                        assertEquals(initial > 2 ? 1 : branch == 0 ? 2 : 3, cursor[0]);
                    }
                }
            }
            assertEquals(6, counts[FlintGravelRules.Drop.FLINT.ordinal()]);
            assertEquals(90, counts[FlintGravelRules.Drop.FLINT_CHIP.ordinal()]);
            assertEquals(denominator * 48 - 96, counts[FlintGravelRules.Drop.GRAVEL.ordinal()]);
        }
    }

    @Test
    void fortuneIsBoundedBeforeComputingTheRandomDenominator() {
        for (int fortune : new int[]{Integer.MIN_VALUE, -1, 0, 3, 4, Integer.MAX_VALUE}) {
            int[] firstBound = {0};
            var drop = FlintGravelRules.roll(fortune, bound -> {
                assertEquals(0, firstBound[0]);
                firstBound[0] = bound;
                return bound - 1;
            });
            assertEquals(fortune <= 0 ? 12 : 6, firstBound[0]);
            assertTrue(drop == FlintGravelRules.Drop.GRAVEL);
        }
    }
}
