package io.github.ningmeng123.mite.rules;

/** Estimates the time spent walking and completing the existing staged excavation. @author NingMeng123 */
public final class ZombieRouteRules {
    private ZombieRouteRules() {}

    public static double walkingTicks(double length, double movementPerTick, double rise, double waterLength, double malus) {
        if (!Double.isFinite(length) || length < 0D || !Double.isFinite(movementPerTick) || movementPerTick <= 0D)
            return Double.POSITIVE_INFINITY;
        return (length + Math.max(0D, waterLength) * 1.8D + Math.max(0D, malus) * .5D) / movementPerTick
                + Math.max(0D, rise) * 4D;
    }

    public static long remainingBreakTicks(int cooldown, int progress, int stageDelay) {
        return Math.max(1, cooldown) + (long)Math.max(0, 9 - Math.clamp(progress, -1, 9)) * Math.max(1, stageDelay);
    }

    /** Equal estimates favour the route that leaves the terrain intact. */
    public static boolean preferBreaking(double walkingTicks, double breakingTicks) {
        return Double.isFinite(breakingTicks) && breakingTicks >= 0D && breakingTicks + 2D < walkingTicks;
    }
}
