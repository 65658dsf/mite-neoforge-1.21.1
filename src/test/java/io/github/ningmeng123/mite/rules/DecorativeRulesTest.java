package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Test oracles transcribed from BlockFlowerMulti and ant, not generated production data. @author NingMeng123 */
class DecorativeRulesTest {
    @Test void sourceOrchidStrictlyWarmerThanPlains() {
        assertFalse(DecorativeRules.flowerBiome(1, .8F, false, false));
        assertTrue(DecorativeRules.flowerBiome(1, .80001F, false, false));
        assertFalse(DecorativeRules.flowerBiome(1, 1.2F, true, false));
    }
    @Test void sourceAlliumIsSwampOnly() {
        assertTrue(DecorativeRules.flowerBiome(2, .8F, true, false));
        assertFalse(DecorativeRules.flowerBiome(2, 1.2F, false, false));
        for (int subtype : new int[] {0, 1, 5, 7, 8}) assertFalse(DecorativeRules.flowerBiome(subtype, 1.2F, true, false));
    }
    @Test void tulipAndDahliaIncludeExactlyForestTemperature() {
        for (int subtype : new int[] {5, 7}) {
            assertFalse(DecorativeRules.flowerBiome(subtype, .69999F, false, false));
            assertTrue(DecorativeRules.flowerBiome(subtype, .7F, false, false));
            assertTrue(DecorativeRules.flowerBiome(subtype, 1.2F, false, true));
        }
    }
    @Test void sourceDaisyCannotGrowInJungle() {
        assertFalse(DecorativeRules.flowerBiome(8, 1.2F, false, true));
        assertTrue(DecorativeRules.flowerBiome(8, .1F, false, false));
    }
    @Test void sourcePatchSizeAndSelectionHeights() {
        assertEquals(8, DecorativeRules.flowerPatchAttempts(2, true, true));
        assertEquals(64, DecorativeRules.flowerPatchAttempts(7, true, false));
        assertEquals(64, DecorativeRules.flowerPatchAttempts(5, false, true));
        assertEquals(16, DecorativeRules.flowerPatchAttempts(7, false, false));
        assertEquals(.875F, DecorativeRules.flowerHeight(2));
        assertEquals(.75F, DecorativeRules.flowerHeight(5));
        for (int subtype : new int[] {1, 7, 8}) assertEquals(.8125F, DecorativeRules.flowerHeight(subtype));
    }
    @Test void sourceWitherRefreshAddsFortyWithoutClampingResidualPhase() {
        assertEquals(200, DecorativeRules.witherContactDuration(160, 0));
        assertEquals(161, DecorativeRules.witherContactDuration(161, 0));
        assertEquals(40, DecorativeRules.witherContactDuration(0, 0));
        assertEquals(500, DecorativeRules.witherContactDuration(500, 0));
        assertEquals(100, DecorativeRules.witherContactDuration(100, 1));
        assertEquals(-1, DecorativeRules.witherContactDuration(-1, 0));
    }
    @Test void sourceBushCushionUsesFractionalFallDamageAndOnePointMinimum() {
        assertEquals(4F, DecorativeRules.witherwoodFallDamage(6F, -1, 1F));
        assertEquals(0F, DecorativeRules.witherwoodFallDamage(3.7F, -1, 1F));
        assertEquals(0F, DecorativeRules.witherwoodFallDamage(4F, -1, 1F));
        assertEquals(1F, DecorativeRules.witherwoodFallDamage(4.125F, -1, 1F));
        assertEquals(4F, DecorativeRules.witherwoodFallDamage(7F, 0, 1F));
        assertEquals(2F, DecorativeRules.witherwoodFallDamage(6F, -1, .5F));
        assertEquals(0F, DecorativeRules.witherwoodFallDamage(6F, -1, 0F));
    }
}
