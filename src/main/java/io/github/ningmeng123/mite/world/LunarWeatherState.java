package io.github.ningmeng123.mite.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;

/** Persist the weather suspended by the modern all-day blood storm, including frozen weather cycles. @author NingMeng123 */
public final class LunarWeatherState extends SavedData {
    public static final String NAME = "mite_lunar_weather";
    public static final Factory<LunarWeatherState> FACTORY = new Factory<>(LunarWeatherState::new, LunarWeatherState::load);
    private boolean active, raining, thundering;
    private int rainTime, thunderTime, clearTime;

    public static LunarWeatherState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void enter(ServerLevelData data) {
        if (active) return;
        active = true; raining = data.isRaining(); thundering = data.isThundering();
        rainTime = data.getRainTime(); thunderTime = data.getThunderTime(); clearTime = data.getClearWeatherTime();
        setDirty();
    }

    public void leave(ServerLevelData data) {
        if (!active) return;
        // A weather command issued after /time has already left the blood date takes precedence
        // over the suspended snapshot. Our own forced timers are always one or two ticks long.
        // Both daytime rain and the dry night use our short timers and thunder flag.
        if (data.isThundering() && data.getClearWeatherTime() == 0
                && data.getRainTime() <= 2 && data.getThunderTime() <= 2) {
            data.setRaining(raining); data.setThundering(thundering);
            data.setRainTime(rainTime); data.setThunderTime(thunderTime); data.setClearWeatherTime(clearTime);
        }
        active = false; setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1); tag.putBoolean("blood_storm_active", active);
        tag.putBoolean("raining", raining); tag.putBoolean("thundering", thundering);
        tag.putInt("rain_time", rainTime); tag.putInt("thunder_time", thunderTime); tag.putInt("clear_time", clearTime);
        return tag;
    }

    public static LunarWeatherState load(CompoundTag tag, HolderLookup.Provider registries) {
        LunarWeatherState state = new LunarWeatherState();
        state.active = tag.getBoolean("blood_storm_active");
        state.raining = tag.getBoolean("raining"); state.thundering = tag.getBoolean("thundering");
        state.rainTime = Math.max(0, tag.getInt("rain_time")); state.thunderTime = Math.max(0, tag.getInt("thunder_time"));
        state.clearTime = Math.max(0, tag.getInt("clear_time"));
        return state;
    }
}
