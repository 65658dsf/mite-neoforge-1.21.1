package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independent elapsed-work boundaries for the persistent crop clock. @author NingMeng123 */
final class TimedCropGrowthRulesTest {
    private static final long MATURITY = 504_000_000_000L;

    @Test void positiveUnboostedLayoutsTakeSixtyMinutesAndAllBoostsHaveAThirtyFiveMinuteFloor() {
        assertEquals(MATURITY,CropGrowthRules.TIMED_MATURITY_WORK);
        for(float rate:new float[]{.01F,.5F,1F,1.25F}) {
            assertEquals(7_000_000L,CropGrowthRules.timedWorkPerTick(rate));
            assertEquals(72_000L,CropGrowthRules.timedRemainingTicks(0L,rate));
        }
        assertEquals(9_500_000L,CropGrowthRules.timedWorkPerTick(2.5F));
        assertEquals(53_053L,CropGrowthRules.timedRemainingTicks(0L,2.5F));
        for(float rate:new float[]{3.75F,5F,Float.MAX_VALUE,Float.POSITIVE_INFINITY}) {
            assertEquals(12_000_000L,CropGrowthRules.timedWorkPerTick(rate));
            assertEquals(42_000L,CropGrowthRules.timedRemainingTicks(0L,rate));
        }
    }

    @Test void blockedOrInvalidRatesNeverAdvanceWork() {
        for(float rate:new float[]{0F,-1F,Float.NEGATIVE_INFINITY,Float.NaN}) {
            assertEquals(0L,CropGrowthRules.timedWorkPerTick(rate));
            assertEquals(123L,CropGrowthRules.advanceTimedWork(123L,72_000L,rate));
            assertEquals(-1L,CropGrowthRules.timedRemainingTicks(123L,rate));
        }
    }

    @Test void fastestClockCannotMatureEvenOneTickBeforeThirtyFiveMinutesForEitherAgeScale() {
        long before=CropGrowthRules.advanceTimedWork(0L,41_999L,Float.MAX_VALUE);
        assertEquals(MATURITY-12_000_000L,before);
        for(int maximum:new int[]{3,7}) {
            assertEquals(maximum-1,CropGrowthRules.timedAge(before,maximum));
            assertEquals(1L,CropGrowthRules.timedRemainingTicks(before,3.75F));
            assertEquals(maximum,CropGrowthRules.timedAge(CropGrowthRules.advanceTimedWork(before,1L,3.75F),maximum));
        }
    }

    @Test void baseClockCannotMatureEvenOneTickBeforeSixtyMinutes() {
        long before=CropGrowthRules.advanceTimedWork(0L,71_999L,1.25F);
        assertEquals(MATURITY-7_000_000L,before);
        assertEquals(6,CropGrowthRules.timedAge(before,7));
        assertEquals(1L,CropGrowthRules.timedRemainingTicks(before,1.25F));
        assertEquals(MATURITY,CropGrowthRules.advanceTimedWork(before,1L,1.25F));
    }

    @Test void changingLayoutsKeepsEarnedProgressAndStillCannotBreakTheGlobalMinimum() {
        long work=CropGrowthRules.advanceTimedWork(0L,20_000L,1.25F);
        assertEquals(140_000_000_000L,work);
        work=CropGrowthRules.advanceTimedWork(work,21_999L,3.75F);
        assertTrue(work<MATURITY,"Even switching to the fastest farm leaves work at total elapsed time 41999 ticks");
        assertTrue(CropGrowthRules.timedAge(work,7)<7);
        assertEquals(8_335L,CropGrowthRules.timedRemainingTicks(work,3.75F));
    }

    @Test void stagesReflectOnlyEarnedWorkWithNoRandomEarlyBoundary() {
        for(int maximum:new int[]{3,7})for(int stage=1;stage<=maximum;stage++) {
            long boundary=MATURITY*stage/maximum;
            assertEquals(stage-1,CropGrowthRules.timedAge(boundary-1L,maximum));
            assertEquals(stage,CropGrowthRules.timedAge(boundary,maximum));
        }
        assertEquals(0,CropGrowthRules.timedAge(0L,7));
        assertEquals(7,CropGrowthRules.timedAge(MATURITY,7));
    }

    @Test void hugeElapsedIntervalsSaturateWithoutOverflowAndNegativeTimeDoesNotReverseGrowth() {
        assertEquals(MATURITY,CropGrowthRules.advanceTimedWork(0L,Long.MAX_VALUE,3.75F));
        assertEquals(MATURITY,CropGrowthRules.advanceTimedWork(MATURITY-1L,Long.MAX_VALUE,1.25F));
        assertEquals(123L,CropGrowthRules.advanceTimedWork(123L,0L,1.25F));
        assertEquals(123L,CropGrowthRules.advanceTimedWork(123L,-1L,1.25F));
        assertEquals(0L,CropGrowthRules.timedRemainingTicks(MATURITY,0F));
        assertEquals(7,CropGrowthRules.timedAge(Long.MAX_VALUE,7));
    }
}
