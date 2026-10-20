package io.github.ningmeng123.mite.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Balance boundaries independent of the Minecraft runtime. @author NingMeng123 */
class GearProgressionRulesTest {
    @Test void fallbackToolsFollowTheSourceLinearCurveWithoutFiveLevelSteps() {
        assertEquals(150_000, GearProgressionRules.nextLevelExperienceMillis(0));
        assertEquals(450_000, GearProgressionRules.nextLevelExperienceMillis(4));
        assertEquals(525_000, GearProgressionRules.nextLevelExperienceMillis(5));
        assertEquals(900_000, GearProgressionRules.nextLevelExperienceMillis(10));
        assertEquals(1_650_000, GearProgressionRules.nextLevelExperienceMillis(20));
        long total = 0;
        for (int level = 0; level < 25; level++) total += GearProgressionRules.nextLevelExperienceMillis(level);
        assertEquals(26_250_000, total);
    }
    @Test void extensionGrowthContinuesTheExistingCurveThroughLevelTwentyEight() {
        assertEquals(2_025_000L, GearProgressionRules.nextLevelExperienceMillis(25));
        assertEquals(2_100_000L, GearProgressionRules.nextLevelExperienceMillis(26));
        assertEquals(2_175_000L, GearProgressionRules.nextLevelExperienceMillis(27));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis(28));
    }
    @Test void toolMaterialsUseTheirSourceBaseAndWeaponOnlyDoublesTheSlope() {
        String[] materials = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium", "wood"};
        int[] bases = {100, 100, 125, 150, 150, 200, 200, 200, 150};
        int[] increases = {50, 50, 60, 75, 75, 100, 120, 150, 75};
        for (int index = 0; index < materials.length; index++) {
            for (int level : new int[]{0, 1, 7, 24, 27}) {
                assertEquals((bases[index] + (long)level * increases[index]) * 1000,
                        GearProgressionRules.nextLevelExperienceMillis(materials[index], level, false, -1), materials[index] + " tool L" + level);
                assertEquals((bases[index] + (long)level * increases[index] * 2) * 1000,
                        GearProgressionRules.nextLevelExperienceMillis(materials[index], level, true, -1), materials[index] + " weapon L" + level);
            }
        }
    }
    @Test void armorMaterialsUseTheirOwnCurvesAndExactHelmetChestLegsBootMultipliers() {
        String[] materials = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium", "leather"};
        int[] bases = {16, 16, 18, 20, 20, 24, 28, 32, 150};
        int[] multipliers = {2, 4, 3, 1};
        for (int index = 0; index < materials.length; index++) for (int slot = 0; slot < 4; slot++) {
            for (int level : new int[]{0, 1, 7, 24, 27})
                assertEquals((bases[index] + (long)level * (bases[index] / 2)) * multipliers[slot] * 1000,
                        GearProgressionRules.nextLevelExperienceMillis(materials[index], level, false, slot),
                        materials[index] + " armor slot " + slot + " L" + level);
        }
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis("iron", 0, false, 4));
    }
    @Test void everyRecognizedStandaloneMaterialHasTheSameTwentyFiveLevelCap() {
        for (String material : new String[]{"copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian",
                "ancient_metal", "iron", "mithril", "diamond", "adamantium", "netherite", "runic", "special", "vibranium", "unique"})
            assertEquals(25, GearProgressionRules.maximumLevel(material), material);
        assertEquals(0, GearProgressionRules.maximumLevel(null));
        assertEquals(0, GearProgressionRules.maximumLevel("unknown"));
    }
    @Test void sourceAwardsUseRawMaterialAndIntegerClampRatherThanModernKillOrBlockHardnessScaling() {
        assertEquals(0, GearProgressionRules.killExperienceMillis(20));
        assertEquals(0, GearProgressionRules.killExperienceMillis(100.25));
        assertEquals(2_000, GearProgressionRules.miningExperienceMillis(1.5F, 2));
        assertEquals(3_000, GearProgressionRules.miningExperienceMillis(50F, 3));
        assertEquals(2_000, GearProgressionRules.miningExperienceMillis(50F, 2));
        assertEquals(2_000, GearProgressionRules.damageExperienceMillis(0F));
        assertEquals(2_000, GearProgressionRules.damageExperienceMillis(.00025F));
        assertEquals(2_000, GearProgressionRules.damageExperienceMillis(1.99F));
        assertEquals(4_000, GearProgressionRules.damageExperienceMillis(2F));
        assertEquals(6_000, GearProgressionRules.damageExperienceMillis(3.99F));
        assertEquals(8_000, GearProgressionRules.damageExperienceMillis(4F));
        assertEquals(8_000, GearProgressionRules.damageExperienceMillis(100F));
        assertEquals(1_000, GearProgressionRules.TILL_EXPERIENCE_MILLIS);
        assertEquals(0, GearProgressionRules.shieldExperienceMillis(4F));
        assertEquals(0, GearProgressionRules.shieldExperienceMillis(2F));
        assertEquals(0, GearProgressionRules.shieldExperienceMillis(Float.NaN));
    }
    @Test void invalidInputsAndOverflowCannotCreateExperience() {
        assertEquals(0, GearProgressionRules.killExperienceMillis(Double.NaN));
        assertEquals(0, GearProgressionRules.killExperienceMillis(Double.POSITIVE_INFINITY));
        assertEquals(0, GearProgressionRules.miningExperienceMillis(-1, 5));
        assertEquals(0, GearProgressionRules.miningExperienceMillis(0, 5));
        assertEquals(0, GearProgressionRules.damageExperienceMillis(Float.POSITIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, GearProgressionRules.saturatedAdd(Long.MAX_VALUE - 10, 11));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.saturatedAdd(-1, 1));
    }
}
