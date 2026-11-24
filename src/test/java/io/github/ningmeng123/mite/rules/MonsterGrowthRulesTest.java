package io.github.ningmeng123.mite.rules;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently tabulated real blood-moon bases and editable-formula safety. @author NingMeng123 */
class MonsterGrowthRulesTest {
    private static final MonsterGrowthRules.Settings DEFAULTS = MonsterGrowthRules.DEFAULTS;
    private static final MonsterGrowthRules.Settings LEGACY_SETTINGS = new MonsterGrowthRules.Settings(
            DEFAULTS.healthBase(), DEFAULTS.damageBase(), DEFAULTS.skeletonReference(), DEFAULTS.healthRate(),
            DEFAULTS.damageRate(), DEFAULTS.healthFormula(), DEFAULTS.damageFormula());

    @Test void growthWithoutAFloorUsesHalfPercentDailyAndRebasesToTheLastBloodMoonValue() {
        long[] days = {1L, 2L, 32L, 33L, 34L, 65L, 97L, 129L, 130L, 161L};
        double[] health = {20D, 20.1D, 23.1D, 23.2D, 23.316D, 26.912D, 31.21792D,
                36.2127872D, 36.3688768D, 41.2076544D};
        for (int index = 0; index < days.length; index++) {
            assertEquals(health[index], MonsterGrowthRules.health(days[index], 20D, false, LEGACY_SETTINGS, 1D), 1E-10);
            assertEquals(24D * health[index] / 20D, MonsterGrowthRules.damage(days[index], 24D, DEFAULTS, 1D), 1E-12);
        }
        var afterFirstMoon = MonsterGrowthRules.healthPreview(34L, 20D, false, LEGACY_SETTINGS, 1D);
        assertEquals(23.2D, afterFirstMoon.base(), 1E-12);
        assertEquals(23.2D, afterFirstMoon.reference(), 1E-12);
        assertEquals(1L, afterFirstMoon.days());
        assertEquals(1L, afterFirstMoon.bloodMoons());
        assertFalse(afterFirstMoon.fallback());
    }

    @Test void lowInnateDamageUsesTwentyUntilItReachesTheThreshold() {
        assertEquals(20D, MonsterGrowthRules.MINIMUM_DAMAGE_REFERENCE);
        long[] days = {1L, 2L, 32L, 33L, 34L, 65L, 97L, 129L, 161L, 170L, 171L, 172L};
        double[] values = {3D, 3.1D, 6.1D, 6.2D, 6.3D, 9.4D, 12.6D, 15.8D, 19D, 19.9D, 20D, 20.1D};
        for (int i = 0; i < days.length; i++)
            assertEquals(values[i], MonsterGrowthRules.damage(days[i], 3D, DEFAULTS, 1D), 1E-12);
        var settlement = MonsterGrowthRules.damagePreview(33L, 3D, DEFAULTS, 1D);
        assertEquals(6.2D, settlement.base(), 1E-12);
        assertEquals(20D, settlement.reference());
        assertEquals(0L, settlement.days());
        assertEquals(1L, settlement.bloodMoons());
        assertEquals(3D, MonsterGrowthRules.damage(172L, 3D, DEFAULTS, 0D));
        assertEquals(0D, MonsterGrowthRules.damage(172L, 0D, DEFAULTS, 1D));
    }

    @Test void damageBaseOverrideRebasesToTheAttainedValueAfterCrossing() {
        var settings = new MonsterGrowthRules.Settings(20D, 19.95D, 20D, .005D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA);
        assertEquals(19.95D, MonsterGrowthRules.damage(1L, 3D, settings, 1D));
        assertEquals(20.05D, MonsterGrowthRules.damage(2L, 3D, settings, 1D), 1E-12);
        var following = MonsterGrowthRules.damagePreview(3L, 3D, settings, 1D);
        assertEquals(20.05D, following.base(), 1E-12);
        assertEquals(20.05D, following.reference(), 1E-12);
        assertEquals(1L, following.days());
        assertEquals(20.15025D, following.value(), 1E-12);
        assertEquals(23.15775D, MonsterGrowthRules.damage(33L, 3D, settings, 1D), 1E-12);
    }

