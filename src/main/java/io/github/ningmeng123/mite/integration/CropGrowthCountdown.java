package io.github.ningmeng123.mite.integration;

/** Read-only stochastic estimates and interpolation of an actual server growth clock. @author NingMeng123 */
public final class CropGrowthCountdown {
    private CropGrowthCountdown() {}

    /** Expected active-growth ticks; daylight and other pauses are handled by the observation clock. */
    public static long expectedTicks(int remainingStages, double chancePerRandomTick, int randomTickSpeed) {
        if (remainingStages < 0) return -1L;
        if (remainingStages == 0) return 0L;
        if (!Double.isFinite(chancePerRandomTick) || chancePerRandomTick <= 0D
                || chancePerRandomTick > 1D || randomTickSpeed <= 0) return -1L;
        // A saturated long still describes an extremely slow valid rate without wrapping negative.
        return (long)Math.ceil(remainingStages * 4096D / (chancePerRandomTick * randomTickSpeed));
    }

    /** Only deterministic clocks have elapsed time to subtract; stochastic expectations do not. */
    public static long remainingAtSample(long remaining, boolean running, long sampledGameTime, long gameTime) {
        if (remaining < 0) return -1;
        if (!running || gameTime <= sampledGameTime) return remaining;
        long elapsed = gameTime - sampledGameTime;
        return elapsed < 0 || elapsed >= remaining ? 0 : remaining - elapsed;
    }
}
