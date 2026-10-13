package io.github.ningmeng123.mite.rules;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static io.github.ningmeng123.mite.rules.R196OreRules.Dimension.*;
import static io.github.ningmeng123.mite.rules.R196OreRules.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independently read acu.java/afq.java boundaries and the explicit modern height adaptation. @author NingMeng123 */
class R196OreRulesTest {
    @ParameterizedTest
    @CsvSource({"DIRT,200,32,128,32", "GRAVEL,200,24,128,32", "COAL,50,16,96,16", "COPPER,40,0,128,6",
            "SILVER,10,0,96,6", "GOLD,20,0,48,4", "IRON,60,0,64,6", "MITHRIL,10,0,32,3",
            "INFESTED,5,0,24,3", "REDSTONE,10,0,24,5", "DIAMOND,5,0,32,3", "LAPIS,5,8,40,3"})
    void originalOverworldCandidateTableIsIndependentOfSuccessfulGrowth(R196OreRules.Kind kind, int candidates, int min, int max, int size) {
        assertEquals(candidates, R196OreRules.candidateCount(kind, OVERWORLD));
        assertEquals(min, R196OreRules.minHeight(kind, OVERWORLD));
        assertEquals(max, R196OreRules.maxHeight(kind, OVERWORLD));
        assertEquals(size, R196OreRules.baseSize(kind));
    }

    @ParameterizedTest
    @CsvSource({"GRAVEL,300", "COPPER,320", "SILVER,80", "GOLD,160", "IRON,480", "MITHRIL,80",
            "ADAMANTIUM,80", "REDSTONE,80", "DIAMOND,40", "LAPIS,40", "INFESTED,400"})
    void underworldMultiplierAppliesToCandidatesWithoutOffsettingEveryOre(R196OreRules.Kind kind, int candidates) {
        assertEquals(candidates, R196OreRules.candidateCount(kind, UNDERWORLD));
        assertEquals(0, R196OreRules.minHeight(kind, UNDERWORLD));
        assertEquals(255, R196OreRules.maxHeight(kind, UNDERWORLD));
    }

    @Test void sourceDimensionListsDoNotInventOverworldAdamantiumOrUnderworldCoal() {
        assertFalse(R196OreRules.OVERWORLD.contains(ADAMANTIUM));
        assertEquals(0, R196OreRules.candidateCount(ADAMANTIUM, OVERWORLD));
        assertFalse(R196OreRules.UNDERWORLD.contains(COAL));
        assertEquals(0, R196OreRules.candidateCount(COAL, UNDERWORLD));
        assertEquals(16, R196OreRules.candidateCount(QUARTZ, NETHER));
        assertEquals(2, R196OreRules.candidateCount(NETHER_GOLD, NETHER));
    }

    @ParameterizedTest
    @CsvSource({"OVERWORLD,0,-64", "OVERWORLD,16,-32", "OVERWORLD,32,0", "OVERWORLD,64,64", "OVERWORLD,65,65", "OVERWORLD,128,128",
            "UNDERWORLD,0,-64", "UNDERWORLD,1,-62", "UNDERWORLD,2,-61", "UNDERWORLD,3,-59", "UNDERWORLD,120,116",
            "UNDERWORLD,136,140", "UNDERWORLD,255,319", "UNDERWORLD,256,320", "NETHER,10,10", "NETHER,117,117"})
    void modernHeightMappingIsExplicitAndDimensionSpecific(R196OreRules.Dimension dimension, int legacyY, int expected) {
        assertEquals(expected, R196OreRules.modernY(dimension, legacyY));
    }

