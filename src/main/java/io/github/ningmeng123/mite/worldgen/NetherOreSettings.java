package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;

/** One snapshot supplies both controls for a Nether ore. @author NingMeng123 */
record NetherOreSettings(double multiplier, OreHeightRange height) {
    static NetherOreSettings read(MiteConfig.Snapshot snapshot, Kind kind) {
        String height = snapshot.netherOreHeights().get(kind.name());
        return new NetherOreSettings(snapshot.netherOres().getOrDefault(kind.name(), 1D),
                height == null ? null : OreHeightRange.parse(height));
    }
}
