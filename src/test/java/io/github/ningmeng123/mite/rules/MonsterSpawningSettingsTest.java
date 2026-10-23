package io.github.ningmeng123.mite.rules;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option.*;

/** Population, probability and live interval boundaries independent of Minecraft. @author NingMeng123 */
class MonsterSpawningSettingsTest {
    @Test void newAndLegacyConfigurationsDefaultToReferenceWithoutLosingCustomNumbers() {
        assertTrue(MonsterSpawningSettings.DEFAULTS.reference());
        assertTrue(settings(BASE_CAP, 999D).reference());
        assertEquals(999D, settings(BASE_CAP, 999D).value(BASE_CAP));
        assertFalse(settings(REFERENCE_RULES, 0D).reference());
    }
    @Test void defaultsPreserveExistingPopulationAndPassProbabilities() {
        var settings = MonsterSpawningSettings.DEFAULTS;
        for (int chunks : new int[]{-1, 0, 1, 225, 289, 306, 578, Integer.MAX_VALUE})
            for (boolean blood : new boolean[]{false, true}) {
                assertEquals(R196SpawnRules.monsterCap(chunks, blood), settings.cap(chunks, blood, 1));
                assertEquals(R196SpawnRules.monsterCap(chunks, blood), settings.cap(chunks, blood, 100000));
            }
        for (boolean deep : new boolean[]{false, true}) for (boolean blood : new boolean[]{false, true})
            for (float modifier : new float[]{0F, .25F, .5F, 1F, 2F, 10F})
                assertEquals(R196SpawnRules.hostilePassProbability(deep, modifier, blood), settings.probability(deep, modifier, blood), .000001F);
    }

    @Test void fractionalDailyGrowthAccumulatesBeforeAreaScalingAndRounding() {
        var settings = settings(BASE_CAP, 10D, BLOOD_CAP, 20D, DAILY_CAP_INCREASE, .5D);
        assertEquals(10, settings.cap(289, false, 1));
        assertEquals(10, settings.cap(289, false, 2));
        assertEquals(11, settings.cap(289, false, 3));
        assertEquals(21, settings.cap(578, false, 2));
        assertEquals(41, settings.cap(578, true, 2));
        assertEquals(10, settings.cap(289, false, Long.MIN_VALUE));
    }

    @Test void maximumAppliesToThePlayerBudgetBeforeScalingAndZeroDisablesOnlyTheCeiling() {
        var limited = settings(BASE_CAP, 10D, BLOOD_CAP, 20D, DAILY_CAP_INCREASE, 5D, MAXIMUM_CAP, 25D);
        assertEquals(25, limited.cap(289, false, 5));
        assertEquals(25, limited.cap(289, true, 5));
        assertEquals(50, limited.cap(578, true, 5));
        var unlimited = settings(BASE_CAP, 10D, DAILY_CAP_INCREASE, 5D, MAXIMUM_CAP, 0D);
        assertEquals(30, unlimited.cap(289, false, 5));
        assertEquals(Integer.MAX_VALUE, unlimited.cap(Integer.MAX_VALUE, false, Long.MAX_VALUE));
        assertEquals(0, unlimited.cap(0, false, Long.MAX_VALUE));
        assertEquals(0, settings(BASE_CAP, 0D, BLOOD_CAP, 0D).cap(289, true, 100));
    }

    @Test void editedProbabilitiesAndMultipliersKeepTheirUnitsAndClampAtOne() {
        var settings = settings(DEEP_CHANCE_PERCENT, 25D, SURFACE_CHANCE_PERCENT, 40D, BLOOD_CHANCE_MULTIPLIER, 3D);
        assertEquals(.25F, settings.probability(true, 1F, false));
        assertEquals(.4F, settings.probability(false, 1F, false));
        assertEquals(.75F, settings.probability(true, 1F, true));
        assertEquals(1F, settings.probability(false, 1F, true));
        assertEquals(.1F, settings.probability(false, .25F, false), .000001F);
        assertEquals(0F, settings(DEEP_CHANCE_PERCENT, 0D).probability(true, 100F, true));
        assertEquals(0F, settings(BLOOD_CHANCE_MULTIPLIER, 0D).probability(false, 1F, true));
    }

    @Test void changingIntervalUsesElapsedTimeAndImmediatelyShortensAnExistingWait() {
        var slow = settings(HOSTILE_INTERVAL_TICKS, 100D);
        var fast = settings(HOSTILE_INTERVAL_TICKS, 5D);
        assertTrue(slow.ready(0, Long.MIN_VALUE));
        assertFalse(slow.ready(105, 100));
        assertTrue(fast.ready(105, 100));
        assertFalse(fast.ready(104, 100));
        assertTrue(slow.ready(200, 100));
        assertTrue(slow.ready(10, 100));
        assertTrue(MonsterSpawningSettings.DEFAULTS.ready(Long.MAX_VALUE, Long.MAX_VALUE - 1));
    }

    @Test void invalidNumbersAndFractionalIntegerFieldsCannotReachRuntimeSettings() {
        for (var option : MonsterSpawningSettings.Option.values()) {
            for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    option.minimum() - 1D, option.maximum() + 1D})
                assertThrows(IllegalArgumentException.class, () -> MonsterSpawningSettings.Settings.from(Map.of(option.key(), invalid)));
            if (option.wholeNumber()) assertThrows(IllegalArgumentException.class,
                    () -> MonsterSpawningSettings.Settings.from(Map.of(option.key(), option.defaultValue() + .5D)));
        }
        var nullValue = new HashMap<String, Double>();
        nullValue.put(BASE_CAP.key(), null);
        assertThrows(IllegalArgumentException.class, () -> MonsterSpawningSettings.Settings.from(nullValue));
    }

    @Test void legacyDefaultsAndDefensiveCopyKeepPublishedSettingsImmutable() {
        assertEquals(MonsterSpawningSettings.DEFAULTS, MonsterSpawningSettings.Settings.from(null));
        var draft = new HashMap<String, Double>();
        draft.put(BASE_CAP.key(), 12D);
        var settings = MonsterSpawningSettings.Settings.from(draft);
        draft.put(BASE_CAP.key(), 25D);
        assertEquals(12D, settings.value(BASE_CAP));
        assertEquals(140D, settings.value(BLOOD_CAP));
        assertThrows(UnsupportedOperationException.class, () -> settings.values().put(BASE_CAP.key(), 30D));
    }

    private static MonsterSpawningSettings.Settings settings(Object... pairs) {
        var values = new HashMap<String, Double>();
        for (int index = 0; index < pairs.length; index += 2)
            values.put(((MonsterSpawningSettings.Option)pairs[index]).key(), (Double)pairs[index + 1]);
        return MonsterSpawningSettings.Settings.from(values);
    }
}
