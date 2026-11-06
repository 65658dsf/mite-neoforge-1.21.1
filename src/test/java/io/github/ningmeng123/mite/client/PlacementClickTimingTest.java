package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Placement retries follow the real-time use lock and preserve food fallback. @author NingMeng123 */
class PlacementClickTimingTest {
    @Test void successfulBlockAndBucketPlacementWaitForDeadlinePolling() {
        assertEquals(1, PlacementClickTiming.rightClickDelay(4, 250L, true, false));
        assertEquals(1, PlacementClickTiming.rightClickDelay(4, 500L, true, false));
        assertEquals(1, PlacementClickTiming.rightClickDelay(4, 1L, true, false));
    }

    @Test void failedPlacementCanRetryOnTheNextInputTick() {
        assertEquals(0, PlacementClickTiming.rightClickDelay(4, 0L, true, false));
        assertEquals(0, PlacementClickTiming.rightClickDelay(9, 0L, true, false));
    }

    @Test void failedPlantingThatStartsEatingKeepsTheNativeDelay() {
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 0L, true, true));
        assertEquals(9, PlacementClickTiming.rightClickDelay(9, 0L, true, true));
    }

    @Test void ordinaryInteractionsKeepTheirNativeRepeatTiming() {
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 0L, false, false));
        assertEquals(9, PlacementClickTiming.rightClickDelay(9, 0L, false, false));
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 0L, false, true));
    }

    @Test void successfulOffhandUseCompletesAFailedMainHandPlacementAttempt() {
        PlacementClickTiming.begin();
        PlacementClickTiming.attempted();
        PlacementClickTiming.actionConsumed();
        boolean attempted = PlacementClickTiming.consumeAttempted();
        assertFalse(attempted);
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 0L, attempted, false));
    }

    @Test void successfulPlacementRetainsItsDeadlineAfterTheActionIsConsumed() {
        PlacementClickTiming.begin();
        PlacementClickTiming.attempted();
        PlacementClickTiming.actionConsumed();
        assertEquals(1, PlacementClickTiming.rightClickDelay(4, 250L,
                PlacementClickTiming.consumeAttempted(), false));
    }

    @Test void anAttemptCannotLeakIntoALaterUnrelatedUse() {
        PlacementClickTiming.begin();
        assertFalse(PlacementClickTiming.consumeAttempted());
        PlacementClickTiming.attempted();
        assertTrue(PlacementClickTiming.consumeAttempted());
        assertFalse(PlacementClickTiming.consumeAttempted());
        PlacementClickTiming.attempted();
        PlacementClickTiming.begin();
        assertFalse(PlacementClickTiming.consumeAttempted());
    }
}
