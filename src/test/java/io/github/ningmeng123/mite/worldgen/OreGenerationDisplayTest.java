package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OreGenerationDisplayTest {
    @Test void sourceProfilesPreserveProbabilityIncludingUnsampledModernLayers() {
        for (var dimension : new Dimension[]{Dimension.OVERWORLD, Dimension.UNDERWORLD}) {
            for (var kind : R196OreRules.generationKinds(dimension)) {
                var bounds = OreHeightRange.defaults(kind, dimension);
                var weights = OreGenerationDisplay.sampleOrigins(kind, dimension, bounds);
                assertEquals(1D, IntStream.rangeClosed(bounds.minimum(), bounds.maximum()).mapToDouble(weights).sum());
                if (dimension == Dimension.OVERWORLD && bounds.minimum() < 0)
                    assertEquals(0D, weights.applyAsDouble(bounds.minimum() + 1));
            }
        }
    }

    @Test void debrisCombinesBothNativeFeaturesAtEqualAttemptRates() {
        assertEquals(1D, IntStream.rangeClosed(0, 255).mapToDouble(OreGenerationDisplay::debrisWeight).sum(), 1E-12);
        assertEquals(.5D / 112, OreGenerationDisplay.debrisWeight(119));
        assertEquals(.5D * (1D / 112 + 9D / 81), OreGenerationDisplay.debrisWeight(16));
        assertEquals(0D, OreGenerationDisplay.debrisWeight(120));
    }

    @Test void densityCountsActualBlocksAndKeepsCustomVeinsInsideTheirLayer() {
        var range = new OreHeightRange(42, 42);
        var stone = new OreBlockDensity.Shape(Dimension.OVERWORLD, Kind.IRON, false, -64, 320, range, true, 6, 1);
        var deep = new OreBlockDensity.Shape(Dimension.OVERWORLD, Kind.IRON, true, -64, 320, range, true, 6, 1);
        var regular = OreBlockDensity.perAttempt(stone, y -> 1);
        assertTrue(regular.applyAsDouble(42) * 256 > 1, "A successful vein contributes more than its single origin");
        assertEquals(0D, regular.applyAsDouble(41));
        assertEquals(0D, OreBlockDensity.perAttempt(deep, y -> 1).applyAsDouble(42));
    }

    @Test void singletonDensitySharesTheStoneTransitionAndNativeRedDiamondsUseRealVeinGeometry() {
        var range = new OreHeightRange(4, 4);
        for (boolean deep : new boolean[]{true, false}) {
            var emerald = new OreBlockDensity.Shape(Dimension.OVERWORLD, Kind.EMERALD, deep, -64, 320, range, true, 1, 1);
            assertEquals(.5D / 256, OreBlockDensity.perAttempt(emerald, y -> 1).applyAsDouble(4));
        }
        var red = new OreBlockDensity.Shape(Dimension.UNDERWORLD, Kind.RED_DIAMOND, false, -64, 320,
                new OreHeightRange(150, 150), false, 3, 1);
        var density = OreBlockDensity.perAttempt(red, y -> 1);
        assertTrue(density.applyAsDouble(149) > 0, "Native ore ellipsoids also place blocks below the seed layer");
    }
}
