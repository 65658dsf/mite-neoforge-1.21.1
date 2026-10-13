package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Boundary vectors from R196 uf.class bytecode, independent of the production
 * closed-form formula. The static initializer adds 20, then 30, then 40, etc.;
 * CFR's pre-increment rendering must not be used as the expected oracle.
 *
 * @author NingMeng123
 */
class ExperienceRulesTest {
    // Literal table obtained by following the original <clinit> accumulation order.
    // Keeping the oracle as data also catches the 10 * level CFR discrepancy.
    private static final int[] R196_LEVEL_THRESHOLDS = {
            0, 20, 50, 90, 140, 200, 270, 350, 440, 540,
            650, 770, 900, 1040, 1190, 1350, 1520, 1700, 1890, 2090,
            2300, 2520, 2750, 2990, 3240, 3500, 3770, 4050, 4340, 4640,
            4950, 5270, 5600, 5940, 6290, 6650, 7020, 7400, 7790, 8190,
            8600, 9020, 9450, 9890, 10340, 10800, 11270, 11750, 12240, 12740,
            13250, 13770, 14300, 14840, 15390, 15950, 16520, 17100, 17690, 18290,
            18900, 19520, 20150, 20790, 21440, 22100, 22770, 23450, 24140, 24840,
            25550, 26270, 27000, 27740, 28490, 29250, 30020, 30800, 31590, 32390,
            33200, 34020, 34850, 35690, 36540, 37400, 38270, 39150, 40040, 40940,
            41850, 42770, 43700, 44640, 45590, 46550, 47520, 48500, 49490, 50490,
            51500, 52520, 53550, 54590, 55640, 56700, 57770, 58850, 59940, 61040,
            62150, 63270, 64400, 65540, 66690, 67850, 69020, 70200, 71390, 72590,
            73800, 75020, 76250, 77490, 78740, 80000, 81270, 82550, 83840, 85140,
            86450, 87770, 89100, 90440, 91790, 93150, 94520, 95900, 97290, 98690,
            100100, 101520, 102950, 104390, 105840, 107300, 108770, 110250, 111740, 113240,
            114750, 116270, 117800, 119340, 120890, 122450, 124020, 125600, 127190, 128790,
            130400, 132020, 133650, 135290, 136940, 138600, 140270, 141950, 143640, 145340,
            147050, 148770, 150500, 152240, 153990, 155750, 157520, 159300, 161090, 162890,
            164700, 166520, 168350, 170190, 172040, 173900, 175770, 177650, 179540, 181440,
            183350, 185270, 187200, 189140, 191090, 193050, 195020, 197000, 198990, 200990,
            203000
    };

    @Test
    void matchesEveryOriginalPositiveThresholdAndBothSidesOfItsBoundary() {
        assertEquals(201, R196_LEVEL_THRESHOLDS.length);
        for (int level = 0; level < R196_LEVEL_THRESHOLDS.length; level++) {
            int threshold = R196_LEVEL_THRESHOLDS[level];
            assertEquals(threshold, ExperienceRules.experienceRequiredForLevel(level), "threshold " + level);
            assertEquals(level, ExperienceRules.levelForTotalExperience(threshold), "level at " + threshold);
            assertEquals(0, ExperienceRules.experienceWithinLevel(threshold), "points at " + threshold);
            assertEquals(0.0f, ExperienceRules.progressForTotalExperience(threshold), "progress at " + threshold);
            if (level > 0) {
                int precedingWidth = threshold - R196_LEVEL_THRESHOLDS[level - 1];
                assertEquals(level - 1, ExperienceRules.levelForTotalExperience(threshold - 1), "level before " + threshold);
                assertEquals(precedingWidth - 1, ExperienceRules.experienceWithinLevel(threshold - 1));
                assertEquals(precedingWidth, ExperienceRules.experienceNeededForNextLevel(level - 1));
            }
        }
    }

    @ParameterizedTest(name = "negative/out-of-range level {0} starts at {1} XP")
    @CsvSource({
            "-2147483648, -800", "-41, -800", "-40, -800", "-39, -780",
            "-2, -40", "-1, -20", "201, 205020", "312, 491400", "403, 818090",
            "20722, 2147317250", "20723, 2147483647", "2147483647, 2147483647"
    })
    void safelyResolvesThresholdsOutsideThePositiveTable(int level, int expected) {
        assertEquals(expected, ExperienceRules.experienceRequiredForLevel(level));
    }

