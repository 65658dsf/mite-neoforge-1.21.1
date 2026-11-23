package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static io.github.ningmeng123.mite.rules.FlintGravelRules.Drop.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact enumeration of variable-length RNG paths, with R196 aoo expectations. @author NingMeng123 */
class MineralGravelRulesTest {
    private static final int[] SOURCE_DENOMINATORS = {12, 10, 8, 6};

    @Test
    void completeOrdinaryTreeMatchesIndependentSourceFractionsAtEveryFortune() {
        for (int fortune = 0; fortune <= 3; fortune++) {
            int d = SOURCE_DENOMINATORS[fortune];
            var expected = emptyDistribution();
            expected.put(GRAVEL, Fraction.of(d - 3, d));
            expected.put(FLINT_CHIP, Fraction.of(15, 8L * d));
            expected.put(FLINT, Fraction.of(1, 8L * d));
            expected.put(COPPER_NUGGET, Fraction.of(2, 3L * d));
            expected.put(SILVER_NUGGET, Fraction.of(2, 9L * d));
            expected.put(GOLD_NUGGET, Fraction.of(2, 27L * d));
            expected.put(OBSIDIAN_SHARD, Fraction.of(2, 81L * d));
            expected.put(EMERALD_SHARD, Fraction.of(2, 243L * d));
            expected.put(DIAMOND_SHARD, Fraction.of(2, 729L * d));
            expected.put(MITHRIL_NUGGET, Fraction.of(2, 2187L * d));
            expected.put(ADAMANTIUM_NUGGET, Fraction.of(1, 2187L * d));
            assertDistribution(expected, distribution(fortune, false), "ordinary fortune " + fortune);
        }
    }

    @Test
    void completeNetherTreeHasOnlyGoldAndQuartzMineralOutputs() {
        for (int fortune = 0; fortune <= 3; fortune++) {
            int d = SOURCE_DENOMINATORS[fortune];
            var expected = emptyDistribution();
            expected.put(GRAVEL, Fraction.of(d - 3, d));
            expected.put(FLINT_CHIP, Fraction.of(15, 8L * d));
            expected.put(FLINT, Fraction.of(1, 8L * d));
            // Independent sums of aoo's converted metal and stone leaves, respectively.
            expected.put(GOLD_NUGGET, Fraction.of(703, 729L * d));
            expected.put(QUARTZ_SHARD, Fraction.of(26, 729L * d));
            assertDistribution(expected, distribution(fortune, true), "nether fortune " + fortune);
        }
    }

    @Test
    void displayedDefaultPercentagesMatchTheUnenchantedDropTree() {
        for (boolean nether : new boolean[]{false, true}) {
            var rates = FlintGravelRules.defaultRates(nether);
            distribution(0, nether).forEach((drop, fraction) -> {
                double percent = 100D * fraction.numerator().doubleValue() / fraction.denominator().doubleValue();
                assertEquals(percent, rates.getOrDefault(drop, 0D), 1E-12D, drop.name());
            });
        }
    }

    @Test
    void customRatesKeepProductProportionsAndZeroPercentOutcomes() {
        var rates = new EnumMap<>(Map.of(GRAVEL, 75D, FLINT, 5D, COPPER_NUGGET, 20D, FLINT_CHIP, 0D));
        assertEquals(FLINT, FlintGravelRules.rollConfigured(rates, 0, 0, () -> .04D));
        assertEquals(FLINT, FlintGravelRules.rollConfigured(rates, 3, 0, () -> .08D));
        assertEquals(COPPER_NUGGET, FlintGravelRules.rollConfigured(rates, 0, 0, () -> .06D));
        assertEquals(COPPER_NUGGET, FlintGravelRules.rollConfigured(rates, 3, 0, () -> .12D));
        assertEquals(GRAVEL, FlintGravelRules.rollConfigured(rates, 0, 0, () -> .30D));
        assertEquals(COPPER_NUGGET, FlintGravelRules.rollConfigured(rates, 3, 0, () -> .30D));
        assertEquals(FLINT, FlintGravelRules.rollConfigured(Map.of(GRAVEL, 0D, FLINT, 99.9999999D),
                0, 0, () -> .9999999999D));
        assertEquals(GRAVEL, FlintGravelRules.rollConfigured(Map.of(GRAVEL, 100D, FLINT, 0D), 3, 4, () -> 0D));
    }

