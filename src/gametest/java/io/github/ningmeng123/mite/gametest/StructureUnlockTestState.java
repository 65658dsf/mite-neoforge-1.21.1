package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureExplorationData;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Restore the complete per-player exploration save after an isolated generation fixture. @author NingMeng123 */
final class StructureUnlockTestState implements AutoCloseable {
    static final long DAY_32 = 738_000L, DAY_60 = 1_410_000L;
    final ServerLevel level;
    final StructureExplorationData exploration;
    private final StructureProgressData progress;
    private final CompoundTag saved;
    private final long time;
    private final int flags;
    private final boolean explorationDirty, progressDirty;
    private final UUID player = UUID.randomUUID();

    StructureUnlockTestState(ServerLevel level) {
        this.level = level.getServer().overworld();
        exploration = StructureExplorationData.get(this.level);
        progress = StructureProgressData.get(this.level);
        saved = exploration.save(new CompoundTag(), this.level.registryAccess());
        time = this.level.getDayTime(); flags = progress.conditions();
        explorationDirty = exploration.isDirty(); progressDirty = progress.isDirty();
        values(Map.of()); at(0L, 0);
    }

    void at(long time, int flags) {
        level.setDayTime(time); progress.restoreConditions(flags); R196StructureGeneration.refresh(level);
    }

    void values(Map<Metric, Long> values) {
        exploration.restore(new CompoundTag());
        values.forEach((metric, value) -> exploration.observe(player, metric, value));
        R196StructureGeneration.refresh(level);
    }

    void complete() {
        java.util.EnumMap<Metric, Long> values = new java.util.EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) values.put(metric, metric.cap());
        values(values); at(DAY_60, StructureProgressData.ALL_CONDITIONS);
    }

    @Override public void close() {
        exploration.restore(saved); exploration.setDirty(explorationDirty);
        progress.restoreConditions(flags); progress.setDirty(progressDirty);
        level.setDayTime(time); R196StructureGeneration.refresh(level);
    }
}
