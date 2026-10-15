package io.github.ningmeng123.mite.worldgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Transfers each original block's provenance through native piston movement. @author NingMeng123 */
public final class TrialBlockMovement {
    private TrialBlockMovement() {}

    /** Snapshot before any source or overlapping destination is replaced by a moving piston. */
    public static Set<BlockPos> naturalDestinations(Level level, List<BlockPos> sources, Direction movement) {
        if (level.isClientSide) return Set.of();
        Set<BlockPos> destinations = new HashSet<>();
        for (BlockPos source : sources) {
            if (NaturalTrialBlocks.isNatural(level, source)) destinations.add(source.relative(movement));
        }
        return destinations;
    }

    /** Source cleanup and final neighbor updates still use the unchanged native piston algorithm. */
    public static void recordMovingDestination(Level level, BlockPos pos, Set<BlockPos> destinations) {
        if (!level.isClientSide && destinations != null && destinations.contains(pos)
                && level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
            NaturalTrialBlocks.record(level.getChunkAt(pos), pos);
        }
    }
}
