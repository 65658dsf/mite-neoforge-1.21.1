package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal combat examples and the damage/speed/mobility tradeoff. @author NingMeng123 */
class NativeWeaponBalanceRulesTest {
    @Test void ironSwordAttacksTwicePerSecondWithoutChangingOtherMaterialsOrFamilies() {
        assertEquals(2D, NativeWeaponBalanceRules.attacksPerSecond(SWORD, "iron"));
        for (String material : new String[]{"copper", "silver", "gold", "ancient_metal", "mithril", "adamantium", "vibranium"})
            assertEquals(1.5D, NativeWeaponBalanceRules.attacksPerSecond(SWORD, material));
        assertEquals(.85D, NativeWeaponBalanceRules.attacksPerSecond(BATTLE_AXE, "iron"));
    }
    @Test void materialExamplesIncludeTheNativeBasePointAndCopperOnlyOnce() {
        assertEquals(7.2F, NativeWeaponBalanceRules.damageBonus(SWORD,"iron",8F)+1F,.00001F);
        assertEquals(11.2F, NativeWeaponBalanceRules.damageBonus(SWORD,"vibranium",13F)+1F,.00001F);
        assertEquals(6F, NativeWeaponBalanceRules.damageBonus(SWORD,"copper",7F)+1F,.00001F);
        assertEquals(6.4F, NativeWeaponBalanceRules.damageBonus(SWORD,"silver",7F)+1F,.00001F);
        assertEquals(8.1F, NativeWeaponBalanceRules.damageBonus(BATTLE_AXE,"iron",8F)+1F,.00001F);
        assertEquals(5.25F, NativeWeaponBalanceRules.damageBonus(CLUB,"copper",6F)+1F,.00001F);
    }
    @Test void allSourceCopperAndSilverFamiliesKeepTheirDistinctSurvivalBudgets() {
        var families = new io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily[]{
                SWORD,DAGGER,BATTLE_AXE,WAR_HAMMER,AXE,HATCHET,PICKAXE,SHOVEL,HOE,MATTOCK,SCYTHE,SHEARS,CLUB};
        float[] sourceBonuses = {7F,5F,7F,5F,6F,5F,5F,4F,4F,4F,4F,3F,6F};
        float[] copper = {6F,4.5F,6.75F,4.5F,4.265625F,3.65625F,3.65625F,3.046875F,3.046875F,3.046875F,3.28125F,2.25F,5.25F};
        float[] silver = {6.4F,4.8F,7.2F,4.8F,4.55F,3.9F,3.9F,3.25F,3.25F,3.25F,3.5F,2.4F,5.6F};
        for (int index=0;index<families.length;index++) {
            assertEquals(copper[index],NativeWeaponBalanceRules.damageBonus(families[index],"copper",sourceBonuses[index])+1F,.00001F);
            assertEquals(silver[index],NativeWeaponBalanceRules.damageBonus(families[index],"silver",sourceBonuses[index])+1F,.00001F);
        }
    }
    @Test void heavyHitsAndLongerReachPayWithSpeedAndMobility() {
        assertTrue(NativeWeaponBalanceRules.damageBonus(BATTLE_AXE,"iron",8F)>NativeWeaponBalanceRules.damageBonus(SWORD,"iron",8F));
        assertEquals(.85D,NativeWeaponBalanceRules.attacksPerSecond(BATTLE_AXE,"iron"));
        assertEquals(.75D,NativeWeaponBalanceRules.attacksPerSecond(WAR_HAMMER,"iron"));
        assertEquals(.15D,NativeWeaponBalanceRules.movementPenalty(BATTLE_AXE,"iron"));
        assertEquals(.18D,NativeWeaponBalanceRules.movementPenalty(WAR_HAMMER,"iron"));
        assertTrue(NativeWeaponBalanceRules.damageBonus(SCYTHE,"iron",5F)<NativeWeaponBalanceRules.damageBonus(SWORD,"iron",8F));
    }
    @Test void starterCombatAndHarvestDefinitionsRetainTheirExistingValues() {
        assertEquals(3F,NativeWeaponBalanceRules.damageBonus(HATCHET,"flint",3F));
        assertEquals(1D,NativeWeaponBalanceRules.attacksPerSecond(HATCHET,"flint"));
        assertEquals(2F,NativeWeaponBalanceRules.damageBonus(CLUB,"wood",2F));
        assertEquals(0D,NativeWeaponBalanceRules.movementPenalty(CLUB,"wood"));
    }
}