    @Test void ownedDamageUsesTheRealHitAsItsBaseAndKeepsTheSameThreshold() {
        var settings = new MonsterGrowthRules.Settings(20D, 12D, 20D, .005D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA);
        assertEquals(2.5D, MonsterGrowthRules.ownedDamagePreview(1L, 2.5D, settings, 1D).value());
        var arrow = MonsterGrowthRules.ownedDamagePreview(2L, 2.5D, settings, 1D);
        assertEquals(2.5D, arrow.base());
        assertEquals(20D, arrow.reference());
        assertEquals(2.6D, arrow.value(), 1E-12);
        assertEquals(10.1D, MonsterGrowthRules.ownedDamagePreview(2L, 10D, settings, 1D).value(), 1E-12);
        var strong = MonsterGrowthRules.ownedDamagePreview(2L, 25D, settings, 1D);
        assertEquals(25D, strong.reference());
        assertEquals(25.125D, strong.value(), 1E-12);
    }

    @Test void customDamageFormulasCanCrossTheThresholdAtBloodMoonSettlement() {
        var settings = new MonsterGrowthRules.Settings(20D, 0D, 20D, .005D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, "base + reference * rate * min(days, 1)",
                MonsterGrowthRules.DEFAULT_FORMULA, "base + 3");
        assertTrue(MonsterGrowthRules.validate(settings).valid());
        assertEquals(17.6D, MonsterGrowthRules.damage(32L, 17.5D, settings, 1D), 1E-12);
        var crossing = MonsterGrowthRules.damagePreview(33L, 17.5D, settings, 1D);
        assertEquals(20.5D, crossing.value());
        assertEquals(20.5D, crossing.reference());
        assertEquals(20.6025D, MonsterGrowthRules.damage(34L, 17.5D, settings, 1D), 1E-12);
    }

    @Test void blueMoonDoesNotChangeTheBasisAndTheFollowingBloodMoonEndsTheLongerPeriod() {
        var blue = MonsterGrowthRules.healthPreview(129L, 20D, false, LEGACY_SETTINGS, 1D);
        assertEquals(31.21792D, blue.base(), 1E-12);
        assertEquals(32L, blue.days());
        assertEquals(3L, blue.bloodMoons());
        var next = MonsterGrowthRules.healthPreview(161L, 20D, false, LEGACY_SETTINGS, 1D);
        assertEquals(41.2076544D, next.base(), 1E-10);
        assertEquals(0L, next.days());
        assertEquals(4L, next.bloodMoons());
    }

    @Test void sixHealthSkeletonUsesTwentyInitiallyThenItsActualBloodMoonHealth() {
        assertEquals(6D, MonsterGrowthRules.health(1L, 6D, true, LEGACY_SETTINGS, 1D));
        assertEquals(6.1D, MonsterGrowthRules.health(2L, 6D, true, LEGACY_SETTINGS, 1D), 1E-12);
        assertEquals(9.2D, MonsterGrowthRules.health(33L, 6D, true, LEGACY_SETTINGS, 1D), 1E-12);
        assertEquals(9.246D, MonsterGrowthRules.health(34L, 6D, true, LEGACY_SETTINGS, 1D), 1E-12);
        assertEquals(10.672D, MonsterGrowthRules.health(65L, 6D, true, LEGACY_SETTINGS, 1D), 1E-12);
        assertEquals(10.672D, MonsterGrowthRules.health(65L, 6D, false, LEGACY_SETTINGS, 1D), 1E-12);
    }

    @Test void defaultFloorPreservesNativeHealthAndAppliesUntilTheThresholdIsReached() {
        assertEquals(40D, DEFAULTS.minimumHealthReference());
        long[] days = {1L, 2L, 32L, 33L, 65L, 97L, 129L, 161L, 171L, 172L};
        double[] values = {6D, 6.2D, 12.2D, 12.4D, 18.8D, 25.2D, 31.6D, 38D, 40D, 40.2D};
        for (int i = 0; i < days.length; i++)
            assertEquals(values[i], MonsterGrowthRules.health(days[i], 6D, true, DEFAULTS, 1D), 1E-12);
        var afterMoon = MonsterGrowthRules.healthPreview(34L, 6D, true, DEFAULTS, 1D);
        assertEquals(12.4D, afterMoon.base(), 1E-12);
        assertEquals(40D, afterMoon.reference());
        assertEquals(1L, afterMoon.bloodMoons());
        assertEquals(40.1D, MonsterGrowthRules.health(2L, 40D, false, DEFAULTS, 1D), 1E-12);
        assertEquals(6D, MonsterGrowthRules.health(Long.MAX_VALUE, 6D, true, DEFAULTS, 0D));
    }

