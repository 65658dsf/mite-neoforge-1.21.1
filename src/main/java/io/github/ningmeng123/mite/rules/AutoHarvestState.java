package io.github.ningmeng123.mite.rules;

import java.util.Objects;
import java.util.function.BiPredicate;

/** R196's target-matched, real-time auto-harvest input state. @author NingMeng123 */
public final class AutoHarvestState<T> {
    public static final long IDLE_TIMEOUT_MILLIS = 5_000L;

    private final BiPredicate<T, T> matcher;
    private T target;
    private boolean armed;
    private boolean releasedSinceLatch;
    private long lastContinueMillis;

    public AutoHarvestState(BiPredicate<T, T> matcher) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    /**
     * Reads physical attack/use bindings before asking whether to continue mining.
     * A preceding attack-only sample arms the chord; use alone cancels it.
     */
    public void update(boolean attack, boolean use, T lookedAt, long now) {
        expire(now);
        if (attack && use) {
            if (armed && lookedAt != null) {
                target = lookedAt;
                releasedSinceLatch = false;
                lastContinueMillis = now;
            }
        } else if (attack) {
            if (target != null && releasedSinceLatch) {
                target = null;
                releasedSinceLatch = false;
                lastContinueMillis = 0L;
            }
            armed = true;
        } else if (use) {
            clear();
        } else if (target != null) {
            releasedSinceLatch = true;
        }
    }

    /** Returns true only for a matching target, refreshing the real-time idle deadline. */
    public boolean wantsContinue(T lookedAt, long now) {
        expire(now);
        if (target == null || lookedAt == null || !matcher.test(target, lookedAt)) {
            return false;
        }
        lastContinueMillis = now;
        return true;
    }

    /** Records an actual held-input mining attempt, even when it deals no block damage. */
    public void recordAttempt(long now) {
        if (target != null) {
            lastContinueMillis = now;
        }
    }

    /** Whether the most recently processed input/deadline still has a captured target. */
    public boolean isActive() {
        return target != null;
    }

    public boolean isArmed() {
        return armed;
    }

    /** Fully clears both a captured target and any preceding attack-only preparation. */
    public void clear() {
        target = null;
        armed = false;
        releasedSinceLatch = false;
        lastContinueMillis = 0L;
    }

    private void expire(long now) {
        if (target != null && now - lastContinueMillis > IDLE_TIMEOUT_MILLIS) {
            clear();
        }
    }
}
