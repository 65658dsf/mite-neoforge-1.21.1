package io.github.ningmeng123.mite.rules;

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
}
