package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static io.github.ningmeng123.mite.rules.StructureDistributionRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures independently transcribed from ahq/agg/ahh/aiw/acv bytecode, including source quirks. @author NingMeng123 */
class StructureDistributionRulesTest {
    @ParameterizedTest
    @CsvSource({"0,-91,-758,889,380,-738,553", "1,-102,-844,686,293,-648,486",
            "196,-58,-672,774,362,-782,546", "-1,-77,642,-784,-587,784,-336",
            "123456789,-449,-750,840,-14,-533,959", "9223372036854775807,-77,642,-784,-587,784,-336"})
    void sourceThreeStrongholdFixturesUseTheLegacyRadiusNotTheModernRingFormula(long seed,
            int x1, int z1, int x2, int z2, int x3, int z3) {
        assertEquals(List.of(new ChunkPosition(x1, z1), new ChunkPosition(x2, z2), new ChunkPosition(x3, z3)),
                strongholdCandidates(seed));
    }

    @Test void sourceRadiiStayInTheOriginalTenToEighteenKilometerBand() {
        for (long seed = 0; seed < 1000; seed++) {
            var positions = strongholdCandidates(seed);
            assertEquals(3, positions.size());
            for (var position : positions) {
                double radius = Math.hypot(position.x(), position.z());
                assertTrue(radius >= 639.2 && radius <= 1152.8, "512*(1.25..2.25), with integer rounding");
            }
        }
    }

    @Test void biomeRelocationConsumesTheParentStreamBeforeTheNextRadius() {
        List<String> order = new ArrayList<>();
        AtomicInteger doubles = new AtomicInteger();
        strongholdCandidates(() -> { order.add("double" + doubles.getAndIncrement()); return .25; }, position -> {
            order.add("biome");
            return position;
        });
        assertEquals(List.of("double0", "double1", "biome", "double2", "biome", "double3", "biome"), order);

        Random random = new Random(196), reference = new Random(196);
        var actual = strongholdCandidates(random::nextDouble, position -> {
            random.nextInt(37);
            return position;
        });
        reference.nextDouble(); reference.nextDouble(); reference.nextInt(37);
        double secondRadius = (1.25 + reference.nextDouble()) * 512;
        assertEquals(Math.hypot(actual.get(1).x(), actual.get(1).z()), secondRadius, .8);
    }

    @Test void noPreferredBiomeLeavesTheSourceCandidateAndConsumesNoSelectionRandom() {
        var original = new ChunkPosition(-91, -758);
        AtomicInteger visited = new AtomicInteger();
        assertSame(original, relocateStrongholdBiome(original, ignored -> { visited.incrementAndGet(); return false; },
                ignored -> { throw new AssertionError("No matching biome must not consume random"); }));
        assertEquals(57 * 57, visited.get());
    }

    @Test void sourceBiomeSelectionCounterOnlyAdvancesWhenTheSelectedPointChanges() {
        List<Integer> bounds = new ArrayList<>();
        var selected = relocateStrongholdBiome(new ChunkPosition(0, 0), ignored -> true, bound -> {
            bounds.add(bound);
            return 1;
        });
        assertEquals(new ChunkPosition(-7, -7), selected, "First quart coordinate is (-104 >> 2), then converted to a chunk");
        assertEquals(57 * 57 - 1, bounds.size());
        assertTrue(bounds.stream().allMatch(bound -> bound == 2), "acv increments var14 only when a replacement is selected");
    }

    @Test void sourceBiomeScanIsXThenZAndIncludesTheFull112BlockBoundary() {
        List<QuartPosition> visited = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        var selected = relocateStrongholdBiome(new ChunkPosition(-1, 2), position -> {
            visited.add(position);
            return true;
        }, bound -> {
            assertEquals(calls.incrementAndGet() + 1, bound);
            return 0;
        });
        assertEquals(new QuartPosition(-30, -18), visited.getFirst());
        assertEquals(new QuartPosition(-29, -18), visited.get(1));
        assertEquals(new QuartPosition(-30, -17), visited.get(57));
        assertEquals(new QuartPosition(26, 38), visited.getLast());
        assertEquals(new ChunkPosition(6, 9), selected);
        assertEquals(57 * 57 - 1, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,false", "1,0,0,true", "1,0,1,false", "-1,0,0,true", "0,-40,39,true",
            "0,-40,40,false", "79,0,78,true", "79,0,79,false", "80,0,79,true", "-80,0,79,true",
            "0,80,79,true", "2147483647,0,79,true", "-2147483648,0,79,false"})
    void originalMineshaftDistanceUsesChebyshevChunkDistanceAndStrictComparison(int x, int z, int draw, boolean expected) {
        assertEquals(expected, mineshaftDistanceAllows(x, z, draw));
    }

    @Test void invalidMineshaftDrawsCannotSilentlyChangeTheProbability() {
        assertThrows(IllegalArgumentException.class, () -> mineshaftDistanceAllows(10, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> mineshaftDistanceAllows(10, 10, 80));
        assertEquals(.005F, MINESHAFT_FREQUENCY);
    }

    @ParameterizedTest
    @CsvSource({"0,-100,-42,true", "1,-100,-38,true", "196,-100,13,true", "196,-99,-22,true",
            "-1,-100,-2,true", "123456789,-98,7,true", "196,1,1,false", "196,0,0,false", "0,80,0,false"})
    void mineshaftSeedAndDiscardFixturesMatchTheInheritedOriginalCandidatePipeline(long seed, int x, int z,
            boolean expected) {
        assertEquals(expected, mineshaftCandidate(seed, x, z, true));
        assertFalse(mineshaftCandidate(seed, x, z, false), "The candidate probability never overrides the hill requirement");
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,10387312,11,8", "0,-1,-1,10387312,-35,-30",
            "196,40,-40,14357617,56,-30", "123456789,79,80,14357617,40,86"})
    void scatterAndVillageCoordinatesPreserveSaltAndNegativeRegionSemantics(long seed, int x, int z, int salt,
            int expectedX, int expectedZ) {
        assertEquals(new ChunkPosition(expectedX, expectedZ), randomSpreadCandidate(seed, x, z, salt));
    }
}
