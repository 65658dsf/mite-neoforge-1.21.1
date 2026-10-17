package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source slot order and directional fallback classes, including exact-item priority. @author NingMeng123 */
class AutoRestockRulesTest {
    @Test void exactInventoryItemOutranksAnEarlierSimilarHotbarTool() {
        int[] scores = new int[36]; scores[3] = 99; scores[24] = 100;
        assertEquals(24, AutoRestockRules.nextSlot(2, 36, i -> scores[i]));
    }
    @Test void equalScoresWrapFromSelectedHotbarBeforeLookingInTheBackpack() {
        int[] scores = new int[36]; scores[0] = scores[8] = scores[9] = 100;
        assertEquals(8, AutoRestockRules.nextSlot(7, 36, i -> scores[i]));
        scores[8] = 0; assertEquals(0, AutoRestockRules.nextSlot(7, 36, i -> scores[i]));
        scores[0] = 0; assertEquals(9, AutoRestockRules.nextSlot(7, 36, i -> scores[i]));
    }
    @Test void noFallbackOrInvalidSelectedSlotDoesNotChooseAnItem() {
        assertEquals(-1, AutoRestockRules.nextSlot(0, 36, i -> 0));
        assertEquals(-1, AutoRestockRules.nextSlot(9, 36, i -> 100));
    }
    @Test void sourceToolsUseOrderedDirectionalSubstitutes() {
        assertEquals(99, AutoRestockRules.familySimilarity("PICKAXE", "PICKAXE"));
        assertEquals(98, AutoRestockRules.familySimilarity("PICKAXE", "WAR_HAMMER"));
        assertEquals(98, AutoRestockRules.familySimilarity("SHOVEL", "MATTOCK"));
        assertEquals(97, AutoRestockRules.familySimilarity("SHOVEL", "HOE"));
        assertEquals(0, AutoRestockRules.familySimilarity("HATCHET", "SWORD"));
        assertEquals(97, AutoRestockRules.familySimilarity("SWORD", "BATTLE_AXE"));
        assertEquals(97, AutoRestockRules.familySimilarity("DAGGER", "BATTLE_AXE"));
        assertEquals(96, AutoRestockRules.familySimilarity("KNIFE", "WAR_HAMMER"));
        assertEquals(0, AutoRestockRules.familySimilarity(null, "PICKAXE"));
    }
}
