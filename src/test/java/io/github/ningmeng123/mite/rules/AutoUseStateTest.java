package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real gestures, item transitions and cancellation boundaries from bdc/atv. @author NingMeng123 */
class AutoUseStateTest {
    @Test void useMustPrecedeTheChord() {
        var s = new AutoUseState<String>();
        s.update(true, true, "apple"); assertFalse(s.isActive());
        s.update(true, false, "apple"); s.update(true, true, "apple"); assertFalse(s.isActive());
        s.update(false, true, "apple"); s.update(true, true, "apple"); assertTrue(s.matches("apple"));
    }
    @Test void releasingInEitherOrderKeepsUseLatchedUntilANewAttack() {
        for (boolean useFirst : new boolean[]{false, true}) {
            var s = active();
            s.update(!useFirst, useFirst, "apple"); assertTrue(s.isActive());
            s.update(false, false, "apple"); assertTrue(s.matches("apple"));
            s.update(true, false, "apple"); assertFalse(s.isActive());
        }
    }
    @Test void depletionMayWaitForRefillButDifferentItemsCancel() {
        var s = active(); s.update(false, false, null); assertTrue(s.isActive());
        assertFalse(s.matches(null)); s.update(false, false, "apple"); assertTrue(s.matches("apple"));
        s.update(false, false, "bread"); assertFalse(s.isActive());
    }
    @Test void ineligibleUseCannotArmAndClearRequiresAFreshGesture() {
        var s = new AutoUseState<String>(); s.update(false, true, null); s.update(true, true, null);
        assertFalse(s.isActive()); s = active(); s.clear();
        s.update(true, true, "apple"); assertFalse(s.isActive());
    }
    @Test void holdingTheChordDoesNotRecaptureAnotherItem() {
        var s = active(); s.update(true, true, "bread"); assertFalse(s.isActive());
    }
    @Test void anActuallyReturnedVesselMayWaitForTheAuthoritativeSameFoodSwitch() {
        var s = active(); s.finished("apple", "bowl", 100L);
        assertTrue(s.waitingForRestock("bowl", 3099L));
        assertFalse(s.waitingForRestock("bowl", 3100L));
        assertFalse(s.waitingForRestock("bucket", 101L));
        assertTrue(s.matches("apple"));
        s.resumed(); assertFalse(s.waitingForRestock("bowl", 101L));
        s.clear(); assertFalse(s.waitingForRestock("bowl", 101L));
    }
    private static AutoUseState<String> active() {
        var s = new AutoUseState<String>(); s.update(false, true, "apple"); s.update(true, true, "apple"); return s;
    }
}
