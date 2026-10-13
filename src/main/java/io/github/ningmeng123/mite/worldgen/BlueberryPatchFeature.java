package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.block.BlueberryBushBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Source 64-attempt patches with a modern surface origin and original early stopping. @author NingMeng123 */
public final class BlueberryPatchFeature extends Feature<NoneFeatureConfiguration> {
    public BlueberryPatchFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int placements = 0;
        var level = context.level();
        var random = context.random();
        var state = MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3);
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = context.origin().getX() + random.nextInt(8) - random.nextInt(8);
            int z = context.origin().getZ() + random.nextInt(8) - random.nextInt(8);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!canPlaceDuringGeneration(level, pos, state)) continue;
            level.setBlock(pos, state, 2);
            if (++placements > 3 && random.nextInt(3) == 0) break;
        }
        return placements > 0;
    }

    /** FEATURES precedes INITIALIZE_LIGHT; read the actual column instead of the sky-light cache. */
    public static boolean canPlaceDuringGeneration(WorldGenLevel level, BlockPos pos, BlockState bush) {
        if (!level.isEmptyBlock(pos) || !level.dimensionType().hasSkyLight()
                || !level.getBiome(pos).is(BlueberryBushBlock.HABITAT)) return false;
        BlockPos soilPos = pos.below();
        BlockState soil = level.getBlockState(soilPos);
        if (soil.is(Blocks.FARMLAND)) return false;
        // Preserve native BushBlock's NeoForge soil decision and fallback, excluding only its
        // runtime light-dependent caller. A modded soil's explicit TRUE/FALSE still takes priority.
        var soilDecision = soil.canSustainPlant(level, soilPos, Direction.UP, bush);
        if (soilDecision.isFalse() || soilDecision.isDefault()
                && !soil.is(BlockTags.DIRT) && !(soil.getBlock() instanceof FarmBlock)) return false;
        int top = Math.min(level.getMaxBuildHeight(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()));
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int y = pos.getY(); y < top; y++) {
            cursor.setY(y);
            if (level.getBlockState(cursor).getLightBlock(level, cursor) > 0) return false;
        }
        return true;
    }
}
