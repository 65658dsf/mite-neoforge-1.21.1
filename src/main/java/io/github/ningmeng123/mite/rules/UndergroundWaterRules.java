package io.github.ningmeng123.mite.rules;

/** Coherent underground-water regions and the requested sixty-percent suppression. @author NingMeng123 */
public final class UndergroundWaterRules {
    public static final int HORIZONTAL_REGION_SIZE = 64;
    public static final int VERTICAL_REGION_SIZE = 40;
    public static final String RANDOM_SALT = "mite:underground_water_regions";

    private UndergroundWaterRules() {}

    public static int horizontalRegion(int coordinate) {
        return Math.floorDiv(coordinate, HORIZONTAL_REGION_SIZE);
    }

    public static int verticalRegion(int coordinate) {
        return Math.floorDiv(coordinate, VERTICAL_REGION_SIZE);
    }

    public static boolean suppress(long regionalSample) {
        return Long.remainderUnsigned(regionalSample, 10L) < 6L;
    }
}
