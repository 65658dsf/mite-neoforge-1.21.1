package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Temperature-independent growth and coordinate-table boundaries. @author NingMeng123 */
final class SpecialPlantRulesTest {
    @Test void stemsKeepTheirOptimalBaseAndOnlyMelonsRetainTheHumidityBonus() {
        assertEquals(.25F,SpecialPlantRules.stemClimate(false,false));
        assertEquals(.25F,SpecialPlantRules.stemClimate(false,true));
        assertEquals(.25F,SpecialPlantRules.stemClimate(true,false));
        assertEquals(.3F,SpecialPlantRules.stemClimate(true,true));
    }
    @Test void caneRetainsItsIndependentSlowdownAndFullLight() {
        assertTrue(SpecialPlantRules.caneClimate(.8F,15));
        assertTrue(SpecialPlantRules.caneClimate(.9999F,15));
        assertFalse(SpecialPlantRules.caneClimate(.7999F,15));
        assertFalse(SpecialPlantRules.caneClimate(0F,15));
        assertFalse(SpecialPlantRules.caneClimate(.8F,14));
    }
    @Test void deadStemsUseTheSourceOchreRatherThanTheYoungGreenColor() {
        assertEquals(0x00ff00,SpecialPlantRules.stemColor(0,false));
        assertEquals(0xe0c71c,SpecialPlantRules.stemColor(7,false));
        assertEquals(0xad8524,SpecialPlantRules.stemColor(0,true));
        assertEquals(0xad8524,SpecialPlantRules.stemColor(7,true));
    }
    @Test void cactusTableUsesLegacySeedConsumptionAndSupportsNegativeHeights() {
        byte[] heights=SpecialPlantRules.cactusHeights(196L);
        assertEquals(32768,heights.length);
        // An independent 48-bit LCG oracle consumed two ints, two floats and a double per row.
        assertArrayEquals(new byte[]{2,2,1,2,3,2,2,1},java.util.Arrays.copyOf(heights,8));
        for(byte height:heights) assertTrue(height>=1&&height<=3);
        assertEquals(31751,SpecialPlantRules.cactusIndex(13,-63,7));
        assertArrayEquals(heights,SpecialPlantRules.cactusHeights(196L));
    }
}
