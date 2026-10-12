package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finite probability-space checks independent of Minecraft RNG. @author NingMeng123 */
class FlintGravelRulesTest {
    @Test
    void enumeratedFlintBranchesRemainUnchangedWithMineralsEnabled() {
        for (int fortune = 0; fortune <= 3; fortune++) {
            int denominator = new int[]{12, 10, 8, 6}[fortune];
            int[] counts = new int[FlintGravelRules.Drop.values().length];
            for (int initial = 0; initial < denominator; initial++) {
                // Branch zero now continues into the mineral tree, tested independently.
                for (int branch = 1; branch < 3; branch++) {
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
                        assertEquals(initial > 2 ? 1 : 3, cursor[0]);
                    }
                }
            }
            assertEquals(6, counts[FlintGravelRules.Drop.FLINT.ordinal()]);
            assertEquals(90, counts[FlintGravelRules.Drop.FLINT_CHIP.ordinal()]);
            assertEquals((denominator - 3) * 32, counts[FlintGravelRules.Drop.GRAVEL.ordinal()]);
            for (var drop : FlintGravelRules.Drop.values()) {
                if (drop != FlintGravelRules.Drop.GRAVEL && drop != FlintGravelRules.Drop.FLINT
                        && drop != FlintGravelRules.Drop.FLINT_CHIP) {
                    assertEquals(0, counts[drop.ordinal()]);
                }
            }
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
