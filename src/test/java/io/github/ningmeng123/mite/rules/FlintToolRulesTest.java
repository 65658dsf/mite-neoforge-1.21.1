package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Literal vectors from the R196 flint hatchet's scaled wear sequence. @author NingMeng123 */
class FlintToolRulesTest {
    @Test
    void averageQualityHatchetRetainsItsRecordedValues() {
        assertEquals(400, FlintToolRules.MAX_DAMAGE);
        assertEquals(2.5F, FlintToolRules.MINING_SPEED);
        assertEquals(133, FlintToolRules.attackWear());
    }

    @Test
    void blockWearUsesTruncationAndTheSixPointMinimum() {
        float[] hardness = {0.0F, -0.0F, 0.001F, 0.05F, 0.1F, 0.2F, 0.4F, 0.5F, 1.0F, 1.2F, 1.5F, 2.0F, 3.0F};
        int[] wear = {0, 0, 6, 6, 13, 26, 53, 66, 133, 160, 200, 266, 400};
        for (int i = 0; i < hardness.length; i++) {
            assertEquals(wear[i], FlintToolRules.blockWear(hardness[i]), "hardness " + hardness[i]);
        }
    }

    @Test
    void wearKeepsTheOriginalFloatMultiplicationAndDivisionOrder() {
        assertEquals(6, FlintToolRules.blockWear(Float.intBitsToFloat(0x3d570a3b)));
        // Multiplying hardness by 100, then 4, then dividing by 3 would incorrectly give 6 here.
        assertEquals(7, FlintToolRules.blockWear(Float.intBitsToFloat(0x3d570a3c)));
        assertEquals(7, FlintToolRules.blockWear(Float.intBitsToFloat(0x3d570a3d)));
        assertEquals(160, FlintToolRules.blockWear(Float.intBitsToFloat(0x3f999999)));
        assertEquals(160, FlintToolRules.blockWear(Float.intBitsToFloat(0x3f99999a)));
    }
}
