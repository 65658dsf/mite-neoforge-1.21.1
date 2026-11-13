package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independently stated slot values and modern damage calibration. @author NingMeng123 */
class ArmorBalanceRulesTest {
    @ParameterizedTest(name = "{0} chain={1} has helmet/chest/legs/boots {2}/{3}/{4}/{5} and per-piece toughness {6}")
    @CsvSource({
            "leather,false,3,4,3,2,0.125",
            "rusted_iron,false,3,5,5,3,0.375",
            "gold,false,3,5,5,3,0.375",
            "copper,false,3,6,5,3,0.5",
            "silver,false,3,6,5,3,0.5",
            "iron,false,4,6,5,3,0.5",
            "ancient_metal,false,4,6,5,3,0.5",
            "diamond,false,4,6,6,3,0.625",
            "mithril,false,4,6,6,3,0.625",
            "netherite,false,4,7,6,3,0.75",
            "adamantium,false,4,7,6,3,0.75",
            "vibranium,false,5,7,6,4,4",
            "rusted_iron,true,3,5,4,2,0.25",
            "gold,true,3,5,4,2,0.25",
            "copper,true,3,5,4,3,0.25",
            "silver,true,3,5,4,3,0.25",
            "iron,true,3,5,5,3,0.375",
            "ancient_metal,true,3,5,5,3,0.375",
            "mithril,true,3,6,5,3,0.5",
            "adamantium,true,4,6,5,3,0.5",
            "turtle,false,2,0,0,0,0.5",
            "turtle_scute,false,2,0,0,0,0.5"
    })
    void modernMaterialTableMatchesIndependentSlotValues(String material, boolean chainmail,
            int helmet, int chest, int legs, int boots, float toughness) {
        var stats = ArmorBalanceRules.stats(material, chainmail);
        assertEquals(helmet, stats.protection(5));
        assertEquals(chest, stats.protection(8));
        assertEquals(legs, stats.protection(7));
        assertEquals(boots, stats.protection(4));
        assertEquals(toughness, stats.toughness(), 0.00001F);
        for (int components : new int[]{5, 8, 7, 4})
            assertEquals(toughness, stats.toughness(components), 0.00001F);
    }

    @ParameterizedTest
    @CsvSource({
            "rusted_iron,gold", "copper,silver", "iron,ancient_metal"
    })
    void equalLegacyProtectionKeepsTheSamePlateAndChainmailTier(String first, String second) {
        for (boolean chainmail : new boolean[]{false, true})
            assertEquals(ArmorBalanceRules.stats(first, chainmail), ArmorBalanceRules.stats(second, chainmail));
    }

    @Test void retainedModernMaterialsFollowTheirSelectedLegacyTier() {
        assertEquals(ArmorBalanceRules.stats("mithril", false), ArmorBalanceRules.stats("diamond", false));
        assertEquals(ArmorBalanceRules.stats("adamantium", false), ArmorBalanceRules.stats("netherite", false));
    }

    @ParameterizedTest
    @CsvSource({
            "leather,false,2,27.12",
            "rusted_iron,false,6,25.957894737",
            "gold,false,6,25.957894737",
            "copper,false,7,24",
            "silver,false,7,24",
            "iron,false,8,22.8",
            "ancient_metal,false,8,22.8",
            "diamond,false,9,20.914285714",
            "mithril,false,9,20.914285714",
            "netherite,false,10,19.090909091",
            "adamantium,false,10,19.090909091",
            "vibranium,false,15,9.6",
            "rusted_iron,true,4,26.64",
            "gold,true,4,26.64",
            "copper,true,5,26.4",
            "silver,true,5,26.4",
            "iron,true,6,25.957894737",
            "ancient_metal,true,6,25.957894737",
            "mithril,true,7,24",
            "adamantium,true,8,22.8"
    })
    void modernDamageTracksLegacyProtectionAtTwentyAndKeepsThirtyDamageAnchors(String material,
            boolean chainmail, int legacyProtection, double expectedAtThirty) {
        var stats = ArmorBalanceRules.stats(material, chainmail);
        assertEquals(20D - legacyProtection, modernDamage(stats, 20D), .21D,
                material + " tracks full-durability ordinary-quality legacy protection at the selected hit size");
        assertEquals(expectedAtThirty, modernDamage(stats, 30D), .000001D);
    }

    @Test void materialProgressionReducesDamageAcrossSmallAndLargeHits() {
        for (double damage : new double[]{3D, 5D, 8D, 12D, 20D, 30D}) {
            double previous = damage;
            for (String material : new String[]{"leather", "rusted_iron", "copper", "iron", "mithril", "adamantium", "vibranium"}) {
                double received = modernDamage(ArmorBalanceRules.stats(material, false), damage);
                assertTrue(received < previous, material + " improves plate damage at " + damage);
                previous = received;
            }
            previous = damage;
            for (String material : new String[]{"rusted_iron", "copper", "iron", "mithril", "adamantium"}) {
                double received = modernDamage(ArmorBalanceRules.stats(material, true), damage);
                assertTrue(received < previous, material + " improves chainmail damage at " + damage);
                previous = received;
            }
        }
    }

