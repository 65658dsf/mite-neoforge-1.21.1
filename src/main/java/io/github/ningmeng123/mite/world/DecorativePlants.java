package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.rules.DecorativeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biomes;

/** Source temperature boundaries applied to modern biome identities. @author NingMeng123 */
public final class DecorativePlants {
    private DecorativePlants() {}
    public static boolean canFlowerSurvive(int subtype, LevelReader level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return DecorativeRules.flowerBiome(subtype, biome.value().getBaseTemperature(),
                biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP), biome.is(BiomeTags.IS_JUNGLE))
                && (level.canSeeSky(pos) || level.getMaxLocalRawBrightness(pos) >= 8);
    }
}
