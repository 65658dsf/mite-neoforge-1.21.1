package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independently enumerated R196 climate and coordinate-table boundaries. @author NingMeng123 */
final class SpecialPlantRulesTest {
    @Test void pumpkinAndMelonHaveDistinctOptimalClimates() {
        assertEquals(.25F,SpecialPlantRules.stemClimate(false,.6F,false));
        assertEquals(.25F,SpecialPlantRules.stemClimate(false,1F,false));
        assertEquals(.15F,SpecialPlantRules.stemClimate(false,1.4F,false),.00001F);
        assertEquals(.15F,SpecialPlantRules.stemClimate(true,.6F,false),.00001F);
        assertEquals(.25F,SpecialPlantRules.stemClimate(true,1.4F,false));
        assertEquals(.3F,SpecialPlantRules.stemClimate(true,1F,true));
        assertEquals(0F,SpecialPlantRules.stemClimate(true,0F,false));
    }
    @Test void caneUsesBothIndependentSlowdownsAndFullLight() {
        assertTrue(SpecialPlantRules.caneClimate(.8F,.6F,.8F,15));
        assertFalse(SpecialPlantRules.caneClimate(.8F,.6001F,.8F,15));
        assertFalse(SpecialPlantRules.caneClimate(.8F,.6F,.7999F,15));
        assertFalse(SpecialPlantRules.caneClimate(.8F,.6F,.8F,14));
        assertFalse(SpecialPlantRules.caneClimate(.19F,0F,.9F,15));
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
