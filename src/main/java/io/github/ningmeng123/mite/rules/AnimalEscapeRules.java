package io.github.ningmeng123.mite.rules;

/** Compares the time needed to gain distance along actual escape routes. @author NingMeng123 */
public final class AnimalEscapeRules {
    /** Maximum for a straight, level, penalty-free route of sixteen blocks. */
    public static final double MAX_SCORE = 10.4D;
    private AnimalEscapeRules() {}

    /**
     * Costs are in walking-block equivalents; earlyGain is measured over the first six blocks.
     * A short side step is useful even when a distant destination would require a long detour.
     */
    public static double score(double length, double cost, double initialDistance, double endDistance,
                               double earlyLength, double earlyGain, double minimumDistance) {
        if (!Double.isFinite(length + cost + initialDistance + endDistance + earlyLength + earlyGain + minimumDistance)
                || length < .5D || cost < length || earlyLength <= 0D
                || endDistance < initialDistance + .5D) return Double.NEGATIVE_INFINITY;
        double approach = Math.max(0D, initialDistance - minimumDistance);
        // Allow a small turn around a block, but never charge towards the attacker to reach a far endpoint.
        if (approach > Math.min(1.5D, initialDistance * .15D)) return Double.NEGATIVE_INFINITY;
        double gain = endDistance - initialDistance;
        return 4D * gain / cost + 6D * earlyGain / earlyLength
                + .04D * Math.min(gain, 16D) - .015D * cost - 2D * approach;
    }
}
