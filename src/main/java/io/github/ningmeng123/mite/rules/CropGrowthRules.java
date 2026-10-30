package io.github.ningmeng123.mite.rules;

/** Source environment arithmetic with hourly crop pacing, independent from world and RNG state. @author NingMeng123 */
public final class CropGrowthRules {
    public static final int GROWTH_ROLL_BOUND = 1_000_000;
    public static final int TARGET_MATURITY_TICKS = 60 * 60 * 20;
    // Clear overworld daylight, including native time-angle smoothing and integer sky darkening.
    public static final double FULL_LIGHT_DAY_FRACTION = 12081D / 24000D;
    public static final float FOOD_REFERENCE_RATE = 1.25F;
    private CropGrowthRules() {}
    public static float temperatureModifier(float temperature,float low,float high) {
        return temperature<low?Math.max(1F-(low-temperature),0F):temperature>high?Math.max(1F-(temperature-high),0F):1F;
    }
    public static float blightChance(float temperature,boolean humid,int light,boolean potato) {
        return (potato?.001F:.0005F)*Math.max(temperatureModifier(temperature,1F,1.2F)*(humid?1.5F:1F),0F)*(1F-light/16F);
    }
    public static int growthDenominator(float rate) { return (int)(25F/rate)+1; }
    /** Default randomTickSpeed=3 samples each position 3/4096 times per game tick. */
    public static int growthThreshold(float rate, float referenceRate, int expectedAdvances) {
        if (!(rate > 0F) || !(referenceRate > 0F) || expectedAdvances <= 0) return 0;
        double chance = expectedAdvances * 4096D / (TARGET_MATURITY_TICKS * 3D * FULL_LIGHT_DAY_FRACTION)
                * rate / referenceRate;
        return (int)Math.round(Math.clamp(chance, 0D, 1D) * GROWTH_ROLL_BOUND);
    }
    public static double expectedMinutes(int expectedAdvances, int threshold) {
        return threshold <= 0 ? Double.POSITIVE_INFINITY : expectedAdvances * 4096D * GROWTH_ROLL_BOUND
                / (threshold * 3D * 20D * 60D * FULL_LIGHT_DAY_FRACTION);
    }
    public static float harvestingBonus(int level) { return Math.clamp(level,0,5)/5F*.5F; }
    public static float fertilityChance(int level) { return Math.clamp(level,0,5)/5F; }
}
