package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.HashSet;
import java.util.Map;

/** Explicit source behavior for legacy tests, without writing or replacing user configuration. @author NingMeng123 */
final class SourceConfigFixture implements AutoCloseable {
    private final MiteConfig.Snapshot previous = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot()
            ? null : MiteConfig.effectiveSnapshot();
    SourceConfigFixture(boolean neutralSpawning, boolean enableBindingCurse) {
        var source = MiteConfig.effectiveSnapshot();
        var disabled = new HashSet<>(source.disabled());
        if (enableBindingCurse) disabled.remove("minecraft:binding_curse");
        MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(source.ores(), source.growth(), source.healthGrowth(), source.damageGrowth(),
                source.reachMultiplier(), source.reachReduction(), source.heightPenalty(), source.underworldTrials(), source.overworldTrials(),
                source.caps(), disabled, source.monsterSettings(), source.oreCandidates(), source.oreChances(),
                source.overworldOreCandidates(), source.overworldOreChances(), source.overworldOres(), source.enchantmentChances(),
                neutralSpawning ? Map.of() : source.monsterSpawnRates(), source.oreHeights(), source.overworldOreHeights(),
                source.spawningSettings(), neutralSpawning ? Map.of() : source.monsterSpawnWeights(), source.betterCombatMobs()));
    }
    @Override public void close() {
        MiteConfig.clearServerSnapshot();
        if (previous != null) MiteConfig.receiveServerSnapshot(previous);
    }
}
