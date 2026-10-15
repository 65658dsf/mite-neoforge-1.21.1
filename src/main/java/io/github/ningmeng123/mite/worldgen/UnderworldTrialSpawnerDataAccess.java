package io.github.ningmeng123.mite.worldgen;

/** Narrow access to normalize only future mob data without resetting a running trial. @author NingMeng123 */
public interface UnderworldTrialSpawnerDataAccess {
    boolean mite$normalizeSpawnData();
    void mite$clearDisplay();
}
