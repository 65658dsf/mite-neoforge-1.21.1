package io.github.ningmeng123.mite.rules;

/** Extracted anr/BlockGrowingPlant arithmetic; independent from engine state and random sources. @author NingMeng123 */
public final class CropGrowthRules {
    private CropGrowthRules() {}
    public static float temperatureModifier(float temperature,float low,float high) {
        return temperature<low?Math.max(1F-(low-temperature),0F):temperature>high?Math.max(1F-(temperature-high),0F):1F;
    }
    public static float blightChance(float temperature,boolean humid,int light,boolean potato) {
        return (potato?.001F:.0005F)*Math.max(temperatureModifier(temperature,1F,1.2F)*(humid?1.5F:1F),0F)*(1F-light/16F);
    }
    public static int growthDenominator(float rate) { return (int)(25F/rate)+1; }
    public static float harvestingBonus(int level) { return Math.clamp(level,0,5)/5F*.5F; }
    public static float fertilityChance(int level) { return Math.clamp(level,0,5)/5F; }
}