    @ParameterizedTest(name = "total {0}: level {1}, points {2}, progress {3}")
    @CsvSource({
            "-2147483648, -40, 0, 0", "-801, -40, 0, 0", "-800, -40, 0, 0",
            "-799, -40, 1, 0.05", "-781, -40, 19, 0.95", "-780, -39, 0, 0",
            "-779, -39, 1, 0.05", "-41, -3, 19, 0.95", "-40, -2, 0, 0",
            "-39, -2, 1, 0.05", "-21, -2, 19, 0.95", "-20, -1, 0, 0",
            "-19, -1, 1, 0.05", "-1, -1, 19, 0.95", "0, 0, 0, 0",
            "1, 0, 1, 0.05", "19, 0, 19, 0.95", "20, 1, 0, 0",
            "35, 1, 15, 0.5", "70, 2, 20, 0.5", "170, 4, 30, 0.5",
            "235, 5, 35, 0.5", "710, 10, 60, 0.5", "6835, 35, 185, 0.5"
    })
    void usesFloorDivisionAndOriginalProgressIntervals(int total, int level, int points, float progress) {
        assertEquals(level, ExperienceRules.levelForTotalExperience(total));
        assertEquals(points, ExperienceRules.experienceWithinLevel(total));
        assertEquals(progress, ExperienceRules.progressForTotalExperience(total), 0.000001f);
    }

    @ParameterizedTest(name = "level {0} bar width {1}")
    @CsvSource({
            "-2147483648, 20", "-41, 20", "-40, 20", "-1, 20",
            "0, 20", "1, 30", "4, 60", "5, 70", "9, 110", "10, 120",
            "34, 360", "35, 370", "199, 2010", "200, 2020",
            "201, 2030", "312, 3140", "403, 4050", "20722, 207240", "2147483647, 2147483647"
    })
    void continuesTheOriginalLevelWidthFormulaPast200(int level, int expectedWidth) {
        assertEquals(expectedWidth, ExperienceRules.experienceNeededForNextLevel(level));
    }

    @Test
    void continuedLevelsAndIntBoundaryTerminateWithoutWrapping() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(199, ExperienceRules.levelForTotalExperience(202999));
            assertEquals(200, ExperienceRules.levelForTotalExperience(203000));
            assertEquals(200, ExperienceRules.levelForTotalExperience(203001));
            assertEquals(1, ExperienceRules.experienceWithinLevel(203001));
            assertEquals(200, ExperienceRules.levelForTotalExperience(205019));
            assertEquals(201, ExperienceRules.levelForTotalExperience(205020));
            assertEquals(20722, ExperienceRules.levelForTotalExperience(Integer.MAX_VALUE - 1));
            assertEquals(20722, ExperienceRules.levelForTotalExperience(Integer.MAX_VALUE));
            assertEquals(166397, ExperienceRules.experienceWithinLevel(Integer.MAX_VALUE));
            assertEquals(166397F / 207240F, ExperienceRules.progressForTotalExperience(Integer.MAX_VALUE));
        });
    }

    @ParameterizedTest(name = "normalize {0} to {1}")
    @CsvSource({
            "-9223372036854775808, -800", "-2147483648, -800", "-801, -800",
            "-800, -800", "-1, -1", "0, 0", "203000, 203000", "203001, 203001",
            "491840, 491840", "819520, 819520", "2147483646, 2147483646",
            "2147483647, 2147483647", "2147483648, 2147483647",
            "9223372036854775807, 2147483647"
    })
    void clampsLongInputsWithoutNarrowingOverflow(long total, int expected) {
        assertEquals(expected, ExperienceRules.clampTotalExperience(total));
    }

    @ParameterizedTest(name = "{0} XP plus signed delta {1} gives {2}")
    @CsvSource({
            "0, 20, 20", "19, 1, 20", "20, -1, 19", "0, -1, -1",
            "-799, -1, -800", "-800, -1, -800", "-800, 1, -799",
            "203000, 1, 203001", "2147483646, 1, 2147483647",
            "2147483647, 1, 2147483647", "2147483647, 2147483647, 2147483647",
            "819520, -491840, 327680", "491840, -491840, 0", "819520, -819520, 0",
            "0, -2147483648, -800", "-800, -2147483648, -800",
            "-2147483648, -2147483648, -800", "2147483647, -2147483648, -1",
            "-2147483648, 2147483647, -1"
    })
    void addsOrSpendsExperienceWithoutIntOverflow(int total, int delta, int expected) {
        assertEquals(expected, ExperienceRules.addTotalExperience(total, delta));
    }

    @ParameterizedTest(name = "total {0}: continued level {1}, points {2}, width {3}")
    @CsvSource({"203001, 200, 1, 2020", "205020, 201, 0, 2030", "491840, 312, 440, 3140",
            "819520, 403, 1430, 4050", "2147483647, 20722, 166397, 207240"})
    void level200DoesNotStopTheQuadraticCurve(int total, int level, int expectedPoints, int width) {
        assertEquals(level, ExperienceRules.levelForTotalExperience(total));
        assertEquals(expectedPoints, ExperienceRules.experienceWithinLevel(total));
        assertEquals(width, ExperienceRules.experienceNeededForNextLevel(level));
        assertEquals((float)expectedPoints / width, ExperienceRules.progressForTotalExperience(total));
    }
}
