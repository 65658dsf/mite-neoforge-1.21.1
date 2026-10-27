package io.github.ningmeng123.mite.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;

/** One native search's loaded-chunk and block-state snapshot. @author NingMeng123 */
final class LoadedPathNavigationRegion extends PathNavigationRegion {
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();

    LoadedPathNavigationRegion(Level level, BlockPos minimum, BlockPos maximum) {
        // The native constructor populates chunks exclusively with getChunkNow; it never loads terrain.
        super(level, minimum, maximum);
    }

    @Override public BlockState getBlockState(BlockPos position) {
        if (isOutsideBuildHeight(position)) return Blocks.BEDROCK.defaultBlockState();
        int x = SectionPos.blockToSectionCoord(position.getX()) - centerX;
        int z = SectionPos.blockToSectionCoord(position.getZ()) - centerZ;
        if (x < 0 || x >= chunks.length || z < 0 || z >= chunks[x].length)
            return Blocks.BEDROCK.defaultBlockState();
        ChunkAccess chunk = chunks[x][z];
        if (chunk == null) return Blocks.BEDROCK.defaultBlockState();
        long key = position.asLong();
        BlockState state = states.get(key);
        if (state == null) {
            state = chunk.getBlockState(position);
            states.put(key, state);
        }
        return state;
    }

    @Override public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }
}
