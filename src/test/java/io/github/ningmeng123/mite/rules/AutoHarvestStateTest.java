package io.github.ningmeng123.mite.rules;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Input sequences and deadline boundaries extracted from R196 atv/bdc. @author NingMeng123 */
class AutoHarvestStateTest {
    @Test void pressingBothWithoutAnEarlierAttackOnlySampleDoesNotStartMining() {
        var state = state();
        state.update(true, true, "stone", 10L);
        assertFalse(state.isActive());
        assertFalse(state.isArmed());
        assertFalse(state.wantsContinue("stone", 11L));
    }

    @Test void attackThenUseCapturesTheBlockWithoutRequiringAnItem() {
        var state = state();
        state.update(true, false, "stone", 10L);
        assertTrue(state.isArmed());
        assertFalse(state.isActive());
        state.update(true, true, "stone", 11L);
        assertTrue(state.isActive());
        assertTrue(state.wantsContinue("stone", 12L));
    }

    @Test void releasingUseWhileStillHoldingAttackDoesNotMistakeItForACancelClick() {
        var state = latched("stone", 100L);
        state.update(true, false, "stone", 101L);
        state.update(true, false, "stone", 102L);
        assertTrue(state.isActive());
        state.update(false, false, "stone", 103L);
        assertTrue(state.wantsContinue("stone", 104L));
    }

    @Test void releasingBothKeepsMiningOnlyTheCapturedBlockType() {
        var state = latched("stone", 100L);
        state.update(false, false, "stone", 101L);
        assertTrue(state.wantsContinue("stone", 102L));
        assertFalse(state.wantsContinue("log", 103L));
        assertFalse(state.wantsContinue(null, 104L));
        assertTrue(state.isActive());
        assertTrue(state.wantsContinue("stone", 105L));
    }

    @Test void theNextAttackAfterReleaseCancelsAutomaticMiningAndPreparesANewChord() {
        var state = latched("stone", 100L);
        state.update(false, false, "stone", 101L);
        state.update(true, false, "log", 102L);
        assertFalse(state.isActive());
        assertTrue(state.isArmed());
        assertFalse(state.wantsContinue("stone", 103L));
        state.update(true, true, "log", 104L);
        assertTrue(state.wantsContinue("log", 105L));
        assertFalse(state.wantsContinue("stone", 106L));
    }

    @Test void useAloneClearsTheTargetAndThePreparation() {
        var state = latched("stone", 100L);
        state.update(false, true, "stone", 101L);
        assertFalse(state.isActive());
        assertFalse(state.isArmed());
        state.update(true, true, "stone", 102L);
        assertFalse(state.isActive());
    }

    @Test void releasingAttackFirstWhileUseIsStillHeldCancelsAsInTheSource() {
        var state = latched("stone", 100L);
        state.update(false, true, "stone", 101L);
        state.update(false, false, "stone", 102L);
        assertFalse(state.wantsContinue("stone", 103L));
    }

    @Test void fiveSecondsIsInclusiveButTheNextMillisecondExpires() {
        var atBoundary = latched("stone", 100L);
        atBoundary.update(false, false, null, 5_100L);
        assertTrue(atBoundary.isActive());
        assertTrue(atBoundary.wantsContinue("stone", 5_100L));

        var expired = latched("stone", 100L);
        assertFalse(expired.wantsContinue("stone", 5_101L));
        assertFalse(expired.isActive());
        assertFalse(expired.isArmed());
    }

    @Test void matchingBlocksRefreshTheDeadlineAcrossManyFiveSecondIntervals() {
        var state = latched("stone", 100L);
        state.update(false, false, "stone", 101L);
        for (long now : new long[]{5_100L, 10_100L, 15_100L}) {
            assertTrue(state.wantsContinue("stone", now));
        }
        assertFalse(state.wantsContinue("stone", 20_101L));
    }

    @Test void lookingAwayPausesWithoutExtendingTheDeadline() {
        var state = latched("stone", 100L);
        assertFalse(state.wantsContinue("log", 4_000L));
        assertFalse(state.wantsContinue(null, 5_100L));
        assertTrue(state.isActive());
        state.update(false, false, "stone", 5_101L);
        assertFalse(state.isActive());
    }

