package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Persistence and exhaustion boundaries remain independent of source mob predicates. @author NingMeng123 */
class SpawnerBalanceRulesTest {
    @Test void nineSecondsAndFifteenSuccessfulSpawns() {
        assertEquals(180, SpawnerBalanceRules.INTERVAL_TICKS);
        assertFalse(SpawnerBalanceRules.exhausted(14));
        assertTrue(SpawnerBalanceRules.exhausted(15));
        assertEquals(0, SpawnerBalanceRules.count(-1));
        assertEquals(14, SpawnerBalanceRules.count(14));
        assertEquals(15, SpawnerBalanceRules.count(Integer.MAX_VALUE));
    }
}
