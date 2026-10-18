package io.github.ningmeng123.mite.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Balance boundaries independent of the Minecraft runtime. @author NingMeng123 */
class GearProgressionRulesTest {
    @Test void fiveLevelStepsAndCompleteCurveAreStable() {
        assertEquals(100_000, GearProgressionRules.nextLevelExperienceMillis(0));
        assertEquals(500_000, GearProgressionRules.nextLevelExperienceMillis(4));
        assertEquals(1_200_000, GearProgressionRules.nextLevelExperienceMillis(5));
        assertEquals(3_300_000, GearProgressionRules.nextLevelExperienceMillis(10));
        assertEquals(10_500_000, GearProgressionRules.nextLevelExperienceMillis(20));
        long total = 0;
        for (int level = 0; level < 25; level++) total += GearProgressionRules.nextLevelExperienceMillis(level);
        assertEquals(122_500_000, total);
    }
    @Test void extensionGrowthContinuesTheExistingCurveThroughLevelTwentyEight() {
        assertEquals(15_600_000L, GearProgressionRules.nextLevelExperienceMillis(25));
        assertEquals(16_200_000L, GearProgressionRules.nextLevelExperienceMillis(26));
        assertEquals(16_800_000L, GearProgressionRules.nextLevelExperienceMillis(27));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis(28));
    }
    @Test void allExplicitMaterialsAndModernSeriesUseTheirApprovedCaps() {
        for (String material : new String[]{"copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian"})
            assertEquals(5, GearProgressionRules.maximumLevel(material));
        assertEquals(10, GearProgressionRules.maximumLevel("ancient_metal"));
        assertEquals(10, GearProgressionRules.maximumLevel("iron"));
        assertEquals(15, GearProgressionRules.maximumLevel("mithril"));
        assertEquals(15, GearProgressionRules.maximumLevel("diamond"));
        for (String material : new String[]{"adamantium", "netherite", "runic", "special"})
            assertEquals(20, GearProgressionRules.maximumLevel(material));
        assertEquals(25, GearProgressionRules.maximumLevel("vibranium"));
        assertEquals(25, GearProgressionRules.maximumLevel("unique"));
        assertEquals(0, GearProgressionRules.maximumLevel(null));
        assertEquals(0, GearProgressionRules.maximumLevel("unknown"));
    }
    @Test void awardsScaleWithHealthHardnessTierAndFractionalDamage() {
        assertEquals(20_000, GearProgressionRules.killExperienceMillis(20));
        assertEquals(101_000, GearProgressionRules.killExperienceMillis(100.25));
        assertEquals(6_000, GearProgressionRules.miningExperienceMillis(1.5F, 2));
        assertEquals(250_000, GearProgressionRules.miningExperienceMillis(50F, 3));
        assertEquals(1, GearProgressionRules.damageExperienceMillis(.00025F));
        assertEquals(0, GearProgressionRules.damageExperienceMillis(.0001F));
        assertEquals(12_000, GearProgressionRules.TILL_EXPERIENCE_MILLIS);
        assertEquals(8_000, GearProgressionRules.shieldExperienceMillis(4F));
        assertEquals(4_000, GearProgressionRules.shieldExperienceMillis(2F));
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