    @Test void crossingUsesTheAttainedValueAndPreservesTheNextSettlementDate() {
        var atCrossing = MonsterGrowthRules.healthPreview(2L, 39.9D, false, DEFAULTS, 1D);
        assertEquals(40.1D, atCrossing.value(), 1E-12);
        var following = MonsterGrowthRules.healthPreview(3L, 39.9D, false, DEFAULTS, 1D);
        assertEquals(40.1D, following.base(), 1E-12);
        assertEquals(40.1D, following.reference(), 1E-12);
        assertEquals(1L, following.days());
        assertEquals(40.3005D, following.value(), 1E-12);
        var settlement = MonsterGrowthRules.healthPreview(33L, 39.9D, false, DEFAULTS, 1D);
        assertEquals(46.3155D, settlement.value(), 1E-12);
        assertEquals(1L, settlement.bloodMoons());
        assertEquals(0L, settlement.days());
        assertEquals(atCrossing, MonsterGrowthRules.healthPreview(2L, 39.9D, false, DEFAULTS, 1D));
        assertEquals(1L, MonsterGrowthRules.lastBloodMoonSettlementDay(3L));
        assertEquals(97L, MonsterGrowthRules.lastBloodMoonSettlementDay(129L));
        assertEquals(161L, MonsterGrowthRules.lastBloodMoonSettlementDay(161L));
    }

    @Test void customFormulaDetectsATransientCrossingAndCanAlsoCrossAtSettlement() {
        var transientCrossing = withFloor(40D,
                "base + reference * rate * min(days, 1) + max(0, 1 - abs(day - 5)) * 3", DEFAULTS.healthBloodMoonFormula());
        assertTrue(MonsterGrowthRules.validate(transientCrossing).valid());
        assertEquals(37.2D, MonsterGrowthRules.health(4L, 37D, false, transientCrossing, 1D), 1E-12);
        assertEquals(40.2D, MonsterGrowthRules.health(5L, 37D, false, transientCrossing, 1D), 1E-12);
        assertEquals(40.401D, MonsterGrowthRules.health(6L, 37D, false, transientCrossing, 1D), 1E-12);
        assertEquals(45.828D, MonsterGrowthRules.health(33L, 37D, false, transientCrossing, 1D), 1E-12);

        var settlementCrossing = withFloor(40D, "base + reference * rate * min(days, 1)", "base + 3");
        assertEquals(37.7D, MonsterGrowthRules.health(32L, 37.5D, false, settlementCrossing, 1D), 1E-12);
        var crossing = MonsterGrowthRules.healthPreview(33L, 37.5D, false, settlementCrossing, 1D);
        assertEquals(40.5D, crossing.value(), 1E-12);
        assertEquals(40.5D, crossing.reference(), 1E-12);
        assertEquals(40.7025D, MonsterGrowthRules.health(34L, 37.5D, false, settlementCrossing, 1D), 1E-12);
    }

    @Test void equivalentLinearAndCustomExpressionsAgreeAtExactThresholdAfterSeveralSettlements() {
        var custom = withFloor(40D, DEFAULTS.healthFormula() + " + 0", DEFAULTS.healthBloodMoonFormula());
        assertEquals(40D, MonsterGrowthRules.health(197L, .8D, false, custom, 1D), 1E-12);
        assertEquals(40.4D, MonsterGrowthRules.health(199L, .8D, false, custom, 1D), 1E-12);
        assertEquals(MonsterGrowthRules.health(199L, .8D, false, DEFAULTS, 1D),
                MonsterGrowthRules.health(199L, .8D, false, custom, 1D), 1E-12);
    }

