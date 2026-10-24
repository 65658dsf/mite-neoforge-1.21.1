package io.github.ningmeng123.mite.rules;

/** Requested ordinary-cage lifetime and real server-tick cooldown. @author NingMeng123 */
public final class SpawnerBalanceRules {
    public static final int SPAWN_LIMIT = 15;
    public static final int INTERVAL_TICKS = 9 * 20;
    public static final String SPAWNED_TAG = "mite:successful_spawns";

    private SpawnerBalanceRules() {}

    public static int count(int saved) { return Math.clamp(saved, 0, SPAWN_LIMIT); }
    public static boolean exhausted(int count) { return count >= SPAWN_LIMIT; }
}
