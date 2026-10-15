package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** R196 abw/WeatherEvent scheduling, with the persisted modern world seed as weather seed. @author NingMeng123 */
public final class LunarWeatherRules {
    private LunarWeatherRules() {}

    public record Weather(long start, long end, long stormStart, long stormEnd, int windType) {
        public boolean rainingAt(long time) { return start <= time && time < end; }
        public boolean thunderingAt(long time) { return stormStart > 0 && stormStart <= time && time < stormEnd; }
    }

    /** Source weather days begin at time-of-day 18000; lunar periods begin at zero. */
    public static long weatherDay(long time) {
        return Math.floorDiv(time, 24000L) + (Math.floorMod(time, 24000L) >= 18000L ? 2L : 1L);
    }

    public static int lightningRarity(boolean bloodPeriod) { return bloodPeriod ? 20000 : 100000; }

    public static boolean ordinaryRainAllowed(long start, long end) {
        return !LunarCalendar.state(start, true).harvestNight()
                && !LunarCalendar.state(end, true).harvestNight()
                && !LunarCalendar.state(start + 6000L, true).harvestNight()
                && !LunarCalendar.state(end - 6000L, true).harvestNight()
                && !LunarCalendar.state(start, true).bloodPeriod()
                && !LunarCalendar.state(end, true).bloodPeriod()
                && !LunarCalendar.state(start, true).bluePeriod()
                && !LunarCalendar.state(end, true).bluePeriod();
    }

    /** Only the previous and current source weather day are consulted by abw.getWeatherEventsForToday. */
    public static List<Weather> schedule(long seed, long day) {
        List<MutableWeather> events = new ArrayList<>();
        generate(seed, day - 1L, events);
        generate(seed, day, events);
        // Keep the source nested merge order, six-thousand-tick padding and first existing storm.
        for (MutableWeather a : events) {
            if (a.removed) continue;
            for (MutableWeather b : events) {
                if (a == b || b.removed || a.end + 6000L < b.start || a.start > b.end + 6000L) continue;
                a.start = Math.min(a.start, b.start);
                a.end = Math.max(a.end, b.end);
                if (b.hasStorm() && !a.hasStorm()) { a.stormStart = b.stormStart; a.stormEnd = b.stormEnd; }
                b.removed = true;
            }
        }
        events.removeIf(event -> event.removed);
        events.sort(Comparator.comparingLong(event -> event.start));
        List<Weather> result = new ArrayList<>();
        for (MutableWeather event : events) {
            int wind;
            if (event.hasStorm()) wind = 3;
            else { Random random = new Random(event.start); random.nextInt(); wind = random.nextInt(3); }
            if (!event.hasStorm()) {
                Random random = new Random(event.start);
                random.nextInt();
                if (random.nextInt(4) == 0) {
                    int duration = (int)Math.min(random.nextInt(4000) + 2000L, event.end - event.start);
                    event.stormStart = random.nextInt(4) == 0
                            ? (random.nextBoolean() ? event.start : event.end - duration)
                            : event.start + random.nextInt((int)(event.end - event.start) - duration + 1);
                    event.stormEnd = event.stormStart + duration;
                    wind = 3;
                }
            }
            result.add(new Weather(event.start, event.end, event.stormStart, event.stormEnd, wind));
        }
        return List.copyOf(result);
    }

    private static void generate(long seed, long day, List<MutableWeather> events) {
        if (day < 2L) return;
        long firstTick = (day - 1L) * 24000L - 6000L;
        Random random = new Random(seed + day);
        random.nextInt();
        for (int i = 0; i < 3 && random.nextInt(4) == 0; i++) {
            long start = firstTick + random.nextInt(24000);
            long end = start + random.nextInt(12000) + 6000L;
            if (ordinaryRainAllowed(start, end)) events.add(new MutableWeather(start, end));
        }
        long midnight = firstTick + 6000L;
        if (LunarCalendar.state(midnight, true).bloodPeriod()) {
            MutableWeather event = new MutableWeather(midnight, midnight + 13000L);
            event.stormStart = event.start;
            event.stormEnd = event.end;
            events.add(event);
        }
    }

    private static final class MutableWeather {
        long start, end, stormStart, stormEnd;
        boolean removed;
        MutableWeather(long start, long end) { this.start = start; this.end = end; }
        boolean hasStorm() { return stormStart > 0L; }
    }
}