    @Test void configuredFloorIsValidatedAndDoesNotChangeDamage() {
        for (double invalid : new double[]{-1D, 1025D, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(MonsterGrowthRules.validate(withFloor(invalid, DEFAULTS.healthFormula(), DEFAULTS.healthBloodMoonFormula())).valid());
        var settings = withFloor(50D, DEFAULTS.healthFormula(), DEFAULTS.healthBloodMoonFormula());
        assertEquals(6.25D, MonsterGrowthRules.health(2L, 6D, true, settings, 1D), 1E-12);
        assertEquals(MonsterGrowthRules.damage(65L, 3D, LEGACY_SETTINGS, 1D),
                MonsterGrowthRules.damage(65L, 3D, settings, 1D));
        var bounded = withFloor(40D, "base", "base");
        var stable = MonsterGrowthRules.healthPreview(100_000L, 6D, true, bounded, 1D);
        assertEquals(6D, stable.value());
        assertFalse(stable.fallback());
        assertEquals(40.5D, MonsterGrowthRules.health(33L, 37.5D, false, withFloor(40D, "base", "base + 3"), 0D));
    }

    private static MonsterGrowthRules.Settings withFloor(double floor, String period, String bloodMoon) {
        return new MonsterGrowthRules.Settings(DEFAULTS.healthBase(), DEFAULTS.damageBase(), DEFAULTS.skeletonReference(),
                DEFAULTS.healthRate(), DEFAULTS.damageRate(), period, DEFAULTS.damageFormula(), bloodMoon,
                DEFAULTS.damageBloodMoonFormula(), floor);
    }

    @Test void allNativeHealthValuesUseTheSameFirstReferenceWithLegacyZeroAndExplicitTwenty() {
        for (double configured : new double[]{0D, 20D}) {
            var settings = new MonsterGrowthRules.Settings(configured, 0D, 1024D, .005D, .0025D,
                    MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA);
            assertTrue(MonsterGrowthRules.validate(settings).valid());
            for (double nativeHealth : new double[]{6D, 20D, 30D}) for (boolean skeleton : new boolean[]{false, true}) {
                assertEquals(nativeHealth, MonsterGrowthRules.health(1L, nativeHealth, skeleton, settings, 1D), 1E-12);
                var secondDay = MonsterGrowthRules.healthPreview(2L, nativeHealth, skeleton, settings, 1D);
                assertEquals(nativeHealth, secondDay.base(), 1E-12);
                assertEquals(20D, secondDay.reference(), 1E-12);
                assertEquals(nativeHealth + .1D, secondDay.value(), 1E-12);
                assertEquals(nativeHealth + 3.2D, MonsterGrowthRules.health(33L, nativeHealth, skeleton, settings, 1D), 1E-12);
                assertEquals((nativeHealth + 3.2D) * 1.16D,
                        MonsterGrowthRules.health(65L, nativeHealth, skeleton, settings, 1D), 1E-12);
            }
        }
    }

    @Test void configurableReferencesRatesAndActualFormulasRetainNativeInitialHealth() {
        var settings = new MonsterGrowthRules.Settings(40D, 6D, 20D, .01D, .005D,
                "base + reference * rate * days * 2", "base + initial * rate * days");
        assertTrue(MonsterGrowthRules.validate(settings).valid());
        assertEquals(20D, MonsterGrowthRules.health(1L, 20D, false, settings, 1D));
        assertEquals(45.6D, MonsterGrowthRules.health(33L, 20D, false, settings, 1D), 1E-12);
        assertEquals(74.784D, MonsterGrowthRules.health(65L, 20D, false, settings, 1D), 1E-10);
        assertEquals(7.92D, MonsterGrowthRules.damage(65L, 3D, settings, 1D), 1E-12);
        assertEquals(20D, MonsterGrowthRules.health(65L, 20D, false, settings, 0D));
    }

    @Test void independentBloodMoonFormulasSetTheNextBasisWithoutUsingThePeriodFormula() {
        var settings = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                "base + initial * rate * days", "base + 2", "base * 2", "base + 5");
        assertTrue(MonsterGrowthRules.validate(settings).valid());
        assertEquals(23.1D, MonsterGrowthRules.health(32L, 20D, false, settings, 1D), 1E-12);
        var first = MonsterGrowthRules.healthPreview(33L, 20D, false, settings, 1D);
        assertEquals(40D, first.base());
        assertEquals(40D, first.reference());
        assertEquals(40D, first.value());
        assertEquals(40.1D, MonsterGrowthRules.health(34L, 20D, false, settings, 1D), 1E-12);
        assertEquals(80D, MonsterGrowthRules.health(65L, 20D, false, settings, 1D));
        assertFalse(first.fallback());
        assertEquals(5D, MonsterGrowthRules.damage(1L, 3D, settings, 1D));
        var damage = MonsterGrowthRules.damagePreview(33L, 3D, settings, 1D);
        assertEquals(8D, damage.base());
        assertEquals(10D, damage.value());
        assertEquals(15D, MonsterGrowthRules.damage(65L, 3D, settings, 1D));
    }

    @Test void independentBloodMoonFormulaSkipsBlueMoonAndReceivesTheLongPeriodLength() {
        var settings = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                "base + days + blood_moons", "base + days + blood_moons");
        assertTrue(MonsterGrowthRules.validate(settings).valid());
        var beforeBlueMoon = MonsterGrowthRules.healthPreview(97L, 20D, false, settings, 1D);
        assertEquals(119D, beforeBlueMoon.base());
        var afterBlueMoon = MonsterGrowthRules.healthPreview(129L, 20D, false, settings, 1D);
        assertEquals(119D, afterBlueMoon.base());
        assertEquals(32L, afterBlueMoon.days());
        assertEquals(3L, afterBlueMoon.bloodMoons());
        var afterLongPeriod = MonsterGrowthRules.healthPreview(161L, 20D, false, settings, 1D);
        assertEquals(186D, afterLongPeriod.base());
        assertEquals(186D, afterLongPeriod.value());
        assertEquals(0L, afterLongPeriod.days());
        assertEquals(4L, afterLongPeriod.bloodMoons());
    }

