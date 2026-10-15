package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.BloodMoonInvasionRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One shared, versioned and persistent unlock, independent of players or subsequent time commands. @author NingMeng123 */
public final class BloodMoonInvasionData extends SavedData {
    public static final String NAME = "mite_blood_moon_invasion";
    public static final Factory<BloodMoonInvasionData> FACTORY = new Factory<>(BloodMoonInvasionData::new, BloodMoonInvasionData::load);
    private boolean unlocked;
    private long highestDay;
    private ServerLevel owner;

    public static BloodMoonInvasionData get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Blood-moon progression must be loaded on the server thread");
        ServerLevel overworld = level.getServer().overworld();
        BloodMoonInvasionData data = overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
        data.owner = overworld;
        BloodMoonInvasion.publish(overworld, data.unlocked, data.highestDay);
        return data;
    }

    public boolean unlocked() { return unlocked; }
    public long highestDay() { return highestDay; }

    public void observeDay(long day) {
        if (day > highestDay) restoreProgress(day);
    }

    public void unlock() { restoreProgress(Math.max(highestDay, BloodMoonInvasionRules.UNLOCK_DAY)); }

    /** Migration/test restore hook; ordinary gameplay only calls unlock and never revokes progress. */
    public void restoreUnlocked(boolean value) {
        restoreProgress(value ? Math.max(highestDay, BloodMoonInvasionRules.UNLOCK_DAY) : Math.min(highestDay, BloodMoonInvasionRules.UNLOCK_DAY - 1L));
    }

    /** Exact progression migration/test restore; normal gameplay only increases the highest reached day. */
    public void restoreProgress(long day) {
        long valid = Math.max(0L, day);
        boolean value = valid >= BloodMoonInvasionRules.UNLOCK_DAY;
        if (highestDay == valid && unlocked == value) return;
        highestDay = valid;
        unlocked = value;
        setDirty();
        if (owner != null) BloodMoonInvasion.publish(owner, unlocked, highestDay);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        tag.putBoolean("unlocked", unlocked);
        tag.putLong("highest_day", highestDay);
        return tag;
    }

    public static BloodMoonInvasionData load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonInvasionData data = new BloodMoonInvasionData();
        data.highestDay = Math.max(Math.max(0L, tag.getLong("highest_day")), tag.getBoolean("unlocked") ? BloodMoonInvasionRules.UNLOCK_DAY : 0L);
        data.unlocked = data.highestDay >= BloodMoonInvasionRules.UNLOCK_DAY;
        return data;
    }
}
