package io.github.ningmeng123.mite.integration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independent timing scenarios never load Minecraft or Jade. @author NingMeng123 */
final class CropGrowthCountdownTest {
    @Test void expectedDurationUsesBlockSelectionAndActualRandomTickSpeed() {
        // Each section contains 4096 positions; three independent picks per tick hit a crop 3/4096 times.
        assertEquals(163840L, CropGrowthCountdown.expectedTicks(3, .025D, 3));
        assertEquals(81920L, CropGrowthCountdown.expectedTicks(3, .025D, 6));
        assertEquals(54614L, CropGrowthCountdown.expectedTicks(1, .025D, 3));
        assertEquals(1366L, CropGrowthCountdown.expectedTicks(1, 1D, 3));
        assertEquals(0L, CropGrowthCountdown.expectedTicks(0, 0D, 0));
    }

    @Test void invalidGrowthInputsDoNotPromiseAMaturityTime() {
        for (double chance : new double[]{0D, -1D, 1.1D, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(-1L, CropGrowthCountdown.expectedTicks(1, chance, 3));
        assertEquals(-1L, CropGrowthCountdown.expectedTicks(-1, .025D, 3));
        assertEquals(-1L, CropGrowthCountdown.expectedTicks(1, .025D, 0));
        assertEquals(-1L, CropGrowthCountdown.expectedTicks(1, .025D, -3));
        assertEquals(Long.MAX_VALUE, CropGrowthCountdown.expectedTicks(1, Double.MIN_VALUE, 1));
    }

    @Test void actualServerSampleCountsGameTicksWithoutAccumulatingViewerState() {
        assertEquals(200L,CropGrowthCountdown.remainingAtSample(200L,true,1000L,1000L));
        assertEquals(180L,CropGrowthCountdown.remainingAtSample(200L,true,1000L,1020L));
        assertEquals(180L,CropGrowthCountdown.remainingAtSample(200L,true,1000L,1020L));
        assertEquals(160L,CropGrowthCountdown.remainingAtSample(200L,true,1000L,1040L));
    }

    @Test void pausedServerSampleNeverChargesItsPausedInterval() {
        assertEquals(500L,CropGrowthCountdown.remainingAtSample(500L,false,0L,20L));
        assertEquals(500L,CropGrowthCountdown.remainingAtSample(500L,false,0L,1000L));
        assertEquals(480L,CropGrowthCountdown.remainingAtSample(500L,true,1000L,1020L));
    }

    @Test void aNewServerSampleReplacesThePredictionWithoutRememberingThePreviousTarget() {
        assertEquals(180L,CropGrowthCountdown.remainingAtSample(200L,true,1000L,1020L));
        assertEquals(150L,CropGrowthCountdown.remainingAtSample(150L,true,1020L,1020L));
        assertEquals(130L,CropGrowthCountdown.remainingAtSample(150L,true,1020L,1040L));
    }

    @Test void aFutureSampleOrRewoundClockCannotIncreaseOrDecreaseTheServerRemainingTime() {
        assertEquals(1000L,CropGrowthCountdown.remainingAtSample(1000L,true,1200L,1100L));
        assertEquals(1000L,CropGrowthCountdown.remainingAtSample(1000L,false,1200L,1100L));
    }

    @Test void expiryClampsAtZeroAndMissingServerTimeNeverBecomesAClock() {
        assertEquals(5L,CropGrowthCountdown.remainingAtSample(25L,true,0L,20L));
        assertEquals(0L,CropGrowthCountdown.remainingAtSample(25L,true,0L,40L));
        assertEquals(0L,CropGrowthCountdown.remainingAtSample(25L,true,0L,60L));
        assertEquals(-1L,CropGrowthCountdown.remainingAtSample(-1L,true,0L,80L));
        assertEquals(-1L,CropGrowthCountdown.remainingAtSample(-2L,false,0L,80L));
    }
}
