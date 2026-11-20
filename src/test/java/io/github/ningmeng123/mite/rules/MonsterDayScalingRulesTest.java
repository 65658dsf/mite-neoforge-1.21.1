package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently tabulated calendar growth; native entity health limits are outside the arithmetic contract. @author NingMeng123 */
class MonsterDayScalingRulesTest {
    @Test void agreedCalendarDatesHaveTheExpectedHealthAndDamageMultipliers() {
        long[] days = {1L, 2L, 21L, 32L, 33L, 34L, 64L, 65L, 66L, 97L, 129L};
        double[] expected = {1D, 1.005D, 1.1D, 1.155D, 1.16D, 1.1658D, 1.3398D, 1.3456D, 1.352328D, 1.560896D, 1.81063936D};
        for (int index = 0; index < days.length; index++) {
            assertEquals(expected[index], MonsterDayScalingRules.healthMultiplier(days[index]), 1E-10, "Health at day " + days[index]);
            assertEquals(expected[index], MonsterDayScalingRules.damageMultiplier(days[index]), 1E-10, "Damage at day " + days[index]);
        }
    }

    @Test void dayOneIsNeutralAndNonpositiveMigratedDatesCannotWeakenAnEntity() {
        for (long day : new long[]{Long.MIN_VALUE, -1L, 0L, 1L}) {
            assertEquals(1D, MonsterDayScalingRules.healthMultiplier(day));
            assertEquals(1D, MonsterDayScalingRules.damageMultiplier(day));
        }
    }

    @Test void eachThirtyTwoDaySegmentIsLinearAndTheNextSegmentUsesItsNewBasis() {
        long[][] dates = {{1L, 20L, 31L, 32L}, {33L, 40L, 63L, 64L}, {65L, 80L, 95L, 96L}};
        double[] step = {.005D, .0058D, .006728D};
        for (int segment = 0; segment < dates.length; segment++) for (long previous : dates[segment]) {
            assertEquals(step[segment], MonsterDayScalingRules.healthMultiplier(previous + 1L)
                    - MonsterDayScalingRules.healthMultiplier(previous), 1E-12);
            assertEquals(step[segment], MonsterDayScalingRules.damageMultiplier(previous + 1L)
                    - MonsterDayScalingRules.damageMultiplier(previous), 1E-12);
        }
    }

    @Test void ruleMultipliersRemainUncappedWhenNativeMaximumHealthWillAlreadyBeClamped() {
        assertEquals(115.51959436843139D, MonsterDayScalingRules.healthMultiplier(1025L), 1E-10);
        assertEquals(115.51959436843139D, MonsterDayScalingRules.damageMultiplier(1025L), 1E-10);
        assertEquals(13344.776683046925D, MonsterDayScalingRules.healthMultiplier(2049L), 1E-8);
        assertEquals(13344.776683046925D, MonsterDayScalingRules.damageMultiplier(2049L), 1E-8);
        assertEquals(Double.MAX_VALUE, MonsterDayScalingRules.healthMultiplier(1_000_000_001L));
        assertEquals(Double.MAX_VALUE, MonsterDayScalingRules.damageMultiplier(1_000_000_001L));
    }

    @Test void maximumLongCalendarDayDoesNotOverflowTheElapsedDayCalculation() {
        double health = MonsterDayScalingRules.healthMultiplier(Long.MAX_VALUE);
        double damage = MonsterDayScalingRules.damageMultiplier(Long.MAX_VALUE);
        assertTrue(Double.isFinite(health) && Double.isFinite(damage));
        assertEquals(Double.MAX_VALUE, health);
        assertEquals(Double.MAX_VALUE, damage);
        assertTrue(health >= MonsterDayScalingRules.healthMultiplier(Long.MAX_VALUE - 1L));
        assertTrue(damage >= MonsterDayScalingRules.damageMultiplier(Long.MAX_VALUE - 1L));
    }

