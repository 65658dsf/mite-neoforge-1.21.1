package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Native repeat timing stays separate from the shared placement deadline. @author NingMeng123 */
class PlacementClickTimingTest {
    @Test void firstSuccessfulPlacementStartsTheTenTickRepeatImmediately() {
        assertEquals(10, PlacementClickTiming.rightClickDelay(4, 0, 10, false));
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 0, 0, false));
    }

    @Test void heldPlacementResumesAtTheSharedDeadline() {
        assertEquals(2, PlacementClickTiming.rightClickDelay(4, 2, 2, true));
        assertEquals(10, PlacementClickTiming.rightClickDelay(4, 0, 10, false));
    }

    @Test void rapidClicksCannotExtendAnExistingPlacementDeadline() {
        for (int remaining = 1; remaining <= 10; remaining++)
            assertEquals(remaining, PlacementClickTiming.rightClickDelay(4, remaining, remaining, true));
        assertEquals(1, PlacementClickTiming.rightClickDelay(4, 1, 1, true));
    }

    @Test void unrelatedInteractionsKeepTheirNativeDelayDuringPlacementCooldown() {
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 2, 2, false));
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 10, 10, false));
        assertEquals(9, PlacementClickTiming.rightClickDelay(9, 1, 1, false));
        assertEquals(4, PlacementClickTiming.rightClickDelay(4, 1, 0, true));
    }

    @Test void aBlockedAttemptCannotLeakIntoALaterUnrelatedUse() {
        PlacementClickTiming.begin();
        assertFalse(PlacementClickTiming.consumeBlocked());
        PlacementClickTiming.blocked();
        assertTrue(PlacementClickTiming.consumeBlocked());
        assertFalse(PlacementClickTiming.consumeBlocked());
        PlacementClickTiming.blocked();
        PlacementClickTiming.begin();
        assertFalse(PlacementClickTiming.consumeBlocked());
    }
}
