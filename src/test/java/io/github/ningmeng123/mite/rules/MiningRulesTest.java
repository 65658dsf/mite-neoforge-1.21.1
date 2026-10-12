package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Independent level-multiplier vectors; gameplay bounds are -40 and 200. @author NingMeng123 */
class MiningRulesTest {
    @ParameterizedTest(name = "level {0} multiplies final mining speed by {1}")
    @CsvSource({
            "-2147483648, 0.2", "-41, 0.2", "-40, 0.2", "-39, 0.22",
            "-20, 0.6", "-1, 0.98", "0, 1.0", "1, 1.02", "5, 1.1",
            "35, 1.7", "100, 3.0", "199, 4.98", "200, 5.0",
            "201, 5.0", "2147483647, 5.0"
    })
    void matchesLiteralMultipliersAndClampsExtremeInputs(int level, float expected) {
        float multiplier = MiningRules.speedMultiplierForLevel(level);
        assertEquals(expected, multiplier, 0.000001F);
        assertTrue(Float.isFinite(multiplier));
        assertTrue(multiplier > 0.0F);
    }

    @Test
    void zeroHardnessPrecedesTheStrengthChecksInTheOriginalPureRule() {
        for (float strength : new float[]{1.0F, 0.0F, -1.0F, Float.NaN}) {
            assertEquals(1.0F, MiningRules.destroyProgress(strength, 0.0F));
            assertEquals(1.0F, MiningRules.destroyProgress(strength, -0.0F));
        }
        assertEquals(0.0F, MiningRules.destroyProgress(1.0F, -1.0F));
        assertEquals(0.0F, MiningRules.destroyProgress(0.0F, 0.5F));
        assertEquals(0.0F, MiningRules.destroyProgress(-1.0F, 0.5F));
        assertEquals(0.0F, MiningRules.destroyProgress(Float.NaN, 0.5F));
        assertEquals(0.0F, MiningRules.destroyProgress(1.0F, Float.NaN));
    }

    @Test
    void theInstantThresholdIsStrictAndUsesTheOriginalFloatOperationOrder() {
        int[] hardnessBits = {0x3b3fffff, 0x3b400000, 0x3b400001};
        int[] progressBits = {0x3f800000, 0x3f2aaaab, 0x3f2aaaaa};
        for (int i = 0; i < hardnessBits.length; i++) {
            float progress = MiningRules.destroyProgress(1.0F, Float.intBitsToFloat(hardnessBits[i]));
            assertEquals(progressBits[i], Float.floatToRawIntBits(progress), "threshold vector " + i);
        }
        // This bytecode vector differs by one ULP if simplified to strength/hardness/512.
        assertEquals(0x3d6fffff, Float.floatToRawIntBits(MiningRules.destroyProgress(3.0F, 0.1F)));
    }

    @Test
    void levelZeroProgressAndFloatAccumulationMatchOriginalPackageVectors() {
        float[] hardness = {0.5F, 0.4F, 0.6F, 0.2F};
        int[] progressBits = {0x3b800000, 0x3ba00000, 0x3b555555, 0x3c200000};
        int[] ticks = {256, 205, 308, 103};
        for (int i = 0; i < hardness.length; i++) {
            float progress = MiningRules.destroyProgress(1.0F, hardness[i]);
            assertEquals(progressBits[i], Float.floatToRawIntBits(progress));
            assertEquals(ticks[i], accumulateTicks(progress));
        }
    }

    @Test
    void dirtProgressAtRepresentativeLevelsMatchesRecordedFloatVectors() {
        int[] levels = {0, -40, 35, 200, 5};
        int[] progressBits = {0x3b800000, 0x3a4ccccf, 0x3bd9999a, 0x3ca00000, 0x3b8ccccd};
        int[] ticks = {256, 1281, 151, 52, 233};
        for (int i = 0; i < levels.length; i++) {
            float progress = MiningRules.destroyProgress(MiningRules.speedMultiplierForLevel(levels[i]), 0.5F);
            assertEquals(progressBits[i], Float.floatToRawIntBits(progress), "level " + levels[i]);
            assertEquals(ticks[i], accumulateTicks(progress), "level " + levels[i]);
        }
    }

    private static int accumulateTicks(float progress) {
        float accumulated = 0.0F;
        int ticks = 0;
        while (accumulated < 1.0F && ticks < 10000) {
            accumulated += progress;
            ticks++;
        }
        assertTrue(accumulated >= 1.0F, "The finite test vector must finish mining");
        return ticks;
    }
}
