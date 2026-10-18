package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent uf:787..833 portable handling cases. @author NingMeng123 */
class PortableBlockRulesTest {
    @Test void portableStartsAtFourHardnessThenAppliesLevelInsteadOfHeldToolEfficiency() {
        assertEquals(8F, PortableBlockRules.strength(2F, 0, -1, -1, false, true, true));
        assertEquals(16F, PortableBlockRules.strength(2F, 50, -1, -1, false, true, true));
        for (int level : new int[]{200, 201, 250, Integer.MAX_VALUE}) {
            assertEquals(40F, PortableBlockRules.strength(2F, level, -1, -1, false, true, true));
        }
        assertEquals(12.8F, PortableBlockRules.strength(2F, 30, -1, -1, false, true, true), 1E-5F);
    }
    @Test void allEnvironmentalAndNegativeLevelPenaltiesAreClampedOnlyAfterTheLevelMultiplier() {
        assertEquals(8F, PortableBlockRules.strength(2F, -40, -1, -1, true, false, false));
        assertEquals(8F, PortableBlockRules.strength(2F, 0, -1, 4, false, true, true));
        assertEquals(8F, PortableBlockRules.strength(2F, 100, -1, -1, false, true, false));
        assertEquals(8F, PortableBlockRules.strength(2F, 450, -1, -1, true, true, true));
        assertEquals(9.6F, PortableBlockRules.strength(2F, 0, 0, -1, false, true, true));
        assertEquals(9.6F, PortableBlockRules.strength(2F, 25, -1, 0, false, true, true));
    }
}
