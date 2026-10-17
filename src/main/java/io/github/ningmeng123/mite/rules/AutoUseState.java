package io.github.ningmeng123.mite.rules;

import java.util.Objects;

/** The use-first R196 ingestion latch, independent of physical key state. @author NingMeng123 */
public final class AutoUseState<T> {
    private T item;
    private boolean armed;
    private boolean released;
    private T returnedItem;
    private long returnedUntil;

    public void update(boolean attack, boolean use, T eligibleItem) {
        if (item != null && eligibleItem != null && !Objects.equals(item, eligibleItem)) clear();
        if (item != null && released && attack) {
            clear();
            return;
        }
        if (attack && use) {
            if (armed && eligibleItem != null) {
                item = eligibleItem;
                armed = false;
                released = false;
                returnedItem = null;
            }
        } else if (use) {
            armed = eligibleItem != null;
        } else if (attack) {
            armed = false;
        } else {
            armed = false;
            if (item != null) released = true;
        }
    }

    public boolean matches(T current) { return item != null && Objects.equals(item, current); }
    public void finished(T consumed, T returned, long now) {
        if (matches(consumed) && !matches(returned)) { returnedItem = returned; returnedUntil = now + 3_000L; }
    }
    public boolean waitingForRestock(T current, long now) {
        return item != null && returnedItem != null && now < returnedUntil && Objects.equals(returnedItem, current);
    }
    public void resumed() { returnedItem = null; returnedUntil = 0L; }
    public boolean isActive() { return item != null; }
    public boolean isArmed() { return armed; }
    public void clear() { item = null; armed = false; released = false; returnedItem = null; returnedUntil = 0L; }
}