    @Test void skeletonGrowthKeepsItsInitialHealthAndUsesTheIndependentlyTabulatedTwentyHealthIncrement() {
        long[] days = {1L, 2L, 21L, 33L, 34L, 65L};
        double[] bases = {6D, 20D, 30D};
        double[][] maximum = {{6D, 6.1D, 8D, 9.2D, 9.316D, 12.912D}, {20D, 20.1D, 22D, 23.2D, 23.316D, 26.912D},
                {30D, 30.15D, 33D, 34.8D, 34.974D, 40.368D}};
        for (int basis = 0; basis < bases.length; basis++) for (int date = 0; date < days.length; date++)
            assertEquals(maximum[basis][date], bases[basis] * MonsterDayScalingRules.healthMultiplier(days[date], bases[basis], true),
                    1E-10, "Skeleton maximum for initial health " + bases[basis] + " on day " + days[date]);
    }

    @Test void anEquallySmallNonskeletonDoesNotReceiveTheSkeletonOnlyGrowthReference() {
        long[] days = {1L, 2L, 21L, 33L, 34L, 65L};
        double[] sixHealthMaximum = {6D, 6.03D, 6.6D, 6.96D, 6.9948D, 8.0736D};
        for (int index = 0; index < days.length; index++)
            assertEquals(sixHealthMaximum[index], 6D * MonsterDayScalingRules.healthMultiplier(days[index], 6D, false), 1E-10);
        for (double basis : new double[]{20D, 30D}) for (long day : days)
            assertEquals(MonsterDayScalingRules.healthMultiplier(day), MonsterDayScalingRules.healthMultiplier(day, basis, false), 1E-12);
    }

    @Test void detachedHealthBasisMustBeFiniteAndPositiveAndDoesNotChangeDayOne() {
        for (boolean skeleton : new boolean[]{false, true}) {
            for (double invalid : new double[]{0D, -1D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
                assertThrows(IllegalArgumentException.class, () -> MonsterDayScalingRules.healthMultiplier(65L, invalid, skeleton));
            for (double basis : new double[]{6D, 20D, 30D})
                assertEquals(1D, MonsterDayScalingRules.healthMultiplier(1L, basis, skeleton));
        }
    }

    @Test void configuredGrowthChangesEachDailyIncreaseAndRetainsTheThirtyTwoDayRebase() {
        assertEquals(1.32D, MonsterDayScalingRules.healthMultiplier(33L, 2D), 1E-12);
        assertEquals(1.7424D, MonsterDayScalingRules.healthMultiplier(65L, 2D), 1E-12);
        assertEquals(1.32D, MonsterDayScalingRules.damageMultiplier(33L, 2D), 1E-12);
        assertEquals(1.7424D, MonsterDayScalingRules.damageMultiplier(65L, 2D), 1E-12);
        assertEquals(1.08D, MonsterDayScalingRules.healthMultiplier(33L, .5D), 1E-12);
        assertEquals(1.08D, MonsterDayScalingRules.damageMultiplier(33L, .5D), 1E-12);
        assertEquals(12.4D, 6D * MonsterDayScalingRules.healthMultiplier(33L, 6D, true, 2D), 1E-12);
        assertEquals(20.848D, 6D * MonsterDayScalingRules.healthMultiplier(65L, 6D, true, 2D), 1E-12);
    }

    @Test void zeroGrowthKeepsTheOriginalBodyAndDamageEvenForAncientWorlds() {
        for (long day : new long[]{1L, 33L, 65L, 1_000_000_001L, Long.MAX_VALUE}) {
            assertEquals(1D, MonsterDayScalingRules.healthMultiplier(day, 0D));
            assertEquals(1D, MonsterDayScalingRules.damageMultiplier(day, 0D));
            assertEquals(1D, MonsterDayScalingRules.healthMultiplier(day, 6D, true, 0D));
        }
    }

    @Test void configuredGrowthCannotIntroduceNegativeOrNonfiniteRates() {
        for (double invalid : new double[]{-1D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> MonsterDayScalingRules.healthMultiplier(65L, invalid));
            assertThrows(IllegalArgumentException.class, () -> MonsterDayScalingRules.damageMultiplier(65L, invalid));
        }
        assertEquals(Double.MAX_VALUE, MonsterDayScalingRules.healthMultiplier(Long.MAX_VALUE, 10_000D));
        assertEquals(Double.MAX_VALUE, MonsterDayScalingRules.damageMultiplier(Long.MAX_VALUE, 10_000D));
    }
}
