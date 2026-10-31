package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import org.junit.jupiter.api.Test;

import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.*;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal expectations extracted from R196 runtime values and bytecode boundaries. @author NingMeng123 */
class EquipmentDurabilityRulesTest {
    private static ToolProfile tool(ToolFamily family) { return new ToolProfile(family, 8F); }

    @Test
    void registeredMaterialAndFamilyMaximaMatchSourceValues() {
        assertEquals(400, baseToolDurability(new ToolProfile(HATCHET, 1F)));
        assertEquals(400, baseToolDurability(new ToolProfile(CLUB, 0.5F)));
        assertEquals(4800, baseToolDurability(new ToolProfile(PICKAXE, 4F)));
        assertEquals(9600, baseToolDurability(new ToolProfile(PICKAXE, 8F)));
        assertEquals(76800, baseToolDurability(new ToolProfile(PICKAXE, 64F)));
        assertEquals(307200, baseToolDurability(new ToolProfile(PICKAXE, 256F)));
        ToolFamily[] families = {PICKAXE, AXE, BATTLE_AXE, HATCHET, SHOVEL, MATTOCK, HOE,
                SWORD, DAGGER, KNIFE, SCYTHE, SHEARS, CLUB, CUDGEL, WAR_HAMMER};
        int[] expected = {9600, 9600, 12800, 3200, 3200, 12800, 6400,
                6400, 3200, 3200, 6400, 6400, 6400, 3200, 16000};
        for (int i = 0; i < families.length; i++)
            assertEquals(expected[i], baseToolDurability(tool(families[i])), families[i].name());
    }

    @Test
    void armorDoesNotReceiveTheToolHundredfoldScale() {
        int[] components = {5, 8, 7, 4};
        int[] leather = {10, 16, 14, 8};
        int[] iron = {80, 128, 112, 64};
        int[] chain = {40, 64, 56, 32};
        for (int i = 0; i < components.length; i++) {
            assertEquals(leather[i], baseArmorDurability(new ArmorProfile(components[i], 1F, 2, false, Quality.FINE)));
            assertEquals(iron[i], baseArmorDurability(new ArmorProfile(components[i], 8F, 8, false, Quality.MASTERWORK)));
            assertEquals(chain[i], baseArmorDurability(new ArmorProfile(components[i], 8F, 8, true, Quality.MASTERWORK)));
        }
    }

