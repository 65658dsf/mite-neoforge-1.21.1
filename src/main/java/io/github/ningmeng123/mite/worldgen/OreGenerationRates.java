package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.R196OreRules;
import java.util.List;
import java.util.Random;

/** Scale new vein attempts, retaining fractional rates and the default random sequence. @author NingMeng123 */
final class OreGenerationRates {
    private OreGenerationRates() {}

    record Batch(double candidates, boolean stone, boolean deepslate) {}

    /** Equal rates share the original batch; only their difference gets a substrate-restricted batch. */
    static List<Batch> batches(double baseCandidates, double stoneMultiplier, double deepslateMultiplier) {
        double shared = Math.min(stoneMultiplier, deepslateMultiplier);
        Batch common = new Batch(baseCandidates * shared, true, true);
        if (stoneMultiplier == deepslateMultiplier) return List.of(common);
        Batch extra = new Batch(baseCandidates * Math.abs(stoneMultiplier - deepslateMultiplier),
                stoneMultiplier > deepslateMultiplier, deepslateMultiplier > stoneMultiplier);
        return shared == 0D ? List.of(extra) : List.of(common, extra);
    }

    /** Integral counts consume no extra random draw; baseCount=1 also accepts actual configured counts. */
    static int scaleCandidates(int baseCount, double multiplier, Random random) {
        double scaled = baseCount * multiplier;
        int whole = (int)scaled;
        double remainder = scaled - whole;
        return whole + (remainder > 0.0D && random.nextDouble() < remainder ? 1 : 0);
    }

    /** Preserve the generator's original integer roll at its default probability, including its seed sequence. */
    static boolean passesChance(double chance, R196OreRules.Dimension dimension, Random random) {
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D)
            throw new IllegalArgumentException("Candidate pass chance must be a finite fraction from 0 to 1");
        if (chance == 0D) return false;
        if (chance == 1D) return true;
        if (chance == R196OreRules.candidateChance(dimension))
            return random.nextInt(R196OreRules.candidateRarity(dimension)) == 0;
        return random.nextDouble() < chance;
    }
}
