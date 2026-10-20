package io.github.ningmeng123.mite.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Balance boundaries independent of the Minecraft runtime. @author NingMeng123 */
class GearProgressionRulesTest {
    @Test void approvedToolWeaponAndSlotCurvesMatchTheSpreadsheet() {
        int[] levels = {0, 1, 5, 10, 24, 27};
        long[][] points = {
                {100, 150, 350, 600, 1300, 1450},
                {150, 250, 650, 1150, 2550, 2850},
                {40, 60, 140, 240, 520, 580},
                {80, 120, 280, 480, 1040, 1160},
                {60, 90, 210, 360, 780, 870},
                {20, 30, 70, 120, 260, 290}
        };
        for (int curve = 0; curve < points.length; curve++) {
            boolean weapon = curve == 1;
            int armorSlot = curve < 2 ? -1 : curve - 2;
            for (String material : new String[]{"copper", "gold", "iron", "mithril", "vibranium", "leather", "wood", "special"})
                for (int index = 0; index < levels.length; index++)
                    assertEquals(points[curve][index] * 1000,
                            GearProgressionRules.nextLevelExperienceMillis(material, levels[index], weapon, armorSlot));
        }
        assertEquals(150_000, GearProgressionRules.nextLevelExperienceMillis(1));
        assertEquals(150_000, GearProgressionRules.nextLevelExperienceMillis("vibranium", 1));
    }

    @Test void completeCurvesGrowSmoothlyThroughTheIteExtension() {
        long[] totals25 = {17_500, 33_750, 7_000, 14_000, 10_500, 3_500};
        long[] totals28 = {21_700, 42_000, 8_680, 17_360, 13_020, 4_340};
        long[] increments = {50, 100, 20, 40, 30, 10};
        for (int curve = 0; curve < totals25.length; curve++) {
            long total = 0, previous = 0;
            for (int level = 0; level < 28; level++) {
                long threshold = GearProgressionRules.nextLevelExperienceMillis("iron", level, curve == 1, curve < 2 ? -1 : curve - 2);
                if (level > 0) assertEquals(increments[curve] * 1000, threshold - previous);
                total += threshold;
                previous = threshold;
                if (level == 24) assertEquals(totals25[curve] * 1000, total);
            }
            assertEquals(totals28[curve] * 1000, total);
        }
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis(28));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis("iron", 28, true, -1));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis("iron", 28, false, 0));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis("iron", 0, false, -2));
        assertThrows(IllegalArgumentException.class, () -> GearProgressionRules.nextLevelExperienceMillis("iron", 0, false, 4));
    }
    @Test void allSupportedStandaloneMaterialsKeepTheApprovedTwentyFiveLevelCap() {
        for (String material : new String[]{"copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian",
                "ancient_metal", "iron", "mithril", "diamond", "adamantium", "netherite", "runic", "special", "vibranium", "unique"})
            assertEquals(25, GearProgressionRules.maximumLevel(material));
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
