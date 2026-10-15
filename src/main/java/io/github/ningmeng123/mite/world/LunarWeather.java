package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.rules.LunarWeatherRules;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ServerLevelData;

/** Common lunar precipitation and server-only deterministic weather flags. @author NingMeng123 */
public final class LunarWeather {
    private static final Map<ServerLevel, Cache> CACHE = new WeakHashMap<>();
    private LunarWeather() {}

    public static boolean bloodPeriod(Level level) {
        return LunarCalendar.state(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)).bloodPeriod();
    }

    /** Shared with client rendering without changing global biome climate or other dimensions. */
    public static Biome.Precipitation precipitation(Level level, Biome biome, BlockPos pos, Biome.Precipitation original) {
        if (original != Biome.Precipitation.NONE || !bloodPeriod(level)) return original;
        return biome.coldEnoughToSnow(pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }

    public static void updateNaturalWeather(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) return;
        long time = level.getDayTime();
        long day = LunarWeatherRules.weatherDay(time);
        Cache cache = CACHE.computeIfAbsent(level, ignored -> new Cache());
        // abw retains a currently occurring event across a weather-day boundary.
        if (cache.current == null || !cache.current.rainingAt(time)) {
            cache.current = null;
            if (cache.day != day) {
                cache.day = day;
                cache.events = LunarWeatherRules.schedule(level.getSeed(), day);
            }
            for (var event : cache.events) {
                if (event.rainingAt(time)) { cache.current = event; break; }
            }
        }
        boolean rain = cache.current != null;
        boolean thunder = rain && cache.current.thunderingAt(time);
        ServerLevelData data = (ServerLevelData)level.getLevelData();
        data.setClearWeatherTime(0);
        // Native advanceWeatherCycle decrements these to one, without toggling our flags.
        data.setRainTime(2);
        data.setThunderTime(2);
        data.setRaining(rain);
        data.setThundering(thunder);
    }

    private static final class Cache {
        long day = Long.MIN_VALUE;
        List<LunarWeatherRules.Weather> events = List.of();
        LunarWeatherRules.Weather current;
    }
}
