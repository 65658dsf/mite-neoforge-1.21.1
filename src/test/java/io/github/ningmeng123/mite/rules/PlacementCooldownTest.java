package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Source millisecond boundaries remain independent of simulation ticks. @author NingMeng123 */
class PlacementCooldownTest {
    @Test void successfulBlockPlacementLocksExactly250Milliseconds() {
        var cooldown = PlacementCooldown.afterPlacement(1_000L, false);
        assertEquals(250L, cooldown.remainingMillis(1_000L));
        assertEquals(1L, cooldown.remainingMillis(1_249L));
        assertEquals(0L, cooldown.remainingMillis(1_250L));
        assertEquals(0L, cooldown.remainingMillis(2_000L));
    }

    @Test void successfulBucketPlacementUsesTheDefault500MillisecondDelay() {
        var cooldown = PlacementCooldown.afterPlacement(1_000L, true);
        assertEquals(500L, cooldown.remainingMillis(1_000L));
        assertEquals(250L, cooldown.remainingMillis(1_250L));
        assertEquals(1L, cooldown.remainingMillis(1_499L));
        assertEquals(0L, cooldown.remainingMillis(1_500L));
    }

    @Test void repeatedChecksCannotExtendTheDeadline() {
        var cooldown = PlacementCooldown.afterPlacement(1_000L, false);
        for (long elapsed = 0; elapsed <= 250L; elapsed++) {
            assertEquals(250L - elapsed, cooldown.remainingMillis(1_000L + elapsed));
        }
        assertEquals(1_250L, cooldown.deadlineMillis());
    }

    @Test void eachSuccessfulPlacementChoosesItsOwnDuration() {
        var bucket = PlacementCooldown.afterPlacement(1_000L, true);
        var block = PlacementCooldown.afterPlacement(bucket.deadlineMillis(), false);
        assertEquals(0L, bucket.remainingMillis(1_500L));
        assertEquals(250L, block.remainingMillis(1_500L));
        assertEquals(0L, block.remainingMillis(1_750L));
    }
}
