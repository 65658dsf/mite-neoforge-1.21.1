package io.github.ningmeng123.mite.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Exhaustive integer draws verify growth rewards without sampling or a Minecraft runtime. @author NingMeng123 */
class GearAffixRulesTest {
    @Test void everyLevelFromZeroThroughTwentyFiveHasItsExactNumberOfSuccessfulIntegerDraws() {
        assertEquals(25, GearAffixRules.BASE_REWARD_CHANCE_PERCENT);
        assertEquals(3, GearAffixRules.REWARD_CHANCE_PER_LEVEL);
        for (int level = 0; level <= 25; level++) {
            int expectedChance = 25 + 3 * level;
            int successes = 0;
            assertEquals(expectedChance, GearAffixRules.rewardChancePercent(level), "Chance at level " + level);
            for (int roll = 0; roll < 100; roll++) {
                boolean granted = GearAffixRules.grantsAffix(level, roll);
                assertEquals(roll < expectedChance, granted, "Level " + level + " reward roll " + roll);
                if (granted) successes++;
            }
            assertEquals(expectedChance, successes, "Successful draws at level " + level);
        }
    }

    @Test void invalidRewardRollsAndNegativeLevelsAreRejectedEvenAtGuaranteedRewardLevels() {
        for (int level : new int[]{0, 1, 25, Integer.MAX_VALUE})
            for (int roll : new int[]{Integer.MIN_VALUE, -1, 100, Integer.MAX_VALUE})
                assertThrows(IllegalArgumentException.class, () -> GearAffixRules.grantsAffix(level, roll));
        for (int level : new int[]{Integer.MIN_VALUE, -1}) {
            assertThrows(IllegalArgumentException.class, () -> GearAffixRules.rewardChancePercent(level));
            assertThrows(IllegalArgumentException.class, () -> GearAffixRules.grantsAffix(level, 0));
        }
    }

    @Test void targetLevelCheckpointsAndProbabilityEdgesUseTheNewlyReachedLevel() {
        int[][] checkpoints = {{0, 25}, {1, 28}, {5, 40}, {10, 55}, {15, 70}, {20, 85}, {25, 100}};
        for (int[] checkpoint : checkpoints)
            assertEquals(checkpoint[1], GearAffixRules.rewardChancePercent(checkpoint[0]), "Target level " + checkpoint[0]);
        assertTrue(GearAffixRules.grantsAffix(0, 24));
        assertFalse(GearAffixRules.grantsAffix(0, 25));
        assertTrue(GearAffixRules.grantsAffix(1, 27));
        assertFalse(GearAffixRules.grantsAffix(1, 28));
        assertTrue(GearAffixRules.grantsAffix(25, 99));
    }

    @Test void levelsAboveTwentyFiveClampAtOneHundredWithoutIntegerOverflow() {
        for (int level : new int[]{26, 100, 715_827_875, 715_827_883, Integer.MAX_VALUE}) {
            assertEquals(100, GearAffixRules.rewardChancePercent(level), "Clamped chance at level " + level);
            for (int roll = 0; roll < 100; roll++)
                assertTrue(GearAffixRules.grantsAffix(level, roll), "Guaranteed reward at level " + level + " roll " + roll);
        }
    }

    @Test void everyLegalWeightedDrawMatchesItsExactReferenceInterval() {
        int[] weights = {40, 20, 6, 2};
        int[] counts = new int[weights.length];
        assertEquals(68, GearAffixRules.totalWeight(weights));
        for (int roll = 0; roll < 68; roll++) {
            int expected = roll < 40 ? 0 : roll < 60 ? 1 : roll < 66 ? 2 : 3;
            int selected = GearAffixRules.selectIndex(weights, roll);
            assertEquals(expected, selected, "Weighted draw " + roll);
            counts[selected]++;
        }
        assertArrayEquals(weights, counts, "Each candidate owns exactly one integer slot per weight unit");
        assertArrayEquals(new int[]{40, 20, 6, 2}, weights, "Drawing does not mutate the candidate weights");
    }

