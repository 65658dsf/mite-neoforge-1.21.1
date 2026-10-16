package io.github.ningmeng123.mite.ranged;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A skeleton's arrow ignores foliage while retaining the native trace through all other obstacles. @author NingMeng123 */
public final class SkeletonArrowLeaves {
    private SkeletonArrowLeaves() {}

    /** Kept in synced entity data because a client may track the arrow without tracking its shooter. */
    public interface Origin {
        boolean mite$firedBySkeleton();
    }

    public static boolean ignores(AbstractArrow arrow, BlockState state) {
        return ((Origin)arrow).mite$firedBySkeleton() && state.is(BlockTags.LEAVES);
    }

    public static ClipContext filter(AbstractArrow arrow, ClipContext original) {
        if (!((Origin)arrow).mite$firedBySkeleton()) return original;
        return new ClipContext(original.getFrom(), original.getTo(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, arrow) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
                return state.is(BlockTags.LEAVES) ? Shapes.empty() : original.getBlockShape(state, level, pos);
            }
            @Override public VoxelShape getFluidShape(FluidState state, BlockGetter level, BlockPos pos) {
                return original.getFluidShape(state, level, pos);
            }
        };
    }
}
