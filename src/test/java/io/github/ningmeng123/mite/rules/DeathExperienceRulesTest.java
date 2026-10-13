package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Death vectors from R196 jv.class, using the bytecode-verified first threshold 20. @author NingMeng123 */
class DeathExperienceRulesTest {
    @ParameterizedTest(name = "death at {0} XP respawns with {1} XP")
    @CsvSource({
            "2147483646, 0", "819520, 0", "491840, 0", "203000, 0", "6650, 0", "493, 0", "21, 0", "20, 0", "19, -1",
            "1, -19", "0, -20", "-1, -21", "-20, -40", "-21, -41",
            "-779, -799", "-780, -800", "-799, -800", "-800, -800",
            "-2147483648, -800", "2147483647, 0"
    })
    void appliesTheThresholdAndDebtFloorWithoutOverflow(int total, int expected) {
        assertEquals(expected, ExperienceRules.experienceAfterDeath(total));
    }

    @ParameterizedTest(name = "death at {0} drops {1} XP")
    @CsvSource({"2147483646, 715827882", "819520, 273173", "491840, 163946", "203000, 67666", "-800, 0"})
    void highBalancesRetainTheOriginalOneThirdDeathReward(int total, int expected) {
        assertEquals(expected, ExperienceRules.deathExperienceReward(total));
    }
}