    @Test void chainmailKeepsLowerSlotDefenseAndLowerMitigationThanMatchingPlate() {
        for (String material : new String[]{"rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium"}) {
            var plate = ArmorBalanceRules.stats(material, false);
            var chain = ArmorBalanceRules.stats(material, true);
            for (int components : new int[]{5, 8, 7, 4}) {
                assertTrue(plate.protection(components) >= chain.protection(components));
                assertTrue(plate.toughness(components) >= chain.toughness(components));
            }
            for (double damage : new double[]{3D, 5D, 8D, 12D, 20D, 30D})
                assertTrue(modernDamage(chain, damage) > modernDamage(plate, damage),
                        material + " chainmail receives more damage at " + damage);
        }
    }

    @Test void vibraniumReachesTheEightyPercentCapAtAndBelowTwelveDamage() {
        var vibranium = ArmorBalanceRules.stats("vibranium", false);
        for (double damage : new double[]{3D, 5D, 8D, 11.999D, 12D})
            assertEquals(damage * .2D, modernDamage(vibranium, damage), .000001D);
        for (double damage : new double[]{12.001D, 20D, 30D})
            assertTrue(modernDamage(vibranium, damage) > damage * .2D);
        for (String material : new String[]{"mithril", "adamantium"})
            for (double damage : new double[]{3D, 5D, 8D, 12D, 20D, 30D})
                assertTrue(modernDamage(ArmorBalanceRules.stats(material, false), damage) > damage * .2D,
                        material + " retains distinct mitigation below the cap");
    }

    @Test void armorBalancingDoesNotReplaceR196DurabilityOrQualityArithmetic() {
        var mithril = new EquipmentDurabilityRules.ArmorProfile(8, 64F, 9, false,
                EquipmentDurabilityRules.Quality.LEGENDARY);
        assertEquals(1024, EquipmentDurabilityRules.baseArmorDurability(mithril));
        assertEquals(3584, EquipmentDurabilityRules.maxDurability(1024, EquipmentDurabilityRules.Quality.LEGENDARY));
        assertEquals(6, ArmorBalanceRules.stats("mithril", false).protection(8));
        assertEquals(0.5F, EquipmentDurabilityRules.armorProtectionFactor(768, 1024, true));
        assertEquals(0F, EquipmentDurabilityRules.armorProtectionFactor(1023, 1024, true));
    }

    @Test void vibraniumKeepsDocumentedKnockbackResistance() {
        var vibranium = ArmorBalanceRules.stats("vibranium", false);
        int[] components = {5, 8, 7, 4};
        float[] knockbackResistance = {.12F, .2F, .18F, .1F};
        for (int i = 0; i < components.length; i++)
            assertEquals(knockbackResistance[i], vibranium.knockbackResistance(components[i]), .00001F);
        assertThrows(IllegalArgumentException.class, () -> vibranium.toughness(0));
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("vibranium", true));
    }

    @Test void allAvailableHumanoidMaterialsHavePositiveToughnessAndSelectedFullSetCaps() {
        for (String material : new String[]{"leather", "rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"}) {
            var stats = ArmorBalanceRules.stats(material, false);
            int total = 0;
            for (int components : new int[]{5, 8, 7, 4}) {
                assertTrue(stats.toughness(components) > 0F, material + " has positive material toughness");
                total += stats.protection(components);
            }
            assertTrue(total <= 22, material + " stays within the selected armor budget");
            assertTrue(stats.toughness() <= 4F, material + " stays within the selected per-piece toughness budget");
        }
    }

    @Test void bardingBufferCannotWeakenTheRetainedFlatDefenseOrApplyArmorTwice() {
        assertEquals(.47619048F, ArmorBalanceRules.bardingToughnessBuffer(10F, 7F, 2.5F), .00001F);
        assertEquals(.49371429F, ArmorBalanceRules.bardingToughnessBuffer(12F, 7F, 2.5F), .00001F);
        assertEquals(0F, ArmorBalanceRules.bardingToughnessBuffer(12F, 7F, 0F));
        for (float damage : new float[]{.25F, 1F, 5F, 10F, 20F, 40F, 100F})
            assertTrue(ArmorBalanceRules.bardingToughnessBuffer(damage, 7F, 2.5F) >= 0F);
    }

    @Test void unknownMaterialsAndNonHumanoidSlotsCannotSilentlyCreateArmorStats() {
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("unknown", false));
        assertThrows(IllegalArgumentException.class, () -> ArmorBalanceRules.stats("diamond", true));
        var mithril = ArmorBalanceRules.stats("mithril", false);
        assertThrows(IllegalArgumentException.class, () -> mithril.protection(0));
        assertThrows(IllegalArgumentException.class, () -> mithril.protection(6));
    }

    private static double modernDamage(ArmorBalanceRules.Stats stats, double incoming) {
        int armor = stats.helmet() + stats.chestplate() + stats.leggings() + stats.boots();
        double toughness = stats.helmetToughness() + stats.chestplateToughness()
                + stats.leggingsToughness() + stats.bootsToughness();
        double effective = Math.min(20D, Math.max(armor * .2D, armor - incoming / (2D + toughness / 4D)));
        return incoming * (1D - effective / 25D);
    }
}
