package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boundary vectors for level-dependent health, food capacity, and melee reach.
 *
 * @author NingMeng123
 */
class ProgressionRulesTest {
    @ParameterizedTest(name = "level {0} uses attribute level {1}")
    @CsvSource({"-2147483648,-2147483648", "-100,-100", "-40,-40", "-1,-1", "0,0", "35,35",
            "199,199", "200,200", "201,200", "312,200", "403,200", "2147483647,200"})
    void attributeCeilingPreservesSignedLevelsWithoutChangingTheExperienceModel(int level, int expected) {
        assertEquals(200, ProgressionRules.MAX_ATTRIBUTE_LEVEL);
        assertEquals(expected, ProgressionRules.attributeLevel(level));
    }

    @ParameterizedTest(name = "level {0} has {1} base HP")
    @CsvSource({
            "-2147483648, 6",
            "-40, 6",
            "-1, 6",
            "0, 6",
            "1, 6",
            "4, 6",
            "5, 8",
            "6, 8",
            "9, 8",
            "10, 10",
            "11, 10",
            "14, 10",
            "15, 12",
            "16, 12",
            "19, 12",
            "20, 14",
            "21, 14",
            "24, 14",
            "25, 16",
            "26, 16",
            "29, 16",
            "30, 18",
            "31, 18",
            "34, 18",
            "35, 20",
            "36, 20",
            "200, 20",
            "201, 20",
            "312, 20",
            "403, 20",
            "2147483647, 20"
    })
    void matchesHealthCurveAtEveryTransition(int level, double expectedHealth) {
        assertEquals(expectedHealth, ProgressionRules.maxHealthForLevel(level));
        assertEquals((int) expectedHealth, ProgressionRules.foodLimitForLevel(level));
    }

    @ParameterizedTest(name = "level {0} grants {1} blocks of melee reach")
    @CsvSource({
            "-2147483648, 0.0",
            "-100, 0.0",
            "-10, 0.0",
            "-1, 0.0",
            "0, 0.0",
            "1, 0.0",
            "5, 0.0",
            "9, 0.0",
            "10, 0.1",
            "11, 0.1",
            "19, 0.1",
            "20, 0.2",
            "29, 0.2",
            "30, 0.3",
            "39, 0.3",
            "40, 0.4",
            "49, 0.4",
            "50, 0.5",
            "59, 0.5",
            "60, 0.6",
            "69, 0.6",
            "70, 0.7",
            "79, 0.7",
            "80, 0.8",
            "89, 0.8",
            "90, 0.9",
            "99, 0.9",
            "100, 1.0",
            "101, 1.0",
            "200, 1.0",
            "2147483647, 1.0"
    })
    void meleeReachUsesCurrentLevelAtEveryTransition(int level, double expectedReachBonus) {
        assertEquals(expectedReachBonus, ProgressionRules.meleeReachBonusForLevel(level));
    }
}