    @Test void zeroWeightCandidatesHaveNoSlotsEvenBetweenPositiveCandidates() {
        int[] weights = {0, 3, 0, 1, 0, 2, 0};
        int[] expected = {1, 1, 1, 3, 5, 5};
        int[] counts = new int[weights.length];
        assertEquals(expected.length, GearAffixRules.totalWeight(weights));
        for (int roll = 0; roll < expected.length; roll++) {
            int selected = GearAffixRules.selectIndex(weights, roll);
            assertEquals(expected[roll], selected, "Weighted draw with zero-weight entries " + roll);
            counts[selected]++;
        }
        assertArrayEquals(weights, counts);
        for (int roll = 0; roll < 4; roll++)
            assertEquals(2, GearAffixRules.selectIndex(new int[]{0, 0, 4, 0}, roll));
    }

    @Test void emptyPoolsNegativeWeightsAndOutOfRangeDrawsAreRejected() {
        for (int[] empty : new int[][]{new int[0], {0}, {0, 0, 0}}) {
            assertEquals(0, GearAffixRules.totalWeight(empty));
            for (int roll : new int[]{-1, 0, 1, Integer.MAX_VALUE})
                assertThrows(IllegalArgumentException.class, () -> GearAffixRules.selectIndex(empty, roll));
        }
        for (int[] invalid : new int[][]{{-1}, {1, -1}, {0, Integer.MIN_VALUE, 2}}) {
            assertThrows(IllegalArgumentException.class, () -> GearAffixRules.totalWeight(invalid));
            assertThrows(IllegalArgumentException.class, () -> GearAffixRules.selectIndex(invalid, 0));
        }
        for (int roll : new int[]{Integer.MIN_VALUE, -1, 3, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> GearAffixRules.selectIndex(new int[]{1, 2}, roll));
        assertThrows(NullPointerException.class, () -> GearAffixRules.totalWeight(null));
        assertThrows(NullPointerException.class, () -> GearAffixRules.selectIndex(null, 0));
    }

    @Test void weightSumOverflowIsExplicitAndTheLargestValidPoolKeepsItsBoundaries() {
        int[] overflowing = {Integer.MAX_VALUE, 1};
        assertThrows(ArithmeticException.class, () -> GearAffixRules.totalWeight(overflowing));
        assertThrows(ArithmeticException.class, () -> GearAffixRules.selectIndex(overflowing, 0));
        int[] largest = {Integer.MAX_VALUE - 1, 1};
        assertEquals(Integer.MAX_VALUE, GearAffixRules.totalWeight(largest));
        assertEquals(0, GearAffixRules.selectIndex(largest, Integer.MAX_VALUE - 2));
        assertEquals(1, GearAffixRules.selectIndex(largest, Integer.MAX_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> GearAffixRules.selectIndex(largest, Integer.MAX_VALUE));
        assertEquals(0, GearAffixRules.selectIndex(new int[]{Integer.MAX_VALUE, 0}, Integer.MAX_VALUE - 1));
        assertEquals(1, GearAffixRules.selectIndex(new int[]{0, Integer.MAX_VALUE}, Integer.MAX_VALUE - 1));
    }

    @Test void definitionsRejectNegativeWeightsAndCapsOutsideOneThroughTwoHundredFiftyFive() {
        assertThrows(IllegalArgumentException.class, () -> new GearAffixRules.Definition(-1, 5));
        for (int cap : new int[]{Integer.MIN_VALUE, -1, 0, 256, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new GearAffixRules.Definition(1, cap));
        assertEquals(0, new GearAffixRules.Definition(0, 1).weight(), "Zero weight is legal for an excluded candidate");
        assertEquals(255, new GearAffixRules.Definition(Integer.MAX_VALUE, 255).maximumLevel());
        assertThrows(NullPointerException.class, () -> GearAffixRules.definition(null, false, 1, 1));
    }

    @Test void unbreakingUsesSeparateArmorAndNonArmorWeightsWithTheSameGrowthCap() {
        assertEquals(new GearAffixRules.Definition(20, 5), GearAffixRules.definition("minecraft:unbreaking", true, 1, 1));
        assertEquals(new GearAffixRules.Definition(40, 5), GearAffixRules.definition("minecraft:unbreaking", false, 1, 1));
    }

    @Test void referenceWeightsAndGrowthCapsOverrideNativeDefinitions() {
        definitions(20, 10, "minecraft:efficiency", "minecraft:sharpness", "minecraft:smite",
                "minecraft:bane_of_arthropods", "minecraft:power", "minecraft:impaling", "minecraft:density", "minecraft:breach");
        definitions(5, 5, "minecraft:protection", "minecraft:thorns", "mite:disarming");
        definitions(7, 5, "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:feather_falling");
        definitions(10, 5, "minecraft:knockback", "minecraft:punch", "mite:stun", "minecraft:respiration",
                "minecraft:loyalty", "minecraft:piercing", "minecraft:quick_charge", "minecraft:lure",
                "mite:arrow_recovery", "mite:fertility", "mite:tree_felling", "mite:harvesting", "mite:butchering");
        definitions(6, 5, "minecraft:fortune", "minecraft:looting", "mite:fishing_fortune", "minecraft:fire_aspect", "mite:poison", "mite:piercing");
        definitions(8, 5, "minecraft:depth_strider", "minecraft:soul_speed", "mite:speed", "mite:regeneration");
        definitions(4, 5, "minecraft:frost_walker", "minecraft:riptide", "minecraft:wind_burst", "mite:vampiric");
        definitions(5, 4, "mite:endurance");
        definitions(3, 4, "mite:free_action");
        definitions(10, 10, "mite:quickness", "minecraft:sweeping_edge");
        definitions(20, 10, "mite:true_flight");
        definitions(2, 1, "minecraft:silk_touch", "minecraft:infinity", "minecraft:channeling");
        definitions(4, 1, "minecraft:multishot", "minecraft:flame");
        definitions(10, 1, "minecraft:aqua_affinity");
    }

    @Test void modernFallbackRetainsNativeRarityDoublesMultiLevelCapsAndNeverExceedsTwoHundredFiftyFive() {
        for (int nativeCap = 2; nativeCap <= 256; nativeCap++) {
            var expected = new GearAffixRules.Definition(7, Math.min(255, 2 * nativeCap));
            assertEquals(expected, GearAffixRules.definition("example:modern", false, 7, nativeCap), "Native cap " + nativeCap);
            assertEquals(expected, GearAffixRules.definition("example:modern", true, 7, nativeCap), "Fallback armor cap " + nativeCap);
        }
        assertEquals(new GearAffixRules.Definition(Integer.MAX_VALUE, 255),
                GearAffixRules.definition("example:very_high_rank", false, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(new GearAffixRules.Definition(0, 6), GearAffixRules.definition("example:zero_weight", false, 0, 3));
        assertEquals(new GearAffixRules.Definition(0, 6), GearAffixRules.definition("example:negative_weight", false, Integer.MIN_VALUE, 3));
        assertEquals(new GearAffixRules.Definition(3, 4), GearAffixRules.definition("example:unbreaking", true, 3, 2),
                "A different namespace uses its own native definition rather than the Minecraft override");
    }

    @Test void fallbackSingleLevelAffixesRemainSingleLevelAndNonPositiveNativeCapsNormalizeToOne() {
        for (int nativeCap : new int[]{Integer.MIN_VALUE, -1, 0, 1})
            assertEquals(new GearAffixRules.Definition(10, 1),
                    GearAffixRules.definition("example:single_level", false, 10, nativeCap));
    }

    private static void definitions(int weight, int cap, String... ids) {
        var expected = new GearAffixRules.Definition(weight, cap);
        for (String id : ids) for (boolean armor : new boolean[]{false, true})
            assertEquals(expected, GearAffixRules.definition(id, armor, 0, 255), id + " armor=" + armor);
    }
}
