package io.github.ningmeng123.mite.rules;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OreHeightRangeTest {
    @Test void parsesAbsoluteInclusiveNegativeHeightsAndRejectsInvalidRanges() {
        assertEquals(new OreHeightRange(-100, 100), OreHeightRange.parse(" [ -100, 100 ] "));
        assertEquals("[-100,100]", OreHeightRange.parse("[-100,100]").toString());
        for (String invalid : new String[]{"100,200", "[100,-100]", "[1.5,20]", "[-2033,0]", "[0,2032]", "[0,999999999999]"})
            assertThrows(IllegalArgumentException.class, () -> OreHeightRange.parse(invalid));
    }

    @Test void clipsToActualBuildLimitsAndSupportsSingleLayerAndEmptyIntersections() {
        var random = new Random(196);
        var range = new OreHeightRange(-100, 100);
        for (int index = 0; index < 1000; index++) {
            int height = range.sample(random, -64, 320);
            assertTrue(height >= -64 && height <= 100);
        }
        assertEquals(12, new OreHeightRange(12, 12).sample(random, -64, 320));
        assertNull(new OreHeightRange(-100, -65).sample(random, -64, 320));
        assertNull(new OreHeightRange(320, 500).sample(random, -64, 320));
        assertTrue(range.contains(-100));
        assertTrue(range.contains(100));
        assertFalse(range.contains(101));
        assertEquals(new OreHeightRange(-64, 139), OreHeightRange.defaults(R196OreRules.Kind.ADAMANTIUM, R196OreRules.Dimension.UNDERWORLD));
    }
}