    @Test
    void bothGravelsClampExtremeFortunesWithoutChangingTheirFullDistribution() {
        for (boolean nether : new boolean[]{false, true}) {
            var zero = distribution(0, nether);
            var three = distribution(3, nether);
            for (int fortune : new int[]{Integer.MIN_VALUE, -1}) {
                assertEquals(zero, distribution(fortune, nether), "fortune " + fortune);
            }
            for (int fortune : new int[]{4, Integer.MAX_VALUE}) {
                assertEquals(three, distribution(fortune, nether), "fortune " + fortune);
            }
        }
    }

    @Test
    void originalOverloadPreservesEveryOrdinaryDrawAndOutcome() {
        for (int fortune = 0; fortune <= 3; fortune++) {
            int level = fortune;
            enumerate(rng -> FlintGravelRules.roll(level, false, rng), new int[0], Fraction.ONE,
                    emptyDistribution(), (choices, expected) -> {
                        int[] index = {0};
                        var actual = FlintGravelRules.roll(level, bound -> choices[index[0]++]);
                        assertEquals(expected, actual);
                        assertEquals(choices.length, index[0], "legacy overload consumed a different path");
                    });
        }
    }

    private static Map<FlintGravelRules.Drop, Fraction> distribution(int fortune, boolean nether) {
        var result = emptyDistribution();
        enumerate(rng -> FlintGravelRules.roll(fortune, nether, rng), new int[0], Fraction.ONE,
                result, (choices, drop) -> {});
        assertEquals(Fraction.ONE, result.values().stream().reduce(Fraction.ZERO, Fraction::plus),
                "every RNG path must terminate in exactly one drop");
        return result;
    }

    private static void assertDistribution(Map<FlintGravelRules.Drop, Fraction> expected,
                                           Map<FlintGravelRules.Drop, Fraction> actual, String context) {
        for (var drop : FlintGravelRules.Drop.values()) {
            assertEquals(expected.get(drop), actual.get(drop), context + ": " + drop);
        }
    }

    private static EnumMap<FlintGravelRules.Drop, Fraction> emptyDistribution() {
        var result = new EnumMap<FlintGravelRules.Drop, Fraction>(FlintGravelRules.Drop.class);
        for (var drop : FlintGravelRules.Drop.values()) result.put(drop, Fraction.ZERO);
        return result;
    }

    /** Discover the next requested bound instead of padding shorter branches with fictitious draws. */
    private static void enumerate(Roller roller, int[] prefix, Fraction probability,
                                  Map<FlintGravelRules.Drop, Fraction> result, LeafVisitor visitor) {
        int[] index = {0};
        try {
            var drop = roller.roll(bound -> {
                assertTrue(bound > 0 && bound <= 16, "unexpected RNG bound " + bound);
                if (index[0] == prefix.length) throw new NextChoice(bound);
                int choice = prefix[index[0]++];
                assertTrue(choice >= 0 && choice < bound, "prefix is outside the requested bound");
                return choice;
            });
            assertEquals(prefix.length, index[0], "unused prefix choices");
            result.put(drop, result.get(drop).plus(probability));
            visitor.visit(prefix, drop);
        } catch (NextChoice next) {
            assertTrue(prefix.length < 9, "R196's longest path has nine RNG draws");
            int[] extended = Arrays.copyOf(prefix, prefix.length + 1);
            Fraction branchProbability = probability.dividedBy(next.bound);
            for (int choice = 0; choice < next.bound; choice++) {
                extended[prefix.length] = choice;
                enumerate(roller, extended, branchProbability, result, visitor);
            }
        }
    }

    @FunctionalInterface
    private interface Roller {
        FlintGravelRules.Drop roll(IntUnaryOperator random);
    }

    @FunctionalInterface
    private interface LeafVisitor {
        void visit(int[] choices, FlintGravelRules.Drop drop);
    }

    private static final class NextChoice extends RuntimeException {
        private final int bound;

        private NextChoice(int bound) {
            super(null, null, false, false);
            this.bound = bound;
        }
    }

    /** Reduced fractions avoid rounding tolerances even for the rarest adamantium leaf. */
    private record Fraction(BigInteger numerator, BigInteger denominator) {
        private static final Fraction ZERO = of(0, 1);
        private static final Fraction ONE = of(1, 1);

        private Fraction {
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }

        private static Fraction of(long numerator, long denominator) {
            return new Fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        private Fraction dividedBy(int divisor) {
            return new Fraction(numerator, denominator.multiply(BigInteger.valueOf(divisor)));
        }

        private Fraction plus(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
    }
}