    @Test void holdingTheChordRecapturesTheLatestBlockTypeAndDeadline() {
        var state = latched("stone", 100L);
        state.update(true, true, "log", 2_000L);
        state.update(false, false, "log", 2_001L);
        assertFalse(state.wantsContinue("stone", 6_900L));
        assertTrue(state.wantsContinue("log", 7_000L));
    }

    @Test void noLookedAtBlockCannotLatchOrReplaceTheExistingTarget() {
        var state = state();
        state.update(true, false, null, 10L);
        state.update(true, true, null, 11L);
        assertFalse(state.isActive());
        state.update(true, true, "stone", 12L);
        state.update(true, true, null, 13L);
        assertTrue(state.wantsContinue("stone", 14L));
    }

    @Test void theCallerSuppliesAsymmetricSourceBlockMatching() {
        var state = new AutoHarvestState<String>((captured, current) ->
            captured.equals(current) || captured.equals("lit_redstone") && current.equals("redstone"));
        state.update(true, false, "lit_redstone", 10L);
        state.update(true, true, "lit_redstone", 11L);
        state.update(false, false, "redstone", 12L);
        assertTrue(state.wantsContinue("redstone", 13L));
        state.clear();
        state.update(true, false, "redstone", 14L);
        state.update(true, true, "redstone", 15L);
        assertFalse(state.wantsContinue("lit_redstone", 16L));
    }

    @Test void clearPreventsRecoveryAfterReturningFromAMenuOrDifferentWorld() {
        var state = latched("stone", 100L);
        state.clear();
        assertFalse(state.isActive());
        assertFalse(state.isArmed());
        assertFalse(state.wantsContinue("stone", 101L));
        state.update(true, true, "stone", 102L);
        assertFalse(state.isActive());
        state.update(true, false, "stone", 103L);
        state.update(true, true, "stone", 104L);
        assertTrue(state.wantsContinue("stone", 105L));
    }

    @Test void updateExpiresABlockBeforeAnUnpreparedChordCanRecaptureIt() {
        var state = latched("stone", 100L);
        state.update(true, true, "log", 5_101L);
        assertFalse(state.isActive());
        assertFalse(state.isArmed());
        state.update(true, false, "log", 5_102L);
        assertTrue(state.isArmed());
    }

    @Test void theMatcherNeverReceivesNullOrRunsWithoutACapturedTarget() {
        var calls = new AtomicInteger();
        var state = new AutoHarvestState<String>((captured, current) -> {
            assertNotNull(captured);
            assertNotNull(current);
            calls.incrementAndGet();
            return captured.equals(current);
        });
        assertFalse(state.wantsContinue("stone", 1L));
        state.update(true, false, "stone", 2L);
        state.update(true, true, "stone", 3L);
        assertFalse(state.wantsContinue(null, 4L));
        assertTrue(state.wantsContinue("stone", 5L));
        assertEquals(1, calls.get());
    }

    @Test void aMissingMatcherIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new AutoHarvestState<String>(null));
    }

    @Test void actualHeldMiningAttemptsKeepTheLatchAliveEvenWithoutAMatchingHit() {
        var state = latched("stone", 100L);
        for (long now = 1_100L; now <= 20_100L; now += 1_000L) {
            state.update(true, false, null, now);
            state.recordAttempt(now);
            assertTrue(state.isActive());
        }
        state.update(false, false, null, 20_101L);
        assertTrue(state.wantsContinue("stone", 25_100L));
    }

    @Test void recordingAMiningAttemptCannotRestoreAClearedModeOrItsPreparation() {
        var state = latched("stone", 100L);
        state.clear();
        state.recordAttempt(10_000L);
        assertFalse(state.isActive());
        assertFalse(state.isArmed());
        assertFalse(state.wantsContinue("stone", 10_001L));
        state.update(true, true, "stone", 10_002L);
        assertFalse(state.isActive());
    }

    private static AutoHarvestState<String> state() {
        return new AutoHarvestState<>(Objects::equals);
    }

    private static AutoHarvestState<String> latched(String target, long now) {
        var state = state();
        state.update(true, false, target, now - 1L);
        state.update(true, true, target, now);
        return state;
    }
}
