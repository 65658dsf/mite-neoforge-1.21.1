package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import java.util.Locale;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Explicit dimension avoids relying on a biome's name to choose progression. @author NingMeng123 */
public record OreFeatureConfiguration(Dimension dimension) implements FeatureConfiguration {
    public static final Codec<OreFeatureConfiguration> CODEC = Codec.STRING
            .xmap(value -> new OreFeatureConfiguration(Dimension.valueOf(value.toUpperCase(Locale.ROOT))),
                    config -> config.dimension().name().toLowerCase(Locale.ROOT))
            .fieldOf("dimension").codec();
}
