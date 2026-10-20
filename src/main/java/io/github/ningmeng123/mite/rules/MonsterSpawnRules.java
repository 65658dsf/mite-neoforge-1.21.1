package io.github.ningmeng123.mite.rules;

/** Fractional natural-spawn weights and conditional replacement arithmetic. @author NingMeng123 */
public final class MonsterSpawnRules {
    private MonsterSpawnRules() {}

    public static double familyMultiplier(double probability, double parentRate, double replacementRate) {
        check(probability, parentRate, replacementRate);
        if (parentRate == replacementRate) return parentRate;
        if (probability == 0D) return parentRate;
        if (probability == 1D) return replacementRate;
        return (1D - probability) * parentRate + probability * replacementRate;
    }

    public static double replacementProbability(double probability, double parentRate, double replacementRate) {
        double total = familyMultiplier(probability, parentRate, replacementRate);
        if (total == 0D) return 0D;
        if (parentRate == replacementRate) return probability;
        return Math.clamp(probability * replacementRate / total, 0D, 1D);
    }

    /** Exactly the joint probability of the existing nextInt(40) depth roll and 50% roll. */
    public static double infernalCreeperProbability(int sourceY) {
        return .5D * (40D - Math.clamp(sourceY, 0, 40)) / 40D;
    }

    /** A unit draw selects fractional weights without rounding or overflowing an integer sum. */
    public static int weightedIndex(double[] weights, double unitDraw) {
        if (!Double.isFinite(unitDraw) || unitDraw < 0D || unitDraw >= 1D)
            throw new IllegalArgumentException("Weighted draw must be in [0, 1)");
        double maximum = 0D;
        for (double weight : weights) {
            if (!Double.isFinite(weight) || weight < 0D) throw new IllegalArgumentException("Invalid natural-spawn weight");
            maximum = Math.max(maximum, weight);
        }
        if (maximum == 0D) return -1;
        double total = 0D;
        for (double weight : weights) total += weight / maximum;
        double draw = unitDraw * total;
        int last = -1;
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] == 0D) continue;
            last = index;
            draw -= weights[index] / maximum;
            if (draw < 0D) return index;
        }
        return last; // Floating-point rounding at the final positive interval.
    }

    private static void check(double probability, double parentRate, double replacementRate) {
        if (!Double.isFinite(probability) || probability < 0D || probability > 1D
                || !Double.isFinite(parentRate) || parentRate < 0D
                || !Double.isFinite(replacementRate) || replacementRate < 0D)
            throw new IllegalArgumentException("Invalid natural-spawn replacement rates");
    }
}
