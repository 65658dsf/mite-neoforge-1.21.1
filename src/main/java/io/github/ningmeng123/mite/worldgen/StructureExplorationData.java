package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player exploration totals publish immutable world-shared maxima. @author NingMeng123 */
public final class StructureExplorationData extends SavedData {
    public static final String NAME = "mite_structure_exploration";
    public static final Factory<StructureExplorationData> FACTORY =
            new Factory<>(StructureExplorationData::new, StructureExplorationData::load);
    private final Map<UUID, EnumMap<Metric, Long>> players = new HashMap<>();
    private Map<Metric, Long> snapshot = Map.of();
    private ServerLevel owner;

    public static StructureExplorationData get(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Structure exploration must be loaded on the server thread");
        ServerLevel overworld = level.getServer().overworld();
        StructureExplorationData data = overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
        data.owner = overworld;
        return data;
    }

    public Map<Metric, Long> snapshot() { return snapshot; }
    public long value(UUID player, Metric metric) {
        var values = players.get(player);
        return values == null ? 0L : values.getOrDefault(metric, 0L);
    }

    public void add(UUID player, Metric metric, long positiveDelta) {
        if (positiveDelta <= 0L) return;
        long current = value(player, metric);
        observe(player, metric, current + Math.min(positiveDelta, metric.cap() - current));
    }

    /** Completed native statistics can backfill old saves without counting the same event twice. */
    public void observe(UUID player, Metric metric, long cumulativeTotal) {
        long next = metric.clamp(cumulativeTotal);
        if (next <= value(player, metric)) return;
        players.computeIfAbsent(player, ignored -> new EnumMap<>(Metric.class)).put(metric, next);
        if (next > snapshot.getOrDefault(metric, 0L)) {
            var maxima = new EnumMap<Metric, Long>(Metric.class);
            maxima.putAll(snapshot);
            maxima.put(metric, next);
            snapshot = Map.copyOf(maxima);
        }
        setDirty();
        if (owner != null) R196StructureGeneration.refresh(owner);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        ListTag records = new ListTag();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag record = new CompoundTag();
            record.putUUID("player", entry.getKey());
            CompoundTag values = new CompoundTag();
            entry.getValue().forEach((metric, total) -> values.putLong(metric.key(), total));
            record.put("totals", values);
            records.add(record);
        });
        tag.put("players", records);
        return tag;
    }

    public static StructureExplorationData load(CompoundTag tag, HolderLookup.Provider registries) {
        StructureExplorationData data = new StructureExplorationData();
        data.read(tag);
        return data;
    }

    /** Explicit restoration supports migrations and isolated tests; gameplay uses add/observe. */
    public void restore(CompoundTag tag) {
        players.clear();
        read(tag);
        setDirty();
        if (owner != null) R196StructureGeneration.refresh(owner);
    }

    private void read(CompoundTag tag) {
        var maxima = new EnumMap<Metric, Long>(Metric.class);
        ListTag records = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = records.getCompound(i);
            if (!record.hasUUID("player")) continue;
            UUID id = record.getUUID("player");
            CompoundTag values = record.getCompound("totals");
            var totals = players.computeIfAbsent(id, ignored -> new EnumMap<>(Metric.class));
            for (Metric metric : Metric.values()) {
                long total = metric.clamp(values.getLong(metric.key()));
                if (total > 0L) {
                    totals.merge(metric, total, Math::max);
                    maxima.merge(metric, total, Math::max);
                }
            }
            if (totals.isEmpty()) players.remove(id);
        }
        snapshot = Map.copyOf(maxima);
    }
}
