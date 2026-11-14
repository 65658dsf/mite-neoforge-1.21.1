package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source damage literals and the selected damage, speed and reach tradeoffs. @author NingMeng123 */
class NativeWeaponBalanceRulesTest {
    @ParameterizedTest
    @CsvSource({
            "SWORD,4,1.5,2.25,2.25",
            "DAGGER,2,1.8,2,2",
            "KNIFE,1,2,1.75,1.75",
            "BATTLE_AXE,4,0.85,2,2.25",
            "WAR_HAMMER,2,0.75,2,2",
            "CLUB,2,1.2,2,2",
            "CUDGEL,1,1.6,1.75,1.75",
            "SCYTHE,1,0.8,2.5,3.25",
            "AXE,3,0.95,2.25,2.25",
            "HATCHET,2,1.25,2,2",
            "PICKAXE,2,1,2.25,2.25",
            "SHOVEL,1,1,2.25,2.25",
            "HOE,1,1.1,2.25,2.25",
            "MATTOCK,1,1,2.25,2.25",
            "SHEARS,0,1.3,2,2"
    })
    void everyFamilyRetainsSourceDamageAndItsSelectedSpeedAndBothReachModes(String name, float typeDamage,
            double speed, double nativeReach, double betterCombatReach) {
        var family = EquipmentDurabilityRules.ToolFamily.valueOf(name);
        String[] materials = {"wood", "flint", "obsidian", "rusted_iron", "gold", "copper", "silver",
                "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        float[] materialDamage = {0F, 1F, 2F, 2F, 2F, 3F, 3F, 4F, 4F, 5F, 6F, 9F};
        for (int index = 0; index < materials.length; index++) {
            float sourceBonus = typeDamage + materialDamage[index];
            assertEquals(sourceBonus, NativeWeaponBalanceRules.damageBonus(family, materials[index], sourceBonus));
            assertEquals(speed, NativeWeaponBalanceRules.attacksPerSecond(family, materials[index]));
            for (boolean betterCombat : new boolean[]{false, true}) {
                float totalDamage = NativeWeaponBalanceRules.damageBonus(family, materials[index], sourceBonus) + 1F;
                float swordDamage = 5F + materialDamage[index];
                int advantages = 0;
                if (totalDamage > swordDamage) advantages++;
                if (NativeWeaponBalanceRules.attacksPerSecond(family, materials[index]) > 1.5D) advantages++;
                if (NativeWeaponBalanceRules.meleeReach(family, betterCombat) > 2.25D) advantages++;
                assertTrue(advantages <= 1, name + " leads the same-material sword in at most one combat attribute, Better Combat=" + betterCombat);
            }
        }
        assertEquals(nativeReach, NativeWeaponBalanceRules.meleeReach(family, false));
        assertEquals(betterCombatReach, NativeWeaponBalanceRules.meleeReach(family, true));
        assertEquals(betterCombatReach, NativeWeaponBalanceRules.meleeReach(family));
    }

    @ParameterizedTest
    @CsvSource({
            "rusted_iron,6,7", "gold,6,7", "copper,7,8", "silver,7,8",
            "iron,8,9", "ancient_metal,8,9", "mithril,9,10", "adamantium,10,11", "vibranium,13,14"
    })
    void everySourceSwordIncludesOneNativePlayerBasePoint(String material, float sourceBonus, float total) {
        assertEquals(total, NativeWeaponBalanceRules.damageBonus(SWORD, material, sourceBonus) + 1F);
        assertEquals(1.5D, NativeWeaponBalanceRules.attacksPerSecond(SWORD, material));
    }

    @Test void theCompatibilityEntryPointPreservesFractionalAndLargeNativeBonuses() {
        for (var family : EquipmentDurabilityRules.ToolFamily.values())
            for (String material : new String[]{"stone", "diamond", "netherite", "unknown"})
                for (float bonus : new float[]{-.75F, 0F, .25F, 13F, Float.MAX_VALUE})
                    assertEquals(bonus, NativeWeaponBalanceRules.damageBonus(family, material, bonus));
    }

    @Test void shortFastAndLongSlowWeaponsKeepDistinctCombatRoles() {
        for (boolean betterCombat : new boolean[]{false, true}) {
            assertTrue(NativeWeaponBalanceRules.meleeReach(DAGGER, betterCombat) < NativeWeaponBalanceRules.meleeReach(SWORD, betterCombat));
            assertTrue(NativeWeaponBalanceRules.meleeReach(SCYTHE, betterCombat) > NativeWeaponBalanceRules.meleeReach(SWORD, betterCombat));
            assertTrue(NativeWeaponBalanceRules.meleeReach(BATTLE_AXE, betterCombat) <= NativeWeaponBalanceRules.meleeReach(SWORD, betterCombat));
            assertTrue(NativeWeaponBalanceRules.meleeReach(WAR_HAMMER, betterCombat) < NativeWeaponBalanceRules.meleeReach(SWORD, betterCombat));
        }
        assertTrue(NativeWeaponBalanceRules.attacksPerSecond(DAGGER, "iron")
                > NativeWeaponBalanceRules.attacksPerSecond(SWORD, "iron"));
        assertTrue(NativeWeaponBalanceRules.attacksPerSecond(SCYTHE, "iron")
                < NativeWeaponBalanceRules.attacksPerSecond(SWORD, "iron"));
        assertTrue(NativeWeaponBalanceRules.damageBonus(SCYTHE, "iron", 5F)
                < NativeWeaponBalanceRules.damageBonus(SWORD, "iron", 8F));
        assertEquals(NativeWeaponBalanceRules.damageBonus(SWORD, "iron", 8F),
                NativeWeaponBalanceRules.damageBonus(BATTLE_AXE, "iron", 8F));
        assertTrue(NativeWeaponBalanceRules.attacksPerSecond(BATTLE_AXE, "iron")
                < NativeWeaponBalanceRules.attacksPerSecond(SWORD, "iron"));
        assertTrue(NativeWeaponBalanceRules.attacksPerSecond(WAR_HAMMER, "iron")
                < NativeWeaponBalanceRules.attacksPerSecond(SWORD, "iron"));
        assertEquals(.15D, NativeWeaponBalanceRules.movementPenalty(BATTLE_AXE, "iron"));
        assertEquals(.18D, NativeWeaponBalanceRules.movementPenalty(WAR_HAMMER, "iron"));
    }

    @Test void starterEquipmentUsesTheFamilyCooldownWithoutAddingWeight() {
        assertEquals(3F, NativeWeaponBalanceRules.damageBonus(HATCHET, "flint", 3F));
        assertEquals(1.25D, NativeWeaponBalanceRules.attacksPerSecond(HATCHET, "flint"));
        assertEquals(2D, NativeWeaponBalanceRules.attacksPerSecond(KNIFE, "flint"));
        assertEquals(1.2D, NativeWeaponBalanceRules.attacksPerSecond(CLUB, "wood"));
        assertEquals(1.6D, NativeWeaponBalanceRules.attacksPerSecond(CUDGEL, "wood"));
        for (var family : EquipmentDurabilityRules.ToolFamily.values())
            for (String material : new String[]{"wood", "stone", "flint", "obsidian"})
                assertEquals(0D, NativeWeaponBalanceRules.movementPenalty(family, material));
    }
}
