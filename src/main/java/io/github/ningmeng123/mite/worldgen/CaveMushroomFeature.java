package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.survival.FungalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** R196 brown mushroom patches from the overworld biome decorator. @author NingMeng123 */
public final class CaveMushroomFeature extends Feature<NoneFeatureConfiguration> {
    public CaveMushroomFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        var chunk = new ChunkPos(context.origin());
        var random = context.random();
        boolean placed = false;
        for (int round = 0; round < 4; round++) {
            if (random.nextInt(4) == 0) placed |= placePatch(level, chunk, random, 48, 32);
            if (random.nextInt(4) == 0) placed |= placePatch(level, chunk, random, 0, 128);
        }
        return placed;
    }

    private static boolean placePatch(WorldGenLevel level, ChunkPos chunk, RandomSource random, int minY, int height) {
        int centerX = chunk.getMinBlockX() + 8 + random.nextInt(16);
        int centerY = minY + random.nextInt(height);
        int centerZ = chunk.getMinBlockZ() + 8 + random.nextInt(16);
        var mushroom = Blocks.BROWN_MUSHROOM.defaultBlockState();
        var pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int attempt = 0; attempt < 64; attempt++) {
            int y = centerY + random.nextInt(4) - random.nextInt(4);
            if (y < 0 || level.isOutsideBuildHeight(y)) continue;
            int x = centerX + random.nextInt(8) - random.nextInt(8);
            int z = centerZ + random.nextInt(8) - random.nextInt(8);
            pos.set(x, y, z);
            if (level.isEmptyBlock(pos) && FungalGrowth.brownMushroomSoil(level.getBlockState(pos.below()))
                    && !R196OutdoorChecks.outdoorsDuringGeneration(level, pos) && !hasAdjacentBrightSource(level, pos)) {
                placed |= level.setBlock(pos, mushroom, 2);
            }
        }
        return placed;
    }

    private static boolean hasAdjacentBrightSource(WorldGenLevel level, BlockPos pos) {
        // Feature decoration precedes lighting. Block light above 13 in this air cell can only come
        // from an adjacent level-15 source because light falls by at least one per block.
        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            if (level.getBlockState(neighbor).getLightEmission(level, neighbor) == 15) return true;
        }
        return false;
    }
}
