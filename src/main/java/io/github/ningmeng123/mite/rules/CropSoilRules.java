package io.github.ningmeng123.mite.rules;

/** Scalar rules transcribed from R196 aof.chanceOfDrying. @author NingMeng123 */
public final class CropSoilRules {
    private CropSoilRules() {}
    public static float dryingChance(float temperature, boolean highHumidity) {
        return Math.max(0F, temperature - .15F - (highHumidity ? .5F : 0F));
    }
}
