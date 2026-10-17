package io.github.ningmeng123.mite.rules;

import java.util.function.DoubleSupplier;

/** Source acq temperatures and yc/ye's independent per-item melting rolls. @author NingMeng123 */
public final class SourceIceMeltingRules {
    private SourceIceMeltingRules() {}

    public static float temperature(int sourceBiome) {
        return switch (sourceBiome) {
            case 1, 6, 25 -> .8F;
            case 2, 17 -> 1.6F;
            case 3, 20 -> .4F;
            case 4, 18 -> .7F;
            case 5, 19 -> .05F;
            case 8 -> 2F;
            case 10, 11, 12, 13 -> 0F;
            case 16, 24, 26 -> 1F;
            case 21, 22 -> 1.2F;
            case 23 -> 1.4F;
            default -> .5F; // Ocean, River and Sky keep acq's default.
        };
    }

    public static float chance(float temperature) {
        return Float.isFinite(temperature) ? Math.max(0F, (temperature - 1F) * .0001F) : 0F;
    }

    public static int remaining(int count, float chance, DoubleSupplier draw) {
        if (count <= 0 || !(chance > 0F)) return Math.max(count, 0);
        int melted = 0;
        for (int i = 0; i < count; i++) if (draw.getAsDouble() < chance) melted++;
        return count - melted;
    }
}
