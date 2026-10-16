package io.github.ningmeng123.mite.curse;

import java.util.HashSet;
import io.github.ningmeng123.mite.rules.WitchCurseRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Expected vectors independently transcribed from R196 tv/Curse bytecode. @author NingMeng123 */
class WitchCursingTest {
    @Test void sourceNamesUsePositionWeightedCharactersAndSixtyFourSlotRejection() {
        assertEquals(5, WitchCurseRules.selectId(0, "NingMeng123"));
        assertEquals(15, WitchCurseRules.selectId(0, "Steve"));
        assertEquals(13, WitchCurseRules.selectId(0, "Alex"));
        assertEquals(15, WitchCurseRules.selectId(0, ""));
    }

    @Test void sourceIntOverflowOccursBeforeRandomSeedWidens() {
        assertEquals(3, WitchCurseRules.selectId(Integer.MAX_VALUE, "NingMeng123"));
        assertEquals(16, WitchCurseRules.selectId(Integer.MIN_VALUE, "A"));
    }

    @Test void theFirstCharacterHasZeroWeightAndSelectionIsStable() {
        assertEquals(WitchCurseRules.selectId(1234, "Steve"), WitchCurseRules.selectId(1234, "Xteve"));
        assertEquals(WitchCurseRules.selectId(-3456, "Alex"), WitchCurseRules.selectId(-3456, "Alex"));
        assertEquals(6000, WitchCurseRules.REALIZATION_DELAY);
    }

    @Test void theEntireReleaseCurseSetRemainsReachable() {
        var observed = new HashSet<Integer>();
        for (int seed = 0; seed < 10000; ++seed) {
            int id = WitchCurseRules.selectId(seed, "Steve");
            assertTrue(id >= 1 && id <= 16);
            observed.add(id);
        }
        assertEquals(16, observed.size());
    }
}
