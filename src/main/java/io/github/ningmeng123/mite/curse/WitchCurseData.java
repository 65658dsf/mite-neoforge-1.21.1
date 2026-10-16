package io.github.ningmeng123.mite.curse;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared world ledger: death, logout and dimension changes cannot lift a witch's curse. @author NingMeng123 */
public final class WitchCurseData extends SavedData {
    public static final int VERSION = 1;
    public static final String NAME = "mite_witch_curses";
    public static final Factory<WitchCurseData> FACTORY = new Factory<>(WitchCurseData::new, WitchCurseData::load);
    public record Entry(UUID player, String username, UUID witch, R196Curse type, long realizationTime,
                        boolean realized, boolean known) {
        Entry realize() { return new Entry(player, username, witch, type, realizationTime, true, known); }
        Entry learn() { return new Entry(player, username, witch, type, realizationTime, realized, true); }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static WitchCurseData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }
    public Entry entry(UUID player) { return entries.get(player); }
    public boolean add(Entry entry) {
        if (entries.putIfAbsent(entry.player(), entry) != null) return false;
        setDirty();
        return true;
    }
    public void replace(Entry entry) { entries.put(entry.player(), entry); setDirty(); }
    public boolean remove(UUID player) {
        if (entries.remove(player) == null) return false;
        setDirty();
        return true;
    }
    public boolean removeForWitch(UUID witch) {
        boolean changed = entries.values().removeIf(entry -> entry.witch().equals(witch));
        if (changed) setDirty();
        return changed;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        var list = new ListTag();
        for (Entry entry : entries.values()) {
            var saved = new CompoundTag();
            saved.putUUID("player", entry.player());
            saved.putString("username", entry.username());
            saved.putUUID("witch", entry.witch());
            saved.putInt("type", entry.type().id());
            saved.putLong("realization_time", entry.realizationTime());
            saved.putBoolean("realized", entry.realized());
            saved.putBoolean("known", entry.known());
            list.add(saved);
        }
        tag.put("curses", list);
        return tag;
    }
    public static WitchCurseData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") > VERSION) throw new IllegalArgumentException("Unsupported MITE curse save version");
        var data = new WitchCurseData();
        for (Tag value : tag.getList("curses", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) value;
            R196Curse type = R196Curse.fromId(saved.getInt("type"));
            if (!saved.hasUUID("player") || !saved.hasUUID("witch") || type == null) {
                LogUtils.getLogger().warn("Ignoring invalid MITE curse record: missing identity or unknown curse type");
                continue;
            }
            var entry = new Entry(saved.getUUID("player"), saved.getString("username"), saved.getUUID("witch"),
                    type, saved.getLong("realization_time"), saved.getBoolean("realized"), saved.getBoolean("known"));
            data.entries.put(entry.player(), entry);
        }
        return data;
    }
}
