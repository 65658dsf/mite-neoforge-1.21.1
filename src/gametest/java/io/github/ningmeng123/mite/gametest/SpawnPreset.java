package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import java.util.HashMap;

/** Synchronous fixture: exercise either preset without changing any configuration file. @author NingMeng123 */
final class SpawnPreset implements AutoCloseable {
    private final MiteConfig.Snapshot previous = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot()
            ? null : MiteConfig.effectiveSnapshot();

    SpawnPreset(boolean reference) {
        var source = MiteConfig.effectiveSnapshot();
        var values = new HashMap<>(source.spawningSettings().values());
        values.put(MonsterSpawningSettings.Option.REFERENCE_RULES.key(), reference ? 1D : 0D);
        MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(source.ores(), source.growth(), source.healthGrowth(), source.damageGrowth(),
                source.reachMultiplier(), source.reachReduction(), source.heightPenalty(), source.underworldTrials(), source.overworldTrials(),
                source.caps(), source.disabled(), source.monsterSettings(), source.oreCandidates(), source.oreChances(),
                source.overworldOreCandidates(), source.overworldOreChances(), source.overworldOres(), source.enchantmentChances(),
                source.monsterSpawnRates(), source.oreHeights(), source.overworldOreHeights(),
                MonsterSpawningSettings.Settings.from(values), source.monsterSpawnWeights()));
    }

    @Override public void close() {
        MiteConfig.clearServerSnapshot();
        if (previous != null) MiteConfig.receiveServerSnapshot(previous);
    }
}