    @Test void redstoneIsNotBlockOreAndRetainsTheUniformUnderworldRandomStream() {
        // aqc extends aqz; the afq "instanceof apr" 75% lower-height branch does not include it.
        for (long seed : new long[]{0, 1, 196, -1, 123456789}) {
            Random original = new Random(seed), port = new Random(seed);
            for (int sample = 0; sample < 100; sample++) {
                assertEquals(original.nextInt(256), R196OreRules.randomHeight(REDSTONE, UNDERWORLD, port));
            }
            assertEquals(original.nextLong(), port.nextLong(), "The same original random stream must remain after the batch");
        }
    }

    @Test void underworldAdamantiumAlwaysUsesTheOriginalOffsetLimitedRange() {
        for (long seed : new long[]{0, 1, 196, -1}) {
            Random original = new Random(seed), port = new Random(seed);
            for (int sample = 0; sample < 128; sample++)
                assertEquals(original.nextInt(136), R196OreRules.randomHeight(ADAMANTIUM, UNDERWORLD, port));
            assertEquals(original.nextLong(), port.nextLong());
        }
    }

    @Test void emptyOrProtectedAnchorsNeverConsumeGrowthRandomValues() {
        Growth blocked = new Growth(pos -> false, pos -> true);
        assertEquals(0, R196OreRules.growVein(blocked, 8, 0, 0, 0, false, noRandom()));
        assertEquals(0, R196OreRules.generateVein(COPPER, OVERWORLD, blocked, 0, 0, 0, 0, true, noRandom()));
        Growth permitted = new Growth(pos -> true, pos -> true);
        assertEquals(0, R196OreRules.growVein(permitted, 0, 0, 0, 0, false, noRandom()));
        assertTrue(permitted.replaced.isEmpty());
    }

    @Test void supportedGravelCannotStartOverAnUnsupportedVoid() {
        Growth unsupported = new Growth(pos -> true, pos -> false);
        assertEquals(0, R196OreRules.growVein(unsupported, 8, 0, 0, 0, true, noRandom()));
        assertTrue(unsupported.replaced.isEmpty());
    }

    @Test void recursiveSourceGrowthRespectsBudgetAndFormsASingleFaceConnectedVein() {
        Growth growth = new Growth(pos -> Math.abs(pos.x()) <= 4 && Math.abs(pos.y()) <= 4 && Math.abs(pos.z()) <= 4,
                pos -> true);
        int count = R196OreRules.growVein(growth, 32, 0, 0, 0, false, new Random(196));
        assertEquals(growth.replaced.size(), count);
        assertTrue(count > 0 && count <= 32);
        Set<Pos> reached = new HashSet<>();
        reached.add(new Pos(0, 0, 0));
        boolean changed;
        do {
            changed = false;
            for (Pos p : growth.replaced) {
                if (reached.contains(p)) continue;
                if (reached.stream().anyMatch(other -> Math.abs(p.x() - other.x()) + Math.abs(p.y() - other.y()) + Math.abs(p.z() - other.z()) == 1)) {
                    reached.add(p); changed = true;
                }
            }
        } while (changed);
        assertEquals(growth.replaced, reached);
    }

    private static Random noRandom() {
        return new Random(0) {
            @Override public int nextInt(int bound) { return fail("Rejected anchors may not consume growth RNG"); }
            @Override public float nextFloat() { return fail("Rejected anchors may not sample a vein scale"); }
        };
    }
    private record Pos(int x, int y, int z) {}
    private static final class Growth implements R196OreRules.VeinAccess {
        final Set<Pos> replaced = new HashSet<>();
        private final Predicate<Pos> eligible, supported;
        Growth(Predicate<Pos> eligible, Predicate<Pos> supported) { this.eligible = eligible; this.supported = supported; }
        @Override public boolean canReplace(int x, int y, int z) { Pos p = new Pos(x, y, z); return eligible.test(p) && !replaced.contains(p); }
        @Override public boolean supported(int x, int y, int z) { return supported.test(new Pos(x, y, z)); }
        @Override public void replace(int x, int y, int z) { assertTrue(replaced.add(new Pos(x, y, z)), "A vein cannot spend two units on the same block"); }
    }
}
