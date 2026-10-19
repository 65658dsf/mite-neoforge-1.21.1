package io.github.ningmeng123.mite.rules;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Arithmetic, domain and resource-limit checks for player-authored formulas. @author NingMeng123 */
class GrowthFormulaTest {
    private static final GrowthFormula.Context CONTEXT = new GrowthFormula.Context(20D, 6D, 30D, .005D, 31D, 64D, 1D);

    @Test void conventionalPrecedenceAndRightAssociativePowersArePreserved() {
        assertEquals(14D, calculate("2 + 3 * 4"));
        assertEquals(20D, calculate("(2 + 3) * 4"));
        assertEquals(512D, calculate("2 ^ 3 ^ 2"));
        assertEquals(4D, calculate("(-2) ^ 2"));
        assertEquals(.25D, calculate("2 ^ -2"));
        assertEquals(1D, calculate("5 + -2 ^ 2"));
        assertEquals(4D, calculate("10 - 3 - 3"));
        assertEquals(2D, calculate("12 / 3 / 2"));
        assertEquals(2D, calculate("17 % 5"));
    }

    @Test void contextVariablesAreIndependentAndDefaultLinearGrowthIsExecutable() {
        assertEquals(20D + 30D * .005D * 31D, calculate("base + reference * rate * days"), 1E-12);
        assertEquals(90D, calculate("base + initial + day"));
        assertEquals(1D, calculate("blood_moons"));
        assertTrue(GrowthFormula.validate("base + reference * rate * days").valid());
        assertTrue(GrowthFormula.validate("initial + (reference * (1 + rate * 32)^blood_moons - reference) + base * rate * days").valid());
    }

    @Test void boundedMathematicalFunctionAllowlistAndDecimalLiteralsWork() {
        assertEquals(6D, calculate("max(2, min(6, 8))"));
        assertEquals(3D, calculate("clamp(10, 1, 3)"));
        assertEquals(7D, calculate("abs(-3) + sqrt(16)"));
        assertEquals(8D, calculate("pow(2, 3)"));
        assertEquals(4D, calculate("floor(2.8) + ceil(1.2)"));
        assertEquals(2D, calculate("log(exp(2))"), 1E-12);
        assertEquals(101.5D, calculate("1e2 + .5 + 1."));
        assertEquals(.002D, calculate("2E-3"));
        assertEquals(20.5D, calculate(" \tbase\n + +.5 "));
    }

