package io.github.ningmeng123.mite.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Replacement slots survive restarts without changing an intact source stronghold. @author NingMeng123 */
public final class StrongholdRecoveryData extends SavedData {
    public static final String NAME = "mite_stronghold_recovery";
    public static final Factory<StrongholdRecoveryData> FACTORY =
            new Factory<>(StrongholdRecoveryData::new, StrongholdRecoveryData::load);
    private final Map<Integer, ChunkPos> replacements = new HashMap<>();
    private final Map<Integer, Integer> attempts = new HashMap<>();

    public List<ChunkPos> positions(List<ChunkPos> originals) {
        List<ChunkPos> result = new ArrayList<>(originals);
        replacements.forEach((slot, coordinate) -> {
            if (slot >= 0 && slot < result.size()) result.set(slot, coordinate);
        });
        return List.copyOf(result);
    }

    public int nextAttempt(int slot) {
        int attempt = Math.min(attempts.getOrDefault(slot, 0), Integer.MAX_VALUE - 1) + 1;
        attempts.put(slot, attempt);
        setDirty();
        return attempt;
    }

    public void replace(int slot, ChunkPos coordinate) {
        replacements.put(slot, coordinate);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag slots = new ListTag();
        var keys = new TreeSet<>(attempts.keySet());
        keys.addAll(replacements.keySet());
        keys.forEach(slot -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.putInt("attempt", attempts.getOrDefault(slot, 0));
            ChunkPos coordinate = replacements.get(slot);
            if (coordinate != null) entry.putLong("replacement", coordinate.toLong());
            slots.add(entry);
        });
        tag.put("slots", slots);
        return tag;
    }

    public static StrongholdRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        StrongholdRecoveryData data = new StrongholdRecoveryData();
        ListTag slots = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag entry = slots.getCompound(index);
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= io.github.ningmeng123.mite.rules.StructureDistributionRules.STRONGHOLD_COUNT) continue;
            data.attempts.put(slot, Math.max(0, entry.getInt("attempt")));
            if (entry.contains("replacement", Tag.TAG_LONG))
                data.replacements.put(slot, new ChunkPos(entry.getLong("replacement")));
        }
        return data;
    }
}
