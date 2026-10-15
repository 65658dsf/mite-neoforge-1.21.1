package io.github.ningmeng123.mite.rules;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent source constants and literal java.util.Random draw fixtures. @author NingMeng123 */
class LunarWeatherRulesTest {
    @Test void weatherDayHasItsOwnSixThousandTickOffset() {
        assertEquals(1, LunarWeatherRules.weatherDay(0));
        assertEquals(1, LunarWeatherRules.weatherDay(17999));
        assertEquals(2, LunarWeatherRules.weatherDay(18000));
        assertEquals(2, LunarWeatherRules.weatherDay(23999));
        assertEquals(2, LunarWeatherRules.weatherDay(41999));
        assertEquals(3, LunarWeatherRules.weatherDay(42000));
        assertTrue(LunarWeatherRules.schedule(3, 1).isEmpty());
    }

    @Test void sourceRandomDrawsGiveTheKnownDayTwoRainAndStormIntervals() {
        assertEquals(List.of(new LunarWeatherRules.Weather(23474, 30898, 0, 0, 1)), LunarWeatherRules.schedule(3, 2));
        assertEquals(List.of(new LunarWeatherRules.Weather(23512, 40893, 35629, 40893, 3)), LunarWeatherRules.schedule(20, 2));
        assertEquals(List.of(new LunarWeatherRules.Weather(32221, 41422, 33386, 36879, 3)), LunarWeatherRules.schedule(27, 2));
    }

    @Test void bloodStormIsDaytimeThirteenThousandTicksAndTheFourthBloodDateIsBlue() {
        long start = 31L * 24000L;
        var weather = LunarWeatherRules.schedule(0, 32);
        assertEquals(List.of(new LunarWeatherRules.Weather(start, start + 13000L, start, start + 13000L, 3)), weather);
        assertFalse(weather.getFirst().rainingAt(start - 1L));
        assertTrue(weather.getFirst().thunderingAt(start));
        assertTrue(weather.getFirst().thunderingAt(start + 12999L));
        assertFalse(weather.getFirst().thunderingAt(start + 13000L));
        assertTrue(LunarWeatherRules.schedule(0, 128).isEmpty());
        assertEquals(20000, LunarWeatherRules.lightningRarity(true));
        assertEquals(100000, LunarWeatherRules.lightningRarity(false));
    }

    @Test void sourceEndpointsKeepBloodBlueAndHarvestNightsFreeOfOrdinaryRain() {
        long blood = 31L * 24000L, blue = 127L * 24000L, harvest = 23L * 24000L;
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(blood, blood + 6000L));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(blood - 6000L, blood));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(blue, blue + 6000L));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(blue - 6000L, blue));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(harvest + 13000L, harvest + 19000L));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(harvest + 7000L, harvest + 12999L));
        assertFalse(LunarWeatherRules.ordinaryRainAllowed(harvest + 23001L, harvest + 29000L));
        assertTrue(LunarWeatherRules.ordinaryRainAllowed(harvest, harvest + 6000L));
    }

    @Test void rebuildsAreStableAndAllEventEndsRemainExclusive() {
        for (int seed = 0; seed < 128; seed++) {
            for (long day : new long[]{2, 23, 24, 31, 32, 33, 120, 127, 128, 129}) {
                var list = LunarWeatherRules.schedule(seed, day);
                assertEquals(list, LunarWeatherRules.schedule(seed, day));
                for (var event : list) {
                    assertTrue(event.end() > event.start());
                    assertFalse(event.rainingAt(event.end()));
                    assertTrue(event.rainingAt(event.start()));
                    if (event.stormStart() > 0) {
                        assertTrue(event.stormStart() >= event.start());
                        assertTrue(event.stormEnd() <= event.end());
                        assertFalse(event.thunderingAt(event.stormEnd()));
                    }
                }
            }
        }
    }
}