    @Test void syntaxUnknownNamesAndExternalExecutionCannotCompile() {
        for (String expression : new String[]{"", " ", "2 +", "(2 + 3", "2 + 3)", "2 3", "2(3)", "1..2", ".", "1e", "1e+",
                "baseHealth", "NaN", "Infinity", "BASE", "sin(1)", "min(1)", "max(1, 2, 3)", "clamp(1, 2)",
                "abs()", "pow(2,,3)", "java.lang.Runtime", "base; 2", "base = 2", "base[0]", "'20'", "2/**/3"}) {
            assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile(expression), expression);
            assertFalse(GrowthFormula.validate(expression).valid(), expression);
        }
        assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile(null));
    }

    @Test void runtimeRejectsNonfiniteIntermediateValuesAndInvalidDomainsEvenWhenWrappedInClamp() {
        for (String expression : new String[]{"1 / 0", "1 / -0", "1 % 0", "sqrt(-1)", "log(0)", "log(-1)",
                "pow(-1, .5)", "0 ^ -1", "exp(1000)", "1e308 * 100", "clamp(1 / 0, 1, 2)", "max(1, sqrt(-1))",
                "clamp(2, 4, 1)", "1 - 2"}) {
            assertThrows(IllegalArgumentException.class, () -> calculate(expression), expression);
            assertFalse(GrowthFormula.validate(expression).valid(), expression);
        }
        assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile("1e309"));
        assertEquals(2D, calculate("-3 + 5"));
    }

    @Test void validationExecutesEarlyAndLateDatesAndZeroGrowthRatherThanOnlyCheckingSyntax() {
        for (String expression : new String[]{"base / days", "base / rate", "base / (days - 31)", "base / (days - 32)",
                "base / (days - 64)", "base / (day - 1)", "base / (day - 33)", "base / (day - 129)",
                "base / (day - 1025)", "base / (reference - 1024)", "base / (base - 30)"}) {
            GrowthFormula.Validation validation = GrowthFormula.validate(expression);
            assertFalse(validation.valid(), expression);
            assertTrue(validation.message().contains("试算"), validation.message());
        }
    }

    @Test void zeroNeedsExplicitDamagePermissionAndCustomBusinessSamplesAreActuallyRun() {
        assertEquals(0D, calculate("base - base"));
        assertFalse(GrowthFormula.validate("0").valid());
        assertTrue(GrowthFormula.validate("0", List.of(CONTEXT), true).valid());
        assertFalse(GrowthFormula.validate("-1", List.of(CONTEXT), true).valid());
        assertTrue(GrowthFormula.validate("1 / (day - 1)", List.of(CONTEXT)).valid());
        GrowthFormula.Context bad = new GrowthFormula.Context(20D, 20D, 20D, .005D, 0D, 1D, 0D);
        assertFalse(GrowthFormula.validate("1 / (day - 1)", List.of(bad)).valid());
        assertFalse(GrowthFormula.validate("base", List.of()).valid());
        assertFalse(GrowthFormula.validate("base", null).valid());
    }

    @Test void allContextValuesMustBeFiniteEvenIfAnExpressionDoesNotReadThem() {
        assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile("base").evaluate(null));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            GrowthFormula.Context context = new GrowthFormula.Context(20D, 6D, 30D, invalid, 31D, 64D, 1D);
            assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile("base").evaluate(context));
            assertFalse(GrowthFormula.validate("base", List.of(context)).valid());
        }
    }

    @Test void resourceLimitsPreventUnboundedExpressionsAndPathologicalRecursion() {
        for (String expression : new String[]{" ".repeat(513), "1+".repeat(256) + "1", "(".repeat(40) + "1" + ")".repeat(40),
                "-".repeat(40) + "1", "2^".repeat(40) + "1", "1+".repeat(40) + "1", "abs(".repeat(40) + "1" + ")".repeat(40),
                "a".repeat(33), "1".repeat(65), "1+".repeat(128) + "1"}) {
            assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile(expression), expression);
        }
        assertEquals(1D, calculate("(".repeat(10) + "1" + ")".repeat(10)));
        assertEquals(11D, calculate("1+".repeat(10) + "1"));
        assertFalse(GrowthFormula.validate("1", java.util.Collections.nCopies(4097, CONTEXT)).valid());
    }

    @Test void errorsPointToTheFailingCharacterAndCompilationIsReusable() {
        IllegalArgumentException syntax = assertThrows(IllegalArgumentException.class, () -> GrowthFormula.compile("base + ?"));
        assertTrue(syntax.getMessage().contains("第 8 个字符"), syntax.getMessage());
        IllegalArgumentException division = assertThrows(IllegalArgumentException.class, () -> calculate("base / 0"));
        assertTrue(division.getMessage().contains("第 6 个字符"), division.getMessage());
        GrowthFormula.Compiled compiled = GrowthFormula.compile("base + reference * rate * days");
        assertEquals(24.65D, compiled.evaluate(CONTEXT), 1E-12);
        assertEquals(10D, compiled.evaluate(new GrowthFormula.Context(10D, 10D, 10D, 0D, 64D, 65D, 2D)));
        assertEquals(24.65D, compiled.evaluate(CONTEXT), 1E-12);
    }

    private static double calculate(String expression) { return GrowthFormula.compile(expression).evaluate(CONTEXT); }
}