    @Test void sevenArgumentSettingsKeepTheirPreviousSharedExpressionSemantics() {
        String health = "base * pow(1 + rate, days)";
        String damage = "base + initial * rate * days";
        var compatible = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D, health, damage);
        assertEquals(health, compatible.healthBloodMoonFormula());
        assertEquals(damage, compatible.damageBloodMoonFormula());
        // These formulas respectively compound every elapsed day and add a fixed initial-based amount each day.
        for (long day : new long[]{1L, 32L, 33L, 65L, 97L, 129L, 161L}) {
            assertEquals(20D * Math.pow(1.005D, day - 1L),
                    MonsterGrowthRules.health(day, 20D, false, compatible, 1D), 1E-10);
            assertEquals(3D + 3D * .0025D * (day - 1L),
                    MonsterGrowthRules.damage(day, 3D, compatible, 1D), 1E-12);
        }
    }

    @Test void validationNamesTheFailingStageAndExecutesLaterBloodMoonBases() {
        for (String expression : new String[]{"unknown + base", "base / abs(blood_moons - 1)",
                "max(0, base * (1 - blood_moons))", "base * exp(blood_moons * 1000)"}) {
            var settings = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                    MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                    expression, MonsterGrowthRules.DEFAULT_FORMULA);
            var validation = MonsterGrowthRules.validate(settings);
            assertFalse(validation.valid(), expression);
            assertTrue(validation.message().contains("生命血月基准公式"), validation.message());
            var fallback = MonsterGrowthRules.healthPreview(65L, 20D, false, settings, 1D);
            assertTrue(fallback.fallback(), expression);
            assertEquals(MonsterGrowthRules.health(65L, 20D, false, LEGACY_SETTINGS, 1D), fallback.value());
        }
        var badPeriod = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                "base / abs(blood_moons - 1)", MonsterGrowthRules.DEFAULT_FORMULA,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA);
        var validation = MonsterGrowthRules.validate(badPeriod);
        assertFalse(validation.valid());
        assertTrue(validation.message().contains("生命周期内公式"), validation.message());
        var badAttackBloodMoon = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                MonsterGrowthRules.DEFAULT_FORMULA, "base / 0");
        var attackValidation = MonsterGrowthRules.validate(badAttackBloodMoon);
        assertFalse(attackValidation.valid());
        assertTrue(attackValidation.message().contains("攻击血月基准公式"), attackValidation.message());
        var invalidForProjectiles = new MonsterGrowthRules.Settings(20D, 5D, 20D, .005D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, "base / (initial - 1)");
        assertFalse(MonsterGrowthRules.validate(invalidForProjectiles).valid());
        var invalidAtOwnedReference = new MonsterGrowthRules.Settings(20D, 30D, 20D, .005D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, "base / abs(reference - 20)");
        assertFalse(MonsterGrowthRules.validate(invalidAtOwnedReference).valid());
        var zeroAttack = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                MonsterGrowthRules.DEFAULT_FORMULA, "0");
        assertTrue(MonsterGrowthRules.validate(zeroAttack).valid());
        assertEquals(0D, MonsterGrowthRules.damage(33L, 3D, zeroAttack, 1D));
    }

    @Test void unchangedInitialBasisMakesRefreshesAndConfigChangesReversible() {
        var faster = new MonsterGrowthRules.Settings(0D, 0D, 20D, .01D, .005D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA);
        double original = MonsterGrowthRules.health(65L, 20D, false, DEFAULTS, 1D);
        assertEquals(34.848D, MonsterGrowthRules.health(65L, 20D, false, faster, 1D), 1E-12);
        assertEquals(original, MonsterGrowthRules.health(65L, 20D, false, DEFAULTS, 1D));
        for (int repeat = 0; repeat < 20; repeat++)
            assertEquals(original, MonsterGrowthRules.health(65L, 20D, false, DEFAULTS, 1D));
    }

    @Test void validationRejectsSyntaxExecutionAndAttributeDomainFailuresBeforeSave() {
        assertTrue(MonsterGrowthRules.validate(DEFAULTS).valid());
        for (String expression : new String[]{"", "base / days", "0", "-1", "base / 0", "sqrt(-1)", "base + typo"}) {
            var invalid = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D, expression,
                    MonsterGrowthRules.DEFAULT_FORMULA);
            assertFalse(MonsterGrowthRules.validate(invalid).valid(), expression);
        }
        var zeroDamage = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, "0");
        assertTrue(MonsterGrowthRules.validate(zeroDamage).valid());
        assertEquals(0D, MonsterGrowthRules.damage(65L, 3D, zeroDamage, 1D));
        assertFalse(MonsterGrowthRules.validate(new MonsterGrowthRules.Settings(-1D, 0D, 20D, .005D, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA)).valid());
        assertFalse(MonsterGrowthRules.validate(new MonsterGrowthRules.Settings(0D, 0D, 20D, Double.NaN, .0025D,
                MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA)).valid());
    }

    @Test void aSpecificFutureInputThatWasNotInTheSaveSamplesFallsBackToTheDefault() {
        var edge = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                "base + 1 / abs(day - 201)", MonsterGrowthRules.DEFAULT_FORMULA);
        assertTrue(MonsterGrowthRules.validate(edge).valid());
        var actual = MonsterGrowthRules.healthPreview(201L, 20D, false, edge, 1D);
        assertTrue(actual.fallback());
        assertEquals(MonsterGrowthRules.health(201L, 20D, false, LEGACY_SETTINGS, 1D), actual.value());
        assertTrue(Double.isFinite(actual.value()) && actual.value() > 0D);
    }

    @Test void validationUsesTheActualGrowthMultipliersForOtherwiseOrdinaryCompoundFormulas() {
        var compound = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                "base * pow(1 + rate, days)", "base * pow(1 + rate, days)");
        assertTrue(MonsterGrowthRules.validate(compound).valid());
        assertTrue(MonsterGrowthRules.validate(compound, 1D, 1D).valid());
        assertFalse(MonsterGrowthRules.validate(compound, 10_000D, 1D).valid());
    }

    @Test void extremeDatesHaveBoundedWorkAndAlwaysProduceFiniteSafeValues() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(Double.MAX_VALUE, MonsterGrowthRules.health(Long.MAX_VALUE, 20D, false, DEFAULTS, 1D));
            assertEquals(Double.MAX_VALUE, MonsterGrowthRules.damage(Long.MAX_VALUE, 3D, DEFAULTS, 1D));
            assertEquals(20D, MonsterGrowthRules.health(Long.MAX_VALUE, 20D, false, DEFAULTS, 0D));
            var custom = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                    "base + reference * rate * days + 0", MonsterGrowthRules.DEFAULT_FORMULA);
            var fallback = MonsterGrowthRules.healthPreview(Long.MAX_VALUE, 20D, false, custom, 1D);
            assertTrue(fallback.fallback());
            assertEquals(Double.MAX_VALUE, fallback.value());
            assertEquals(0D, MonsterGrowthRules.damage(Long.MAX_VALUE, 0D, DEFAULTS, 1D));
        });
    }
}
