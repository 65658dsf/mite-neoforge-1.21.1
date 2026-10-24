package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal modern balance expectations, independently stated from source durability. @author NingMeng123 */
class ArmorBalanceRulesTest {
    @ParameterizedTest(name = "{0} chain={1} has helmet/chest/legs/boots {2}/{3}/{4}/{5} and toughness {6}")
    @CsvSource({
            "leather,false,1,3,2,1,0.25",
            "rusted_iron,false,1,4,3,1,0.375",
            "gold,false,2,4,3,1,0.5",
            "copper,false,2,5,3,2,0.75",
            "silver,false,2,5,3,2,0.75",
            "iron,false,2,5,5,2,1",
            "ancient_metal,false,2,6,5,3,1.25",
            "diamond,false,3,7,5,3,2",
            "mithril,false,3,7,5,3,2",
            "netherite,false,3,8,6,3,2.5",
            "adamantium,false,3,8,6,3,2.5",
            "rusted_iron,true,1,3,2,1,0.28125",
            "gold,true,1,3,2,1,0.375",
            "copper,true,1,4,3,1,0.5625",
            "silver,true,1,4,3,1,0.5625",
            "iron,true,2,4,4,1,0.75",
            "ancient_metal,true,2,5,4,2,0.9375",
            "mithril,true,2,6,5,2,1.5",
            "adamantium,true,3,6,5,3,1.875"
    })
    void modernMaterialTableMatchesIndependentSlotValues(String material, boolean chainmail,
            int helmet, int chest, int legs, int boots, float toughness) {
        var stats = ArmorBalanceRules.stats(material, chainmail);
        assertEquals(helmet, stats.protection(5));
        assertEquals(chest, stats.protection(8));
        assertEquals(legs, stats.protection(7));
        assertEquals(boots, stats.protection(4));
        assertEquals(toughness, stats.toughness(), 0.00001F);
    }

    @Test void copperAndSilverRemainOneProgressionTierForPlateAndChainmail() {
        for (boolean chainmail : new boolean[]{false, true}) {
            var copper = ArmorBalanceRules.stats("copper", chainmail);
            var silver = ArmorBalanceRules.stats("silver", chainmail);
            for (int components : new int[]{5, 8, 7, 4})
                assertEquals(copper.protection(components), silver.protection(components));
            assertEquals(copper.toughness(), silver.toughness());
        }
    }

    @Test void addedHighTierSetsKeepTheirMaterialProgressionAndNeverWeakenPlateToChain() {
        int previousTotal = 0;
        float previousToughness = 0F;
        for (String material : new String[]{"copper", "iron", "ancient_metal", "mithril", "adamantium"}) {
            var plate = ArmorBalanceRules.stats(material, false);
            var chain = ArmorBalanceRules.stats(material, true);
            int total = 0;
            for (int components : new int[]{5, 8, 7, 4}) {
                total += plate.protection(components);
                assertTrue(plate.protection(components) >= chain.protection(components));
            }
            assertTrue(total > previousTotal, material + " increases full-set protection");
            assertTrue(plate.toughness() >= previousToughness, material + " retains toughness progression");
            assertTrue(plate.toughness() >= chain.toughness());
            previousTotal = total;
            previousToughness = plate.toughness();
        }
    }

    @Test void armorBalancingDoesNotReplaceR196DurabilityOrQualityArithmetic() {
        var mithril = new EquipmentDurabilityRules.ArmorProfile(8, 64F, 9, false,
                EquipmentDurabilityRules.Quality.LEGENDARY);
        assertEquals(1024, EquipmentDurabilityRules.baseArmorDurability(mithril));
        assertEquals(3584, EquipmentDurabilityRules.maxDurability(1024, EquipmentDurabilityRules.Quality.LEGENDARY));
        assertEquals(7, ArmorBalanceRules.stats("mithril", false).protection(8));
        assertEquals(0.5F, EquipmentDurabilityRules.armorProtectionFactor(768, 1024, true));
        assertEquals(0F, EquipmentDurabilityRules.armorProtectionFactor(1023, 1024, true));
    }

    @Test void vibraniumKeepsDocumentedSlotDefenseAndToughness() {
        var vibranium = ArmorBalanceRules.stats("vibranium", false);
        int[] components = {5, 8, 7, 4};
        int[] defense = {4, 8, 6, 4};
        float[] toughness = {3F, 3F, 3F, 3F};
        float[] knockbackResistance = {.12F, .2F, .18F, .1F};
        for (int i = 0; i < components.length; i++) {
            assertEquals(defense[i], vibranium.protection(components[i]));
            assertEquals(toughness[i], vibranium.toughness(components[i]), 0.00001F);
            assertEquals(knockbackResistance[i], vibranium.knockbackResistance(components[i]), 0.00001F);
        }
        assertEquals(3F, vibranium.toughness(), 0.00001F);
        assertThrows(IllegalArgumentException.class, () -> vibranium.toughness(0));
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("vibranium", true));
    }

    @Test void allAvailableHumanoidMaterialsHavePositiveToughnessAndConservativeFullSetCaps() {
        for (String material : new String[]{"leather","rusted_iron","gold","copper","silver","iron","ancient_metal","mithril","adamantium","vibranium"}) {
            var stats=ArmorBalanceRules.stats(material,false);
            int total=0;
            for(int components:new int[]{5,8,7,4}) {
                assertTrue(stats.toughness(components)>0F,material+" has real positive material toughness");
                total+=stats.protection(components);
            }
            assertTrue(total<=22,material+" does not exceed the selected top armor budget");
            assertTrue(stats.toughness()<=3F,material+" does not exceed the selected material toughness budget");
        }
    }

    @Test void bardingBufferCannotWeakenTheRetainedFlatDefenseOrApplyArmorTwice() {
        assertEquals(.47619048F,ArmorBalanceRules.bardingToughnessBuffer(10F,7F,2.5F),.00001F);
        assertEquals(.49371429F,ArmorBalanceRules.bardingToughnessBuffer(12F,7F,2.5F),.00001F);
        assertEquals(0F,ArmorBalanceRules.bardingToughnessBuffer(12F,7F,0F));
        for(float damage:new float[]{.25F,1F,5F,10F,20F,40F,100F})
            assertTrue(ArmorBalanceRules.bardingToughnessBuffer(damage,7F,2.5F)>=0F);
    }

    @Test void unknownMaterialsAndNonHumanoidSlotsCannotSilentlyCreateArmorStats() {
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("unknown", false));
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("diamond", true));
        var mithril = ArmorBalanceRules.stats("mithril", false);
        assertThrows(IllegalArgumentException.class, () -> mithril.protection(0));
        assertThrows(IllegalArgumentException.class, () -> mithril.protection(6));
    }
}