    @Test
    void qualityUsesRoundAndNotTruncationAndKeepsAtLeastOnePoint() {
        int[] expected = {200, 300, 400, 600, 800, 1000, 1200, 1400};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], maxDurability(400, Quality.values()[i]));
        assertEquals(3, maxDurability(5, Quality.WRETCHED));
        assertEquals(1, maxDurability(1, Quality.WRETCHED));
        assertEquals(Quality.WRETCHED, Quality.fromOrdinal(Integer.MIN_VALUE));
        assertEquals(Quality.LEGENDARY, Quality.fromOrdinal(Integer.MAX_VALUE));
    }

    @Test
    void blockWearPreservesRateOrderAndMinimumPerAction() {
        assertEquals(160, blockWear(1.2F, tool(HATCHET), false, false));
        assertEquals(120, blockWear(1.2F, tool(AXE), false, false));
        assertEquals(150, blockWear(1.2F, tool(BATTLE_AXE), false, false));
        assertEquals(300, blockWear(1.2F, tool(HATCHET), true, false));
        assertEquals(25, blockWear(0.5F, tool(SHOVEL), false, false));
        assertEquals(20, blockWear(0.5F, tool(MATTOCK), false, false));
        assertEquals(100, blockWear(0.5F, tool(HOE), false, false));
        assertEquals(33, blockWear(0.5F, tool(WAR_HAMMER), false, false));
        assertEquals(12, blockWear(0.5F, tool(CLUB), false, false));
        assertEquals(5, blockWear(0.02F, tool(SHEARS), false, false));
        assertEquals(6, blockWear(0.02F, tool(HATCHET), false, false));
        assertEquals(10, blockWear(0.02F, tool(SWORD), false, false));
        assertEquals(1, blockWear(0.02F, tool(CLUB), false, false));
    }

    @Test
    void scytheAndKnifeSpecialTargetsUseTheirOwnRates() {
        assertEquals(2, blockWear(0.02F, tool(SCYTHE), false, true));
        assertEquals(10, blockWear(0.02F, tool(SCYTHE), false, false));
        assertEquals(2, blockWear(0.02F, tool(KNIFE), false, true));
        assertEquals(5, blockWear(0.02F, tool(KNIFE), false, false));
        assertEquals(200, blockWear(1F, tool(DAGGER), false, true));
    }

    @Test
    void attacksUseFamilyCostsWithoutTheLegacyScytheCombatPenalty() {
        ToolFamily[] families = {PICKAXE, AXE, BATTLE_AXE, HATCHET, SHOVEL, MATTOCK, HOE,
                SWORD, DAGGER, KNIFE, SCYTHE, SHEARS, CLUB, CUDGEL, WAR_HAMMER};
        int[] expected = {100, 100, 75, 133, 100, 100, 200, 50, 50, 50, 50, 200, 25, 25, 66};
        for (int i = 0; i < families.length; i++)
            assertEquals(expected[i], attackWear(tool(families[i]), false, false), families[i].name());
        assertEquals(50, attackWear(tool(SCYTHE), false, true));
        assertEquals(50, attackWear(tool(SCYTHE), true, false));
        assertEquals(50, attackWear(tool(SCYTHE), true, true));
    }

    @Test
    void invalidHardnessDoesNotCauseWearAndInvalidProfilesAreRejected() {
        for (float h : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY})
            assertEquals(0, blockWear(h, tool(AXE), false, false));
        assertThrows(IllegalArgumentException.class, () -> new ToolProfile(AXE, 0F));
        assertThrows(IllegalArgumentException.class, () -> new ToolProfile(AXE, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> maxDurability(0, Quality.AVERAGE));
    }

    @Test
    void unbreakingUsesOriginalFiveLevelFloatFractionsAndStrictComparison() {
        int[] bits = {0, 0x3e19999a, 0x3e99999a, 0x3ee66667, 0x3f19999a, 0x3f400000};
        for (int level = 0; level <= 5; level++)
            assertEquals(bits[level], Float.floatToIntBits(unbreakingNegationChance(level)));
        assertEquals(0F, unbreakingNegationChance(Integer.MIN_VALUE));
        assertEquals(0.75F, unbreakingNegationChance(Integer.MAX_VALUE));
        assertEquals(0, afterUnbreaking(1, 3, () -> 0.45F));
        assertEquals(1, afterUnbreaking(1, 3, () -> Float.intBitsToFloat(0x3ee66667)));
        assertEquals(1, afterUnbreaking(1, 5, () -> 0.75F));
        assertEquals(0, afterUnbreaking(1, 5, () -> 0.749F));
    }

    @Test
    void unbreakingConsumesExactlyOneDrawPerPointAndNoneWithoutEnchantment() {
        assertEquals(137, afterUnbreaking(137, 0, () -> { fail("No RNG at level zero"); return 0F; }));
        float[] samples = {0F, 0.149F, 0.15F, 0.999F};
        int[] index = {0};
        assertEquals(2, afterUnbreaking(4, 1, () -> samples[index[0]++]));
        assertEquals(4, index[0]);
        assertEquals(0, afterUnbreaking(-1, 5, () -> { fail("No RNG for no wear"); return 0F; }));
    }

    @Test
    void ordinaryArmorRetainsItsLastPointAfterWearReduction() {
        assertEquals(9, preventDestruction(100, 118, 128));
        assertEquals(0, preventDestruction(100, 127, 128));
        assertEquals(0, preventDestruction(100, 128, 128));
        assertEquals(0, preventDestruction(1, 0, 1));
        assertEquals(2, preventDestruction(2, 118, 128));
    }

    @Test
    void armorProtectionFallsOnlyInSecondHalfAndIsZeroAtOneRemaining() {
        assertEquals(1F, armorProtectionFactor(0, 128, true));
        assertEquals(1F, armorProtectionFactor(64, 128, true));
        assertEquals(0.5F, armorProtectionFactor(96, 128, true));
        assertEquals(0F, armorProtectionFactor(127, 128, true));
        assertEquals(0.5F, armorProtectionFactor(127, 128, false));
        ArmorProfile ironChest = new ArmorProfile(8, 8F, 8, false, Quality.MASTERWORK);
        assertEquals(8F / 3F, armorProtection(ironChest, 0, 128));
        assertEquals(4F / 3F, armorProtection(ironChest, 96, 128));
        assertEquals(4F, armorProtection(ironChest, 0, 128, 4));
        assertEquals(8F, roundedArmorProtection(7.999999523162842F));
        assertEquals(7.999F, roundedArmorProtection(7.999F));
        assertEquals(8F, roundedArmorProtection(8.00001F));
    }

    @Test
    void sameQualityRepairOnlyAddsRemainingPointsWithoutAnyBonus() {
        assertEquals(5600, combinedRepairDamage(10000, 10000, 14400));
        assertEquals(50, combinedRepairDamage(70, 80, 100));
        assertEquals(0, combinedRepairDamage(10, 20, 100));
        assertEquals(0, combinedRepairDamage(0, 0, Integer.MAX_VALUE));
    }

    @Test
    void oneArmorPieceCanLoseBudgetToAnEmptySlotBeforeBeingSelected() {
        int[] choices = {0, 2, 2};
        int[] bounds = {4, 8, 4};
        int[] cursor = {0};
        int[] wear = distributeArmorWear(8F, new boolean[]{false, false, true, false}, bound -> {
            int i = cursor[0]++; assertEquals(bounds[i], bound); return choices[i];
        });
        assertArrayEquals(new int[]{0, 0, 5, 0}, wear);
        assertEquals(3, cursor[0]);
    }

    @Test
    void severalPiecesSplitBudgetRatherThanEachTakingModernQuarterDamage() {
        int[] choices = {0, 1, 3, 5};
        int[] bounds = {4, 8, 4, 6};
        int[] cursor = {0};
        assertArrayEquals(new int[]{2, 0, 0, 6}, distributeArmorWear(8.9F,
                new boolean[]{true, true, true, true}, bound -> {
                    int i = cursor[0]++; assertEquals(bounds[i], bound); return choices[i];
                }));
        assertEquals(4, cursor[0]);
    }

    @Test
    void zeroProtectionStillHasOriginalMinimumAttemptButNoArmorSkipsRng() {
        assertArrayEquals(new int[]{0, 0, 1, 0}, distributeArmorWear(0F,
                new boolean[]{false, false, true, false}, bound -> { assertEquals(4, bound); return 2; }));
        assertArrayEquals(new int[4], distributeArmorWear(8F, new boolean[4], bound -> {
            fail("No armor must not consume RNG"); return 0;
        }));
    }
}
